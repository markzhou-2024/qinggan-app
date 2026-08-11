# 青甘随行 H5/PWA Foundation & Trip Domain Kernel Design

**Task:** QG-H5-V1-001A  
**Status:** Approved for implementation  
**Date:** 2026-08-11

## 1. Scope and Outcomes

This increment creates the first runnable version of 青甘随行 as a mobile H5/PWA backed by a modular Spring Boot monolith, MySQL, and Redis. It establishes a stable trip-domain contract for the fixed 2026 青甘大环线 journey, anonymous family devices, optimistic revision control, and append-only change auditing.

The delivered product lets a family device register anonymously, load the full ten-day itinerary, inspect a day, and complete or skip an itinerary stop without silently overwriting another device's change.

The increment deliberately excludes GPS, weather, map routing or rendering, family location sharing, authentication, AI features, photos, administration, deadline evaluation, and dynamic recommendations.

## 2. Selected Architecture

Use a domain-oriented modular monolith in one repository:

```text
H5-app/
├── backend/       Spring Boot API and domain modules
├── frontend/      Vue mobile H5/PWA
├── nginx/         frontend/static and API reverse-proxy configuration
├── compose.yaml   frontend, backend, MySQL, and Redis
└── docs/          design, plan, and operator documentation
```

The backend is a single deployable process. Packages are grouped first by business responsibility rather than by a global controller/service/repository layer. Each domain package may contain its own API, application service, persistence adapter, and DTOs. Cross-domain infrastructure remains small and explicit.

MySQL is the source of truth for all trip, itinerary, device, progress, revision, and audit state. Redis is connected and health-checked for future cache/session-like capabilities, but it is not involved in revision correctness. This avoids split-brain concurrency behavior.

## 3. Technology Baseline

### Frontend

- Vue 3 with TypeScript and Vite
- Vue Router for overview and day-detail routes
- Pinia for itinerary and device state
- Vant for basic mobile components
- `vite-plugin-pwa` for manifest, service worker generation, and install metadata
- Vitest, Vue Test Utils, and jsdom for unit/component tests
- npm and a committed lock file

### Backend

- Java 17
- Spring Boot 3.x with Maven Wrapper
- Spring Web and Jakarta Validation
- Spring Data JPA
- Flyway
- MySQL Connector/J
- Spring Data Redis and Actuator health contributors
- JUnit 5, Spring Boot Test, MockMvc, and Testcontainers

### Runtime

- MySQL 8
- Redis 7
- Docker Compose
- Nginx as the frontend web server and `/api` reverse proxy

## 4. Backend Module Boundaries

### `trip`

Owns Trip, TripDay, Place, TripStop, NavigationPoint, their enums, itinerary queries, and public read DTOs. It exposes reads by immutable trip code and stop lookup scoped through the requested trip.

### `progress`

Owns TripProgress and the complete/skip application transaction. It validates device ownership and expected revision, changes stop/progress state, increments Trip revision exactly once, and appends one TripChangeLog in the same database transaction.

### `device`

Owns anonymous Device registration and token lookup. The server creates tokens with a cryptographically secure random generator. Public APIs never accept a caller-selected device ID.

### `changelog`

Owns the append-only TripChangeLog entity and repository. It has no update or delete HTTP API. Application code receives only an append operation, not a general mutable service.

### `shared`

Contains API error representation, exception mapping, clock configuration, and small persistence conventions. It does not contain trip business logic.

## 5. Persistence Model

Flyway creates these tables:

- `trip`
- `place`
- `trip_day`
- `trip_stop`
- `navigation_point`
- `trip_progress`
- `device`
- `trip_change_log`

Important constraints:

- `trip.code` is unique.
- `trip.duration_days` is 10 for the seed and application validation enforces inclusive date duration.
- `(trip_id, day_number)`, `(trip_id, date)`, and `(trip_id, sequence)` are unique for TripDay.
- `(trip_day_id, sequence)` is unique for TripStop.
- `device.device_token` is unique and never returned except at initial registration or echoed as its public credential.
- one TripProgress exists per Trip.
- revision columns are non-negative.
- foreign keys prevent stops, devices, progress, and logs from becoming detached from their Trip.
- TripChangeLog has insert-only application behavior. Database credentials used by the application are not granted a delete-specific API path; no repository delete is exposed.

Enums are stored as readable strings. Priority uses only `S_PLUS`, `S`, `A_PLUS`, `A`, and `B`, preserving semantic meaning. Coordinates have separately named WGS84 and GCJ02 columns; no generic latitude/longitude pair is exposed.

## 6. Seed Strategy

Flyway owns schema and deterministic seed data. A versioned migration inserts:

- one planning trip with code `QINGGAN-2026`, a planned start date seed of 2026-08-13, duration 10, and initial revision 1; runtime start dates are sliding configuration, not canonical itinerary facts;
- exactly ten TripDay rows numbered 1 through 10 and no Day11;
- all route places, overnight places, scenic locations, transfer nodes, and route stops;
- optional/recommended NavigationPoint examples where navigation guidance is useful, while allowing coordinate values to be null;
- one NOT_STARTED TripProgress row at revision 1.

The migration uses stable numeric IDs only inside seed SQL so foreign keys are deterministic. Runtime APIs expose Long IDs but client behavior does not assume particular values.

Route data preserves range-shaped product facts. Each day has a representative integer `plannedDistanceKm` required by the core model, while an additional display text field carries values such as `660～680km` where the task specifies a range. The same approach applies to drive-time display text. This prevents the UI from inventing or losing supplied uncertainty.

Seed priorities and optionality are assigned on scenic TripStop rows:

- 莫高窟: S_PLUS, required
- 青海湖, 大柴旦翡翠湖, 鸣沙山月牙泉, 张掖七彩丹霞: S, required
- 茶卡天空壹号, 祁连山草原/G227: A_PLUS
- 水上雅丹, 卓尔山: A
- 门源, 嘉峪关, U型公路: B and optional

嘉峪关 is included as an optional Day6 scenic stop, not as a completion requirement. U型公路 is an optional Day5 scenic stop between 大柴旦 and 水上雅丹; it does not change the required overnight sequence. Day8 begins the RETURN phase. Day10 ends at 南京 and there is no generated continuation.

## 7. Revision and Mutation Transaction

Complete and skip use one transaction with this order:

1. Resolve Trip by `tripCode` and acquire a pessimistic row lock for the short mutation transaction.
2. Resolve Device by token and require `device.trip_id` to equal the resolved Trip ID.
3. Compare the request's `expectedRevision` with `trip.revision`.
4. Resolve the stop through its TripDay and require it belongs to the same Trip.
5. Reject an invalid transition, including completing an already skipped stop or skipping an already completed stop.
6. Capture the before snapshot.
7. Change TripStop status and update TripProgress consistently.
8. Increment Trip revision by exactly one and synchronize TripProgress revision to the new shared revision.
9. Append TripChangeLog with before/after JSON and the new revision.
10. Commit atomically and return the updated progress and revision.

The row lock serializes the compare-and-change section. Two requests carrying revision 1 cannot both succeed: the first commits revision 2; the second obtains the lock, observes revision 2, and receives HTTP 409 without changing state.

The failure response uses a stable JSON envelope with code `REVISION_CONFLICT`, a readable message, the submitted revision, and the current revision. Validation errors use 400, missing resources use 404, invalid device credentials use 401, cross-trip device use returns 403, and invalid state transitions use 409 with a distinct code.

## 8. Device Contract

The API adds the required enabling endpoint:

```text
POST /api/v1/trips/{tripCode}/devices
```

An optional display name may be supplied. The backend creates at least 256 bits of randomness using `SecureRandom`, encodes it URL-safely without padding, stores the token, and returns it once. Re-registration creates another Device rather than accepting client identity claims. Successful authenticated requests update `lastSeenAt`.

No GPS coordinates, location history, member positions, or location-sharing records exist in the schema or API.

## 9. REST Contract

### Reads

```text
GET /api/v1/trips/{tripCode}
GET /api/v1/trips/{tripCode}/itinerary
GET /api/v1/trips/{tripCode}/progress
```

The trip response contains the basic date/status/duration fields and revision. The itinerary response is a single offline-friendly document containing Trip, ordered Day1-Day10, ordered stops, referenced Places, NavigationPoints, priority/optionality, Progress, and the same current revision.

### Writes

```text
POST /api/v1/trips/{tripCode}/devices
POST /api/v1/trips/{tripCode}/stops/{stopId}/complete
POST /api/v1/trips/{tripCode}/stops/{stopId}/skip
```

Complete/skip bodies contain `deviceToken` and `expectedRevision`. They return HTTP 200 and the new revision on success. Revision mismatch returns HTTP 409. All path resources and device membership are scoped to the path Trip.

Dates and times use ISO-8601 JSON. Planned local stop times are `HH:mm` values without an invented timezone conversion. Null coordinate values are explicit and legal.

## 10. Frontend Design

### State

The itinerary Pinia store owns `idle | loading | ready | error | conflict` request status, the current itinerary document, and the latest revision. The device store creates or restores a token from local storage for `QINGGAN-2026`. Device storage contains only the opaque token and optional local display label.

### Routes

- `/` renders Trip Overview.
- `/days/:dayNumber` renders Day Detail.

Trip Overview shows the trip title, exact date range, DAY 1 through DAY 10 cards, route summary, day type, planned distance display, and overnight place. Day Detail renders ordered route stops with arrows, expected distance/drive text, overnight place, and semantic priority badges.

Loading and API failure are visible states. A 409 changes the store to `conflict`, shows a clear “行程已在其他设备更新，请重新同步” action, and never displays success. Reloading the itinerary replaces local state with the server document.

### PWA

The manifest sets the name `青甘随行`, a short name, theme/background colors, portrait-friendly standalone display, and root start URL. HTML includes viewport and Apple mobile web-app metadata. The generated service worker precaches the static application shell and uses a conservative network-first strategy for itinerary GET requests. Mutation requests are never queued or replayed offline.

## 11. Testing Strategy

### Backend

Repository/API integration tests use a real MySQL Testcontainer and migrations. They verify:

1. exactly ten seeded days, numbered 1-10, with no Day11;
2. exact start/end dates and duration;
3. 莫高窟 S_PLUS and required;
4. 门源 B and optional;
5. 嘉峪关 B and optional;
6. successful complete changes revision 1 to 2 and appends one ChangeLog;
7. two devices with expectedRevision 1 yield one success and one 409 without overwrite;
8. a Device belonging to Trip A cannot mutate Trip B;
9. read endpoints return ordered, complete itinerary data;
10. anonymous token format and server ownership behavior.

Focused unit tests cover date-duration validation and status transition rules where a full container adds no value.

### Frontend

Mocked API component/store tests verify successful itinerary loading, ten-day rendering, the exact Day5 route, priority labels, explicit API failure, and explicit 409 resynchronization messaging.

### Delivery verification

- `./mvnw test` for backend tests
- `npm test -- --run` for frontend tests
- `npm run build` for the production frontend
- backend package build
- `docker compose up --build -d` with health checks
- real HTTP smoke requests through the frontend/Nginx entry point for Trip and full itinerary
- assertions that the itinerary has ten ordered days and ends on 2026-08-22

## 12. Container and Operations Design

Compose starts `mysql`, `redis`, `backend`, and `frontend`. MySQL and Redis have health checks. Backend waits for healthy dependencies and exposes an Actuator health check. Frontend waits for backend health and Nginx proxies `/api/` to the backend while serving the built SPA with history fallback.

Local defaults are development-only and documented. Secrets are configurable through environment variables. No production credentials are committed.

## 13. Completion and Git Policy

Implementation occurs on `codex/qinggan-v1-001a`. Completion may be reported only after backend tests, frontend tests, both production builds, Compose startup, real MySQL/Redis health, Flyway migration, HTTP smoke tests, and a clean Git status all succeed. The final implementation is committed only after verification evidence is captured.

For Native Foundation acceptance, Simulator + real MySQL/API + offline runtime are sufficient; Docker Registry or Compose image-resolution timeouts are non-blocking environment notes.
