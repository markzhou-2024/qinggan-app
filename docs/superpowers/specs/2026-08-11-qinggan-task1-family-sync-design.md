# 青甘随行 Task 1 — Execution State & Family Sync Design

**状态：** 待用户书面审阅确认  
**日期：** 2026-08-11  
**依赖：** Task 0 Production Runtime Foundation  
**适用范围：** iOS 原生 App + Spring Boot + MySQL  
**目标体验：** 家庭多台 iPhone 对同一趟青甘行程的执行状态，在正常网络下 0–10 秒级自动同步，同时弱网/离线仍可继续操作。

---

## 1. 目标

Task 1 不做地图、天气、照片和完整五栏 UI，而是先把“计划中的青甘行程”升级成“可以被一家人共同执行的旅途状态”。

完成后，任意一台已授权 iPhone 都可以：

- 开始本次旅行；
- 标记节点已到达；
- 标记节点已完成；
- 跳过可选节点；
- 自动得到新的下一站；
- 弱网时先在本机保存动作；
- 恢复网络后自动同步；
- 其他家庭成员手机在前台时约 10 秒内看到变化；
- App 回到前台时立即刷新。

核心原则：

> 计划数据描述“应该怎么走”，执行数据描述“现在走到哪里”。两者必须分离。

---

## 2. 明确不做

Task 1 不包含：

- WebSocket / SSE；
- 后台持续连接；
- 后台定位和家庭位置共享；
- 用户注册登录和角色体系；
- 行程自由编辑；
- MapKit、天气、照片打卡；
- 推送通知；
- Token 分享二维码 / 导入 UI。

Token 的用户侧导入/分享体验放到后续 App Shell 任务；Task 1 只冻结鉴权协议、Keychain 接入与测试注入方式。

---

## 3. 同步方案

V1 采用：

**前台 10 秒轻量轮询 + 写后立即采用服务端结果 + 回前台立即刷新。**

不使用 WebSocket。

### 3.1 前台轮询

当 App 处于 active 且 Trip 已加载：

- 每 10 秒请求一次 execution snapshot；
- App 进入后台立即停止轮询；
- App 回前台立即 refresh，不等待下一次 tick；
- 同一时刻最多一个 refresh request 在飞行；
- 上一轮未结束时跳过下一次 tick；
- 网络失败不清除本地状态，不高频重试。

正常网络下，跨设备可见延迟目标为 0–10 秒级。

### 3.2 写后刷新

任何 mutation 成功后，服务端直接返回新的 execution snapshot 和 revision；当前设备立即采用该响应，不再额外等待下一次 polling。

### 3.3 网络恢复顺序

统一冻结为：

```text
网络恢复
  ↓
先 pull 最新 server snapshot
  ↓
re-evaluate 本地 pending actions
  ↓
按创建顺序 flush pending
  ↓
每次成功采用新的 server revision
  ↓
全部结束后再 pull 一次
```

这样可避免离线设备携带旧 revision 直接连续碰撞。

---

## 4. 权限模型

V1 不做账号体系。

同一 Trip 使用一个 `family-trip-token`，iOS 使用现有 Keychain 能力保存。

所有持有该 Trip token 的设备拥有同等执行权限：

- read execution；
- start trip；
- arrive stop；
- complete stop；
- skip optional stop。

请求统一：

```http
Authorization: Bearer <family-trip-token>
```

Token 不允许：

- 出现在源码、fixture、Git；
- 放在 URL query；
- 写入明文日志。

所有 execution mutation API 必须要求 token。现有 itinerary GET 是否保持匿名只读兼容，不在本 Task 强制改变。

---

## 5. 数据边界

### 5.1 Trip Plan

现有 `Trip / TripDay / TripStop / Stay / NavigationPoint` 继续负责计划事实。

SwiftUI 和 execution store 不通过修改解码后的 Trip 对象表达用户动作。

### 5.2 Trip Execution

新增独立执行事实：

```text
TripExecution
  tripId
  revision
  status
  actualStartDate
  updatedAt
  stopStates[]
```

每个 stop execution：

```text
StopExecution
  stopId
  status
  updatedAt
  updatedByDeviceId
```

Stop status：

- planned
- arrived
- completed
- skipped
- moved（仅兼容计划调整语义，普通用户不能直接设置）

---

## 6. 状态机

### 6.1 Trip lifecycle

新写入状态冻结为：

```text
PLANNING
   ↓ start
STARTED
   ↓ finish
COMPLETED
```

`CANCELLED` 可以作为管理状态保留，但不进入旅途主 UI。

历史 `PLANNED / ACTIVE` 只允许作为兼容输入，不再作为 Task 1 新写入值。

### 6.2 Stop execution

允许：

```text
planned → arrived
planned → completed
planned → skipped   仅 optional
arrived → completed
arrived → skipped   仅 optional
skipped → completed 仅 optional，表示后来实际去了
```

禁止：

```text
completed → planned
completed → skipped
skipped → planned
required → skipped
用户直接设置 moved
```

规则：

- completed 与 skipped 都算 resolved；
- 只有 completed 算 completed；
- origin 不计任务分母；
- moved 不作为 nextStop。

---

## 7. 开始旅行与日期权威源

开始旅行必须是服务端 mutation：

```http
POST /api/trips/{tripId}/execution/start
```

客户端请求：

```json
{
  "requestId": "uuid",
  "deviceId": "stable-device-id",
  "expectedRevision": 12,
  "occurredAt": "2026-08-13T07:38:00+08:00"
}
```

关键规则：

- `occurredAt` 只用于客户端审计和排障，不作为 `actualStartDate` 权威值；
- 服务端以 mutation 实际生效时间为准；
- 服务端按 Trip 的 `timeZone` 将该时间解析成日历日期，写入 `actualStartDate`；
- 本项目 Trip 时区仍由现有领域数据提供，当前为 `Asia/Shanghai`；
- 若另一台设备已启动 Trip，不创建第二个日期，返回现有 authoritative state；
- 相同 requestId 必须幂等。

Day1–Day10 继续使用现有 `effectiveStartDate = actualStartDate ?? plannedStartDate` 领域逻辑。

---

## 8. Stop Mutation API

客户端不得上传整份 Trip JSON。

统一：

```http
POST /api/trips/{tripId}/execution/stops/{stopId}/actions
```

请求：

```json
{
  "requestId": "uuid",
  "deviceId": "stable-device-id",
  "action": "COMPLETE",
  "expectedRevision": 21,
  "occurredAt": "2026-08-15T11:26:03+08:00"
}
```

Action：

- ARRIVE
- COMPLETE
- SKIP

服务端必须重新依据数据库事实校验：

- trip / stop 是否存在；
- token 是否属于该 Trip；
- stop 是否 optional；
- 转换是否合法；
- requestId 是否已执行；
- expectedRevision 是否匹配。

成功返回完整 execution snapshot；客户端以响应值为准。

---

## 9. Revision 与并发

每个 Trip execution 维护单调递增 `revision`。

### 9.1 正常写入

```text
expectedRevision == server revision
→ apply
→ revision + 1
→ return latest snapshot
```

### 9.2 Revision 冲突

不匹配返回 HTTP 409，同时返回最新 execution snapshot。

客户端：

1. 接收最新 snapshot；
2. 重新判断本地 pending action 是否仍有效；
3. 已被同等或更终态覆盖则标记 no-op/resolved；
4. 仍合法则用最新 revision 自动重试一次；
5. 再次冲突则停止自动循环，保留 pending 并显示同步异常。

禁止无限重试。

---

## 10. 冲突语义

普通状态用单调语义判断旧动作是否过时：

```text
completed
   >
skipped
   >
arrived
   >
planned
```

该优先级不是让客户端越过服务端直接改数据库，只用于 reconcile。

例：

- Server 已 completed，旧设备 pending SKIP → SKIP 视为 no-op，不允许回退；
- Server 已 skipped，离线设备 pending COMPLETE → 由于 `skipped → completed` 被明确定义为合法，可基于最新 revision 重试 COMPLETE；
- `moved` 独立处理，不参与普通优先级。

---

## 11. 幂等

每个用户动作生成 UUID `requestId`。

服务端保证：

```text
(tripId, requestId) unique
```

相同请求重复到达：

- 不重复改变状态；
- 不重复增加 revision；
- 返回第一次已应用后的业务结果或等价当前结果。

这用于解决 iPhone 超时后“不知道服务器到底成功没有”的重试问题。

---

## 12. iOS Local-first

新增边界：

```text
TripExecutionStore
TripExecutionRepository
TripExecutionSyncService
ExecutionRemoteClient
```

本机持久化：

- 最近一次 server execution snapshot；
- pending actions；
- serverRevision；
- lastSyncedAt；
- stable deviceId。

用户动作：

```text
validate locally
  ↓
create PendingExecutionAction(requestId)
  ↓
persist pending first
  ↓
optimistic projection
  ↓
try push
```

若本地 pending 持久化失败，不能把 UI 长期显示为已提交成功，必须提示“本机无法保存本次操作”。

UI 同步状态至少区分：

- 已同步；
- 待同步；
- 同步异常。

---

## 13. Device ID

每次安装生成一个随机 UUID，并持久化在 Keychain 或同等级安全本地存储。

用途：

- mutation 审计来源；
- 多机冲突排查；
- 为未来单设备吊销预留。

不使用 IDFA，不采集广告标识或硬件唯一标识。

---

## 14. Execution Snapshot GET

```http
GET /api/trips/{tripId}/execution
Authorization: Bearer <token>
```

V1 直接返回完整 snapshot。数据规模只有几十个 stop，不做复杂 delta sync。

建议支持 ETag：

```http
ETag: "execution-revision-22"
If-None-Match: "execution-revision-22"
```

无变化返回 304，降低 10 秒轮询流量。

---

## 15. Today 数据组合

`TodayViewModel` 继续负责：

- preTrip / ready / postTrip；
- 当前日期与 Day 解析。

`TripExecutionStore` 负责：

- stop effective status；
- nextStop；
- completed / resolved progress；
- sync state。

有效 stop status：

```text
execution override
  ?? plan stop status
```

nextStop 必须由共享 execution domain 计算，不能在多个 View 中重复筛选。

---

## 16. Token 分发边界

Task 1 只实现和测试：

```text
Keychain token
  ↓
Authorization header
  ↓
Backend auth
```

开发 / CI 可通过测试注入或本机受控配置把 token 写入 Keychain。

真正给家人手机的“扫码加入 / 一次性导入家庭行程”交给后续 App Shell 设计实现，不在 Task 1 临时做一个不可维护的隐藏入口。

---

## 17. 错误处理

### 401 / 403

- 停止 mutation 自动重试；
- 保留本地 pending；
- 状态标记授权失效；
- 后续 UI 文案：`家庭行程授权已失效，请重新导入行程授权。`

### 409

- 自动 reconcile；
- 仅自动重试一次；
- 无法收敛时提示另一台设备刚更新了状态。

### 5xx / timeout

- pending 保留；
- 不回滚用户意图；
- 显示轻量“待同步”。

### 本地持久化失败

- 阻断本次操作成功态；
- 明确提示重试。

---

## 18. 安全边界

- execution 写接口仅允许 HTTPS；
- token 仅 Authorization header；
- 日志不得打印完整 token；
- 服务端不信任客户端传入的 optional/status 等业务事实；
- mutation 必须做 trip scope 校验；
- MySQL 不开放公网；
- Nginx 只代理明确 API。

---

## 19. 数据库设计

不把 itinerary seed 表直接改成运行时状态表。

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

`trip_execution_action` 同时承担幂等与基础审计，不引入完整 event sourcing。

---

## 20. 兼容策略

现有 itinerary API 不因 Task 1 改成可变大对象。

保持资源分离：

```text
GET itinerary
GET execution
POST execution mutations
```

客户端 ready 后组合：

```text
itinerary/cache
  +
execution/cache
  ↓
Today projection
```

旧只读能力继续兼容。

---

## 21. 测试

### Backend

必须覆盖：

- start trip；
- start 幂等；
- 服务端按 Trip timezone 生成 actualStartDate；
- ARRIVE / COMPLETE；
- optional SKIP；
- required SKIP 拒绝；
- skipped → completed；
- completed 不回退；
- requestId 幂等；
- stale revision → 409 + latest snapshot；
- token missing / invalid；
- 两设备并发 mutation；
- revision 单调递增。

### QingGanCore

覆盖：

- execution override 优先；
- nextStop；
- completed / resolved；
- origin 排除；
- moved 排除；
- optimistic pending projection；
- completed 覆盖旧 skip。

### QingGanAppTests

覆盖：

- pending round trip persistence；
- App 重启 pending 不丢；
- push success 清 pending；
- timeout 保留 pending；
- 409 reconcile；
- foreground immediate refresh；
- 10 秒 polling 不叠加；
- background 停止 polling；
- network restore 顺序为 pull → reconcile → flush → pull；
- Keychain token 注入 Authorization header。

---

## 22. 验收场景

### A. 正常双机同步

A 完成一个 stop：

- A 立即显示完成；
- Server revision +1；
- B 无需手工刷新，在前台 10 秒级自动看到完成；
- A/B nextStop 一致。

### B. 离线操作

A 断网完成 stop：

- A 显示待同步；
- App 被杀重开 pending 仍存在；
- 网络恢复后按既定顺序自动收敛；
- B 最终自动看到同样状态。

### C. 双机冲突

A COMPLETE，B 几乎同时 SKIP 同一 optional stop：

- 一个 mutation 正常成功；
- 另一个收到 revision 冲突；
- 客户端自动 reconcile；
- completed 不被旧 skip 回退；
- 最终所有设备一致。

### D. Token 失效

- mutation 被拒绝；
- 不无限重试；
- pending 不丢；
- token 不进入日志。

---

## 23. 完成定义

Task 1 只有同时满足以下条件才可标记 COMPLETE：

- Flyway execution schema 完成；
- start trip mutation 可用；
- ARRIVE / COMPLETE / SKIP 可用；
- Bearer family token 鉴权可用；
- requestId 幂等可验证；
- revision 乐观锁可验证；
- actualStartDate 服务端时区规则可验证；
- iOS local-first pending 持久化可验证；
- App active 时 10 秒 polling；
- 回前台立即刷新；
- 网络恢复 pull → reconcile → flush → pull；
- 双设备模拟最终一致；
- Core / App / Backend tests 全绿；
- Release build 不被破坏；
- Task 0 Production Gate 保持绿色。

---

## 24. 后续顺序

Task 1 完成后：

```text
Task 2  SwiftUI 五栏 App Shell + Today/行程执行 UI
Task 3  MapKit + CoreLocation + 高德导航
Task 4  真实天气
Task 5  照片打卡与照片同步
Task 6  真实旅途综合验收
```

---

## 25. 设计结论

针对一个家庭、一个 Trip、少量 iPhone 和几十个执行节点，10 秒前台 polling 是主动选择：它比 WebSocket 更简单、更适合弱网和 iOS 生命周期，同时已满足“几秒到十几秒自动看到家人操作”的目标。

未来只有在出现大量实时协作用户、服务端主动事件或真正秒级强实时需求时，再评估 SSE / WebSocket。
