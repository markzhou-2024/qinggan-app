# 青甘随行 Task 1 — Execution State & Family Sync Design

**状态：** 待用户书面审阅确认  
**日期：** 2026-08-11  
**依赖：** Task 0 Production Runtime Foundation  
**适用范围：** iOS 原生 App + Spring Boot + MySQL  
**目标体验：** 家庭多台 iPhone 在旅途中对同一行程执行状态实现几秒到十几秒内自动同步，同时支持弱网和离线继续操作。

---

## 1. 目标

Task 1 不做地图、天气、照片和完整五栏 UI，而是先把“计划中的青甘行程”升级成“可以被一家人共同执行的实时旅途状态”。

完成后，任意一台 iPhone 都可以：

- 开始本次旅行；
- 标记当前节点已到达；
- 标记节点已完成；
- 跳过可选节点；
- 立即计算新的下一站；
- 在弱网时先本地保存；
- 恢复网络后自动同步；
- 在其他家庭成员手机上约 10 秒内看到状态变化；
- App 回到前台时立即拉取最新状态。

Task 1 的核心原则：

> 计划数据描述“应该怎么走”，执行数据描述“现在已经走到哪里”。两者分离。

---

## 2. 明确不做

Task 1 不包含：

- WebSocket；
- SSE；
- 后台持续网络连接；
- 后台定位；
- 家庭成员位置共享；
- 用户登录注册；
- 用户角色和复杂权限体系；
- 行程自由编辑；
- 天气；
- MapKit；
- 照片打卡；
- 推送通知。

这些能力不得混入本 Task 扩大范围。

---

## 3. 同步方案选择

V1 采用：

**前台 10 秒轻量轮询 + 写后立即刷新 + 回前台立即刷新。**

不使用 WebSocket。

### 3.1 前台轮询

当 App 位于 active 状态且 Trip 已加载：

- 每 10 秒请求一次最新 execution snapshot；
- App 进入后台时停止轮询；
- App 回到前台时立即 refresh；
- 网络不可用时不高频重试，保留 pending actions；
- 网络恢复后优先 flush pending，再 pull 最新状态。

### 3.2 写后刷新

任何 mutation 成功后：

1. 服务器返回新的 revision 和 execution snapshot；
2. 当前设备立即使用服务器返回值更新；
3. 不等待下一个 10 秒轮询。

其他设备下一轮轮询时同步到同样状态。

### 3.3 预期用户体验

手机 A：

```text
完成“茶卡盐湖”
  ↓
本机立即显示完成
  ↓
POST mutation
  ↓
Server revision +1
```

手机 B/C：

```text
前台 10 秒 polling
  ↓
GET execution snapshot
  ↓
看到“茶卡盐湖 已完成”
```

正常网络下，跨设备可见延迟目标为 0–10 秒级。

---

## 4. 权限模型

V1 不做账号体系。

同一家庭 Trip 使用一个 `family-trip-token`，存储在 iOS Keychain 中。

所有持有该 Trip token 的设备拥有同等执行权限：

- read trip；
- read execution snapshot；
- start trip；
- arrive stop；
- complete stop；
- skip optional stop。

Token 不允许：

- 出现在源码；
- 出现在 fixture；
- 出现在 Git；
- 通过 URL query 参数传输；
- 写入日志明文。

请求统一：

```http
Authorization: Bearer <family-trip-token>
```

Trip 内容 GET 是否继续允许无 token 可由后端保持当前兼容行为，但所有 execution mutation API 必须要求 token。

---

## 5. 数据边界

### 5.1 Trip Plan

现有模型继续负责计划事实：

- Trip；
- TripDay；
- TripStop；
- Stay；
- NavigationPoint；
- plannedStartDate；
- actualStartDate；
- Trip lifecycle。

UI 不修改这些解码对象来表达用户执行动作。

### 5.2 Trip Execution

新增独立 execution state。

建议服务端概念：

```text
TripExecution
  tripId
  revision
  status
  actualStartDate
  updatedAt
  stopStates[]
```

每个 stop state：

```text
StopExecution
  stopId
  status
  updatedAt
  updatedByDeviceId
```

支持状态：

- planned
- arrived
- completed
- skipped
- moved（只兼容计划变更语义，不由普通用户直接设置）

---

## 6. 状态机

### 6.1 Trip lifecycle

正式冻结为：

```text
PLANNING
   ↓ start trip
STARTED
   ↓ finish trip
COMPLETED
```

`CANCELLED` 如后端保留，作为管理状态存在，但不属于旅途 UI 主流程。

`PLANNED / ACTIVE` 仅作为历史兼容输入，不再作为新写入状态。

### 6.2 Stop execution

允许的用户转换：

```text
planned → arrived
planned → completed
planned → skipped 仅 optional
arrived → completed
arrived → skipped 仅 optional
```

不允许：

```text
completed → planned
skipped → planned
required stop → skipped
用户直接设置 moved
```

completed 和 skipped 都算 resolved；只有 completed 算 completed。

起点不进入任务分母。

`moved` 不作为 nextStop。

---

## 7. 开始旅行

旅行开始不是由某一台手机本地修改日期，而是明确服务器 mutation。

建议接口：

```http
POST /api/trips/{tripId}/execution/start
```

Body：

```json
{
  "requestId": "uuid",
  "deviceId": "stable-device-id",
  "expectedRevision": 12,
  "startedAt": "2026-08-13T07:38:00+08:00"
}
```

服务器行为：

- 校验 Bearer token；
- 若仍是 PLANNING：写 `actualStartDate`；
- status → STARTED；
- revision +1；
- 返回完整 execution snapshot；
- 相同 `requestId` 重复请求必须幂等返回同一业务结果。

若另一台设备已经启动 Trip：

- 不创建第二个 actualStartDate；
- 返回服务器当前 authoritative state。

Day1–Day10 日期继续由现有 effectiveStartDate 领域逻辑计算。

---

## 8. Stop Mutation API

不允许客户端上传整份 Trip JSON。

建议统一 mutation：

```http
POST /api/trips/{tripId}/execution/stops/{stopId}/actions
```

Body：

```json
{
  "requestId": "uuid",
  "deviceId": "stable-device-id",
  "action": "COMPLETE",
  "expectedRevision": 21,
  "occurredAt": "2026-08-15T11:26:03+08:00"
}
```

`action`：

- ARRIVE
- COMPLETE
- SKIP

成功响应：

```json
{
  "schemaVersion": "1.0",
  "tripId": "qinggan-2026-family",
  "revision": 22,
  "status": "STARTED",
  "actualStartDate": "2026-08-13",
  "updatedAt": "2026-08-15T11:26:04+08:00",
  "stopStates": []
}
```

服务器负责：

- stop 是否存在；
- stop 是否 optional；
- 状态转换是否合法；
- requestId 幂等；
- revision 并发控制；
- authoritative timestamp。

---

## 9. Revision 与并发冲突

每个 Trip execution 维护单调递增 `revision`。

客户端 mutation 带 `expectedRevision`。

### 9.1 Revision 匹配

正常写入：

```text
expectedRevision == server revision
→ apply
→ revision + 1
→ return latest snapshot
```

### 9.2 Revision 不匹配

返回 HTTP 409，并附最新 execution snapshot。

客户端处理：

1. 接收服务器最新状态；
2. 重新判断本地 pending action 是否仍有意义；
3. 若动作已经被服务器状态覆盖为同等或更终态，视为 resolved；
4. 若仍合法，使用最新 revision 自动重试一次；
5. 再冲突则停止自动循环，保留 pending 并显示同步异常。

禁止无限重试。

---

## 10. 冲突合并规则

执行状态使用“不可随意回退”的单调原则。

普通状态优先级：

```text
completed
   >
skipped
   >
arrived
   >
planned
```

但这不是让客户端本地直接强行合并数据库，而是用于判断：

- 旧动作是否已经过时；
- pull 后是否还应重试 pending action。

例如：

手机 A 离线时记录 COMPLETE；手机 B 已经把同一 optional stop SKIP。

A 恢复网络后：

- pull 得到 SKIPPED；
- COMPLETE 是更终态且转换仍合法；
- A 可基于最新 revision 重试 COMPLETE；
- 最终服务器变为 COMPLETED。

反方向：服务器已经 COMPLETED，而旧设备提交 SKIP：

- 不允许把 completed 回退为 skipped；
- 旧 SKIP 标记为 resolved/no-op。

`moved` 使用独立规则，不参与上述普通优先级。

---

## 11. 幂等

每一次本地用户动作生成 UUID `requestId`。

服务器需要持久化近期 mutation request ID 或使用唯一约束确保：

```text
相同 tripId + requestId
```

只产生一次状态变化和一次 revision 增量。

原因：

- iPhone 网络超时后不知道服务端是否已成功；
- App 会重试 pending；
- 多次发送同一请求不能重复推进 revision。

---

## 12. iOS Local-first 设计

客户端新增：

```text
TripExecutionStore
TripExecutionRepository
TripExecutionSyncService
ExecutionRemoteClient
```

### 12.1 本地事实

本机保存：

- 最近一次 server execution snapshot；
- pending actions；
- lastSyncedAt；
- serverRevision；
- stable deviceId。

### 12.2 用户操作流程

用户点击完成：

```text
validate locally
  ↓
create PendingExecutionAction(requestId)
  ↓
persist pending first
  ↓
optimistic UI projection
  ↓
try push
```

必须先成功写本地 pending，才能把 UI 长期显示为“已提交动作”。

如果本地持久化失败：

- 不得静默显示完成；
- 显示“本地保存失败，请重试”。

### 12.3 Pending 状态 UI

区分：

- 已同步；
- 待同步；
- 同步异常。

不要把 pending 伪装成已经得到服务器确认。

---

## 13. Polling 生命周期

新增前台 sync coordinator。

规则：

```text
scenePhase active
→ immediate refresh
→ start 10s polling

scenePhase inactive/background
→ stop polling

network restored
→ flush pending
→ refresh
```

同一时刻最多允许一个 refresh request 在飞行。

如果上一轮请求还未结束，下一轮 tick 不叠加请求。

建议：

- 成功：下一轮保持 10 秒；
- 暂时网络失败：不改变用户本地数据；
- 连续失败：轮询仍以低频继续，但错误信息节流，不反复弹 Toast。

---

## 14. 网络恢复顺序

网络重新可用：

1. pull server snapshot；
2. merge / re-evaluate pending against latest revision；
3. flush pending actions in local creation order；
4. 每个成功 mutation 使用服务器返回 revision 更新后续动作；
5. flush 结束后再 pull 一次；
6. 更新 `lastSyncedAt`。

这样避免离线多动作拿旧 revision 连续碰撞。

---

## 15. Device ID

V1 每次安装生成一个随机 UUID，持久化在 Keychain 或稳定本地安全存储：

```text
deviceId = UUID
```

用途：

- 审计 mutation 来源；
- 排查多机冲突；
- 后续如果需要吊销单设备 Token，可扩展。

不使用 IDFA，不需要广告标识，不采集设备硬件身份。

---

## 16. Execution Snapshot GET

建议：

```http
GET /api/trips/{tripId}/execution
Authorization: Bearer <token>
```

支持：

```http
If-None-Match: "execution-revision-22"
```

或简单使用 query / header revision 优化：

```text
clientRevision=22
```

V1 可以先返回完整小型 snapshot；Trip 只有几十个 stop，负载极低，不需要过早做差量同步。

服务器可返回 ETag，以后 10 秒轮询多数情况直接 304，进一步降低流量。

---

## 17. Today 与 nextStop

`TodayViewModel` 继续负责：

- preTrip；
- ready；
- postTrip；
- 日期和 Day 解析。

`TripExecutionStore` 负责：

- stop effective status；
- nextStop；
- completed / resolved progress；
- sync state。

Today UI 不再只看 `TripStop.status`。

有效状态计算：

```text
server execution override
  ?? plan stop status
```

nextStop 必须由共享 execution domain 计算，不能让每个 View 自己筛数组。

---

## 18. 错误处理

### 18.1 401 / 403

显示：

> 家庭行程授权已失效，请重新导入行程授权。

停止 mutation 自动重试。

### 18.2 409

执行 revision 冲突流程，不直接提示用户失败。

只有自动 reconcile 失败时才显示：

> 行程状态刚刚在另一台手机上更新，请刷新后重试。

### 18.3 5xx / timeout

动作留在 pending，不回滚本地用户意图。

显示轻量：

> 待同步

### 18.4 本地存储失败

属于阻断错误：

> 本机无法保存本次操作，请重试。

不进入 pending 假状态。

---

## 19. 安全边界

- execution 写接口必须 HTTPS；
- token 只放 Authorization header；
- Server 日志不得打印完整 token；
- 失败日志最多输出 token hash / suffix；
- MySQL 不暴露公网；
- Nginx 只代理明确 API；
- mutation 做 trip scope 校验；
- 服务端不信任客户端传入 stop optional / current status；
- 权限和合法状态全部依据数据库事实重新校验。

---

## 20. 数据库建议

新增执行表，不修改 itinerary seed 表作为运行时状态源。

建议：

```text
trip_execution
- trip_id PK/FK
- status
- actual_start_date
- revision
- updated_at

trip_stop_execution
- trip_id
- stop_id
- status
- updated_at
- updated_by_device_id
- PRIMARY KEY(trip_id, stop_id)

trip_execution_action
- trip_id
- request_id
- device_id
- action_type
- stop_id nullable
- created_at
- applied_revision
- PRIMARY KEY(trip_id, request_id)
```

`trip_execution_action` 既用于幂等，也保留基础审计证据。

不需要 V1 建复杂 event sourcing。

---

## 21. 迁移与兼容

现有 Trip API 不直接被 execution mutation 修改格式。

建议保持：

```text
GET itinerary
GET execution
```

两个资源分开。

客户端 ready 后：

```text
load itinerary/cache
  +
load execution/cache
  ↓
compose Today UI
```

旧客户端只读取 itinerary 时仍可工作，不因 Task 1 破坏现有接口。

---

## 22. 测试设计

### 22.1 Backend

必须覆盖：

- start trip 成功；
- start 幂等；
- COMPLETE 成功；
- optional SKIP 成功；
- required SKIP 拒绝；
- completed 不回退；
- requestId 幂等；
- stale revision 返回 409 + latest snapshot；
- token 缺失拒绝；
- token 错误拒绝；
- 两设备并发 mutation；
- revision 单调递增。

### 22.2 QingGanCore

覆盖：

- execution override 高于 plan status；
- nextStop；
- completed / resolved；
- moved 排除；
- origin 排除；
- pending optimistic projection；
- server completed 覆盖旧 pending skip。

### 22.3 QingGanAppTests

覆盖：

- pending action 持久化 / reload；
- App restart 后 pending 不丢；
- push success 清 pending；
- push timeout 保留 pending；
- 409 reconcile；
- foreground immediate refresh；
- polling 不并发重叠；
- background 停止 polling；
- token 从 Keychain 注入 header。

---

## 23. 验收场景

### 场景 A：正常双机同步

设备 A、B 打开同一 Trip。

A 完成 stop。

验收：

- A 立即显示完成；
- Server revision +1；
- B 在不手工刷新的情况下 10 秒级自动显示完成；
- A/B nextStop 一致。

### 场景 B：离线完成

A 断网完成 stop。

验收：

- A 显示待同步；
- App 被杀后重开，pending 仍存在；
- 恢复网络后自动同步；
- B 最终自动看到相同结果。

### 场景 C：同时操作

A 完成，B 几乎同时跳过同一 optional stop。

验收：

- 一个请求正常成功；
- 另一个收到 revision 冲突；
- 客户端自动 reconcile；
- completed 不被旧 skip 回退；
- UI 最终一致。

### 场景 D：Token 无效

验收：

- read/write execution 返回未授权；
- App 停止自动 mutation retry；
- 明确提示重新导入家庭行程授权；
- token 不出现在日志。

---

## 24. 完成定义

Task 1 只有同时满足以下条件才算 COMPLETE：

- Backend execution schema 和 Flyway migration 完成；
- start trip mutation 可用；
- stop ARRIVE / COMPLETE / SKIP mutation 可用；
- Bearer family token 鉴权可用；
- requestId 幂等可验证；
- revision 乐观锁可验证；
- iOS local-first pending 持久化可验证；
- App 前台 10 秒 polling；
- 回前台立即刷新；
- 网络恢复 flush pending；
- 双设备模拟场景状态最终一致；
- Core tests 全绿；
- App tests 全绿；
- Backend tests 全绿；
- Release build 不受此功能破坏；
- Task 0 Production Gate 仍保持绿色。

---

## 25. 后续 Task 边界

Task 1 完成后，后续顺序建议为：

```text
Task 2  SwiftUI 五栏 App Shell + Today/行程执行 UI
Task 3  MapKit + CoreLocation + 高德导航
Task 4  真实天气
Task 5  照片打卡与照片同步
Task 6  真实旅途综合验收
```

Task 1 不提前实现这些 UI/系统能力，只提供稳定的执行事实与同步底座。

---

## 26. 设计结论

采用 10 秒前台轮询不是技术妥协，而是针对本项目规模的主动选择。

本次旅行只有一个家庭、一个 Trip、少量设备和几十个执行节点。与 WebSocket 相比，该方案：

- 实现更简单；
- 弱网恢复更可靠；
- iOS 生命周期处理更清晰；
- 服务端维护成本更低；
- 已能达到“几秒到十几秒自动看到家人操作”的体验目标。

只有未来出现大量实时协作用户、秒级强实时要求或服务端主动事件后，再评估 SSE / WebSocket。
