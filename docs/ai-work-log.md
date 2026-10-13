# AI-assisted engineering work log

Date: 2026-10-13. Project owner: Rakesh Thoutam. Assistant: Codex. This log describes the current implementation session, not fabricated multi-day work or completed human approvals.

## Inputs and task definition

Owner requested a new standalone Java/Spring Boot implementation based on the assessment requirements, with simple setup. Requirements: runnable prototype, architecture, three scenarios, tests, risk/trade-off discussion and evidence of disciplined AI use.

Implementation prompt constraints used: keep one service and H2, make links survive restart, implement core APIs and analytics, show honest greenfield/brownfield sequencing, test failure paths, use a fresh repository and independent application/UI source.

## Traceability

| Decision / output | Disposition | Reason and validation |
|---|---|---|
| Single Spring Boot process + JDBC + file H2 | Accepted implementation approach | Matches local simplicity and explicit transaction behavior |
| Separate Redis, external database, AI orchestration runtime | Not adopted | Not needed to demonstrate AI-assisted engineering |
| New domain records, endpoint namespace, schema and dashboard | Generated, edited and tested | Fresh application source; no imported project Git history |
| Baseline first, analytics enhancement second | Accepted | Actual commit boundary supports the brownfield scenario |
| Atomic total/day visit accounting | Accepted | Failure-injection test proves rollback rather than misleading partial counts |
| HEAD counted as a visit | Rejected design | Link checkers should not inflate counts; separate read path |
| Unique-human / country analytics | Not adopted | Would require extra privacy and identification assumptions |
| Expiry uses an injected Clock | Accepted | Boundary tested without sleeps |
| URL length checked before encoding only | Revised during review | URI encoding can expand the stored string; final length now checked |
| Unknown fields / fractional lifetimes silently accepted | Rejected | Explicit strict JSON binding settings and tests |
| Initial formatter run under Homebrew Maven's Java 26 | Tooling failure, corrected | Formatter/JDK incompatibility; switched to supported Java 17, then verified Java 21 |
| Rebuilding a JAR while executing it | Runtime verification mistake, corrected | Class loading failed after replacement; restarted from a stable copied JAR and tested persistence |
| Claiming production readiness | Not adopted | Local-only controls and remaining deployment gaps are documented |

## Quality gates and security

Maven verify runs tests, JaCoCo report, strict formatting and SpotBugs. SpotBugs has one narrow constructor-only exclusion for intentionally shared injected collaborators; mutable data exposure remains checked. Reproduction commands and actual results are in validation.md. Live HTTP and browser checks supplement source/test inspection.

Only synthetic example.com URLs were used for tests and demos. No assignment PDF, recruiter correspondence, credentials, runtime database or personal logs are committed. Generated text/code was checked by automated tools and reviewed within this AI session. Owner's final human review and approval remain pending.
