# QingGan Foundation & Trip Domain Kernel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a Docker-runnable QingGan mobile H5/PWA and Spring Boot modular monolith with the fixed Day1-Day10 itinerary, anonymous devices, MySQL/Redis infrastructure, and conflict-safe shared stop progress.

**Architecture:** A Vue 3 frontend consumes a domain-oriented Spring Boot monolith. MySQL is the only shared-state authority; each stop mutation locks its Trip row, compares `expectedRevision`, changes stop/progress, increments the revision, and appends a change log atomically. Redis is connected and health-checked but remains outside correctness-critical revision logic.

**Tech Stack:** Vue 3, TypeScript, Vite, Vue Router, Pinia, Vant, vite-plugin-pwa, Vitest; Java 17, Spring Boot 3, Maven, Spring Web, Validation, JPA, Flyway, MySQL, Redis, JUnit 5, Testcontainers; Docker Compose and Nginx.

## Global Constraints

- The trip is exactly 2026-08-13 through 2026-08-22 inclusive, with duration 10 and no Day11.
- The repository branch is `codex/qinggan-v1-001a`; do not add GPS, weather, maps, realtime location sharing, login, AI, photos, CMS, Deadline Engine, or dynamic recommendations.
- Device tokens are server-generated with at least 256 bits of cryptographic randomness; write requests accept a token, never a client-selected device ID.
- Every shared write validates Trip, stop ownership, and Device membership; a stale revision fails closed with HTTP 409.
- Every successful shared write increments Trip revision exactly once and appends one immutable TripChangeLog in the same MySQL transaction.
- Coordinates are always named by WGS84 or GCJ02; null seed coordinates are allowed and generic coordinate fields are forbidden.
- MySQL and Redis must be real healthy Docker services for completion verification.
- Follow strict RED → verify failure → GREEN → verify success for behavior code. Configuration is introduced only to make the next failing behavior test runnable.

---

## File Map

### Backend

- `backend/pom.xml`, `backend/mvnw`, `backend/.mvn/wrapper/*`: Java build and reproducible Maven wrapper.
- `backend/src/main/java/com/qinggan/travel/QingGanApplication.java`: Spring Boot entry point.
- `backend/src/main/java/com/qinggan/travel/trip/domain/*`: Trip, TripDay, Place, TripStop, NavigationPoint and enums.
- `backend/src/main/java/com/qinggan/travel/trip/persistence/*`: repositories, including locked Trip lookup.
- `backend/src/main/java/com/qinggan/travel/trip/api/*`: trip/itinerary response records and read controller.
- `backend/src/main/java/com/qinggan/travel/trip/application/TripQueryService.java`: read aggregation.
- `backend/src/main/java/com/qinggan/travel/progress/*`: TripProgress, mutation request/result, transaction service, controller, and repository.
- `backend/src/main/java/com/qinggan/travel/device/*`: Device entity, secure token factory, registration service/controller, and repository.
- `backend/src/main/java/com/qinggan/travel/changelog/*`: append-only TripChangeLog and insert repository.
- `backend/src/main/java/com/qinggan/travel/shared/api/*`: error codes, error payload, and exception advice.
- `backend/src/main/resources/db/migration/V1__create_trip_domain.sql`: schema.
- `backend/src/main/resources/db/migration/V2__seed_qinggan_2026.sql`: deterministic fixed itinerary.
- `backend/src/main/resources/application.yml`: environment-driven MySQL/Redis/Flyway and health configuration.
- `backend/src/test/java/com/qinggan/travel/*`: domain, migration, API, security, and revision integration tests.

### Frontend

- `frontend/package.json`, `frontend/package-lock.json`, `frontend/tsconfig*.json`, `frontend/vite.config.ts`: build, test, and PWA configuration.
- `frontend/src/api/tripApi.ts`: typed HTTP boundary and `ApiError`.
- `frontend/src/types/itinerary.ts`: complete API document types and semantic enums.
- `frontend/src/stores/itinerary.ts`: load/mutate/conflict states.
- `frontend/src/stores/device.ts`: local anonymous token creation flow.
- `frontend/src/views/TripOverviewView.vue`: exact ten-day overview.
- `frontend/src/views/DayDetailView.vue`: ordered stops and priority badges.
- `frontend/src/components/*`: status panel, day card, route list, and priority badge.
- `frontend/src/router/index.ts`, `frontend/src/main.ts`, `frontend/src/App.vue`: application shell.
- `frontend/src/test/*`: complete API fixture and setup.
- `frontend/src/**/*.spec.ts`: store and component behavior tests.
- `frontend/public/icons/*`: install icons.

### Runtime

- `backend/Dockerfile`, `frontend/Dockerfile`: production images.
- `nginx/default.conf`: SPA history fallback and `/api/` proxy.
- `compose.yaml`: MySQL 8, Redis 7, backend, and frontend with health dependencies.
- `.env.example`: non-secret local configuration names.
- `.gitignore`: build outputs, local environment, worktree, and macOS exclusions.
- `README.md`: local, test, build, Compose, API, and smoke-test commands.

---

### Task 1: Bootstrap Backend and Domain Invariants

**Files:**
- Create: `backend/pom.xml`, `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/maven-wrapper.properties`
- Create: `backend/src/main/java/com/qinggan/travel/QingGanApplication.java`
- Create: `backend/src/test/java/com/qinggan/travel/trip/domain/TripTest.java`
- Create: `backend/src/main/java/com/qinggan/travel/trip/domain/Trip.java`
- Create: `backend/src/main/java/com/qinggan/travel/trip/domain/TripStatus.java`
- Create: `backend/src/main/java/com/qinggan/travel/trip/domain/TripDay.java`, `Place.java`, `TripStop.java`, `NavigationPoint.java`
- Create: `backend/src/main/java/com/qinggan/travel/trip/domain/DayType.java`, `PlaceType.java`, `Priority.java`, `StopType.java`, `StopStatus.java`, `NavigationType.java`
- Create: `backend/src/main/java/com/qinggan/travel/progress/domain/TripProgress.java`, `ProgressState.java`

**Interfaces:**
- Produces: `Trip.create(String code, String name, LocalDate startDate, LocalDate endDate, int durationDays, TripStatus status)` and JPA entities with Long IDs.
- Produces enums: `TripStatus`, `DayType`, `PlaceType`, `Priority`, `StopType`, `StopStatus`, `NavigationType`, `ProgressState` with exactly the specification values.

- [ ] **Step 1: Add only Maven/build configuration needed to compile a Spring Boot test**

Use Java 17, Spring Boot 3.5.7, and Maven 3.9.11 dependencies for web, validation, JPA, Flyway MySQL, Redis, Actuator, MySQL runtime, test, and Testcontainers MySQL.

- [ ] **Step 2: Write the failing inclusive-duration test**

```java
@Test
void rejectsDatesThatDoNotMatchInclusiveDuration() {
    assertThatThrownBy(() -> Trip.create(
        "QINGGAN-2026", "青甘大环线10天自驾",
        LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 22), 9, TripStatus.ACTIVE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("inclusive");
}
```

- [ ] **Step 3: Run the test and observe RED**

Run: `cd backend && ./mvnw -Dtest=TripTest test`  
Expected: compilation failure because `Trip` and `TripStatus` do not exist.

- [ ] **Step 4: Implement the minimum Trip invariant and enums/entities**

Implement inclusive duration as `ChronoUnit.DAYS.between(startDate, endDate) + 1`. Map enums with `EnumType.STRING`; name every coordinate field with `Wgs84` or `Gcj02`; keep nullable seed coordinates as `BigDecimal`.

- [ ] **Step 5: Verify GREEN and add the valid ten-day case**

Run: `cd backend && ./mvnw -Dtest=TripTest test`  
Expected: two tests pass, including a valid 2026-08-13 to 2026-08-22 Trip with duration 10.

- [ ] **Step 6: Commit the domain kernel**

```bash
git add backend .gitignore
git commit -m "feat: establish trip domain kernel"
```

### Task 2: Flyway Schema and Exact Ten-Day Seed

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__create_trip_domain.sql`
- Create: `backend/src/main/resources/db/migration/V2__seed_qinggan_2026.sql`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/test/java/com/qinggan/travel/support/MySqlIntegrationTest.java`
- Create: `backend/src/test/java/com/qinggan/travel/trip/persistence/QingGanSeedIntegrationTest.java`
- Create: repository interfaces under `trip/persistence` and `progress/persistence`

**Interfaces:**
- Produces: `TripRepository.findByCode(String)`, `TripDayRepository.findByTripIdOrderBySequence(Long)`, `TripStopRepository.findByTripDayTripIdOrderByTripDaySequenceAscSequenceAsc(Long)`.
- Produces seed Trip code `QINGGAN-2026` at revision 1 and TripProgress revision 1.

- [ ] **Step 1: Write the container-backed seed tests before migrations**

Tests query repositories and assert literal facts: count 10; day numbers 1-10; no 11; exact trip dates/duration; 莫高窟 `S_PLUS,false`; 门源 and 嘉峪关 `B,true`; Day5 ordered names `大柴旦镇, U型公路, 水上雅丹, 敦煌, 鸣沙山月牙泉`; Day10 destination 南京.

- [ ] **Step 2: Run and observe RED**

Run: `cd backend && ./mvnw -Dtest=QingGanSeedIntegrationTest test`  
Expected: Spring context/migration failure because the schema and seed do not exist.

- [ ] **Step 3: Implement V1 schema and V2 deterministic seed**

Use explicit IDs and foreign keys, unique day/stop ordering constraints, string enums, JSON-capable `LONGTEXT` audit snapshots, `DECIMAL(10,7)` coordinates, and exact Day1-Day10 content. Seed all specified priority places and optional flags without inserting Day11.

- [ ] **Step 4: Verify GREEN and migration repeatability**

Run: `cd backend && ./mvnw -Dtest=QingGanSeedIntegrationTest test` twice.  
Expected each run: all seed assertions pass against a fresh MySQL container.

- [ ] **Step 5: Commit schema and seed**

```bash
git add backend/src/main/resources backend/src/main/java/com/qinggan/travel/trip/persistence backend/src/test
git commit -m "feat: seed exact qinggan ten day itinerary"
```

### Task 3: Offline-Friendly Read API

**Files:**
- Create: response records under `backend/src/main/java/com/qinggan/travel/trip/api/dto`
- Create: `backend/src/main/java/com/qinggan/travel/trip/application/TripQueryService.java`
- Create: `backend/src/main/java/com/qinggan/travel/trip/api/TripQueryController.java`
- Create: `backend/src/main/java/com/qinggan/travel/shared/api/ApiError.java`
- Create: `backend/src/main/java/com/qinggan/travel/shared/api/ApiExceptionHandler.java`
- Create: `backend/src/test/java/com/qinggan/travel/trip/api/TripQueryApiIntegrationTest.java`

**Interfaces:**
- Produces: `GET /api/v1/trips/{tripCode}`, `/itinerary`, and `/progress`.
- Produces: `ItineraryResponse(TripResponse trip, List<TripDayResponse> days, ProgressResponse progress, long revision)` where each day embeds ordered stop responses and each stop embeds Place plus NavigationPoints.

- [ ] **Step 1: Write failing MockMvc read-contract tests**

Assert status 200, trip revision 1, exactly 10 ordered days, Day5 exact route, priority/optionality, Progress NOT_STARTED, and 404 JSON code `TRIP_NOT_FOUND` for an unknown code.

- [ ] **Step 2: Run and observe RED**

Run: `cd backend && ./mvnw -Dtest=TripQueryApiIntegrationTest test`  
Expected: 404 for the required routes because no controller exists.

- [ ] **Step 3: Implement query aggregation and API DTOs**

Map JPA entities to immutable records inside a read-only transaction. Sort by stored sequence, never by ID. Do not expose persistence entities or device/change-log state.

- [ ] **Step 4: Verify GREEN**

Run: `cd backend && ./mvnw -Dtest=TripQueryApiIntegrationTest test`  
Expected: all read-contract tests pass.

- [ ] **Step 5: Commit read API**

```bash
git add backend/src/main/java backend/src/test/java/com/qinggan/travel/trip/api
git commit -m "feat: expose complete itinerary read api"
```

### Task 4: Anonymous Devices and Conflict-Safe Stop Mutations

**Files:**
- Create: Device, TripProgress, and TripChangeLog files described in the File Map
- Create: `backend/src/main/java/com/qinggan/travel/progress/application/StopMutationService.java`
- Create: `backend/src/main/java/com/qinggan/travel/progress/api/StopMutationController.java`
- Create: `backend/src/test/java/com/qinggan/travel/device/DeviceRegistrationApiIntegrationTest.java`
- Create: `backend/src/test/java/com/qinggan/travel/progress/StopMutationApiIntegrationTest.java`

**Interfaces:**
- Produces: `POST /api/v1/trips/{tripCode}/devices` with `{displayName?}` and `{deviceToken, displayName, createdAt}`.
- Produces: complete/skip body `StopMutationRequest(@NotBlank String deviceToken, @PositiveOrZero long expectedRevision)`.
- Produces: `StopMutationResponse(long revision, ProgressResponse progress)`.

- [ ] **Step 1: Write failing secure-device tests**

Assert two registrations return distinct URL-safe tokens with at least 43 characters, caller cannot set a device ID/token, and the stored Device belongs to the path Trip.

- [ ] **Step 2: Run device tests and observe RED**

Run: `cd backend && ./mvnw -Dtest=DeviceRegistrationApiIntegrationTest test`  
Expected: 404 because registration does not exist.

- [ ] **Step 3: Implement secure registration and verify GREEN**

Generate 32 random bytes with `SecureRandom`, encode using URL-safe Base64 without padding, persist the Device, and return the token. Run the focused test until green.

- [ ] **Step 4: Write failing mutation and concurrency tests**

Cover revision 1→2 plus one ChangeLog; stale second request returns 409/currentRevision 2 and preserves A's result; skip writes `SKIPPED`; unknown token is 401; Device A for Trip A receives 403 on Trip B; stop not belonging to path Trip is rejected; invalid terminal transition does not append a log.

- [ ] **Step 5: Run mutation tests and observe RED**

Run: `cd backend && ./mvnw -Dtest=StopMutationApiIntegrationTest test`  
Expected: 404 because mutation controllers/services do not exist.

- [ ] **Step 6: Implement the atomic transaction**

Use `TripRepository.findByCodeForUpdate` with `@Lock(PESSIMISTIC_WRITE)`. Validate Device membership and revision before changing the stop. Serialize explicit before/after snapshot records with Jackson. Save progress and append a new change log in the same transaction; expose no log mutation endpoint.

- [ ] **Step 7: Verify GREEN and all backend tests**

Run: `cd backend && ./mvnw test`  
Expected: all domain, seed, read, device, and mutation tests pass with zero failures/errors.

- [ ] **Step 8: Commit shared-state capability**

```bash
git add backend/src/main/java backend/src/test
git commit -m "feat: add conflict safe shared trip progress"
```

### Task 5: Vue H5 Screens, Client State, and PWA Foundation

**Files:**
- Create all Frontend files in the File Map
- Create: `frontend/index.html`, `frontend/src/styles.css`
- Create: `frontend/src/stores/itinerary.spec.ts`
- Create: `frontend/src/views/TripOverviewView.spec.ts`
- Create: `frontend/src/views/DayDetailView.spec.ts`

**Interfaces:**
- Consumes the Task 3/4 JSON contracts through `tripApi`.
- Produces store actions `loadItinerary()`, `completeStop(stopId)`, `skipStop(stopId)`, `resync()` and status `idle | loading | ready | error | conflict`.

- [ ] **Step 1: Add only Vite/Vitest project configuration and a complete literal API fixture**

The fixture contains all documented response fields, ten days, exact Day5 route, semantic priorities, navigation point arrays, Progress, and revision 1.

- [ ] **Step 2: Write failing store tests**

Assert successful itinerary load, explicit network error state, complete request sends stored token/current revision, and HTTP 409 produces conflict state plus resync behavior without applying success.

- [ ] **Step 3: Run store tests and observe RED**

Run: `cd frontend && npm test -- --run src/stores/itinerary.spec.ts`  
Expected: module resolution failure because API/store production modules do not exist.

- [ ] **Step 4: Implement API types/client and Pinia stores, then verify GREEN**

Use fetch with JSON/error parsing. Keep mutation requests out of the service worker background queue. Persist only the anonymous token in localStorage.

- [ ] **Step 5: Write failing overview and detail component tests**

Assert overview title/date/DAY 1-DAY 10/ten cards; exact Day5 route order and distance `660～680km`; semantic labels `最高优先级`, `核心必去`, `强烈保留`, `推荐保留`, `可选`; visible failure and conflict resync copy.

- [ ] **Step 6: Run component tests and observe RED**

Run: `cd frontend && npm test -- --run src/views`  
Expected: component imports fail because views do not exist.

- [ ] **Step 7: Implement the minimal mobile UI and routing**

Build overview/detail views from focused components, use Vant only for basic presentation, and add manifest/iPhone metadata/service-worker configuration. Create valid 192px and 512px PNG app icons.

- [ ] **Step 8: Verify GREEN, typecheck, and production build**

Run: `cd frontend && npm test -- --run && npm run typecheck && npm run build`  
Expected: all frontend tests pass; TypeScript and Vite production build exit 0; generated PWA manifest and service worker exist.

- [ ] **Step 9: Commit frontend**

```bash
git add frontend
git commit -m "feat: add ten day qinggan pwa interface"
```

### Task 6: Docker Runtime and Real Smoke Verification

**Files:**
- Create: `backend/Dockerfile`, `frontend/Dockerfile`, `nginx/default.conf`
- Create: `compose.yaml`, `.env.example`, `README.md`

**Interfaces:**
- Produces services named exactly `frontend`, `backend`, `mysql`, and `redis`.
- Produces frontend entry point `http://localhost:8080` with `/api` proxied to backend.

- [ ] **Step 1: Add production Dockerfiles, Nginx route, Compose health checks, and environment documentation**

Use multi-stage builds, MySQL `mysqladmin ping`, Redis `redis-cli ping`, backend Actuator health, and dependency conditions. Configure backend datasource/Redis entirely through environment variables.

- [ ] **Step 2: Validate Compose configuration before startup**

Run: `docker compose config`  
Expected: exit 0 and exactly four service definitions.

- [ ] **Step 3: Build and start the complete stack**

Run: `docker compose up --build -d` and `docker compose ps`.  
Expected: all four services running; health-enabled services become healthy.

- [ ] **Step 4: Execute real HTTP smoke assertions**

Run Trip and itinerary requests through `http://localhost:8080/api/v1/trips/QINGGAN-2026`; assert HTTP 200, trip end date `2026-08-22`, itinerary day count 10, ordered day numbers 1-10, and no Day11.

- [ ] **Step 5: Verify Flyway, MySQL, and Redis evidence**

Inspect backend logs for successful Flyway migration and healthy Redis/MySQL indicators. Query `/actuator/health` inside the backend network and require status `UP` with database and Redis components healthy.

- [ ] **Step 6: Stop the stack without deleting persistent project files**

Run: `docker compose down`  
Expected: containers/network stop cleanly. Do not pass `-v`.

- [ ] **Step 7: Commit runtime files**

```bash
git add compose.yaml backend/Dockerfile frontend/Dockerfile nginx .env.example README.md
git commit -m "build: add complete docker runtime"
```

### Task 7: Final Verification, Review, and Delivery Commit

**Files:**
- Modify only files required by verification or review findings.

**Interfaces:**
- Produces final evidence for every Definition of Done item and a clean committed branch.

- [ ] **Step 1: Run fresh backend verification**

Run: `cd backend && ./mvnw clean test package` and record test counts with zero failures/errors.

- [ ] **Step 2: Run fresh frontend verification**

Run: `cd frontend && npm test -- --run && npm run typecheck && npm run build` and record test counts/build exit codes.

- [ ] **Step 3: Run fresh Docker integration and smoke verification**

Rebuild from clean images as practical, start the stack, wait for health, run both required HTTP requests plus ten-day assertions, capture status codes, then stop without volumes.

- [ ] **Step 4: Review requirement coverage and prohibited scope**

Compare implementation against every Task Definition and design section. Inspect schema/API for forbidden GPS history, location sharing, auth, maps, weather, or recommendation functionality. Confirm no mutable ChangeLog API exists.

- [ ] **Step 5: Request code review and resolve findings with TDD**

Review the full branch diff against this plan. For every valid behavior defect, first add a reproducing failing test, observe RED, implement the correction, and rerun the relevant suite.

- [ ] **Step 6: Run the entire fresh verification set after the last change**

No pass from before the last edit counts. Re-run backend, frontend, build, Compose, and smoke commands.

- [ ] **Step 7: Commit verified corrections and confirm clean Git state**

```bash
git add .gitignore .env.example README.md compose.yaml backend frontend nginx docs/superpowers/plans/2026-08-11-qinggan-foundation.md
git commit -m "chore: finalize verified qinggan foundation"
git status --short --branch
git rev-parse HEAD
```

Expected: branch `codex/qinggan-v1-001a`, no status entries, and a printed commit SHA.
