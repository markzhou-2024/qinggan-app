# 青甘随行原生 SwiftUI UI V1 Design V2

**状态：** 修订版，待最终执行确认  
**日期：** 2026-08-11  
**适用平台：** iPhone 原生 iOS App  
**技术基线：** Swift 6 / SwiftUI / iOS 17+  
**视觉依据：** `UI/UI.png` 与《青甘大环线自驾助手 V1 UI 页面设计说明》  
**前版依据：**《青甘随行原生 SwiftUI UI V1 设计说明》

---

## 0. V2 修订摘要

V2 不推翻原 V1 的 SwiftUI 架构、五栏信息架构、`TodayViewModel`、`TripExecutionState`、高德深链和现有 Trip 领域模型，只修订此前过于保守的 V1 能力边界。

V2 将以下能力正式纳入 V1：

- 原生 MapKit 地图与预设 POI 展示；
- Core Location 前台定位；
- 高德一键驾车导航，Apple Maps 保底；
- 真实天气数据展示；
- 真实照片拍摄 / 相册选择与旅途打卡；
- 清单完成、跳过、打卡等执行状态的本地持久化；
- 面向多台 iPhone 的远端同步边界；
- 离线优先，网络恢复后同步。

V2 仍不包含：

- 登录、注册和账户体系；
- 家庭成员位置共享；
- 后台持续定位或轨迹上传；
- App 内 turn-by-turn 驾车导航；
- 用户自由编辑 10 天主行程；
- 社交、点赞、公开分享；
- 复杂路线实时重规划；
- 推送通知。

产品定位从“原生 UI 展示版”明确为：

> **能够真实陪伴一家人在 10 天青甘大环线旅途中每日执行行程的原生 iOS 助手。**

---

# 1. 产品目标与核心使用场景

青甘随行不是旅行规划工具，而是“行程已经规划完成后的每日执行助手”。

10 天路线、关键导航点、景点、充电点、住宿点和主要提醒在出发前已经配置进系统。旅途中，用户每天只需要打开 App，完成以下动作：

1. 确认今天是 Day 几；
2. 查看今天从哪里到哪里；
3. 看当天总里程、预计驾驶时间和天气；
4. 按今日清单从上到下执行；
5. 到下一个固定导航点时一键打开高德；
6. 在 App 内地图查看当前位置、当天路线与关键 POI；
7. 到景点、餐饮、酒店等位置拍照打卡；
8. 完成的清单、打卡和进度不会因 App 重启而丢失；
9. 多台 iPhone 使用同一趟行程时，可在有网络时同步共享执行状态。

核心设计原则：

> **不让用户在旅途中重新做规划，只让用户快速知道“今天去哪、下一站去哪、还有什么没完成”。**

---

# 2. 设计原则

## 2.1 原生优先

使用 SwiftUI 原生交互模式：

- `TabView` 作为五栏根导航；
- `NavigationStack` 承载层级导航；
- `sheet` / `fullScreenCover` 承载清单、打卡等短流程；
- MapKit 负责 App 内地图态势展示；
- Core Location 提供前台当前位置；
- PhotosPicker / Camera 提供照片能力。

不将 H5 交互模式机械搬进 iOS。

## 2.2 数据驱动，不把青甘路线写死在 View

设计稿中的“大柴旦 → 敦煌”“阿克塞”“阳关遗址”等只作为视觉示例。

实际界面必须由：

```text
Trip
  → TodaySnapshot
  → TripDay
  → Stops / NavigationPoints / Stay / ChecklistItems
  → SwiftUI
```

驱动。

SwiftUI View 中不得将某一天真实业务内容硬编码为固定字符串。

## 2.3 Local-first

青甘路途中存在弱网和断网场景，因此执行动作必须先在本机成功：

```text
用户操作
  → TripExecutionStore
  → Local Persistence
  → UI 立即刷新
  → 网络可用时 Remote Sync
```

网络失败不能阻塞勾选清单、查看已缓存行程、查看已缓存天气、查看打卡记录。

## 2.4 状态必须可恢复

以下信息不能仅存在内存：

- stop 完成；
- stop 跳过；
- Day 执行进度；
- 打卡记录；
- 照片元数据；
- 最近一次天气缓存；
- 最近一次远端同步状态。

App 被系统杀掉或手机重启后，重新打开必须恢复。

## 2.5 权限最小化

V1 只申请实际使用所需权限：

- 定位：仅“使用 App 期间”；
- 相机：用户主动拍照时；
- 照片：优先使用系统 PhotosPicker，不进行整个相册扫描；
- 不申请后台定位；
- 不上传家庭成员连续位置。

---

# 3. 信息架构

根视图使用 5 个 Tab：

1. **今日**
2. **地图**
3. **打卡**
4. **行程**
5. **我的**

```text
今日
 ├─ 今日 Dashboard
 ├─ 今日清单
 ├─ 下一站导航
 ├─ 充电点
 └─ 今晚住宿

地图
 ├─ 当前定位
 ├─ 当日路线
 ├─ 预设 POI
 ├─ 当前下一站
 └─ 高德导航

打卡
 ├─ 立即打卡
 ├─ 拍照 / 选照片
 ├─ 地点 / 时间
 ├─ 一句话
 └─ 时间轴

行程
 ├─ Day1–Day10 总览
 └─ Day 详情

我的
 ├─ 行程信息
 ├─ 数据与同步状态
 ├─ 地图设置
 ├─ 隐私权限提示
 └─ App 版本
```

---

# 4. 根状态与 Today 生命周期

保留现有 `TodayViewModel` 及日期领域逻辑。

根状态继续包括：

- loading；
- preTrip；
- ready；
- postTrip；
- unavailable / error。

只有 `ready` 状态显示五栏主界面。

旅行有效开始日期仍按既有领域规则解析，不允许 UI Store 自行决定“今天是哪一天”。

`AppDateProvider` 等调试日期注入能力继续保留，但必须：

- 只用于 Debug / Test；
- UI 中可识别当前是否使用 Debug Date；
- Release 不允许通过共享 Scheme 参数意外固定旅行日期。

---

# 5. 领域与状态边界

## 5.1 保留 Trip 核心模型

继续以现有：

- `Trip`
- `TripDay`
- `TripStop`
- `Stay`
- `NavigationPoint`

为旅行内容事实来源。

这些模型负责“计划是什么”。

## 5.2 新增执行态，不污染计划态

`TripStop.status` 的服务端计划/原始状态与旅途执行状态分离。

新增：

```text
TripExecutionState
```

负责：

- stop completed；
- stop skipped；
- moved 等既有特殊状态解释；
- 当前下一站；
- Day 进度；
- Day resolved；
- 全程完成日数量。

UI 不直接修改 fixture 或 Trip 解码结果。

## 5.3 新增 ChecklistItem 概念

原 V1 将“车辆检查”“补水”“今日拍照”等 UI 提醒硬编码在页面中。V2 不再将其长期写死在 SwiftUI。

目标领域结构：

```text
TripDay
 ├─ Stops
 ├─ NavigationPoints
 ├─ Stay
 └─ ChecklistItems
```

`ChecklistItem` 至少支持：

```text
id
dayNumber
category
title
subtitle
kind
isOptional
sortOrder
linkedStopID?
linkedNavigationPointID?
```

`kind` 建议覆盖：

- preparation
- scenic
- charging
- meal
- rest
- stay
- checkIn
- reminder

若当前后端尚未提供 `ChecklistItem`，V1 首个实现允许通过一个独立的 `ChecklistProjection` 在应用层从现有 Trip 数据投影，但投影必须集中在单一适配器中，不能散落在 SwiftUI View。

---

# 6. 执行状态持久化与多机同步

## 6.1 本地持久化

V1 必须实现本地持久化。

建议新增：

```text
TripExecutionRepository
```

接口职责：

```text
load(tripID)
save(stop state)
save(check-in)
load check-ins
load last sync metadata
```

底层可以使用项目当前最合适的 iOS 本地持久化方式；实现必须满足：

- App 重启后状态恢复；
- 数据按 tripID 隔离；
- 单条动作可增量写入；
- 写入失败必须可见；
- 不因网络失败回滚用户刚刚完成的本地动作。

## 6.2 远端同步

为了支持家人多台 iPhone 使用同一趟旅行，V1 定义远端同步能力：

```text
TripExecutionSyncService
```

同步内容：

- stop completed / skipped；
- check-in 元数据；
- 可上传的打卡照片引用；
- action timestamp；
- device-generated action ID。

同步策略：

- local-first；
- 远端成功后记录 `lastSyncedAt`；
- 网络不可用时保留 pending actions；
- 恢复网络后重试；
- 同一 stop 的冲突采用单调状态优先，不把 completed 自动回退为 planned；
- 不同步连续设备位置。

如果后端同步接口尚未在本次 iOS 任务前提供，则 iOS 必须先完整实现 Repository + pending action queue + SyncService 接口，并保证本地 V1 独立可用；服务端 API 实现作为配套后端任务，不得以此为理由退回“纯内存状态”。

---

# 7. 页面设计

## 7.1 今日 / TodayDashboardView

### 页面目标

每天打开 App 的第一屏，只回答：

- 今天去哪；
- 今天开多久；
- 天气如何；
- 当前执行到哪里；
- 下一站是谁；
- 充电和住宿在哪里。

### A. Day Header

展示：

- `Day N`
- 日期 / 星期
- 起点 → 终点
- 当天标题或一句简短主题

内容来自 `TodaySnapshot`。

### B. 今日概览卡

字段：

- 计划里程；
- 计划驾驶时长；
- 当前目的地天气；
- 今日清单进度。

天气不得使用演示值冒充真实值。

状态包括：

- fresh；
- cached；
- unavailable。

离线时可展示最近缓存并注明更新时间。

### C. 四个快捷入口

#### 一键导航
导航到当前 `nextStop` 的推荐 `NavigationPoint`。

#### 查看清单
进入 `TodayChecklistView`。

#### 充电点
显示今日路线中 `charging` 类型节点和备用点。

#### 住宿
进入今晚 `Stay` 详情。

### D. 下一站卡片

展示：

- 下一站名称；
- 类型；
- 计划距离 / 时间（有真实数据才显示）；
- 导航按钮；
- 已完成状态。

完成当前 stop 后必须自动刷新下一站。

---

## 7.2 今日清单 / TodayChecklistView

清单按照当天执行顺序展示，而不是普通 Todo。

分组：

1. 出发准备
2. 沿途必经
3. 补能
4. 用餐与休息
5. 住宿
6. 旅途记录

每项支持：

- 未完成；
- 已完成；
- 可选项跳过；
- 导航；
- 关联打卡；
- 备注。

交互规则：

- 点击完成后立即本地持久化；
- `isOptional == false` 不显示跳过；
- 跳过不计入 completed 数，但计入 resolved；
- 起点不计入任务分母；
- 完成 / 跳过后首页、地图、总览同步刷新；
- 失败时不能静默，显示“本地保存失败”。

---

## 7.3 地图 / RouteNavigationView

V2 取消“竖向假地图”。

App 内使用原生 MapKit 展示真实地图态势。

### 地图内容

- 当前设备位置；
- 当日起点；
- 景点；
- 充电点；
- 用餐 / 休息点；
- 酒店；
- 当前下一站；
- 当日预设路线折线（有路线 geometry 时）；
- 若后端只有离散点，使用节点连线作为“计划路线示意”，明确不冒充实时导航路线。

### Marker 状态

- 起点：蓝色；
- 景点：青色；
- 充电：绿色闪电；
- 酒店：紫色；
- 已完成：灰化 + ✓；
- 下一站：高亮；
- 当前位置：系统定位样式。

### 底部卡

固定展示当前下一站：

```text
下一站：XXX
距离 / 预计时间（有可用数据时）
[查看详情] [导航到下一站]
```

### 导航职责边界

App 内 MapKit：

> 看今天路线、当前位置、关键点和执行状态。

高德：

> 执行真正的驾车 turn-by-turn 导航。

导航优先级：

1. 高德地图；
2. 高德不可用 → Apple Maps；
3. 坐标缺失 → 显示不可导航原因，不拼接错误 URL。

---

## 7.4 定位 / LocationService

新增前台定位服务。

用途：

- 地图显示当前位置；
- 一键回到当前位置；
- 打卡自动附带当前坐标；
- 可计算“距下一站”的近似直线距离作为兜底；
- 辅助判断用户是否已经接近预设点。

权限：

- 仅 `whenInUse`；
- 拒绝权限后 App 其他功能仍可用；
- 地图退化为计划路线视图；
- 不做后台定位；
- 不做家庭位置共享。

---

## 7.5 天气 / Weather

天气正式进入 V1。

推荐数据流：

```text
Weather API / existing server
   ↓
WeatherRepository
   ↓
Local Weather Cache
   ↓
TodayDashboardView
```

首页只展示决策需要的信息：

- 地点；
- 当前 / 当日天气；
- 最低 / 最高温；
- 风力或风速；
- 简短天气状态。

天气失败时：

- 优先使用未过期缓存；
- 过期缓存可显示但注明更新时间；
- 无缓存时显示“天气暂不可用”，不得编造天气。

天气不是决定 Day 的依据。

---

## 7.6 打卡 / JourneyCheckInView

V2 将“打卡”升级为真实旅途记录。

### 创建打卡

点击：

`+ 立即打卡`

进入 `CheckInEditorView`。

字段：

```text
CheckInRecord
  id
  tripID
  dayNumber
  stopID?
  locationName
  latitude?
  longitude?
  createdAt
  note?
  photos[]
  syncState
```

### 照片能力

支持：

- 系统相机拍照；
- PhotosPicker 从相册选图；
- 多图；
- 删除未提交照片；
- 查看缩略图。

V1 不要求把照片重新写回系统相册。

### 地点

优先级：

1. 用户从当前 Day 的预设 stop 选择；
2. 当前下一站；
3. Core Location 当前坐标；
4. 允许仅文字地点。

不要求在线逆地理编码才能完成打卡。

### 时间轴

按 Day / 时间展示：

- 时间；
- 地点；
- 类型；
- 照片缩略图；
- 一句话；
- 同步状态。

打卡必须持久化，不能只是 `checkInDate: Date?`。

---

## 7.7 10 天总览 / TenDayOverviewView

顶部展示：

- 已完成 Day 数；
- 总 Day 数；
- 百分比。

Day 状态：

- 已完成；
- 进行中；
- 待开始。

当前 Day 由日期领域逻辑决定，不因用户点击未来 Day 而改变。

点击某 Day：

进入 Day 详情。

详情包括：

- 起终点；
- 里程；
- 驾驶时间；
- 节点；
- 住宿；
- 充电；
- Checklist；
- 注意事项；
- 当天打卡数量。

---

## 7.8 我的 / ProfileView

V1 不做账户。

页面只展示：

- App 名称与版本；
- 当前 Trip 名称；
- 数据来源；
- 最近同步时间；
- Pending actions 数；
- 定位权限状态；
- 相机 / 照片权限说明；
- 地图导航设置；
- 清理本地缓存入口（必须二次确认）。

不得展示虚构头像、积分、会员或社交信息。

---

# 8. 视觉系统

延续视觉稿：

- 湖蓝：主要信息与导航；
- 翡翠青：完成态 / 主行动；
- 沙漠暖金：天气、提醒、青甘地域强调；
- 深海军蓝：主文本；
- 白色：卡片；
- 浅灰蓝：页面背景。

规范：

- 主要卡片圆角约 18–20pt；
- 页面水平 padding 16pt；
- 卡片间距 12–16pt；
- 主操作最小 44×44pt；
- 支持 Dynamic Type；
- 不只依赖颜色表达状态；
- VoiceOver 提供状态与进度说明；
- 大号字体下不得裁切核心操作。

旅行摄影图片只有在具备明确、合法的应用资源后才进入正式产品；资源缺失时使用设计化渐变 / SF Symbols，不下载来源不明的网络图片。

---

# 9. 错误与离线设计

必须覆盖：

## Trip 数据加载失败
显示已有 unavailable / retry 体验。

## 无网络
可继续：

- 看已缓存的 10 天行程；
- 勾清单；
- 查看本地地图 POI；
- 打卡；
- 查看本地照片；
- 查看最近天气缓存；
- 打开已知坐标的外部导航。

## 定位拒绝
地图正常打开，但不显示“当前位置”。

## 照片权限拒绝
允许继续纯文字打卡；提供再次选择照片入口。

## 相机权限拒绝
PhotosPicker 仍可用。

## 天气失败
显示缓存或 unavailable。

## 同步失败
用户操作不回滚，显示：

`待同步`

并在网络恢复时重试。

---

# 10. 组件边界

建议文件职责：

```text
QingGanApp
 ├─ TodayViewModel
 ├─ ReadyTripTabView
 ├─ TripExecutionStore
 ├─ TripExecutionRepository
 ├─ TripExecutionSyncService
 ├─ LocationService
 ├─ WeatherRepository
 ├─ CheckInRepository
 ├─ TodayDashboardView
 ├─ TodayChecklistView
 ├─ RouteNavigationView
 ├─ JourneyCheckInView
 ├─ CheckInEditorView
 ├─ TenDayOverviewView
 └─ ProfileView
```

原则：

- `TodayViewModel`：旅行日期 / 根生命周期；
- `TripExecutionStore`：五个 Tab 的可观察 UI 状态；
- Repository：持久化与数据来源；
- SyncService：远端同步；
- Service：系统能力；
- View：只负责显示和触发意图。

---

# 11. 测试与验收

## 11.1 Core 单元测试

至少覆盖：

1. 完成当前 stop 后 `nextStop` 前进；
2. optional 可跳过；
3. required 不可跳过；
4. skipped 不计 completed；
5. completed + skipped 可使 Day resolved；
6. 起点不计分母；
7. moved 不成为 nextStop；
8. App 重建 execution state 后能从持久层恢复；
9. pending sync 不回退本地状态。

## 11.2 Repository 测试

覆盖：

- save → reload；
- 多 trip 隔离；
- duplicate action id 幂等；
- sync failure 保留 pending；
- sync success 清 pending。

## 11.3 Map / Location

人工验收：

- 权限允许；
- 权限拒绝；
- 真机定位；
- 当前下一站 marker；
- 高德可用；
- 高德不可用 Apple Maps 回退。

## 11.4 Check-in

验收：

- 相机拍照；
- PhotosPicker 选多张；
- 删除照片；
- 保存打卡；
- App 重启后仍存在；
- 无网络可保存；
- 有网络后同步状态改变。

## 11.5 Weather

验收：

- 正常数据；
- 缓存；
- 无网络；
- API 失败；
- 不出现演示天气。

## 11.6 UI

至少在：

- iPhone 16；
- iPhone 16 Pro Max；
- Dynamic Type 大号文字；

检查：

- 无裁切；
- 无重叠；
- 44pt 命中区；
- 中文长名称；
- 深浅状态；
- Tab 选择态；
- Map 底卡；
- 打卡图片网格。

---

# 12. V1 完成定义

只有同时满足以下条件，才可称为“青甘随行原生 iOS V1 可用于真实旅途”：

- Day1–Day10 可真实读取；
- Today 能正确解析当前 Day；
- 今日清单可完成 / 跳过；
- App 重启后执行状态不丢失；
- App 内可查看真实地图与预设 POI；
- 能获取前台当前位置；
- 能一键打开高德导航，失败时回退 Apple Maps；
- 首页天气来自真实数据或明确缓存；
- 能拍照 / 选照片并保存真实打卡；
- 无网络时核心旅途执行功能可用；
- 多机同步边界已具备，远端接口可用时能同步；
- preTrip / ready / postTrip 回归正确；
- Core tests 和 Xcode Simulator build 全部通过；
- Release 不受 Debug Date Scheme 参数污染。

---

# 13. V2 设计审计结论

V2 保留了 V1 正确的架构基础：

- SwiftUI 五栏；
- Today 根状态；
- Trip 数据驱动；
- 执行状态独立；
- 高德优先 / Apple Maps 回退；
- TDD；
- 无登录与无位置共享。

同时修复 V1 的四个关键缺口：

1. “路线页像地图但不是真地图” → MapKit；
2. “打卡只有内存布尔状态” → 真实照片打卡；
3. “勾选状态 App 重启会丢” → local-first 持久化；
4. “首页天气只是设计稿字段” → 真实天气数据。

本设计的目标不是做一个完整通用旅行平台，而是做一个边界明确、能够支撑本次 10 天青甘大环线真实执行的原生 iOS V1。
