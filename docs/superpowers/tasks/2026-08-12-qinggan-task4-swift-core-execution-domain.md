# QG-V1 Task 4 / 7 — Swift Core Family & Execution Pure Domain

`protocolVersion: v2`

## Task Status

`DESIGN_READY`

Execution is authorized only after Codex reads this task record and `docs/superpowers/GITHUB_TASK_LOOP_V1.md`, posts `CODEX_IN_PROGRESS` on the Task 4 PR, verifies RED, and then implements GREEN. Codex must stop at `CODEX_DONE / WAIT_FOR_CHATGPT_AUDIT`.

## Baseline

Approved Task 3 final head:

`10e1b94bb4d088953424d860d9fb88a4962af4d8`

Task 4 branch:

`codex/qinggan-v1-task4-swift-core-execution-domain`

This is a stacked task on the approved Task 3 branch. Task 3 PR #5 remains unmerged unless the user separately authorizes merge.

## Authoritative Inputs

- Workflow protocol: `docs/superpowers/GITHUB_TASK_LOOP_V1.md`
- Approved family-sync implementation plan: `docs/superpowers/plans/2026-08-11-qinggan-task1-family-sync.md`
- Approved family-sync design: `docs/superpowers/specs/2026-08-11-qinggan-task1-family-sync-design.md`
- Existing Swift trip domain: `ios/QingGanCore/Sources/QingGanCore/Domain/TripModels.swift`
- Task 3 server contract: `docs/superpowers/tasks/2026-08-12-qinggan-task3-execution-api.md`

If this task record conflicts with older examples in the implementation plan, this task record is authoritative for Task 4 because it has been normalized against the actually implemented Task 2/3 server contracts.

---

# 01 Task Design

## Goal

Add a UI-free, network-free Swift Core domain for family identity, execution snapshot decoding, pending execution intent, and deterministic execution projection.

The Core layer must answer, using only immutable inputs:

```text
Trip plan
+ latest confirmed server execution snapshot
+ locally persisted pending actions
= effective stop status / next stop / day progress / pending reconciliation meaning
```

This task does **not** perform network synchronization. It only provides the value types and pure algorithms that later Task 5/6/7 will use.

## Frozen Scope

Create exactly these production files unless a compile-required neighboring Core file needs a minimal edit:

- `ios/QingGanCore/Sources/QingGanCore/Domain/FamilyModels.swift`
- `ios/QingGanCore/Sources/QingGanCore/Domain/ExecutionModels.swift`
- `ios/QingGanCore/Sources/QingGanCore/Presentation/TripExecutionState.swift`

Create tests:

- `ios/QingGanCore/Tests/QingGanCoreTests/FamilyModelsTests.swift`
- `ios/QingGanCore/Tests/QingGanCoreTests/TripExecutionStateTests.swift`

Minimal modification to an existing QingGanCore file is allowed only when required to reuse an existing shared codec/helper cleanly. Do not refactor unrelated trip/navigation models.

## Explicit Exclusions

Do **not** implement or modify in Task 4:

- `QingGanApp` application target
- Keychain
- Family Join Token storage
- Device Token storage
- URLSession/network clients
- API request sending
- execution file repository/outbox
- 10-second polling
- retry/backoff coordinator
- network reachability
- SwiftUI views
- Observation stores/view models
- Today UI mutation buttons
- MapKit/CoreLocation
- weather
- photo check-in
- WebSocket/SSE
- backend Java/Flyway files

No `SwiftUI`, `Security`, `Network`, or app-target imports are allowed in the new Core files. `Foundation` is allowed.

---

## Family Domain Contract

### FamilyRole

Use the existing `NormalizedStringCodable` convention so the Swift raw values remain normalized/lowercase while server JSON continues to decode from and encode to uppercase codes.

```swift
public enum FamilyRole: String, CaseIterable, NormalizedStringCodable, Sendable {
    case father
    case mother
    case olderSister = "older_sister"
    case youngerBrother = "younger_brother"
    case grandfather
    case grandmother

    public var label: String { get }
}
```

Stable labels:

```text
father          → 爸爸
mother          → 妈妈
olderSister     → 姐姐
youngerBrother  → 弟弟
grandfather     → 爷爷
grandmother     → 奶奶
```

Do not use Chinese display text as the persistence/API identity key.

### Family binding values

Required public values:

```swift
public enum FamilyRoleBindingStatus: String, NormalizedStringCodable, Sendable {
    case available
    case bound
}

public struct FamilyRoleOption: Codable, Equatable, Sendable {
    public let role: FamilyRole
    public let label: String
    public let bindingStatus: FamilyRoleBindingStatus
    public let boundDeviceDisplayName: String?
    public let boundAt: Date?
    public let bindingVersion: Int64
}

public struct FamilyRolesSnapshot: Codable, Equatable, Sendable {
    public let tripID: String
    public let roles: [FamilyRoleOption]
}

public enum DeviceBindingStatus: String, Codable, Sendable {
    case active
    case revoked
}

public struct DeviceBindingSnapshot: Codable, Equatable, Sendable {
    public let tripID: String
    public let role: FamilyRole
    public let deviceID: String
    public let deviceName: String
    public let bindingVersion: Int64
    public let status: DeviceBindingStatus
}
```

Server `/family/me` success does not need to contain a JSON `status` field. Task 5 may map a successful authenticated response to `.active`; revocation/authorization failure is represented by HTTP error/state. Task 4 only defines the pure client domain value.

For server-backed family DTO decoding, use explicit coding keys where JSON uses `tripId`, `deviceId`, etc. `boundAt` is an ISO timestamp when present.

---

## Execution Domain Contract

### StopExecution

```swift
public struct StopExecution: Codable, Equatable, Sendable {
    public let stopID: String
    public let status: StopStatus
    public let updatedAt: Date
    public let updatedByRole: FamilyRole
    public let updatedByDeviceID: String
}
```

It must decode the Task 3 server fields:

```json
{
  "stopId": "12",
  "status": "COMPLETED",
  "updatedAt": "2026-08-15T03:26:04Z",
  "updatedByRole": "FATHER",
  "updatedByDeviceId": "device-dad-a"
}
```

### TripExecutionSnapshot

```swift
public struct TripExecutionSnapshot: Codable, Equatable, Sendable {
    public let schemaVersion: String
    public let tripID: String
    public let revision: Int64
    public let status: TripLifecycleStatus
    public let actualStartDate: Date?
    public let updatedAt: Date
    public let stopStates: [StopExecution]
}
```

Decode/encode formats must match the implemented Task 3 server contract:

- `tripId`: string
- `revision`: integer
- lifecycle status: uppercase server string such as `PLANNING`, `STARTED`, `COMPLETED`
- `actualStartDate`: date-only `yyyy-MM-dd`, nullable
- `updatedAt`: ISO timestamp
- stop actors/status: uppercase server enum strings

Reuse existing Core date codecs where suitable instead of inventing a second inconsistent date interpretation.

### Pending intent

```swift
public enum ExecutionAction: String, NormalizedStringCodable, Sendable {
    case start
    case arrive
    case complete
    case skip
}

public struct PendingExecutionAction: Codable, Equatable, Identifiable, Sendable {
    public let id: UUID          // requestId
    public let tripID: String
    public let stopID: String?
    public let action: ExecutionAction
    public let occurredAt: Date
    public let createdAt: Date
    public let deviceID: String
    public let bindingVersion: Int64
}
```

No Device Token, Family Join Token, Authorization header, or other secret may exist in `PendingExecutionAction`.

### Pending reconciliation meaning

Define:

```swift
public enum PendingActionResolution: Equatable, Sendable {
    case retryable
    case resolvedNoOp
    case invalid
}
```

Meaning:

- `retryable`: based on confirmed server/plan state, the intent is still legal and may later be submitted with a fresh revision.
- `resolvedNoOp`: authoritative state already satisfies or supersedes the intent, so it must not be replayed.
- `invalid`: the intent is not legal for the authoritative state/plan rule and requires caller intervention rather than automatic replay.

Minimum semantics:

```text
server COMPLETED + pending SKIP      → resolvedNoOp
server COMPLETED + pending ARRIVE    → resolvedNoOp
server COMPLETED + pending COMPLETE  → resolvedNoOp
server SKIPPED + pending COMPLETE    → retryable when the stop is optional
server SKIPPED + pending SKIP        → resolvedNoOp
server ARRIVED + pending ARRIVE      → resolvedNoOp
server PLANNED + legal ARRIVE        → retryable
server PLANNED + legal COMPLETE      → retryable
required stop + pending SKIP         → invalid
MOVED + ordinary ARRIVE/COMPLETE/SKIP → invalid
```

`START` resolution:

```text
confirmed lifecycle STARTED/COMPLETED → resolvedNoOp
confirmed lifecycle PLANNING           → retryable
```

Task 4 does not perform retry. It only classifies.

---

## TripExecutionState Projection Contract

```swift
public struct TripDayProgress: Equatable, Sendable {
    public let completed: Int
    public let resolved: Int
    public let total: Int
}

public struct TripExecutionState: Equatable, Sendable {
    public init(
        trip: Trip,
        snapshot: TripExecutionSnapshot?,
        pendingActions: [PendingExecutionAction]
    )

    public func status(for stop: TripStop) -> StopStatus
    public func nextStop(on day: TripDay) -> TripStop?
    public func progress(for day: TripDay) -> TripDayProgress
    public func resolution(of action: PendingExecutionAction) -> PendingActionResolution
}
```

### One effective-status algorithm

There must be one deterministic algorithm for a stop:

```text
1. begin with plan `TripStop.status`
2. apply matching confirmed server override, if present
3. apply matching local pending stop actions in `createdAt` order
4. each pending transition must pass the same legal transition rules as the server
5. illegal pending transition is ignored for optimistic projection
```

Do not implement separate slightly-different status algorithms for progress and nextStop.

### Local projection transition rules

Mirror Task 3 server rules:

```text
PLANNED  + ARRIVE   → ARRIVED
PLANNED  + COMPLETE → COMPLETED
PLANNED  + SKIP     → SKIPPED only when optional
ARRIVED  + COMPLETE → COMPLETED
ARRIVED  + SKIP     → SKIPPED only when optional
SKIPPED  + COMPLETE → COMPLETED only when optional
```

Forbidden projection transitions include:

```text
COMPLETED regression
required SKIP
SKIPPED → PLANNED
user action against MOVED
```

`START` does not mutate an individual stop status.

### nextStop

`nextStop(on:)` must use projected effective statuses and stable stop sequence order.

Rules:

- `ORIGIN` stops are not actionable next stops.
- `MOVED` is not a next stop.
- `COMPLETED` and `SKIPPED` are resolved and are skipped when finding the next stop.
- `ARRIVED` is not resolved, so it remains the current/next actionable stop until completed/skipped.
- return `nil` when no actionable unresolved stop remains.

### Day progress

For `progress(for:)`:

- `ORIGIN` is excluded from `total`.
- `COMPLETED` increments both `completed` and `resolved`.
- `SKIPPED` increments `resolved` only.
- `PLANNED` / `ARRIVED` do not increment `resolved`.
- `MOVED` does not count as completed/resolved. Do not invent a new meaning for it in this task.

Keep `completed <= resolved <= total` for ordinary seeded trip data.

---

# 02 Implementation Plan

## Step 1 — RED family enum/value tests

Create `FamilyModelsTests.swift` first.

Required tests include:

```swift
func testFamilyRoleDecodesServerCodesAndHasStableChineseLabels() throws
func testFamilyRoleEncodesServerUppercaseCodes() throws
func testFamilyRolesSnapshotDecodesTask2Shape() throws
```

At minimum verify:

```text
"FATHER"       → .father → 爸爸
"MOTHER"       → .mother → 妈妈
"OLDER_SISTER" → .olderSister → 姐姐
"YOUNGER_BROTHER" → .youngerBrother → 弟弟
"GRANDFATHER"  → .grandfather → 爷爷
"GRANDMOTHER"  → .grandmother → 奶奶
AVAILABLE / BOUND decode using the same normalization convention
```

Run focused tests and preserve RED evidence caused by missing Task 4 types.

## Step 2 — GREEN family values

Implement `FamilyModels.swift` with public initializers needed by tests/later app layers, explicit coding keys, Sendable conformance, and no SwiftUI dependency.

Use the existing `NormalizedStringCodable` behavior rather than duplicating custom uppercase/lowercase enum logic.

## Step 3 — RED Task 3 execution JSON decoding tests

Add tests using inline JSON shaped exactly like the implemented Task 3 API, including:

```json
{
  "schemaVersion": "1.0",
  "tripId": "qinggan-2026-family",
  "revision": 22,
  "status": "STARTED",
  "actualStartDate": "2026-08-13",
  "updatedAt": "2026-08-15T03:26:04Z",
  "stopStates": [
    {
      "stopId": "12",
      "status": "COMPLETED",
      "updatedAt": "2026-08-15T03:26:04Z",
      "updatedByRole": "FATHER",
      "updatedByDeviceId": "device-dad-a"
    }
  ]
}
```

Assert date-only actualStartDate, ISO updatedAt, uppercase lifecycle/status/actor decoding, and exact revision/IDs.

Also test a nullable `actualStartDate` PLANNING snapshot.

## Step 4 — GREEN execution values and pending intent

Implement `ExecutionModels.swift`.

Required public initializers must allow later app tests to construct values without JSON fixtures.

Add Codable round-trip evidence for `PendingExecutionAction` and ensure its encoded representation contains no token/secret field.

## Step 5 — RED projection/reconciliation tests

Create `TripExecutionStateTests.swift` and cover all of these directly:

```text
server COMPLETED overrides plan PLANNED
pending COMPLETE optimistically overrides confirmed PLANNED
pending actions on the same stop apply in createdAt order
illegal optimistic transition is ignored
origin excluded from progress total
moved excluded from nextStop
skipped counts resolved but not completed
completed counts both completed and resolved
nextStop advances after completed/skipped
arrived remains nextStop until resolved
server COMPLETED makes pending SKIP resolvedNoOp
server SKIPPED keeps pending COMPLETE retryable for optional stop
required pending SKIP is invalid
START is retryable while lifecycle PLANNING
START is resolvedNoOp once lifecycle STARTED
```

Tests should construct a minimal in-memory `Trip`/`TripDay`/`TripStop` fixture. Do not depend on network, file system, app target, or production fixture JSON unless a tiny existing Core fixture helper is clearly reusable.

## Step 6 — GREEN one-source projection algorithm

Implement `TripExecutionState.swift`.

Prefer private pure helpers for:

```text
confirmedStatus(for:)
projectedStatus(for:)
transition(current:action:isOptional:)
```

`status(for:)`, `nextStop(on:)`, `progress(for:)`, and `resolution(of:)` must share those semantics rather than reimplementing transition rules independently.

Pending actions must be ordered deterministically by `createdAt`; if timestamps tie, use UUID string as a deterministic secondary key.

## Step 7 — Verification

Run at minimum:

```bash
swift test --package-path ios/QingGanCore
swift build --package-path ios/QingGanCore
git diff --check
```

Also run the existing applicable iOS/Core GitHub Actions workflow for the final head.

No backend Maven rerun is required solely for Task 4 because backend production code is frozen from approved Task 3; however, if the repository CI automatically runs backend checks on the stacked PR, do not disable them.

## Step 8 — Review and execution report

Before posting `CODEX_DONE`:

- inspect final diff for scope leakage;
- confirm no `QingGanApp` files changed;
- confirm no network/UI/security imports entered Core;
- confirm every new persisted/API value is Codable + Sendable where specified;
- confirm tests prove both projection and reconciliation semantics;
- confirm PR remains unmerged.

Post `03 Codex Execution Report` using `protocolVersion: v2` and end with:

`Next: WAIT_FOR_CHATGPT_AUDIT`

Do not start Task 5.

---

# Acceptance Criteria

Task 4 is `CODEX_DONE` only when all are true:

- six stable roles decode from Task 2 uppercase server codes and expose stable Chinese labels;
- family role/binding values are UI-free, Sendable, and Codable where specified;
- Task 3 execution snapshot JSON decodes exactly, including date-only `actualStartDate` and ISO timestamps;
- pending actions contain request/device/binding intent but no Device Token or other secret;
- server stop override takes precedence over plan state;
- legal local pending actions project optimistically in deterministic order;
- illegal pending transitions do not corrupt projected status;
- progress follows origin/completed/skipped rules;
- nextStop follows resolved/moved/arrived rules;
- pending reconciliation distinguishes retryable / resolvedNoOp / invalid for the frozen cases;
- `swift test --package-path ios/QingGanCore` is GREEN;
- `swift build --package-path ios/QingGanCore` is GREEN;
- final applicable GitHub Actions/Core CI is GREEN;
- no app-target, network, Keychain, polling, UI, backend, Task 5, or other excluded scope was implemented;
- Codex posts `03 Codex Execution Report` with RED/GREEN/build/CI/final SHA evidence;
- Task 4 PR remains unmerged;
- final state is `WAIT_FOR_CHATGPT_AUDIT`.

## Codex Execution Model

Recommended: **GPT-5.6 Luma / strongest available coding model**  
Reasoning: **High**

## Stop Rule

Codex completion is not approval. After posting the execution report, stop. ChatGPT performs an independent GitHub Audit and returns only `APPROVED`, `NEEDS_FIX`, or `BLOCKED` before any Task 5 work begins.
