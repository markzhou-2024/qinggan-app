# QG-V1 Task 3 / 7 — Execution Snapshot, Start/Stop Mutations, Revision & Idempotency

`protocolVersion: v2`

## Task Status

`DESIGN_READY`

Execution is authorized after Codex reads this task record and `docs/superpowers/GITHUB_TASK_LOOP_V1.md`. Codex must change the task state to `CODEX_IN_PROGRESS` in PR #5 before continuing implementation and must stop at `CODEX_DONE / WAIT_FOR_CHATGPT_AUDIT`.

## 01 Task Design

### Goal

Build the server-authoritative execution state for the fixed Qinghai–Gansu family trip. Task 2 already established family role/device binding and Device Token authentication. Task 3 makes the backend the authority for whether the trip has started and for runtime stop progress, with revision control suitable for multiple family iPhones.

### Authoritative model

- `trip_execution` is the runtime authority for trip execution status and revision.
- Stop execution state is sparse: seeded itinerary data remains the baseline; runtime stop rows exist only when a stop is changed during travel.
- Existing `trip.status` and `trip.actual_start_date` remain compatibility mirrors and must be updated when the authoritative execution state starts.
- Existing itinerary revision behavior must remain compatible with current clients.
- Actor identity must come only from the authenticated active Device Token context from Task 2. Do not accept role/device identity from mutation request bodies.

### Frozen API contract

#### `GET /api/v1/trips/{tripId}/execution`

Authentication: `Authorization: Bearer <Device Token>`.

Successful response includes at minimum:

- `schemaVersion: "1.0"`
- `tripId`
- `revision`
- `status`
- optional `actualStartDate`
- `stopStates` array containing sparse runtime stop overrides

Response header:

`ETag: "execution-revision-<revision>"`

If the client sends the current ETag in `If-None-Match`, return HTTP `304` with the same ETag and no snapshot body.

#### `POST /api/v1/trips/{tripId}/execution/start`

Authentication: Device Token.

Request contains:

- `requestId`
- `expectedRevision`
- optional/client `occurredAt` may be accepted for compatibility/audit input, but it is **not** authoritative for `actualStartDate`

Rules:

- Only the server clock is authoritative.
- Compute `actualStartDate` from server `now` in the Trip timezone.
- `PLANNING -> STARTED` increments execution revision exactly once.
- Mirror `trip.status = STARTED` and `trip.actual_start_date = actualStartDate` in the same successful mutation path.
- Preserve the itinerary compatibility revision behavior already asserted by the RED integration test.
- Return the latest authoritative snapshot and matching ETag.

#### `POST /api/v1/trips/{tripId}/execution/stops/{stopId}/actions`

Authentication: Device Token.

Request contains:

- `requestId`
- `expectedRevision`
- `action`: `ARRIVE | COMPLETE | SKIP`

Runtime transition rules frozen for Task 3:

- Stop mutations require the trip execution status to be `STARTED`; otherwise return `422 TRIP_NOT_STARTED`.
- `ARRIVE`: effective `PLANNED -> ARRIVED`.
- `COMPLETE`: effective `PLANNED | ARRIVED | SKIPPED -> COMPLETED`.
- `SKIP`: only an optional stop may be skipped; required-stop skip is rejected with HTTP 422.
- `COMPLETED` is terminal for Task 3 and must not regress to `ARRIVED`, `SKIPPED`, or `PLANNED`.
- Effective current status is the sparse runtime override when present; otherwise the seeded itinerary stop status.
- A successful stop mutation writes/updates the sparse runtime stop state, records authenticated actor fields, appends one execution action record, and increments execution revision exactly once.

Runtime stop state must expose enough data for subsequent iOS sync, including at least:

- `stopId`
- `status`
- `updatedByRole`
- `updatedByDeviceId`
- update timestamp if present in the approved schema

### Revision control

Every mutation is guarded by `expectedRevision`.

- If `expectedRevision` equals the current execution revision, the mutation may proceed if the domain transition is valid.
- If stale, return HTTP `409` with code `EXECUTION_REVISION_CONFLICT` and include the latest authoritative execution snapshot in `latest`.
- Revision must never increment on rejected mutations.
- Real MySQL concurrency must prove that two competing mutations using the same expected revision cannot both commit successfully.

### Idempotency

`requestId` applies to start and stop mutations.

- Same `requestId` + same semantic inputs must replay the original successful result without a second mutation and without another revision increment.
- Only one `trip_execution_action` row may exist for a successful requestId.
- Reuse of a requestId with different semantic inputs must return an explicit idempotency conflict; do not silently treat it as a new action.
- Idempotency must remain correct under retry after an uncertain network result.

### Error contract

Use structured error responses. At minimum Task 3 must distinguish:

- `INVALID_DEVICE_TOKEN` / authentication failures from Task 2
- `EXECUTION_REVISION_CONFLICT` — HTTP 409 with `latest`
- `IDEMPOTENCY_CONFLICT` — conflict response
- `TRIP_NOT_STARTED` — HTTP 422 with `latest`
- invalid stop/action/transition — HTTP 4xx with a stable code and latest snapshot where it helps client reconciliation

### Scope exclusions

Do **not** implement in Task 3:

- Swift Core execution models
- iOS role-selection UI
- 10-second polling
- local outbox / offline retry
- Today screen mutations
- weather
- MapKit/CoreLocation
- photo check-in/upload
- WebSocket or SSE
- charging/rest operational data work

Record any discovered out-of-scope issue under `Known Gaps`; do not fix it opportunistically.

## 02 Implementation Plan

### Baseline and existing RED evidence

Task branch: `codex/qinggan-v1-task3-execution-api`

Approved Task 2 base: `9a2a94a4c45efadb6816e361919ca30bbc8270bf`

Existing RED test commit: `337468b817fe1a6f6e47da64de1847a93216c6b8`

Git Task Loop v1 was merged into this branch by merge commit:

`b5b23b9db4e5dd30a9c03cf1b18d86de12217e1c`

Existing RED test file:

`backend/src/test/java/com/qinggan/travel/execution/api/ExecutionApiIntegrationTest.java`

Codex must **first run the current backend test suite and preserve the RED evidence**. Confirm the failure is due to the unimplemented Task 3 execution capability. Do not rewrite the frozen tests merely to manufacture GREEN. If a test itself is proven inconsistent with this task record, stop and report `BLOCKED` or request audit rather than silently changing the contract.

### Step 1 — Domain and persistence

Implement the execution runtime domain/persistence around the existing Task 1 schema, including the equivalent responsibilities of:

- authoritative trip execution row
- sparse stop execution row
- immutable/idempotent execution action record
- repositories with the locking/query methods needed for revision-safe mutation

Use explicit database locking/transaction boundaries so revision comparison and mutation are atomic.

### Step 2 — Snapshot query

Implement a query service that:

- authenticates the active device through Task 2 authorization code;
- resolves the Trip by code;
- loads authoritative `trip_execution` and sparse runtime stop overrides;
- produces the frozen schema version `1.0` response;
- emits `ETag: "execution-revision-N"`;
- returns `304` for matching `If-None-Match`.

### Step 3 — Start mutation

Implement start mutation with this order inside the mutation transaction:

1. authenticate Device Token;
2. lock the authoritative execution state needed for this Trip;
3. resolve idempotent replay before performing a second mutation;
4. validate expected revision;
5. validate legal trip transition;
6. derive `actualStartDate` from injected server Clock + Trip timezone;
7. update authoritative execution state;
8. update Trip compatibility mirror and required itinerary revision behavior;
9. write one action/audit record with authenticated actor identity;
10. increment revision exactly once;
11. return latest snapshot + ETag.

The service must use an injectable `Clock` so integration tests can prove timezone behavior deterministically.

### Step 4 — Stop mutation state machine

Implement ARRIVE / COMPLETE / SKIP using the frozen transition rules. The mutation transaction must:

1. authenticate Device Token;
2. lock authoritative execution state;
3. resolve requestId replay/conflict;
4. validate expected revision;
5. require trip STARTED;
6. resolve requested itinerary stop and whether it is optional;
7. compute effective current status from sparse override or seeded baseline;
8. validate transition;
9. upsert sparse stop execution state with actor role/device from authentication context;
10. write exactly one execution action record;
11. increment revision once;
12. return latest snapshot + ETag.

### Step 5 — Conflict/error envelope

Implement stable structured responses for revision conflict, idempotency conflict, pre-start mutation, illegal transition, unknown stop/action, and authentication errors. Revision/transition failures must not mutate persistent state.

### Step 6 — MySQL concurrency verification

Add a Testcontainers MySQL 8.4 integration test for competing execution mutations using the same `expectedRevision`.

Required evidence:

- both requests race against the same current revision;
- exactly one mutation succeeds;
- the loser receives the expected revision-conflict response;
- final execution revision increases only once;
- only the winning action/state is authoritative;
- no duplicate action row is created.

Do not substitute H2 for this concurrency gate.

### Step 7 — Full verification

Run and report:

1. focused execution API tests;
2. complete backend Maven test suite;
3. real MySQL Task 3 concurrency test;
4. production Docker Compose validation;
5. any existing CI workflow required by the branch.

No iOS build is required because Task 3 must not change iOS code.

## Acceptance Criteria

Task 3 is `CODEX_DONE` only when all of these are true:

- execution snapshot requires valid Device Token;
- snapshot returns schemaVersion `1.0`, authoritative revision/status, sparse stop states, and ETag;
- matching ETag returns 304;
- start date is derived from server Clock in Trip timezone, not the phone/client timestamp;
- start updates authoritative state and required Trip/itinerary compatibility mirror;
- ARRIVE / COMPLETE / SKIP rules match this task record;
- actor role/device are derived from authenticated Device Token context;
- stale revision returns 409 + latest authoritative snapshot;
- rejected mutations never increment revision;
- same requestId replay never increments revision twice;
- different-input requestId reuse returns explicit conflict;
- historical/completed runtime state cannot regress through stale requests;
- MySQL 8.4 race proves exactly one winner for same-revision competing writes;
- backend full tests and production Compose validation are GREEN;
- no iOS or other excluded feature code was pulled into the PR;
- Codex posts `03 Codex Execution Report` to PR #5 using `protocolVersion: v2` and ends with `Next: WAIT_FOR_CHATGPT_AUDIT`;
- PR remains unmerged.

## Codex Execution Rules

Recommended execution model: **GPT-5.6 Luma / strongest available coding model**.

Reasoning: **High**.

Before editing production code:

1. pull latest `codex/qinggan-v1-task3-execution-api`;
2. read `docs/superpowers/GITHUB_TASK_LOOP_V1.md`;
3. read this task file completely;
4. inspect PR #5 and existing RED test;
5. post/update task status to `CODEX_IN_PROGRESS` on PR #5;
6. verify RED;
7. implement only this task;
8. verify GREEN with concrete evidence;
9. post the required Codex Execution Report;
10. stop and wait for ChatGPT Audit.

Do not merge PR #5 and do not start Task 4.