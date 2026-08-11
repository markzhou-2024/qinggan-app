# 青甘随行原生 SwiftUI UI V1 Implementation Plan V2

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 QingGan 原生 iOS 工程上实现可真实用于 10 天青甘大环线的 SwiftUI 旅途执行助手，包括持久化执行状态、今日清单、MapKit + CoreLocation、真实天气、照片打卡、十天总览与远端同步边界。

**Architecture:** 保留 `TodayViewModel` 负责旅行日期和根生命周期；`QingGanCore` 提供纯值执行状态；App 层 `@Observable TripExecutionStore` 作为五个 Tab 的统一状态入口。所有执行动作 local-first 写入本地 Repository，再由 SyncService 异步同步；MapKit 负责 App 内地图态势，高德 / Apple Maps 负责外部驾车导航。

**Tech Stack:** Swift 6、SwiftUI、Observation、MapKit、CoreLocation、PhotosUI、UIKit Camera bridge、XCTest、iOS 17、Xcode 本地 Swift Package。

## Global Constraints

- 在现有工程上增量开发，不新建第二个 Xcode App 项目。
- 保持现有 `Trip`、`TripDay`、`TripStop`、`Stay`、`NavigationPoint` 解码契约兼容。
- `TodayViewModel` 继续负责 `preTrip / ready / postTrip / unavailable` 根状态。
- Release 不得受 `-QingGanToday` Debug Scheme 参数污染。
- App 内 MapKit 只负责地图态势，不实现 turn-by-turn 驾车导航。
- 驾车导航继续高德优先，Apple Maps 回退。
- 定位只申请 When In Use，不做后台定位和家庭位置共享。
- Checklist、stop 状态和打卡必须持久化，禁止仅内存保存。
- 网络失败不得回滚已经成功写入本地的用户操作。
- 天气必须来自真实数据或明确标记的缓存，禁止写死演示天气。
- SwiftUI View 不硬编码 Day1–Day10 的具体业务路线。
- 所有可点击目标至少 44×44pt；支持 Dynamic Type 和 VoiceOver。
- 不实现登录、行程编辑、社交、推送、后台轨迹。
- 不暂存或提交未跟踪的 `UI/` 参考目录。
- 每个 Task 独立测试、独立 review、独立 commit。

---

# File / Responsibility Map

## QingGanCore

- `ios/QingGanCore/Sources/QingGanCore/Presentation/TripExecutionState.swift`
  - 纯值执行状态、next stop、progress、resolved。

- `ios/QingGanCore/Sources/QingGanCore/Presentation/ChecklistProjection.swift`
  - 在后端 ChecklistItem 完整接入前，将 Trip 数据集中投影为 UI checklist；不得散落到 View。

- `ios/QingGanCore/Tests/QingGanCoreTests/TripExecutionStateTests.swift`
  - 执行态测试。

- `ios/QingGanCore/Tests/QingGanCoreTests/ChecklistProjectionTests.swift`
  - Checklist 映射稳定性测试。

## QingGanApp State / Persistence

- `ios/QingGanApp/Execution/TripExecutionStore.swift`
  - 五栏共享 Observable 状态。

- `ios/QingGanApp/Execution/TripExecutionRepository.swift`
  - 本地执行状态持久化接口。

- `ios/QingGanApp/Execution/FileTripExecutionRepository.swift`
  - V1 JSON / Codable local-first 持久化实现；按 tripID 存储，写入采用原子 replace。

- `ios/QingGanApp/Execution/TripExecutionSyncService.swift`
  - Pending action queue 与远端同步边界。

## System Services

- `ios/QingGanApp/Location/LocationService.swift`
  - Core Location 前台定位。

- `ios/QingGanApp/Weather/WeatherRepository.swift`
  - 真实天气加载与本地缓存。

- `ios/QingGanApp/CheckIn/CheckInRepository.swift`
  - 打卡元数据与照片文件引用持久化。

- `ios/QingGanApp/CheckIn/CheckInModels.swift`
  - `CheckInRecord` / `CheckInPhoto` / sync state。

## SwiftUI

- `ios/QingGanApp/Today/TodayDashboardView.swift`
- `ios/QingGanApp/Today/TodayChecklistView.swift`
- `ios/QingGanApp/Navigation/RouteNavigationView.swift`
- `ios/QingGanApp/CheckIn/JourneyCheckInView.swift`
- `ios/QingGanApp/CheckIn/CheckInEditorView.swift`
- `ios/QingGanApp/Overview/TenDayOverviewView.swift`
- `ios/QingGanApp/Profile/ProfileView.swift`
- `ios/QingGanApp/UI/TripDesignSystem.swift`

---

# Task 1: 冻结执行领域状态与 Checklist Projection

**Files:**
- Create: `ios/QingGanCore/Sources/QingGanCore/Presentation/TripExecutionState.swift`
- Create: `ios/QingGanCore/Sources/QingGanCore/Presentation/ChecklistProjection.swift`
- Create: `ios/QingGanCore/Tests/QingGanCoreTests/TripExecutionStateTests.swift`
- Create: `ios/QingGanCore/Tests/QingGanCoreTests/ChecklistProjectionTests.swift`

**Interfaces:**

Produces:

```swift
public struct TripDayProgress: Equatable, Sendable {
    public let completed: Int
    public let resolved: Int
    public let total: Int
    public var completedFraction: Double
    public var resolvedFraction: Double
}

public struct TripExecutionState: Equatable, Sendable {
    public init(trip: Trip)
    public func status(for stop: TripStop) -> StopStatus
    public mutating func complete(stopID: String) -> Bool
    public mutating func skip(stopID: String) -> Bool
    public func nextStop(on day: TripDay) -> TripStop?
    public func progress(for day: TripDay) -> TripDayProgress
    public func isDayResolved(_ day: TripDay) -> Bool
}

public struct ChecklistProjection {
    public func items(for day: TripDay) -> [ProjectedChecklistItem]
}
```

- [ ] **Step 1: 写 `TripExecutionState` 红灯测试**

至少写：

```swift
func testCompletingCurrentStopAdvancesNextStop() throws
func testOptionalStopCanBeSkipped() throws
func testRequiredStopCannotBeSkipped() throws
func testSkippedCountsResolvedButNotCompleted() throws
func testOriginIsExcludedFromProgressDenominator() throws
func testMovedStopIsNotReturnedAsNextStop() throws
func testAllNonOriginStopsResolvedMarksDayResolved() throws
```

示例：

```swift
func testSkippedCountsResolvedButNotCompleted() throws {
    let trip = try fixtureTrip()
    let day = try XCTUnwrap(trip.days.first(where: { $0.stops.contains(where: \.isOptional) }))
    let optional = try XCTUnwrap(day.stops.first(where: \.isOptional))

    var state = TripExecutionState(trip: trip)
    XCTAssertTrue(state.skip(stopID: optional.id))

    let progress = state.progress(for: day)
    XCTAssertEqual(progress.completed, 0)
    XCTAssertEqual(progress.resolved, 1)
}
```

- [ ] **Step 2: 运行红灯**

```bash
swift test --package-path ios/QingGanCore --filter TripExecutionStateTests
```

Expected: compile / test FAIL because types are not implemented.

- [ ] **Step 3: 最小实现执行态**

Rules:

```text
planned / arrived → unresolved
completed → completed + resolved
skipped → resolved only
moved → excluded from nextStop
origin → excluded from progress denominator
skip only when isOptional == true
completed/skipped cannot silently revert to planned
```

- [ ] **Step 4: 写 Checklist Projection 红灯测试**

覆盖：

```swift
func testStopsAreProjectedInStableSortOrder() throws
func testOvernightStopProjectsToStayCategory() throws
func testOptionalStopPreservesOptionalFlag() throws
func testProjectionDoesNotHardCodeDaySpecificPlaceNames() throws
```

`ChecklistProjection` 只做集中适配，不在 View 中写 switch 业务映射。

- [ ] **Step 5: 实现 Projection 到绿灯**

推荐接口：

```swift
public struct ProjectedChecklistItem: Identifiable, Equatable, Sendable {
    public enum Category: String, Sendable {
        case preparation, scenic, charging, meal, rest, stay, checkIn, reminder
    }

    public let id: String
    public let title: String
    public let subtitle: String?
    public let category: Category
    public let isOptional: Bool
    public let linkedStopID: String?
    public let linkedNavigationPointID: String?
    public let sortOrder: Int
}
```

- [ ] **Step 6: Core 全量测试**

```bash
swift test --package-path ios/QingGanCore
```

Expected: all tests PASS.

- [ ] **Step 7: Commit**

```bash
git add ios/QingGanCore/Sources/QingGanCore/Presentation \
        ios/QingGanCore/Tests/QingGanCoreTests
git commit -m "feat: define trip execution and checklist projection"
```

---

# Task 2: 实现 local-first 持久化与五栏共享 Store

**Files:**
- Create: `ios/QingGanApp/Execution/TripExecutionStore.swift`
- Create: `ios/QingGanApp/Execution/TripExecutionRepository.swift`
- Create: `ios/QingGanApp/Execution/FileTripExecutionRepository.swift`
- Create: `ios/QingGanApp/Execution/TripExecutionSyncService.swift`
- Modify: `ios/QingGanApp/QingGanApp.swift`
- Modify: `ios/QingGanApp/Today/TodayView.swift`
- Modify: `ios/QingGanApp/Today/TodayViewModel.swift`
- Modify: `QingGanApp.xcodeproj/project.pbxproj`

**Interfaces:**

```swift
struct PersistedStopOverride: Codable, Equatable, Sendable {
    let stopID: String
    let status: StopStatus
    let updatedAt: Date
    let actionID: UUID
}

struct PendingExecutionAction: Codable, Identifiable, Equatable, Sendable {
    let id: UUID
    let tripID: String
    let stopID: String
    let status: StopStatus
    let createdAt: Date
}

protocol TripExecutionRepository {
    func loadOverrides(tripID: String) async throws -> [PersistedStopOverride]
    func saveOverride(tripID: String, override: PersistedStopOverride) async throws
    func loadPendingActions(tripID: String) async throws -> [PendingExecutionAction]
    func savePendingAction(_ action: PendingExecutionAction) async throws
    func removePendingAction(id: UUID, tripID: String) async throws
}
```

- [ ] **Step 1: 先写 Repository round-trip 测试**

在 App test target 增加：

```swift
func testSavedOverrideReloadsAfterRepositoryRecreation() async throws
func testTripsAreIsolatedByTripID() async throws
func testPendingActionSurvivesRepositoryRecreation() async throws
func testRemovingPendingActionDoesNotRemoveOverride() async throws
```

使用临时目录构造 `FileTripExecutionRepository(baseURL:)`。

- [ ] **Step 2: 运行红灯**

```bash
xcodebuild test \
  -project QingGanApp.xcodeproj \
  -scheme QingGanApp \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  CODE_SIGNING_ALLOWED=NO
```

Expected: FAIL because repository types do not exist.

- [ ] **Step 3: 实现 File Repository**

要求：

- JSON + Codable；
- 目录：Application Support/QingGan/<tripID>/；
- 原子写；
- 未知 / 损坏文件返回明确 error；
- 不覆盖别的 trip；
- action ID 保持幂等。

- [ ] **Step 4: 实现 TripExecutionStore**

推荐：

```swift
@MainActor
@Observable
final class TripExecutionStore {
    private(set) var execution: TripExecutionState?
    private(set) var persistenceError: String?
    private(set) var pendingActionCount = 0

    func load(trip: Trip) async
    func complete(_ stop: TripStop) async
    func skip(_ stop: TripStop) async
    func status(for stop: TripStop) -> StopStatus
    func progress(for day: TripDay) -> TripDayProgress?
    func nextStop(on day: TripDay) -> TripStop?
    func isDayResolved(_ day: TripDay) -> Bool
}
```

操作顺序：

```text
validate domain transition
→ update in-memory execution
→ write override locally
→ enqueue pending action
→ publish UI
→ request sync attempt
```

若本地写入失败：

- UI 必须显示错误；
- 不声明动作已成功持久化。

- [ ] **Step 5: SyncService 先实现明确边界**

```swift
protocol TripExecutionRemoteClient {
    func push(action: PendingExecutionAction) async throws
}

actor TripExecutionSyncService {
    func syncPending(tripID: String) async
}
```

要求：

- push success → remove pending；
- push failure → keep pending；
- 不回滚本地 override；
- 无 remote client 时使用明确的 disabled client，不伪造同步成功。

- [ ] **Step 6: 接入 Ready 五栏 Shell**

保留根状态：

```text
loading
preTrip
ready
postTrip
unavailable
```

`.ready` 时：

```swift
ReadyTripTabView(snapshot: snapshot, store: store)
    .task(id: snapshot.trip.id) {
        await store.load(trip: snapshot.trip)
    }
```

五 Tab：

```text
今日 sun.max.fill
地图 map.fill
打卡 camera.fill
行程 calendar
我的 person.crop.circle
```

- [ ] **Step 7: 回归 Debug Date**

确保：

- Debug 可使用 `-QingGanToday`；
- Release 配置不自动携带该参数；
- 当前真实系统日期能解析真实 `plannedStartDate / actualStartDate / status / timeZone`。

- [ ] **Step 8: Test + Build**

```bash
swift test --package-path ios/QingGanCore

xcodebuild \
  -project QingGanApp.xcodeproj \
  -scheme QingGanApp \
  -sdk iphonesimulator \
  -configuration Debug \
  -derivedDataPath /tmp/qinggan-derived \
  build CODE_SIGNING_ALLOWED=NO
```

Expected: Core tests PASS and `** BUILD SUCCEEDED **`.

- [ ] **Step 9: Commit**

```bash
git add ios/QingGanApp/Execution \
        ios/QingGanApp/QingGanApp.swift \
        ios/QingGanApp/Today/TodayView.swift \
        ios/QingGanApp/Today/TodayViewModel.swift \
        QingGanApp.xcodeproj/project.pbxproj
git commit -m "feat: persist trip execution state"
```

---

# Task 3: 今日 Dashboard + Checklist + Design System

**Files:**
- Create: `ios/QingGanApp/UI/TripDesignSystem.swift`
- Create: `ios/QingGanApp/Today/TodayDashboardView.swift`
- Create: `ios/QingGanApp/Today/TodayChecklistView.swift`
- Modify: `ios/QingGanApp/Today/TodayView.swift`
- Modify: `QingGanApp.xcodeproj/project.pbxproj`

**Interfaces:**

Consumes:

- `TodaySnapshot`
- `TripExecutionStore`
- `ChecklistProjection`
- `NavigationLauncher`

Produces:

- Today 主页面；
- 今日清单；
- 统一视觉组件。

- [ ] **Step 1: 实现 Design System**

定义：

```swift
enum TripTheme {
    static let lakeBlue: Color
    static let emerald: Color
    static let desertGold: Color
    static let ink: Color
    static let surface: Color
    static let background: Color
}
```

以及：

```text
TripCard
TripProgressBar
StopStatusBadge
TripMetric
EmptyStateCard
```

要求：

- 20pt continuous card radius；
- horizontal padding 16；
- action target 44pt；
- progress VoiceOver value。

- [ ] **Step 2: TodayDashboard 只读真实 snapshot**

展示：

```text
Day badge
date
origin → destination
day title
planned distance
planned drive duration
weather slot
progress
一键导航
查看清单
充电点
住宿
下一站
```

禁止：

```swift
Text("大柴旦 → 敦煌")
Text("18~31°C")
```

这样的业务硬编码。

- [ ] **Step 3: Checklist 使用 Projection**

分组：

```text
出发准备
沿途必经
补能
用餐与休息
住宿
旅途记录
```

每行：

- title / subtitle；
- status badge；
- complete；
- optional skip；
- linked navigation；
- linked check-in。

点击 complete / skip 调：

```swift
await store.complete(stop)
await store.skip(stop)
```

- [ ] **Step 4: UI 状态测试**

至少验证：

```text
完成当前项 → Today progress 更新
skip optional → resolved 更新但 completed 不增
required → 不出现 skip
没有住宿 → 住宿快捷卡为空态
没有导航坐标 → 不出现可用导航按钮
```

- [ ] **Step 5: Simulator Visual QA**

启动 iPhone 16 Simulator，验证：

- 中文长名称；
- Dynamic Type；
- 44pt；
- Tab 选择态；
- 设计稿层级；
- 无硬编码示例数据。

- [ ] **Step 6: Commit**

```bash
git add ios/QingGanApp/UI \
        ios/QingGanApp/Today \
        QingGanApp.xcodeproj/project.pbxproj
git commit -m "feat: build today dashboard and checklist"
```

---

# Task 4: MapKit + CoreLocation + 外部导航

**Files:**
- Create: `ios/QingGanApp/Location/LocationService.swift`
- Create: `ios/QingGanApp/Navigation/RouteNavigationView.swift`
- Create: `ios/QingGanApp/Navigation/RouteMapContent.swift`
- Modify: `ios/QingGanApp/Navigation/NavigationLauncher.swift` if it already exists, otherwise create it at this path
- Modify: `ios/QingGanApp/Info.plist`
- Modify: `QingGanApp.xcodeproj/project.pbxproj`

**Interfaces:**

```swift
@MainActor
@Observable
final class LocationService: NSObject, CLLocationManagerDelegate {
    private(set) var authorizationStatus: CLAuthorizationStatus
    private(set) var location: CLLocation?
    private(set) var errorMessage: String?

    func requestWhenInUseAuthorization()
    func start()
    func stop()
}
```

`NavigationLauncher`：

```swift
@MainActor
protocol NavigationLaunching {
    func canOpenAmap() -> Bool
    func openNavigation(to point: NavigationPoint) -> Bool
}
```

- [ ] **Step 1: LocationService 状态测试 / delegate 测试**

通过可注入 location manager wrapper 测：

```text
notDetermined → request permission
denied → no crash, error state
authorized → start updates
location update → published location
```

- [ ] **Step 2: 配置 Info.plist**

添加明确用户文案：

```text
NSLocationWhenInUseUsageDescription
“用于在青甘旅途中显示当前位置、计算下一站距离和记录打卡地点。”
```

- [ ] **Step 3: 实现真实 MapKit 页面**

使用 SwiftUI `Map`：

- 当前 location；
- stop / navigation point markers；
- 当前下一站高亮；
- completed 灰化；
- charging / stay / scenic 使用不同 symbol；
- map camera 能定位当前 Day 所有节点。

有 route geometry：

```text
MapPolyline(real geometry)
```

没有 geometry：

```text
以节点顺序连线
+ UI 标注“计划路线示意”
```

不得声称为实时道路路线。

- [ ] **Step 4: 实现高德优先 / Apple Maps 回退**

规则：

```text
坐标有效 + 高德 installed → 高德
坐标有效 + 高德 unavailable → Apple Maps
坐标无效 → return false + UI 提示
```

保留现有 URL encoding / coordinate contract 回归测试。

- [ ] **Step 5: 地图底部下一站卡**

显示：

```text
下一站
name
type
distance when available
[查看详情]
[导航到下一站]
```

当前位置存在时可计算直线距离作为近似值，必须在 UI 标注“约”。

- [ ] **Step 6: 权限拒绝 QA**

拒绝位置权限后：

- Today 可用；
- Checklist 可用；
- Map 仍展示预设点；
- 一键导航仍可用；
- 仅不展示当前位置。

- [ ] **Step 7: Build + 真机验证记录**

Simulator build：

```bash
xcodebuild \
  -project QingGanApp.xcodeproj \
  -scheme QingGanApp \
  -sdk iphonesimulator \
  -configuration Debug \
  -derivedDataPath /tmp/qinggan-derived \
  build CODE_SIGNING_ALLOWED=NO
```

真机人工验证：

```text
When In Use permission
定位点
Map marker
高德打开
Apple Maps fallback
```

- [ ] **Step 8: Commit**

```bash
git add ios/QingGanApp/Location \
        ios/QingGanApp/Navigation \
        ios/QingGanApp/Info.plist \
        QingGanApp.xcodeproj/project.pbxproj
git commit -m "feat: add live trip map and location"
```

---

# Task 5: 真实照片打卡 + 本地持久化

**Files:**
- Create: `ios/QingGanApp/CheckIn/CheckInModels.swift`
- Create: `ios/QingGanApp/CheckIn/CheckInRepository.swift`
- Create: `ios/QingGanApp/CheckIn/FileCheckInRepository.swift`
- Create: `ios/QingGanApp/CheckIn/JourneyCheckInView.swift`
- Create: `ios/QingGanApp/CheckIn/CheckInEditorView.swift`
- Create: `ios/QingGanApp/CheckIn/CameraPicker.swift`
- Modify: `ios/QingGanApp/Info.plist`
- Modify: `QingGanApp.xcodeproj/project.pbxproj`

**Interfaces:**

```swift
struct CheckInRecord: Codable, Identifiable, Equatable, Sendable {
    let id: UUID
    let tripID: String
    let dayNumber: Int
    let stopID: String?
    var locationName: String
    var latitude: Double?
    var longitude: Double?
    let createdAt: Date
    var note: String?
    var photos: [CheckInPhoto]
    var syncState: CheckInSyncState
}

struct CheckInPhoto: Codable, Identifiable, Equatable, Sendable {
    let id: UUID
    let localRelativePath: String
    let createdAt: Date
}

enum CheckInSyncState: String, Codable, Sendable {
    case localOnly
    case pending
    case synced
    case failed
}
```

Repository：

```swift
protocol CheckInRepository {
    func list(tripID: String) async throws -> [CheckInRecord]
    func save(_ record: CheckInRecord) async throws
    func delete(id: UUID, tripID: String) async throws
}
```

- [ ] **Step 1: 写持久化红灯测试**

覆盖：

```swift
func testSavedCheckInReloadsAfterRepositoryRecreation() async throws
func testCheckInsAreSeparatedByTrip() async throws
func testDeletingCheckInRemovesMetadataButNotOtherRecords() async throws
func testPhotoPathsRemainRelativeToCheckInStorageRoot() async throws
```

- [ ] **Step 2: 实现 FileCheckInRepository**

目录：

```text
Application Support/QingGan/<tripID>/checkins/
  records.json
  photos/
    <photo-uuid>.jpg
```

规则：

- 照片存 App 私有目录；
- 元数据只存 relative path；
- 使用原子 JSON 写入；
- 不把用户原相册资产删除；
- 删除打卡时只删除 App 自己复制的照片文件。

- [ ] **Step 3: 配置 Camera Permission**

`Info.plist`：

```text
NSCameraUsageDescription
“用于记录青甘旅途中的景点、住宿和沿途打卡照片。”
```

使用 PhotosPicker 时不主动申请整个相册读取权限。

- [ ] **Step 4: CheckInEditor**

支持：

```text
当前 Day
当前 / 选择 stop
地点名称
当前坐标（如果有）
拍照
PhotosPicker 多选
缩略图
删除待提交照片
一句话
保存
```

PhotosPicker load 失败时显示具体失败项，不丢失已选择成功的图片。

- [ ] **Step 5: JourneyCheckIn Timeline**

分组：

```text
全部
景点
美食
住宿
其他
```

每条：

```text
time
location
note
thumbnail grid
sync badge
```

- [ ] **Step 6: 无权限 / 无网络 QA**

验证：

```text
相机拒绝 → 仍可 PhotosPicker / 纯文字
无定位 → 仍可选预设 stop / 手写地点
无网络 → 保存成功，syncState localOnly/pending
App 重启 → 打卡仍存在
```

- [ ] **Step 7: Test + Build**

运行 App tests、Core tests、Simulator build。

- [ ] **Step 8: Commit**

```bash
git add ios/QingGanApp/CheckIn \
        ios/QingGanApp/Info.plist \
        QingGanApp.xcodeproj/project.pbxproj
git commit -m "feat: add persistent photo check-ins"
```

---

# Task 6: 真实天气 + 10 天总览 + 我的 + 最终验收

**Files:**
- Create: `ios/QingGanApp/Weather/WeatherModels.swift`
- Create: `ios/QingGanApp/Weather/WeatherRepository.swift`
- Create: `ios/QingGanApp/Weather/CachedWeatherRepository.swift`
- Create: `ios/QingGanApp/Overview/TenDayOverviewView.swift`
- Create: `ios/QingGanApp/Overview/TripDayDetailView.swift`
- Create: `ios/QingGanApp/Profile/ProfileView.swift`
- Modify: `ios/QingGanApp/Today/TodayDashboardView.swift`
- Modify: `ios/QingGanApp/Today/TodayView.swift`
- Modify: `QingGanApp.xcodeproj/project.pbxproj`

**Interfaces:**

```swift
struct TripWeather: Codable, Equatable, Sendable {
    let placeName: String
    let observedAt: Date
    let conditionText: String
    let minTemperatureC: Double?
    let maxTemperatureC: Double?
    let currentTemperatureC: Double?
    let windDescription: String?
}

enum WeatherFreshness: Equatable, Sendable {
    case fresh
    case cached(updatedAt: Date)
    case unavailable
}

struct WeatherSnapshot: Equatable, Sendable {
    let weather: TripWeather?
    let freshness: WeatherFreshness
}

protocol WeatherRepository {
    func weather(for day: TripDay) async -> WeatherSnapshot
}
```

- [ ] **Step 1: 天气缓存测试**

覆盖：

```text
remote success → fresh + cache
remote failure + valid cache → cached
remote failure + stale cache → cached with timestamp
no remote + no cache → unavailable
```

“stale cache”允许展示，但 UI 必须显式显示更新时间。

- [ ] **Step 2: 接入现有真实天气数据源**

优先通过项目现有后端天气接口。

如果当前后端已经存在天气 endpoint，复用现有 API client / auth / baseURL，不创建第二套网络栈。

如果当前仓库中尚不存在天气 endpoint，本 Task 仍需实现：

- `WeatherRemoteClient` 协议；
- cache；
- unavailable UI；
- 不写死演示天气。

服务端 endpoint 的实现必须作为独立后端任务跟踪，不能用固定天气替代。

- [ ] **Step 3: Today 天气卡**

显示：

```text
place
condition
current / min / max
wind
freshness
```

缓存时：

```text
“缓存 · 更新于 08:10”
```

不可用：

```text
“天气暂不可用”
```

- [ ] **Step 4: TenDayOverview**

严格读取 `trip.days`，不假定永远恰好 10 个数组下标。

展示：

```text
Day N
origin → destination
planned distance / duration
completed / current / upcoming
```

当前 Day 由 Today date domain 决定。

- [ ] **Step 5: Day Detail**

展示：

```text
route summary
stops
charging
stay
checklist
notes
check-in count
```

只读未来 Day，不修改 current date。

- [ ] **Step 6: Profile**

展示：

```text
App version
trip name
data source
last sync
pending action count
location permission
camera availability
local cache status
```

“清理本地缓存”必须二次确认，并不能误删服务端 Trip。

- [ ] **Step 7: 完整测试矩阵**

Core：

```bash
swift test --package-path ios/QingGanCore
```

App build：

```bash
xcodebuild \
  -project QingGanApp.xcodeproj \
  -scheme QingGanApp \
  -sdk iphonesimulator \
  -configuration Debug \
  -derivedDataPath /tmp/qinggan-derived \
  build CODE_SIGNING_ALLOWED=NO
```

再运行 App test target。

Expected:

```text
Core all PASS
App tests PASS
** BUILD SUCCEEDED **
```

- [ ] **Step 8: 状态场景验收**

逐个验证：

```text
PRE_TRIP
READY Day1
READY middle day
READY final day
POST_TRIP
API unavailable with cache
API unavailable without cache
location denied
camera denied
weather unavailable
local execution pending sync
```

- [ ] **Step 9: 视觉验收**

至少采集：

```text
今日
今日清单
路线地图
打卡时间轴
打卡编辑
10 天总览
Day 详情
我的
```

设备：

```text
iPhone 16
iPhone 16 Pro Max
```

检查：

```text
无裁切
无重叠
44pt target
Dynamic Type
VoiceOver labels
中文长文案
Map bottom sheet
照片网格
离线 badge
同步 badge
```

- [ ] **Step 10: Debug Date Release Gate**

最终检查：

```text
Debug Scheme 可注入 -QingGanToday
Release Scheme 无固定 QingGanToday 参数
真实系统日期 2026-08-11 能解析真实旅行阶段
```

- [ ] **Step 11: Final Commit**

```bash
git add ios/QingGanApp/Weather \
        ios/QingGanApp/Overview \
        ios/QingGanApp/Profile \
        ios/QingGanApp/Today \
        QingGanApp.xcodeproj/project.pbxproj
git commit -m "feat: complete QingGan native trip assistant v1"
```

---

# Integration Gate: 服务端多机同步

本 iOS V2 计划已经定义：

```text
PendingExecutionAction
TripExecutionRemoteClient
TripExecutionSyncService
CheckIn syncState
```

如果现有 Spring Boot API 尚无以下能力，则必须单独下达 backend task：

```text
POST /trip-execution/actions
GET  /trip-execution/state?tripId=...
POST /trip-checkins
GET  /trip-checkins?tripId=...
```

服务端要求：

- action id 幂等；
- trip scope；
- completed 不被旧 planned 覆盖；
- check-in 元数据可增量同步；
- 照片上传采用独立 object/file endpoint；
- 不接受连续后台位置轨迹。

在服务端接口完成前：

- iOS local-first 必须完整可用；
- UI 显示 `本地` / `待同步`；
- 禁止假装多机已经同步成功。

---

# Plan Self-Review

## 1. Spec Coverage

V2 Design 的关键能力均有对应 Task：

- Execution domain → Task 1
- Checklist projection → Task 1
- Local persistence → Task 2
- Shared store / five tabs → Task 2
- Today / checklist → Task 3
- MapKit → Task 4
- CoreLocation → Task 4
- Amap / Apple Maps → Task 4
- Real photo check-in → Task 5
- Check-in persistence → Task 5
- Weather → Task 6
- Ten-day overview → Task 6
- Profile / sync state → Task 6
- Debug date gate → Tasks 2 and 6
- Offline behavior → Tasks 2, 5, 6
- Multi-device sync boundary → Task 2 + Integration Gate

No design requirement is intentionally left to a UI placeholder.

## 2. Placeholder Scan

The plan does not permit:

- hard-coded weather;
- fake map tiles;
- fake photo check-ins;
- memory-only execution state;
- silent sync success.

Where backend capability may not yet exist, the plan defines an explicit protocol, offline behavior and a separate Integration Gate rather than substituting fake data.

## 3. Type Consistency

Shared types are consistently named:

```text
TripExecutionState
TripDayProgress
TripExecutionStore
TripExecutionRepository
PendingExecutionAction
TripExecutionSyncService
LocationService
CheckInRecord
CheckInPhoto
CheckInRepository
WeatherSnapshot
WeatherRepository
```

Later Tasks only consume interfaces defined by earlier Tasks or within their own Task.

---

# Recommended Codex Execution

**Model:** GPT-5.6 Luma / current strongest Codex coding model available  
**Reasoning:** High

Reason:

- 涉及 SwiftUI、Observation、MapKit、CoreLocation、PhotosPicker、持久化和现有日期领域逻辑；
- 需要持续保证现有真实 API / cache / Today 链路不被 UI 改造破坏；
- 涉及权限、异步状态和本地持久化，适合高推理等级；
- 每个 Task 都需要测试 → 实现 → review → commit。

**Execution workflow:** `superpowers:subagent-driven-development` preferred.

每完成一个 Task，必须先返回：

```text
protocolVersion: v2

Task:
Status:
Changed Files:
Tests:
Build:
Runtime Evidence:
Known Gaps:
Commit:
Next Task:
```

再进入下一个 Task。
