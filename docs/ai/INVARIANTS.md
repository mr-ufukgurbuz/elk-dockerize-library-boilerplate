# Invariants and pitfalls

## Invariants (must remain true)

### Java library
1. **Zero runtime dependencies** for `elk-logger`. `pom.xml` has only test-scoped JUnit. `libs/build-without-maven.sh` must keep working with plain `javac`.
2. **Java 11 API only.** `maven.compiler.release=11`. No records, no `switch` expressions, no `String.formatted`, no `List.toList()` on streams, no `HttpClient.close()` (Java 21).
3. **Never throw to the caller.** `LoggerContext.dispatch()` wraps everything in `try/catch Throwable`. Config parsing falls back to defaults on bad values.
4. **Never block the caller**, except `OverflowPolicy.BLOCK` (bounded by `elk.overflow.block.timeout.ms`). No network I/O on caller threads.
5. **Transport is single-threaded**: only the dispatcher worker calls `Transport.send/close`.
6. **No event is silently lost**: every event ends as `sent`, `writtenToFallback` or `dropped` (counted in `ElkStats`).
7. **Library never logs through itself.** Internal diagnostics go to `InternalLog` (stderr, rate-limited). Using `ElkLogger` inside the library causes recursion when the network is down.
8. **Level checks are cheap.** `DefaultElkLogger.isEnabled` reads one volatile `CachedLevel`; do not add locking there.
9. **Snapshot at call time.** Message formatting, MDC copy and JSON serialization happen on the caller thread so later mutation of arguments does not change the log.
10. `Level.value()` numbers are persisted in ES as `level_value` — never change them.
11. `ElkConfig.sanitizeProject` and the Logstash `gsub` must implement the same rule.

### ELK stack
12. Setup scripts are **idempotent** (they run on every `elk up`). Use `PUT` (create-or-update), never "create-only" calls that fail on second run (Kibana import uses `overwrite=true`; space creation tolerates HTTP 409).
13. Images are never pulled on the target: `pull_policy: never`. Any new image must be added to `build-bundle.sh` (`IMAGES`), `install.sh` checks and `elk.sh check_prereqs`.
14. No internet access from containers is assumed (geoip downloader off, telemetry off, newsfeed off).
15. `.env` is never overwritten by `install.sh` on upgrades (passwords are preserved). `ELASTIC_PASSWORD` is only applied by ES on first start of an empty volume.
16. Scripts executed on the RHEL host must only use: bash, coreutils, sed, awk, grep, curl, tar, systemctl, docker. (`python3` is optional and always has a fallback.)
17. Container user for ES-based setup containers is the image default (uid 1000) — bind-mounted files must be world-readable (`install.sh` does `chmod -R o+rX`).

## Pitfalls already hit (do not reintroduce)

| Pitfall | Why it broke | Current solution |
|---|---|---|
| Index name `logs-<p>-<date>` | ES 8 built-in template `logs-*-*` turns it into a **data stream**, plain index writes fail | Prefix `applogs-` |
| Top-level field `host` | Logstash tcp/http inputs (ECS disabled) write `host` = sender IP, colliding with library data | Library sends `hostname`; Logstash renames input `host` → `client_ip` |
| Input `tags => [...]` in Logstash | Merged into the user's `tags` array | Inputs use `add_field => { "transport" => ... }` |
| `ulimits.memlock` / `nofile` in compose | Fails in restricted runtimes ("error setting rlimit") | Removed; `daemon.json` sets `default-ulimits.nofile`; `bootstrap.memory_lock` not used |
| `/usr/local/bin/elk` symlink | `dirname $0` resolved to `/usr/local/bin` | `readlink -f "$0"` |
| Exited setup containers not re-run by `compose up` | Changes to setup JSON were not applied | `elk up` runs `compose rm -f -s setup kibana-setup` first |
| Docker binaries in `/usr/local/bin` invisible to `sudo` | RHEL `secure_path` lacks `/usr/local/bin` | `/usr/bin/docker` symlink + `PATH` in `docker.service` |
| Overwriting RHEL's `/usr/bin/runc` | RPM-owned file | Static binaries stay in `/usr/local/bin` |
| ILM warm phase min_age > delete min_age | invalid policy when retention < 2d | Only hot + delete phases |
| Logstash deprecation warnings (`http.host`, `pipeline.buffer.type`, `decode_size_limit_bytes`) | Noise / future breakage | Explicit settings in `logstash.yml` and the codec |
| Docker Hub 429 rate limit in bundle build | Pull fails | 3 retries, then fall back to local image |

## Things that look wrong but are intentional

- HTTP TLS on ES is disabled: isolated-network assumption; see `docs/10-GUVENLIK.md` for enabling it.
- Legacy "internal collection" monitoring is used (deprecated in 8.x): it works offline without Metricbeat. Removed in 9.x — see RECIPES "Upgrade to 9.x".
- `index.mapping.ignore_malformed: true`: a type conflict in `fields.*` should not drop the whole event.
- The SLF4J logger ignores Marker-specific `isXEnabled(Marker)`: level check only.
