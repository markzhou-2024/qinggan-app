# 青甘随行 Task 1 — Execution State, Family Identity & Device Sync Design

**状态：** 待用户书面审阅确认  
**日期：** 2026-08-11  
**依赖：** Task 0 Production Runtime Foundation  
**适用范围：** iOS 原生 App + Spring Boot + MySQL  
**目标体验：** 家庭成员首次加入时选择“爸爸 / 妈妈 / 姐姐 / 弟弟 / 爷爷 / 奶奶”身份并与当前 iPhone 绑定；同一身份同一时间只允许一台活跃设备。已绑定设备对同一趟青甘行程的执行状态，在正常网络下 0–10 秒级自动同步，同时弱网 / 离线仍可继续操作。

---

## 1. 目标

Task 1 不做地图、天气、照片和完整五栏 UI，而是先把“计划中的青甘行程”升级成“可以被一家人共同执行、并知道是谁在操作的旅途状态”。

完成后，任意一台已加入家庭行程并完成身份绑定的 iPhone 都可以：

- 首次加入时选择家庭身份；
- 将该身份与当前设备唯一绑定；
- 在换机时接管原身份并自动撤销旧设备；
- 开始本次旅行；
- 标记节点已到达；
- 标记节点已完成；
- 跳过可选节点；
- 自动得到新的下一站；
- 弱网时先在本机保存动作；
- 恢复网络后自动同步；
- 其他家庭成员手机在前台时约 10 秒内看到变化；
- App 回到前台时立即刷新；
- 服务端能够记录“谁、用哪台设备、在什么时间”完成了操作。

核心原则：

> 计划数据描述“应该怎么走”，执行数据描述“现在走到哪里”，家庭身份描述“是谁在执行”。三者分离。

---

## 2. 明确不做

Task 1 不包含：

- WebSocket / SSE；
- 后台持续连接；
- 后台定位和家庭位置共享；
- 传统用户注册 / 密码登录；
- 手机号、短信验证码、Apple ID 登录；
- 管理员审批换机；
- 复杂角色权限体系；
- 行程自由编辑；
- MapKit、天气、照片打卡；
- 推送通知；
- 家庭 Token 的二维码分享 / AirDrop 导入 UI。

V1 的六个身份拥有相同旅途执行权限，角色的意义是“身份标识、设备绑定和操作归属”，不是权限等级。

家庭 Token 的可视化分享 / 扫码加入体验放到后续 App Shell；Task 1 实现底层加入协议、角色选择数据、设备绑定、换机接管、Keychain 和测试注入能力。

---

## 3. 家庭身份模型

V1 固定支持六个家庭身份：

```text
FATHER       爸爸
MOTHER       妈妈
OLDER_SISTER 姐姐
YOUNGER_BROTHER 弟弟
GRANDFATHER  爷爷
GRANDMOTHER  奶奶
```

如果产品中文最终希望将“姐姐 / 弟弟”调整成更通用称呼，只修改服务端 displayName / UI 文案；稳定的 role code 不随展示文案变化。

### 3.1 单角色单设备规则

同一个 Trip 中：

```text
一个 role
  ↓
同一时间最多一个 ACTIVE device
```

例如：

```text
爸爸 → iPhone A  ACTIVE
妈妈 → iPhone B  ACTIVE
姐姐 → iPhone C  ACTIVE
```

当“爸爸”已经绑定 iPhone A 时，新设备不能普通绑定为爸爸，只能执行“更换此角色设备”。

### 3.2 角色不是账号

V1 不建立 FamilyMember 用户账号，不要求用户名密码。

角色只承担：

- 家庭身份显示；
- 当前设备归属；
- mutation actor；
- 后续打卡 / 时间轴中的操作者名称；
- 单设备撤销边界。

---

## 4. 加入家庭行程与设备绑定

### 4.1 两级凭据

为支持“换机后旧设备真正失效”，V1 使用两级凭据，而不是所有设备长期共用同一个 Bearer Token。

#### Family Join Token

`family-trip-token`

用途仅限：

- 获取可选角色；
- 首次绑定角色与设备；
- 用户明确确认后的换机接管。

它是家庭行程的“加入密钥”。

#### Device Token

角色绑定成功后，服务器为该设备签发随机 `device-token`。

用途：

- GET execution；
- start trip；
- ARRIVE / COMPLETE / SKIP；
- 10 秒 polling；
- 后续需要识别设备身份的旅途 API。

设备 Token 写入 iOS Keychain；服务端只保存安全哈希，不保存可直接使用的明文值。

这样换机时可以只撤销旧设备 Token，而无需更换整个家庭的 Join Token。

### 4.2 新设备首次加入流程

```text
App 首次启动 / 尚未绑定家庭身份
  ↓
获得 family-trip-token
  ↓
GET available roles
  ↓
选择：爸爸 / 妈妈 / 姐姐 / 弟弟 / 爷爷 / 奶奶
  ↓
POST bind device
  ↓
Server 原子绑定 role + deviceId
  ↓
Server 返回 device-token
  ↓
Device token 存 Keychain
  ↓
进入已绑定状态
```

### 4.3 角色选择状态

角色列表至少返回：

```text
role
label
bindingStatus = AVAILABLE / BOUND
boundDeviceDisplayName?  // 只允许非敏感展示，例如“爸爸的 iPhone”
boundAt?
```

UI 规则：

- `AVAILABLE`：可以直接选择；
- `BOUND`：显示“已使用”；
- 点击已绑定角色只能进入“更换此角色设备”确认流程，不能静默抢占。

---

## 5. 换机接管

用户已确认：**新设备只要持有 Family Join Token，选择“更换此角色设备”并二次确认即可，不增加短信、账号或管理员审批。**

流程：

```text
新 iPhone
  ↓
选择已绑定角色“爸爸”
  ↓
提示：该身份已绑定其他设备
  ↓
用户点击“更换此角色设备”
  ↓
二次确认
  ↓
POST takeover
  ↓
Server 在一个事务中：
  1. revoke 旧 device session
  2. old device → REVOKED
  3. bind role → new deviceId
  4. issue new device-token
  ↓
新设备进入 App
```

旧设备下次 polling / mutation：

- device-token 校验失败或 session 为 REVOKED；
- 停止同步和 mutation；
- 不删除本地 Trip / 照片 / pending 数据；
- UI 进入 `deviceBindingRevoked`；
- 文案：`“爸爸”已绑定到另一台设备，请重新加入家庭行程。`

旧设备不能因为仍知道原来的 deviceId 而继续操作。

---

## 6. Device ID

每次 App 安装生成一个随机 UUID `deviceId`，持久化在 Keychain 或同等级安全本地存储。

用途：

- 唯一设备绑定；
- mutation 审计来源；
- 多机冲突排查；
- 设备撤销；
- lastSeenAt。

不使用 IDFA，不采集广告标识或硬件永久唯一标识。

如果系统卸载 / Keychain 清理后丢失 deviceId，则该安装被视为新设备，需要重新绑定或执行换机接管。

---

## 7. 家庭绑定 API

### 7.1 获取角色

```http
GET /api/trips/{tripId}/family/roles
Authorization: Bearer <family-trip-token>
```

返回：

```json
{
  "tripId": "qinggan-2026-family",
  "roles": [
    { "role": "FATHER", "label": "爸爸", "bindingStatus": "BOUND" },
    { "role": "MOTHER", "label": "妈妈", "bindingStatus": "AVAILABLE" }
  ]
}
```

### 7.2 首次绑定

```http
POST /api/trips/{tripId}/family/devices/bind
Authorization: Bearer <family-trip-token>
```

Body：

```json
{
  "requestId": "uuid",
  "deviceId": "stable-device-id",
  "role": "MOTHER",
  "deviceName": "妈妈的 iPhone"
}
```

规则：

- role 必须属于预设角色；
- role 当前必须 AVAILABLE；
- 同一个 deviceId 在同一个 Trip 只能有一个 ACTIVE role；
- `(tripId, requestId)` 幂等；
- 绑定和 device-token 签发为一个原子事务；
- 并发两台设备抢同一 AVAILABLE role，只能一台成功，另一台返回 409 + 最新 roles snapshot。

### 7.3 换机接管

```http
POST /api/trips/{tripId}/family/devices/takeover
Authorization: Bearer <family-trip-token>
```

Body：

```json
{
  "requestId": "uuid",
  "deviceId": "new-device-id",
  "role": "FATHER",
  "deviceName": "爸爸的新 iPhone",
  "confirmed": true
}
```

服务端必须：

- 要求 `confirmed == true`；
- 在数据库事务内撤销旧设备、绑定新设备；
- 立即使旧 device-token 失效；
- 签发新 device-token；
- 记录 replacedDeviceId 和 takeover audit；
- 相同 requestId 重试不重复产生多个 token / 多次撤销。

---

## 8. 设备鉴权

绑定后所有 execution 请求使用：

```http
Authorization: Bearer <device-token>
```

服务器根据 token 定位：

```text
tripId
role
active deviceId
device binding status
```

并校验请求路径 tripId 与 token scope 一致。

客户端 mutation body 中仍可带 deviceId 作为审计字段，但服务端的 actor 身份必须来自验证后的 Device Token，不信任客户端自己声明的 role。

换句话说：

```text
updatedByRole
updatedByDeviceId
```

必须由后端认证上下文写入，不接受客户端直接指定。

---

## 9. 同步方案

V1 采用：

**前台 10 秒轻量轮询 + 写后立即采用服务端结果 + 回前台立即刷新。**

不使用 WebSocket。

### 9.1 前台轮询

当 App 处于 active、Trip 已加载且 device binding 为 ACTIVE：

- 每 10 秒请求一次 execution snapshot；
- App 进入后台立即停止轮询；
- App 回前台立即 refresh，不等待下一次 tick；
- 同一时刻最多一个 refresh request 在飞行；
- 上一轮未结束时跳过下一次 tick；
- 网络失败不清除本地状态，不高频重试。

正常网络下，跨设备可见延迟目标为 0–10 秒级。

### 9.2 写后刷新

任何 mutation 成功后，服务端直接返回新的 execution snapshot 和 revision；当前设备立即采用该响应，不再额外等待下一次 polling。

### 9.3 网络恢复顺序

统一冻结为：

```text
网络恢复
  ↓
验证 device binding 仍 ACTIVE
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

若设备在离线期间被换机撤销：

- 不 flush pending；
- pending 保留；
- 进入 `deviceBindingRevoked`；
- 后续由用户重新加入后再决定如何处理旧 pending，不自动冒充新身份提交。

---

## 10. 数据边界

### 10.1 Trip Plan

现有 `Trip / TripDay / TripStop / Stay / NavigationPoint` 继续负责计划事实。

SwiftUI 和 execution store 不通过修改解码后的 Trip 对象表达用户动作。

### 10.2 Family Binding

新增身份与设备事实：

```text
FamilyRoleBinding
  tripId
  role
  displayName
  activeDeviceId?
  boundAt?
  bindingVersion
```

```text
TripDevice
  tripId
  deviceId
  role
  deviceName
  status = ACTIVE / REVOKED
  tokenHash
  boundAt
  revokedAt?
  lastSeenAt?
```

### 10.3 Trip Execution

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
  updatedByRole
  updatedByDeviceId
```

Stop status：

- planned
- arrived
- completed
- skipped
- moved（仅兼容计划调整语义，普通用户不能直接设置）

---

## 11. 状态机

### 11.1 Trip lifecycle

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

### 11.2 Stop execution

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

## 12. 开始旅行与日期权威源

开始旅行必须是服务端 mutation：

```http
POST /api/trips/{tripId}/execution/start
Authorization: Bearer <device-token>
```

客户端请求：

```json
{
  "requestId": "uuid",
  "expectedRevision": 12,
  "occurredAt": "2026-08-13T07:38:00+08:00"
}
```

关键规则：

- actor role / deviceId 来自认证后的 device-token；
- `occurredAt` 只用于客户端审计和排障，不作为 `actualStartDate` 权威值；
- 服务端以 mutation 实际生效时间为准；
- 服务端按 Trip 的 `timeZone` 将该时间解析成日历日期，写入 `actualStartDate`；
- 本项目 Trip 时区由现有领域数据提供；
- 若另一台设备已启动 Trip，不创建第二个日期，返回现有 authoritative state；
- 相同 requestId 必须幂等。

Day1–Day10 继续使用现有 `effectiveStartDate = actualStartDate ?? plannedStartDate` 领域逻辑。

---

## 13. Stop Mutation API

客户端不得上传整份 Trip JSON。

统一：

```http
POST /api/trips/{tripId}/execution/stops/{stopId}/actions
Authorization: Bearer <device-token>
```

请求：

```json
{
  "requestId": "uuid",
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
- device token 是否属于该 Trip 且 ACTIVE；
- stop 是否 optional；
- 转换是否合法；
- requestId 是否已执行；
- expectedRevision 是否匹配。

成功返回完整 execution snapshot；客户端以响应值为准。

执行记录由后端自动附加：

```text
updatedByRole = authenticated role
updatedByDeviceId = authenticated deviceId
```

---

## 14. Revision 与并发

每个 Trip execution 维护单调递增 `revision`。

### 14.1 正常写入

```text
expectedRevision == server revision
→ apply
→ revision + 1
→ return latest snapshot
```

### 14.2 Revision 冲突

不匹配返回 HTTP 409，同时返回最新 execution snapshot。

客户端：

1. 接收最新 snapshot；
2. 重新判断本地 pending action 是否仍有效；
3. 已被同等或更终态覆盖则标记 no-op/resolved；
4. 仍合法则用最新 revision 自动重试一次；
5. 再次冲突则停止自动循环，保留 pending 并显示同步异常。

禁止无限重试。

---

## 15. 冲突语义

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
- Server 已 skipped，离线设备 pending COMPLETE → 由于 `skipped → completed` 合法，可基于最新 revision 重试 COMPLETE；
- `moved` 独立处理，不参与普通优先级。

如果 pending 所属设备已经 REVOKED，则不进入上述 reconcile 自动重试流程。

---

## 16. 幂等

每个用户动作或设备绑定动作生成 UUID `requestId`。

服务端保证：

```text
(tripId, requestId) unique
```

相同请求重复到达：

- 不重复改变状态；
- 不重复增加 revision；
- 绑定 / 换机不重复签发多个有效设备会话；
- 返回第一次已应用后的业务结果或等价当前结果。

这用于解决 iPhone 超时后“不知道服务器到底成功没有”的重试问题。

---

## 17. iOS Local-first

新增边界：

```text
FamilyBindingStore
FamilyBindingRemoteClient
TripExecutionStore
TripExecutionRepository
TripExecutionSyncService
ExecutionRemoteClient
```

本机安全持久化：

```text
Keychain
- familyJoinToken（若用户选择保留）
- deviceToken
- stableDeviceId
- selectedRole
```

本机执行持久化：

- 最近一次 server execution snapshot；
- pending actions；
- serverRevision；
- lastSyncedAt；
- device binding state。

用户执行动作：

```text
validate locally
  ↓
create PendingExecutionAction(requestId, actorBindingVersion)
  ↓
persist pending first
  ↓
optimistic projection
  ↓
try push
```

Pending action 必须记录创建时的 bindingVersion / deviceId。设备被换机撤销后，旧 pending 不能自动迁移到新设备身份。

若本地 pending 持久化失败，不能把 UI 长期显示为已提交成功，必须提示“本机无法保存本次操作”。

UI 同步状态至少区分：

- 已同步；
- 待同步；
- 同步异常；
- 设备绑定已失效。

---

## 18. Execution Snapshot GET

```http
GET /api/trips/{tripId}/execution
Authorization: Bearer <device-token>
```

V1 直接返回完整 snapshot。数据规模只有几十个 stop，不做复杂 delta sync。

建议支持 ETag：

```http
ETag: "execution-revision-22"
If-None-Match: "execution-revision-22"
```

无变化返回 304，降低 10 秒轮询流量。

每个 stop state 可返回：

```json
{
  "stopId": "day3-chaka",
  "status": "COMPLETED",
  "updatedAt": "2026-08-15T11:26:04+08:00",
  "updatedByRole": "FATHER"
}
```

客户端展示时使用 role 的本地化 label，例如：

`爸爸 · 11:26 完成`

---

## 19. Today 数据组合

`TodayViewModel` 继续负责：

- preTrip / ready / postTrip；
- 当前日期与 Day 解析。

`TripExecutionStore` 负责：

- stop effective status；
- nextStop；
- completed / resolved progress；
- sync state；
- latest actor information。

有效 stop status：

```text
execution override
  ?? plan stop status
```

nextStop 必须由共享 execution domain 计算，不能在多个 View 中重复筛选。

后续 UI 可以直接表达：

```text
茶卡盐湖  ✓
爸爸 · 11:26 完成
```

---

## 20. Token 分发边界

Task 1 实现和测试：

```text
Family Join Token
  ↓
角色列表 / bind / takeover
  ↓
Device Token
  ↓
Keychain
  ↓
Execution Authorization header
```

Family Join Token：

- 不进入源码、fixture、Git；
- 不放 URL query；
- 不写明文日志；
- 可通过开发 / CI 测试注入。

Device Token：

- 只通过 HTTPS response 返回一次明文；
- iOS 写 Keychain；
- 服务端仅保存 hash；
- REVOKED 后立即无效。

真正给家人手机的“扫码加入 / 分享家庭行程 Token”在后续 App Shell 设计中提供正式用户入口，不在 Task 1 使用隐藏 debug UI 代替。

---

## 21. 错误处理

### Family Join Token 401 / 403

- 不能查看 / 绑定角色；
- 提示 `家庭行程加入凭据无效，请重新获取。`

### Role bind 409

- 重新获取角色列表；
- 显示该角色刚被其他设备占用；
- 不自动执行 takeover。

### Device Token 401 / 403 / REVOKED

- 停止 polling 和 mutation 自动重试；
- 保留本地 pending；
- 状态标记 `deviceBindingRevoked` 或 `authorizationInvalid`；
- 文案优先显示：`该家庭身份已绑定到另一台设备，请重新加入。`

### Execution 409

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

## 22. 安全边界

- family bind / takeover / execution 写接口仅允许 HTTPS；
- Join Token 和 Device Token 仅 Authorization header；
- 日志不得打印完整 token；
- 服务端不信任客户端传入的 role、optional、status 等业务事实；
- actor role / deviceId 只从认证上下文获取；
- mutation 必须做 trip scope 校验；
- takeover 必须原子 revoke old + activate new；
- Device Token 使用高熵随机值，服务端存 hash；
- MySQL 不开放公网；
- Nginx 只代理明确 API。

V1 的安全目标是防止误绑定和旧设备继续操作，不引入复杂家庭管理员体系。

---

## 23. 数据库设计

不把 itinerary seed 表直接改成运行时状态表。

建议：

```text
trip_family_role_binding
- trip_id
- role
- display_name
- active_device_id nullable
- binding_version
- bound_at nullable
- PRIMARY KEY(trip_id, role)
```

```text
trip_device
- trip_id
- device_id
- role
- device_name
- status              ACTIVE / REVOKED
- device_token_hash
- bound_at
- revoked_at nullable
- last_seen_at nullable
- PRIMARY KEY(trip_id, device_id)
```

约束：

- `(trip_id, role)` 同一时刻最多一个 active_device_id；
- 一个 `(trip_id, device_id)` 同一时刻只能有一个 role；
- takeover 使用事务和行锁 / 等价数据库并发控制。

```text
trip_execution
- trip_id PK/FK
- status
- actual_start_date
- revision
- updated_at
```

```text
trip_stop_execution
- trip_id
- stop_id
- status
- updated_at
- updated_by_role
- updated_by_device_id
- PRIMARY KEY(trip_id, stop_id)
```

```text
trip_execution_action
- trip_id
- request_id
- device_id
- role
- action_type
- stop_id nullable
- created_at
- applied_revision nullable
- PRIMARY KEY(trip_id, request_id)
```

```text
trip_device_binding_action
- trip_id
- request_id
- action_type          BIND / TAKEOVER / REVOKE
- role
- new_device_id
- replaced_device_id nullable
- created_at
- PRIMARY KEY(trip_id, request_id)
```

Action 表承担幂等与基础审计，不引入完整 event sourcing。

---

## 24. 兼容策略

现有 itinerary API 不因 Task 1 改成可变大对象。

保持资源分离：

```text
GET itinerary
GET family roles
POST family bind/takeover
GET execution
POST execution mutations
```

客户端 ready 后组合：

```text
itinerary/cache
  +
active family binding
  +
execution/cache
  ↓
Today projection
```

旧只读 itinerary 能力继续兼容。

---

## 25. 测试

### Backend — Family Binding

必须覆盖：

- 六个角色初始化；
- AVAILABLE role 正常绑定；
- 同一 role 普通重复绑定拒绝；
- 两设备并发抢同一 role 只有一个成功；
- 一个 device 不能同时绑定两个 role；
- bind requestId 幂等；
- takeover 必须 confirmed；
- takeover 原子 revoke old + activate new；
- takeover requestId 幂等；
- 旧 device-token takeover 后立即失效；
- 新 device-token 可用；
- client 伪造 role 不改变认证 actor；
- token 明文不写数据库。

### Backend — Execution

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
- device token missing / invalid / revoked；
- 两设备并发 mutation；
- revision 单调递增；
- updatedByRole / updatedByDeviceId 来自认证上下文。

### QingGanCore

覆盖：

- execution override 优先；
- nextStop；
- completed / resolved；
- origin 排除；
- moved 排除；
- optimistic pending projection；
- completed 覆盖旧 skip；
- role code 与展示 label 映射稳定。

### QingGanAppTests — Binding

覆盖：

- stable deviceId Keychain round trip；
- Join Token 注入 family API；
- bind success 存 device-token + role；
- role 409 刷新角色列表；
- takeover success 替换 device-token；
- revoked binding 停止 polling；
- revoked binding 不 flush 旧 pending；
- App 重启恢复已绑定身份。

### QingGanAppTests — Sync

覆盖：

- pending round trip persistence；
- App 重启 pending 不丢；
- push success 清 pending；
- timeout 保留 pending；
- 409 reconcile；
- foreground immediate refresh；
- 10 秒 polling 不叠加；
- background 停止 polling；
- network restore 顺序为 binding validation → pull → reconcile → flush → pull；
- Device Token 注入 execution Authorization header。

---

## 26. 验收场景

### A. 新设备加入

妈妈第一次打开 App：

- 使用家庭 Join Token；
- 看到六个角色及占用状态；
- 选择 AVAILABLE 的“妈妈”；
- 绑定成功；
- App 重启后仍识别为妈妈；
- 后续 execution 请求使用妈妈设备的 Device Token。

### B. 角色占用

爸爸已经绑定 iPhone A：

- iPhone B 角色列表显示“爸爸 · 已使用”；
- 普通 bind 不能抢占；
- 只有用户明确进入“更换此角色设备”才能继续。

### C. 换机

爸爸从 iPhone A 换到 iPhone B：

- B 持有 Join Token；
- 选择爸爸 → 更换此角色设备 → 二次确认；
- B 获得新的 Device Token；
- A 下一次请求立即被判定 REVOKED；
- A 不再自动提交旧 pending；
- 爸爸历史 execution actor 记录仍保留。

### D. 正常双机同步

爸爸完成一个 stop：

- 爸爸手机立即显示完成；
- Server revision +1；
- 妈妈手机无需手工刷新，在前台 10 秒级自动看到完成；
- 页面可显示 `爸爸 · 完成`；
- 两台手机 nextStop 一致。

### E. 离线操作

妈妈断网完成 stop：

- 妈妈手机显示待同步；
- App 被杀重开 pending 仍存在；
- 网络恢复先确认设备仍 ACTIVE；
- 再按 pull → reconcile → flush → pull 自动收敛；
- 其他设备最终自动看到同样状态。

### F. 离线期间发生换机

旧“爸爸”设备离线留下 pending，随后爸爸在新手机完成 takeover：

- 旧设备恢复网络时发现 binding REVOKED；
- 不自动提交旧 pending；
- 不把旧 pending 伪装成新设备动作；
- pending 保留供用户确认 / 后续迁移策略处理。

### G. 双机冲突

爸爸 COMPLETE，妈妈几乎同时 SKIP 同一 optional stop：

- 一个 mutation 正常成功；
- 另一个收到 revision 冲突；
- 客户端自动 reconcile；
- completed 不被旧 skip 回退；
- 最终所有设备一致。

---

## 27. 完成定义

Task 1 只有同时满足以下条件才可标记 COMPLETE：

- Flyway family binding + execution schema 完成；
- 六个角色初始化并可查询；
- Family Join Token 可验证；
- AVAILABLE role 可绑定；
- 单角色单 ACTIVE device 约束可验证；
- 换机 takeover 可原子撤销旧设备；
- 旧 Device Token takeover 后立即失效；
- Device Token Keychain 持久化可验证；
- start trip mutation 可用；
- ARRIVE / COMPLETE / SKIP 可用；
- requestId 幂等可验证；
- revision 乐观锁可验证；
- actor role / deviceId 由服务端认证上下文产生；
- actualStartDate 服务端时区规则可验证；
- iOS local-first pending 持久化可验证；
- App active 时 10 秒 polling；
- 回前台立即刷新；
- 网络恢复 binding validation → pull → reconcile → flush → pull；
- 双设备模拟最终一致；
- Core / App / Backend tests 全绿；
- Release build 不被破坏；
- Task 0 Production Gate 保持绿色。

---

## 28. 后续顺序

Task 1 完成后：

```text
Task 2  SwiftUI 五栏 App Shell + 正式加入/角色选择/换机 UI + Today/行程执行 UI
Task 3  MapKit + CoreLocation + 高德导航
Task 4  真实天气
Task 5  照片打卡与照片同步（沿用 role actor）
Task 6  真实旅途综合验收
```

Task 2 负责把 Task 1 已经具备的 family binding API 做成正式用户体验，包括：

- 家庭行程加入页；
- 六角色选择页；
- “已使用”状态；
- 换机二次确认；
- 当前身份展示。

Task 1 先保证这些行为在 API / Store / 测试层完全成立。

---

## 29. 设计结论

针对一个家庭、一个 Trip、六个身份、少量 iPhone 和几十个执行节点，推荐架构为：

```text
Family Join Token
      ↓
选择家庭角色
      ↓
唯一设备绑定
      ↓
Device Token
      ↓
10 秒前台 polling + local-first mutation
      ↓
Spring Boot authoritative execution
```

该方案比传统账号系统轻，也比“所有设备永久共用一个 Token”更能满足单设备绑定与换机撤销要求。

10 秒前台 polling 仍是主动选择：它比 WebSocket 更简单、更适合弱网和 iOS 生命周期，同时满足“几秒到十几秒自动看到家人操作”的目标。

未来只有在出现大量实时协作用户、服务端主动事件或真正秒级强实时需求时，再评估 SSE / WebSocket。
