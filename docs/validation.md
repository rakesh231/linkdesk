# Validation and final engineering summary

Recorded 2026-10-13. Commands were run against this new LinkDesk implementation.

| Check | Observed result |
|---|---|
| JUnit / integration tests | 29 tests; 0 failures, 0 errors, 0 skipped |
| JaCoCo lines | 167 / 185 = 90.27% |
| JaCoCo branches | 49 / 64 = 76.56% |
| Spotless formatting gate | Passed |
| SpotBugs static-analysis gate | Passed; zero remaining findings after the documented narrow DI-constructor exclusion |
| Maven verify / packaged JAR | Passed on JDK 17.0.19 and 21.0.11 |
| Live HTTP smoke | Passed health/create/HEAD/GET/counts/stats/conflict/validation/disable/missing cases |
| Small HTTP load probe | 120 successful redirects with 8 workers; stored count exactly 120 |
| Persistence | Graceful restart preserved the saved link and all 120 aggregate/daily visits |
| Browser | Created a named link; checked library, daily chart and health; no warning/error console entries observed |

The final build enforces 85% line and 70% branch coverage minima, formatting and SpotBugs. Constructor-injected singleton collaborators intentionally share mutable state; only their constructor `EI_EXPOSE_REP2` reports are excluded in `config/spotbugs-exclude.xml`. Data returned to callers is not broadly excluded.

## Reproduce

```sh
./mvnw verify
./scripts/run.sh
# In another terminal:
./scripts/smoke.sh
java scripts/LoadCheck.java
```

The live scripts create synthetic example.com links; they do not follow external redirects. Smoke disables its own link. The load probe leaves a link/count available for inspection. `docs/performance.json` records one small loopback run with a file H2 database on macOS/ARM and JDK 21; it is not a sustained throughput or production capacity claim. `docs/validation.json` contains the machine-readable local results. Reports regenerate in `target/`, which is not committed. GitHub Actions runs the same checks on JDK 17 and 21.

## Important behavioral tests

- Exact expiration instant using a fixed clock; no timing sleeps.
- Random-code collisions at actual insertion, bounded exhaustion, alias conflict.
- HEAD has no aggregate or daily side effects.
- Forty concurrent HTTP redirects preserve forty counts.
- Twelve simultaneous alias requests have exactly one 201 winner; others return 409.
- Concurrent disable/GET preserves already-admitted counts and blocks subsequent requests.
- Forced daily-storage failure returns 503 and rolls back the aggregate write.
- Additive schema initialization preserves pre-existing links and is repeatable.
- Malformed URLs, credential URLs, control characters, bad aliases, unknown JSON fields, fractional/out-of-range lifetime, malformed bodies and unsupported methods/content types.

## Remaining limits

Local single-user prototype: no authentication, ownership, traffic limits, malicious-link screening or public deployment configuration. Stats count requests rather than people. H2 row locks serialize hot-link visits; database outage prevents redirects to preserve accounting correctness. File storage persistence was checked, but backup recovery, process-kill recovery and multi-node failover were not. Additive schema initialization is not a replacement for managed production migrations. No dependency vulnerability audit, external penetration test or large-scale benchmark is claimed. UI pagination is offset-based and may move while concurrent creations occur. Daily chart accessibility provides a label per bar, but no formal accessibility audit was conducted.

The delivered artifacts are runnable source/UI, API/schema definitions, tests, CI, architecture, scenario breakdown, AI traceability and this validation report. Final human review and sign-off remain pending.
