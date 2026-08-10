# Native iOS Migration Assessment — 青甘随行

**Protocol:** v2  
**Date:** 2026-08-11  
**Decision:** Native iOS becomes the primary product client. The planned H5 remains a future backup client and is not deleted.

## 1. Audit Snapshot

### Repository state

The repository is a new project on branch `codex/qinggan-v1-001a`. The working tree is clean. Current commits are:

- `6474fea` — foundation architecture specification
- `acc0775` — H5/backend TDD implementation plan
- `0c7a6e3` — initial backend trip domain kernel

The only executable implementation is a Spring Boot domain skeleton plus two `Trip` date-duration unit tests. There is no frontend directory, no Vue/PWA implementation, and no iOS project. The prior H5 design and plan are documentation only, not a Web client that must be migrated or preserved at code level.

### Existing backend implementation

The following JPA domain types already exist:

- `Trip`: code, name, start/end date, inclusive duration, status, revision, timestamps.
- `TripDay`: order, date, title, type, distance/drive displays, overnight place.
- `Place`: city/scenic/navigation/overnight/transfer data, semantic priority, duration, WGS84 and GCJ02 coordinate pairs.
- `TripStop`: day/place sequencing, stop type, priority, planned timings, optional flag, status.
- `NavigationPoint`: a Place-owned point with name, type, **GCJ02** coordinates, keyword, recommendation flag, and warning note.
- `TripProgress`: shared trip state fields, but no mutation behaviour or persistence migration yet.

The existing `NavigationPoint` model is the correct conceptual starting point for the Native core feature. It does not yet provide WGS84 coordinates required for reliable Apple Maps/MapKit use, nor a database/API/seed implementation.

### Missing runtime capabilities

None of the following is currently implemented:

- Flyway schema or Day1-Day10 seed data;
- MySQL/Redis runtime configuration;
- REST controllers, DTOs, or API contract;
- Device registration, revision mutation, or append-only ChangeLog;
- weather, hotel/stay, POI, or navigation recommendation API;
- H5/PWA pages or deployment configuration;
- CoreLocation, native map launching, cache, notification, or SwiftUI code.

The previous task’s desired endpoints are documented but do not exist at runtime. Consequently, there is no existing weather API or “recommended next attraction” implementation to reuse beyond the domain design.

### Toolchain state

The machine initially had no Java/Maven/Node/Docker on PATH. Temporary JDK 17 and Maven were used outside the repository to run the two backend tests. Docker/Docker Desktop/Colima is not installed, so Testcontainers and Compose verification are blocked. Full Xcode, Simulator, `xcodebuild`, and `simctl` are also absent; only Command Line Tools are selected.

## 2. Migration Mapping

| Category | Current evidence | Native direction |
| --- | --- | --- |
| Reusable server domain | `Trip`, `TripDay`, `Place`, `TripStop`, `NavigationPoint`, `TripProgress` skeleton | Complete and expose as a versioned REST document consumed by iOS and backup H5. |
| Reusable business rules | Inclusive ten-day duration validation; priority and stop/navigation enums | Move presentation-independent next-stop, current-day, and navigation-point selection into a testable Swift domain package. Server remains authoritative for shared status. |
| Recommended navigation point | JPA type exists but has no data/API and only GCJ02 coordinates | Keep the concept; add explicit WGS84 support and return all points plus one recommended point per attraction. |
| Web UI | No implementation exists | Do not create as the primary client. Keep the documented H5 plan as a future backup-client contract target. |
| Weather | Mentioned only in requested future scope | Do not invent a client-side weather system. Define a separate server contract only after an existing provider/service is available. |
| Stay/Tonight | `TripDay.overnightPlace` can name a city/place, not a hotel | Add an optional `Stay` API model later with hotel name, address, WGS84/GCJ02 coordinates, phone, check-in note, and parking note. |
| Shared family status | `TripProgress` skeleton only | Preserve server-authoritative shared state and optimistic revisions. Store no family member locations. |

## 3. Required Contract Corrections Before iOS Work

### Navigation coordinate policy

The existing model uses WGS84 and GCJ02 correctly for `Place`, but `NavigationPoint` has only GCJ02 coordinates. Native mapping requires an unambiguous coordinate-system boundary:

- Apple MapKit and Apple Maps destinations use WGS84 coordinates.
- Chinese map deep links may require GCJ02 coordinates; provider-specific URL generation must select only the coordinate pair documented for that provider.
- The API must return `latitudeWgs84`, `longitudeWgs84`, `latitudeGcj02`, and `longitudeGcj02` separately for every NavigationPoint. A response without the requested coordinate system must fall back to keyword/address search rather than silently using the other coordinate system.

### Navigation point response shape

Keep an attraction’s places separate from its navigation targets. The iOS DTO should express:

```text
AttractionStop
├─ stopId, sequence, status, priority, optional
├─ attraction: Place
├─ recommendedNavigationPoint: NavigationPoint?
└─ alternativeNavigationPoints: [NavigationPoint]

NavigationPoint
├─ id, name, type, note, address, navigationKeyword
├─ latitudeWgs84, longitudeWgs84
├─ latitudeGcj02, longitudeGcj02
└─ isRecommended
```

The existing generic `Place` relationship can remain the storage model. API aggregation, not a duplicate database “Attraction” entity, should express attraction-specific navigation information.

### Itinerary and shared-state contract

Implement the planned read contract first:

```text
GET /api/v1/trips/{tripCode}/itinerary
GET /api/v1/trips/{tripCode}/progress
```

The first response needs the entire ordered Day1-Day10 document, stops, places, all navigation points, a precomputed `recommendedNavigationPoint` per relevant stop, tonight/stay data when available, shared progress, revision, and `generatedAt`.

Write endpoints remain server-owned and require `deviceToken` plus `expectedRevision`. iOS must never calculate or fabricate shared completion state locally; it may show optimistic UI only after a server acknowledgement.

### Weather contract

No weather server contract is present. Do not block the vertical slice on it. When the service is available, add a separate optional endpoint such as:

```text
GET /api/v1/trips/{tripCode}/weather?dayNumber={dayNumber}
```

It should return source timestamp, forecast location, temperature, conditions, wind/UV summaries, and a server-provided travel advice string. Cache it independently from the itinerary so stale weather cannot make the itinerary unavailable.

## 4. Recommended iOS Architecture

Create one native project rooted at `ios/QingGanTravel/` with no WebView dependency. Use a small Swift Package, `QingGanCore`, for pure domain and infrastructure logic so tests can execute without SwiftUI or a simulator.

```text
ios/
├── QingGanTravel.xcodeproj
├── QingGanTravel/
│   ├── App/                    App entry, dependency container, environment
│   ├── Presentation/
│   │   ├── Today/              TodayView, TodayViewModel, route cards
│   │   ├── Trip/               overview/day detail (after vertical slice)
│   │   ├── Map/                route/points visualization (after slice)
│   │   ├── Memories/           empty state only
│   │   └── Shared/             loading, error, offline and accessibility UI
│   └── Resources/              asset catalog and non-secret config template
├── QingGanCore/
│   └── Sources/QingGanCore/
│       ├── Domain/             immutable models and use cases
│       ├── Repository/         protocols and implementations
│       ├── Remote/             URLSession client and wire DTOs
│       ├── Local/              file cache and cache metadata
│       ├── Location/           CoreLocation abstraction
│       ├── Navigation/         provider detection and URL building
│       └── Notifications/      local-notification abstraction
└── QingGanTravelTests/         unit, integration, and UI tests
```

### Boundaries

```text
SwiftUI View
  → @MainActor ViewModel
  → Use case (ResolveToday / ResolveNextStop / StartNavigation)
  → Repository protocol
  → Remote URLSession client + Local file cache
```

`TodayView` never performs HTTP requests, reads UserDefaults, or constructs map URLs. `TodayViewModel` receives protocols through the app dependency container. Domain types are immutable `struct`s and use `Date`/`Calendar` injected via a clock protocol for deterministic current-day tests.

### Persistence recommendation

Use an atomic JSON file in Application Support for the complete last-known itinerary document and a small `UserDefaults` record for cache metadata, selected trip code, and non-secret environment selection. Use Keychain only for a server-issued device token if/when shared writes are enabled. Do not introduce SwiftData in the first vertical slice: it adds schema/migration work without improving the required offline itinerary read path.

CoreLocation remains local-only. No location samples, tracks, or member positions are sent to or persisted on the server.

## 5. Core Native Domain Model

```swift
struct Trip: Sendable, Equatable {
    let code: String
    let name: String
    let startDate: Date
    let endDate: Date
    let durationDays: Int
    let revision: Int
}

struct TripDay: Identifiable, Sendable, Equatable {
    let id: Int
    let number: Int
    let date: Date
    let title: String
    let route: [TripStop]
    let stay: Stay?
}

struct TripStop: Identifiable, Sendable, Equatable {
    let id: Int
    let place: Place
    let type: StopType
    let priority: Priority?
    let status: StopStatus
    let isOptional: Bool
    let navigationPoints: [NavigationPoint]
}

struct NavigationPoint: Identifiable, Sendable, Equatable {
    let id: Int
    let name: String
    let type: NavigationPointType
    let note: String?
    let address: String?
    let keyword: String?
    let wgs84: Coordinate?
    let gcj02: Coordinate?
    let isRecommended: Bool
}

struct Stay: Sendable, Equatable {
    let hotelName: String
    let address: String?
    let wgs84: Coordinate?
    let gcj02: Coordinate?
    let phone: String?
    let checkInNote: String?
    let parkingNote: String?
}
```

`ResolveTodayUseCase` returns a stateful result: an in-trip day, pre-trip state, or post-trip/completed state. `ResolveNextStopUseCase` returns the first non-completed/non-skipped stop after shared progress; if none exists, it returns the overnight destination or a graceful “today completed” state. `ResolveRecommendedNavigationPointUseCase` returns the one recommended point, otherwise a stable type-ranked alternative, otherwise a keyword/address fallback.

## 6. Vertical Slice: Today → Navigation

The first implementation must be a narrow, real path:

1. Download the real full itinerary response from the backend.
2. Persist a successful response atomically to the local cache.
3. Resolve the travel day using the injected local calendar/time zone and Trip dates.
4. Resolve the next actionable stop from Progress and its ordered route.
5. Render Today with day/date/route/next stop/recommended navigation point/tonight card.
6. Build available navigation choices from installed-provider capability checks.
7. Open Apple Maps by default; offer installed Amap and Baidu choices without leaving the user to manually copy an address.
8. If the network fails, load the last cached itinerary and visibly label it `当前使用离线行程数据`; show cache timestamp and any stale-weather timestamp separately.

Location and weather are deliberately not dependencies of this acceptance path. If location permission is denied, the screen still works; only live distance/city embellishments are absent.

## 7. Test Plan

### Unit tests in `QingGanCoreTests`

- Inclusive Trip date and calendar/time-zone day resolution including before/after trip dates.
- Current day resolution on every Day1-Day10 boundary.
- First actionable next-stop selection after completed and skipped stops.
- Recommended navigation-point selection, alternative ranking, no-point, and no-coordinate cases.
- Tonight stay selection from the current day.
- Wire DTO decoding against a literal full itinerary fixture.
- Remote-success writes cache; remote-failure reads valid cache; remote-failure with empty cache produces a visible failure.
- Apple Maps URL creation with WGS84 coordinates and keyword fallback.
- Amap/Baidu provider URLs only when their corresponding application availability probe succeeds; unavailable providers are omitted.
- Notification permission abstraction and local-notification request construction.

### Integration/UI tests

- A fake `TripRepository` feeds decoded itinerary → Today view model → Today screen and confirms the next-stop card’s visible name/recommended point.
- Offline cache fixture produces the offline banner and cache timestamp.
- Empty/unavailable navigation target produces a disabled, explained state rather than a crashing action.
- Dynamic Type and dark/light preview/UI checks for Today’s primary card hierarchy.

### Required environments

- `xcodebuild test` against an iPhone simulator for all unit and UI tests.
- Manual real-device check for Apple Maps launch, installed/uninstalled third-party map fallback, local notification permission, and cache after Airplane Mode.

## 8. Risks and Decisions Needed Before Coding

1. **Xcode is not installed.** Native compilation, simulator verification, signing, archives, and device checks cannot currently run. Install full Xcode and an iPhone simulator before beginning the implementation phase.
2. **No backend API or seed exists.** The required real-data vertical slice cannot be built until the backend foundation continues through Flyway seed and the itinerary read endpoint. This is the primary sequencing dependency.
3. **Docker is absent.** MySQL/Testcontainers/Compose verification is still blocked. Docker Desktop must be installed and running for the server foundation’s required verification.
4. **Navigation coordinate ambiguity.** The API must expose coordinate reference systems explicitly. Apple and Chinese map URLs must not share an unlabeled coordinate pair.
5. **Map URL scheme verification.** Provider schemes and capability checks need validation on target iOS versions and real devices during implementation; the abstraction should keep provider URL formats replaceable.
6. **Family Trip access.** A fixed public trip identifier is appropriate only for read-only private distribution. If server writes are enabled, use a server-issued non-secret-in-source device token delivered through environment/configuration, never a Git-committed secret.

## 9. Impact on the Current H5 Foundation Task

- Keep the completed backend domain kernel and its tests.
- Continue backend Tasks 2–4 first: Flyway schema/seed, itinerary read API, devices/progress/revision. These are shared data-layer work and directly enable iOS.
- Defer the previous H5 frontend Task 5. Do not remove its design; it becomes the future backup web client after the API is stable.
- Replace the previous “frontend first” delivery target with the native vertical slice described above.
- Do not begin weather, Map route rendering, advanced location behaviour, memories, or notification strategy in the first native task.

## 10. Recommended Approval Gate

Approve this architecture only if the following sequencing is accepted:

```text
Complete shared backend data contract
  → Install/verify Docker and Xcode
  → Build/test native core and Today vertical slice
  → Verify Apple Maps + cache on simulator/device
  → Add Trip, Map, Memories shells after the real navigation path works
```

This avoids building several attractive but disconnected SwiftUI tabs before the family’s most important action—opening the correct navigation destination—works against real shared itinerary data.
