# Git Task Loop v1

## Purpose

Git Task Loop v1 is the operating agreement for ChatGPT, Codex, and GitHub work in this repository. It creates a traceable loop from a frozen task through implementation and independent audit. It applies to every ChatGPT / Codex development task unless an approved task explicitly supersedes it.

GitHub is the authoritative task and evidence ledger. Chat history is not a substitute for a frozen task record.

## Roles

### ChatGPT

ChatGPT owns task design and quality decisions. It must:

- define and split tasks, including scope, acceptance criteria, and implementation constraints;
- write the task design and implementation plan to GitHub before implementation starts;
- audit the latest GitHub code, tests, build evidence, and Codex Execution Report independently;
- decide `APPROVED`, `NEEDS_FIX`, or `BLOCKED` and record a ChatGPT Audit Report; and
- decide whether the next task may begin.

ChatGPT does not treat a Codex completion report as approval without an independent audit.

### Codex

Codex owns implementation within a frozen task. It must:

- pull the latest GitHub state for the assigned task;
- read the task's `01 Task Design` and `02 Implementation Plan` before changing code;
- implement only the stated scope, test it, and commit the changes on the task branch;
- preserve applicable RED → GREEN evidence for TDD-suitable work;
- write a `03 Codex Execution Report` back to GitHub after implementation; and
- stop at `WAIT_FOR_CHATGPT_AUDIT`.

Codex must not mark a task `APPROVED` or `CLOSED`, infer requirements from chat memory, expand scope, merge to `main`, or start the next task without explicit authorization.

### GitHub

GitHub is the only task ledger and evidence center. It stores task design, implementation plans, source code, commits, test and build evidence, execution reports, audit reports, PR discussion, and task status. A reviewer must be able to reconstruct a task completely from GitHub without relying on a chat transcript.

## Task Lifecycle

```text
DESIGN_READY
↓
CODEX_IN_PROGRESS
↓
CODEX_DONE
↓
CHATGPT_AUDIT
↓
├─ APPROVED → CLOSED
├─ NEEDS_FIX → CODEX_IN_PROGRESS
└─ BLOCKED
```

Only ChatGPT may decide `APPROVED` and `CLOSED`. `CODEX_DONE` means implementation evidence is ready for audit; it does not mean the task passed.

For `NEEDS_FIX`, ChatGPT records concrete `Required Fixes`. Codex repairs the same task on its existing task branch and returns to `CODEX_DONE`. For `BLOCKED`, the report must name the blocking condition and the authority or change required to unblock it.

## Required GitHub Evidence

Every task has these four traceable artifacts. They may be task documents, PR descriptions, or PR comments, as long as their GitHub locations are linked from the task PR or design record.

1. **01 Task Design** — task identifier, problem, scope, exclusions, acceptance criteria, constraints, and base branch or commit.
2. **02 Implementation Plan** — ordered implementation and verification steps, files or components in scope, and TDD expectations where applicable.
3. **03 Codex Execution Report** — the final Codex implementation evidence, using the template below.
4. **04 ChatGPT Audit Report** — the independent audit decision, using the template below.

## Codex Execution Report

Codex posts this report to the task PR when implementation is complete or blocked. Replace every angle-bracket placeholder with task-specific information.

```text
protocolVersion: v2

Task:
<task id and name>

Status:
CODEX_DONE | BLOCKED

Base:
<base branch / commit>

Branch:
<implementation branch>

Changed Files:
<files>

Tests:
<RED/GREEN evidence>

Build:
<build evidence>

Runtime Evidence:
<runtime evidence if applicable>

Known Gaps:
<known gaps>

Commit:
<final commit sha>

PR:
<PR number>

Next:
WAIT_FOR_CHATGPT_AUDIT
```

## ChatGPT Audit Report

ChatGPT posts this report to the same task record after independently reviewing the current GitHub state.

```text
protocolVersion: v2

Task:
<task>

Audit Status:
APPROVED | NEEDS_FIX | BLOCKED

Scope Review:
<result>

Spec Review:
<result>

Code Review:
<result>

Test Evidence:
<result>

Build Evidence:
<result>

Runtime Evidence:
<result>

Findings:
<findings>

Required Fixes:
<only when NEEDS_FIX>

Decision:
<final decision>

Next Task:
<only when APPROVED>
```

## Closed-Loop Rules

1. ChatGPT designs a task and writes `01 Task Design` and `02 Implementation Plan` to GitHub before Codex begins.
2. Codex executes only tasks that are frozen in GitHub. It reads both required artifacts before starting and works from the stated base.
3. Codex does not use chat memory as a requirement source and does not extend task scope on its own.
4. Every implementation has reproducible test evidence. For work suitable for TDD, preserve the failing RED run and the passing GREEN run in the execution report or linked GitHub Actions evidence.
5. Codex records actual commands, counts, outputs or run links, commit SHA, and applicable build or runtime evidence. Statements such as `tests passed`, `works`, or `done` without evidence are invalid.
6. Codex writes `03 Codex Execution Report` to GitHub, marks the task `CODEX_DONE` or `BLOCKED`, and waits for audit.
7. ChatGPT re-reads the latest code, tests, and execution report from GitHub, then writes `04 ChatGPT Audit Report` with the audit decision.
8. `APPROVED` is required before the next task begins. `NEEDS_FIX` returns the original task to Codex; `BLOCKED` remains blocked until the stated condition changes.
9. No task is merged to `main` automatically. Merge requires explicit authorization after the appropriate audit decision.

## Scope Discipline

If Codex finds a problem outside the frozen task, it must not modify it opportunistically. It records the issue under `Known Gaps` or `Follow-up` for ChatGPT to turn into a later task. If that issue prevents completion of the frozen task, Codex uses `BLOCKED` and explains why. Codex then waits for ChatGPT direction.

## Branch and PR Discipline

One task normally uses one implementation branch and one PR. Recommended names are:

```text
codex/qinggan-v1-task3-execution-api
codex/qinggan-v1-task4-<short-name>
```

When an audit returns `NEEDS_FIX`, Codex continues on the original task branch and PR; a small correction does not create a new business task. The PR should link all four required evidence artifacts and must remain unmerged until explicitly authorized.
