# Architecture map

## Runtime data flow

```
App thread ── ElkLogger.info() ── DefaultElkLogger (level cache)
                                     │ enabled?
                                     ▼
                          LoggerContext.dispatch()
                             MessageFormatter.format()  ({} + trailing Throwable)
                             ElkMDC.getCopyOfContextMap()
                             [caller via StackWalker if elk.include.caller]
                             new LogEvent(...)
                             ├─ ConsoleSink.write()          (sync, optional)
                             └─ EventSerializer.toJson() ─► AsyncDispatcher.enqueue()  (non-blocking offer)
                                                                  │ ArrayBlockingQueue<String>
                                                                  ▼
                                                      worker thread "elk-logger-dispatcher"
                                                      drain batch → Transport.send(batch)
                                                         ok: stats.sent += n
                                                         fail: backoff ×2 (≤ max), retry
                                                               attempts ≥ max or shutting down → FallbackFileSink / dropped
Logstash (applogs.conf) → ES index applogs-<project>-YYYY.MM.dd → Kibana data view applogs-* / applogs-<p>-*
```

## Java library — file ownership

| File | Responsibility | Touch when |
|---|---|---|
| `ElkLoggerFactory` | Static entry point; lazily creates `LoggerContext`; `configure`, `setLevel`, `flush`, `shutdown`, `getStats`; shutdown hook; `GENERATION` counter invalidates level caches | Lifecycle/API changes |
| `ElkLogger` | Public API. Only 4 abstract methods (`getName`, `isEnabled`, `log`, `atLevel`); everything else is `default` | Adding convenience methods |
| `LogEventBuilder` | Fluent structured API | New builder options |
| `ElkMDC` | ThreadLocal MDC + `wrap()` helpers | MDC behavior |
| `Level` | Levels and numeric `value()` written as `level_value` | Never renumber (ES queries depend on it) |
| `ElkStats` | Immutable stats snapshot | New counters |
| `config/ElkConfig` | ALL configuration keys (`KEY_*` constants), parsing, defaults, clamping | New setting |
| `config/ConfigLoader` | Layered loading: classpath → file → env `ELK_*` → sysprops `elk.*`; `${VAR:default}` | Loading rules |
| `internal/LoggerContext` | Runtime of one config; `dispatch()` is the hot path; owns sinks and dispatcher | Pipeline changes |
| `internal/DefaultElkLogger` | Level check with immutable `CachedLevel` (context + generation) | Level performance |
| `internal/MessageFormatter` | SLF4J-compatible `{}` formatting | Formatting |
| `internal/EventSerializer` | Event → JSON. **Schema contract** | Fields |
| `internal/JsonWriter` | Dependency-free JSON writer | JSON bugs |
| `internal/AsyncDispatcher` | Queue, batching, retry/backoff, overflow, fallback, flush/shutdown | Delivery semantics |
| `internal/Transport` + `TcpTransport`, `HttpTransport`, `Tls`, `Transports` | Network | New transport / TLS |
| `internal/ConsoleSink`, `FallbackFileSink`, `LevelResolver`, `InternalLog`, `Version` | Helpers | — |

SLF4J module (`libs/elk-logger-slf4j`): `ElkServiceProvider` (registered in `META-INF/services/org.slf4j.spi.SLF4JServiceProvider`),
`ElkSlf4jLoggerFactory`, `ElkSlf4jLogger` (extends `AbstractLogger`, implements `LoggingEventAware` for fluent key/values),
`ElkMDCAdapter` (delegates to `ElkMDC`).

## ELK stack — file ownership

| File | Responsibility |
|---|---|
| `elk-stack/docker-compose.yml` | Services, ports, volumes, healthchecks, startup order (`depends_on` conditions), `pull_policy: never` |
| `elk-stack/.env.example` | Versions, ports, heaps, retention, password placeholders `CHANGE_ME` |
| `elk-stack/elasticsearch/elasticsearch.yml` | Single node, security on, monitoring collection, offline tweaks |
| `elk-stack/logstash/config/logstash.yml` | Persisted queue, DLQ, legacy monitoring, `ecs_compatibility: disabled` |
| `elk-stack/logstash/pipeline/applogs.conf` | Inputs tcp:5000 / http:8080, normalization, index routing |
| `elk-stack/kibana/kibana.yml` | ES connection, encryption keys, offline flags |
| `elk-stack/setup/setup-elasticsearch.sh` + `setup/es/*.json` | Built-in user passwords, `applogs_writer` role, `logstash_writer` user, `applogs_reader_all` role, ILM `applogs-retention`, index template `applogs` |
| `elk-stack/setup/setup-kibana.sh` + `setup/kibana/applogs-saved-objects.ndjson` | Imports data view `applogs-all` + searches + visualizations + dashboard |
| `elk-stack/setup/kibana/generate-saved-objects.py` | Source of truth for the ndjson (dev-time only). CI diff-checks output |
| `elk-stack/bin/elk.sh` | Operator CLI: init-secrets, up (waits for health), status, logs, test-log, project create/list/delete-data, snapshot, destroy |

Startup order: `elasticsearch (healthy)` → `setup (exit 0)` → `logstash`, `kibana (healthy)` → `kibana-setup (exit 0)`.
`elk up` removes and re-creates the two setup containers each time so they always re-run.

## Offline bundle

| File | Responsibility |
|---|---|
| `offline-bundle/build-bundle.sh` | Online machine. Downloads static docker + compose, `docker/podman save` images, builds jars, copies stack/docs, writes `SHA256SUMS`, tars |
| `offline-bundle/install.sh` | Offline RHEL. Verifies checksums, installs docker (static, `/usr/local/bin`, systemd unit, daemon.json, iptables fallback), sysctl, `docker load`, copies stack to `/opt/elk-stack` keeping existing `.env`, symlink `/usr/local/bin/elk`, firewall, `elk up` |
| `offline-bundle/uninstall.sh` | Reverse |
| `offline-bundle/docker-files/` | `docker.service`, `daemon.json` |
| `.github/workflows/offline-bundle.yml` | Manual workflow producing the bundle as artifact |
| `.github/workflows/ci.yml` | Java build/tests, shell syntax, compose validation, ndjson drift check, e2e |
