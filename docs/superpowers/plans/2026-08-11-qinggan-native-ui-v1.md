# 青甘随行原生 SwiftUI UI V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有单页 QingGanApp 实现为已确认的五栏原生 SwiftUI 旅途执行助手。

**Architecture:** `QingGanCore` 新增带单元测试的纯值执行状态；应用层 `@Observable` store 持有它并为五个 Tab 提供同一份派生状态。V1 不接入地图 SDK、照片权限或写入持久化。

**Tech Stack:** Swift 6、SwiftUI、Observation、XCTest、iOS 17、Xcode 本地 Swift Package。

## Global Constraints

- 匹配 `UI/UI.png` 的层级、青蓝/翡翠青、圆角卡片与中文文案；不编造旅行摄影或地图瓦片。
- 保持高德优先、Apple Maps 回退和既有坐标规则；所有交互区至少 44×44pt。
- 仅内存更新 stop 状态；不改 fixture、后端 API 或 Core 解码契约。
- 不暂存或提交未跟踪的 `UI/` 目录。

---

### Task 1: 添加测试优先的执行状态

**Files:**
- Create: `ios/QingGanCore/Sources/QingGanCore/Presentation/TripExecutionState.swift`
- Create: `ios/QingGanCore/Tests/QingGanCoreTests/TripExecutionStateTests.swift`

**Interfaces:** Produces `TripExecutionState`, `TripDayProgress`, `complete(stopID:)`, `skip(stopID:)`, `status(for:)`, `nextStop(on:)`, `progress(for:)`, `isDayResolved(_:)`.

- [ ] **Step 1: 先写失败测试**

```swift
func testCompletingTheCurrentStopAdvancesNextStopAndProgress() throws {
    let trip = try fixtureTrip()
    let day = trip.days[4]
    var state = TripExecutionState(trip: trip)
    let current = try XCTUnwrap(state.nextStop(on: day))
    XCTAssertTrue(state.complete(stopID: current.id))
    XCTAssertEqual(state.status(for: current), .completed)
    XCTAssertNotEqual(state.nextStop(on: day)?.id, current.id)
    XCTAssertEqual(state.progress(for: day).completed, 1)
}
```

- [ ] **Step 2: 验证红灯**

Run: `swift test --package-path ios/QingGanCore --filter TripExecutionStateTests/testCompletingTheCurrentStopAdvancesNextStopAndProgress`

Expected: 因 `TripExecutionState` 未定义而编译失败。

- [ ] **Step 3: 最小实现**

```swift
public struct TripDayProgress: Equatable, Sendable {
    public let completed: Int
    public let total: Int
    public var fraction: Double { total == 0 ? 0 : Double(completed) / Double(total) }
}

public struct TripExecutionState: Equatable, Sendable {
    public let trip: Trip
    private var overrides: [String: StopStatus] = [:]
    public init(trip: Trip) { self.trip = trip }
    public func status(for stop: TripStop) -> StopStatus { overrides[stop.id] ?? stop.status }
    @discardableResult public mutating func complete(stopID: String) -> Bool { set(stopID, to: .completed) }
}
```

`set` 拒绝未知 ID 以及 completed/skipped 间的反向转换；`skip` 只允许 optional；`nextStop` 排除 origin、completed、skipped、moved；进度排除 origin、只计 completed；所有非起点节点解决后该日 resolved。

- [ ] **Step 4: 补充失败测试、实现到绿灯、回归并提交**

补写 optional 跳过、必选不可跳过、跳过不加完成数、分母不含起点、日 resolved 测试。

Run: `swift test --package-path ios/QingGanCore`

Expected: 所有 Core 测试通过。

```bash
git add ios/QingGanCore/Sources/QingGanCore/Presentation/TripExecutionState.swift ios/QingGanCore/Tests/QingGanCoreTests/TripExecutionStateTests.swift
git commit -m "feat: add in-memory trip execution state"
```

### Task 2: 接入 observable store 与五栏根壳

**Files:**
- Create: `ios/QingGanApp/TripExecutionStore.swift`
- Modify: `ios/QingGanApp/QingGanApp.swift`, `ios/QingGanApp/Today/TodayView.swift`, `ios/QingGanApp/Today/TodayViewModel.swift`, `QingGanApp.xcodeproj/project.pbxproj`

**Interfaces:** Consumes `TripExecutionState` / `TodaySnapshot`; produces共享 `TripExecutionStore` 和五栏 `TabView`。

- [ ] **Step 1: 创建 store**

```swift
@MainActor @Observable
final class TripExecutionStore {
    private(set) var execution: TripExecutionState?
    private(set) var checkInDate: Date?
    func load(trip: Trip) { execution = TripExecutionState(trip: trip) }
    func complete(_ stop: TripStop) { execution?.complete(stopID: stop.id) }
    func skip(_ stop: TripStop) { execution?.skip(stopID: stop.id) }
    func status(for stop: TripStop) -> StopStatus { execution?.status(for: stop) ?? stop.status }
}
```

增加 `progress(for:)`、`nextStop(on:)`、`isDayResolved(_:)`、`completedDayCount` 和 `markCheckedIn(now:)` 转发。

- [ ] **Step 2: 添加一致的加载边界**

为 `TodaySnapshot` 添加 `let trip: Trip`。`TodayView` 的 `.ready` 使用：

```swift
ReadyTripTabView(snapshot: snapshot, store: store)
    .task(id: snapshot.trip.id) { store.load(trip: snapshot.trip) }
```

保留 loading、preTrip、postTrip、unavailable。`QingGanApp` 创建一个 `@State` store；就绪时创建今日、地图、打卡、行程、我的 Tab（依次用 `sun.max.fill`、`map.fill`、`camera.fill`、`calendar`、`person.crop.circle`）。

- [ ] **Step 3: 登记 project source、构建并提交**

Run: `xcodebuild -project QingGanApp.xcodeproj -scheme QingGanApp -sdk iphonesimulator -configuration Debug -derivedDataPath /tmp/qinggan-derived build CODE_SIGNING_ALLOWED=NO`

Expected: `** BUILD SUCCEEDED **`。

```bash
git add ios/QingGanApp/TripExecutionStore.swift ios/QingGanApp/QingGanApp.swift ios/QingGanApp/Today/TodayView.swift ios/QingGanApp/Today/TodayViewModel.swift QingGanApp.xcodeproj/project.pbxproj
git commit -m "feat: add trip execution app shell"
```

### Task 3: 实现设计系统、今日页和清单

**Files:**
- Create: `ios/QingGanApp/UI/TripDesignSystem.swift`, `ios/QingGanApp/Today/TodayDashboardView.swift`, `ios/QingGanApp/Today/TodayChecklistView.swift`
- Modify: `ios/QingGanApp/Today/TodayView.swift`, `QingGanApp.xcodeproj/project.pbxproj`

- [ ] **Step 1: 建立复用组件**

创建 `lakeBlue`、`emerald`、`desertGold`、`ink`、`surface`；20pt continuous `TripCard`；带 accessibility 百分比的 `TripProgressBar(fraction:)`；每个状态都显示符号、中文、颜色的 `StopStatusBadge`。

- [ ] **Step 2: 以真实 snapshot 做今日页**

显示 Day、日期、出发到目的地、title、计划公里/时长、今日进度、下一站、住宿和四个快捷卡；一键导航/下一站共用推荐导航点；查看清单以 sheet 打开；充电点和住宿展示已有数据。无数据时提供空状态，不伪造天气或图片。

- [ ] **Step 3: 实现清单互动**

按 `.origin`、`.scenic/.destination`、`.transfer`、`.meal`、`.overnight` 分到“出发准备、沿途必经、补能、用餐与休息、住宿”；planned/arrived 可完成，optional 才显示跳过，有导航点显示导航，“旅途记录”选择打卡 Tab。每行状态按钮为 44pt。

- [ ] **Step 4: 构建、检查大字号并提交**

Run: `xcodebuild -project QingGanApp.xcodeproj -scheme QingGanApp -sdk iphonesimulator -configuration Debug -derivedDataPath /tmp/qinggan-derived build CODE_SIGNING_ALLOWED=NO`

Expected: `** BUILD SUCCEEDED **`；以 `-QingGanToday 2026-08-17` 检查中文长名和大字号无裁切。

```bash
git add ios/QingGanApp/UI/TripDesignSystem.swift ios/QingGanApp/Today/TodayDashboardView.swift ios/QingGanApp/Today/TodayChecklistView.swift ios/QingGanApp/Today/TodayView.swift QingGanApp.xcodeproj/project.pbxproj
git commit -m "feat: build today dashboard and checklist"
```

### Task 4: 实现路线、打卡、总览、我的与最终审校

**Files:**
- Create: `ios/QingGanApp/Navigation/RouteNavigationView.swift`, `ios/QingGanApp/CheckIn/JourneyCheckInView.swift`, `ios/QingGanApp/Overview/TenDayOverviewView.swift`, `ios/QingGanApp/Profile/ProfileView.swift`
- Modify: `ios/QingGanApp/Today/TodayView.swift`, `QingGanApp.xcodeproj/project.pbxproj`

- [ ] **Step 1: 使用节点轨迹实现路线页**

以 teal 竖向连接线、节点类型 SF Symbol、状态徽标、当前“下一站”标签实现；显示导航点备注和核验状态。仅有可用导航点才显示底部“导航到下一站”；高德不可用时复用 Apple Maps 回退。

- [ ] **Step 2: 实现 V1 打卡、总览和我的**

打卡页使用“立即打卡”、稳定时间、`全部/景点/美食/住宿` 筛选和 `photo.on.rectangle.angled` 空状态；调用 `markCheckedIn(now:)`，不调用 Photos API。总览显示完成数、百分比，且正确表达已完成/进行中/待开始；我的页仅显示数据来源、会话内状态、隐私与 V1.0。

- [ ] **Step 3: 回归构建与视觉比较**

```bash
swift test --package-path ios/QingGanCore
xcodebuild -project QingGanApp.xcodeproj -scheme QingGanApp -sdk iphonesimulator -configuration Debug -derivedDataPath /tmp/qinggan-derived build CODE_SIGNING_ALLOWED=NO
```

Expected: 所有测试通过且输出以 `** BUILD SUCCEEDED **` 结束。采集今日、清单、路线、打卡、总览，和 `UI/UI.png` 同视口比较，修正可见的层级、留白、圆角、字重、裁切、对比度和选择态偏差。

- [ ] **Step 4: 提交完成项**

```bash
git add ios/QingGanApp/Navigation/RouteNavigationView.swift ios/QingGanApp/CheckIn/JourneyCheckInView.swift ios/QingGanApp/Overview/TenDayOverviewView.swift ios/QingGanApp/Profile/ProfileView.swift ios/QingGanApp/Today/TodayView.swift QingGanApp.xcodeproj/project.pbxproj
git commit -m "feat: complete QingGan native UI v1"
```

## Plan Self-Review

- Spec coverage: Tasks 1–2 cover state and root transitions; Task 3 covers today's core flow; Task 4 covers the other design screens, regression, and visual QA.
- No placeholders: map, weather, photo persistence, and cloud synchronization have explicit V1 boundaries.
- Type consistency: Core exports a value type; app-only `@Observable` store owns mutable UI state; each screen consumes that store.
