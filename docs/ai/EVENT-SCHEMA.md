# Event schema (the contract)

One log event = one JSON object. Produced by `internal/EventSerializer.toJson()`.

## The four places that must agree

| # | Location | What to update |
|---|---|---|
| 1 | `libs/elk-logger/src/main/java/.../internal/EventSerializer.java` | Field emission |
| 2 | `elk-stack/logstash/pipeline/applogs.conf` | Normalization / renames / routing |
| 3 | `elk-stack/setup/es/index-template.json` | ES mapping (and `index.query.default_field`) |
| 4 | `elk-stack/setup/kibana/generate-saved-objects.py` (+ regenerate ndjson) | Columns, visualizations, queries |

Also update: `docs/03-ELASTICSEARCH.md` (field table), this file, and assertions in
`libs/elk-logger/src/test/java/.../TransportIntegrationTest.java`.

## Fields

| Field | JSON type | Always? | ES type | Source |
|---|---|---|---|---|
| `@timestamp` | string ISO-8601 UTC, millis (`2026-01-31T10:15:30.123Z`) | yes | date | `Instant.now()` at log call; Logstash json codecs parse it into the event timestamp; the daily index name uses it |
| `project` | string `[a-z0-9_-]+` | yes | keyword | `ElkConfig.sanitizeProject(elk.project.name)`; re-sanitized in Logstash (same rule) |
| `application` | string | yes | keyword | `elk.application.name` (default: raw project name) |
| `environment` | string | yes | keyword | `elk.environment` |
| `hostname` | string | yes | keyword | `elk.hostname` or detected |
| `pid` | number | yes | long | `ProcessHandle` |
| `level` | `TRACE|DEBUG|INFO|WARN|ERROR` | yes | keyword | `Level.name()` |
| `level_value` | 0/10/20/30/40 | yes | integer | `Level.value()` — DO NOT renumber |
| `logger` | string | yes | keyword | logger name |
| `thread` | string | yes | keyword | current thread |
| `message` | string (truncated at `elk.message.max.length`) | yes | text + `.keyword`(512) | formatted message |
| `message_template` | string ≤1024 | only if args were given | keyword | raw pattern |
| `labels` | object<string,string> | if configured | `labels.*` keyword | `elk.labels.*` |
| `mdc` | object<string,string> | if MDC non-empty and `elk.include.mdc` | `mdc.*` keyword | `ElkMDC` |
| `fields` | object<string, any JSON> | if builder fields | dynamic (strings → keyword + `.text`) | `LogEventBuilder.field()`, SLF4J `addKeyValue` |
| `tags` | array<string> | if tags | keyword | `LogEventBuilder.tag()`, SLF4J markers |
| `exception.class` | string | if throwable | keyword | |
| `exception.message` | string | if throwable | text + keyword | |
| `exception.root_class` / `root_message` | string | if cause chain depth > 0 | keyword / text | deepest cause |
| `exception.stacktrace` | string (truncated at `elk.stacktrace.max.length`) | if throwable | text | `printStackTrace` |
| `caller.class/method/file/line` | strings / number | if `elk.include.caller` | keyword / integer | `StackWalker` |
| `lib_version` | string | yes | keyword | `internal/Version.VERSION` |

Added by Logstash (never send these from the library):

| Field | Meaning |
|---|---|
| `client_ip` | sender IP (tcp `host` renamed; http `remote_host`) |
| `transport` | `tcp` or `http` |

Reserved top-level names the library must NOT emit: `host`, `port`, `@version`, `headers`, `type`
(Logstash inputs use or remove them). Use nested objects (`fields.*`) for user data.

## Wire formats

- TCP: UTF-8, one JSON object per line, `\n` terminated (`json_lines` codec). No ack.
- HTTP: `POST /` with `Content-Type: application/json`, body is a JSON **array** of events. 2xx = accepted.

## Index naming

`applogs-<project>-YYYY.MM.dd` where the date is the UTC day of `@timestamp`.
Prefix `applogs-` is referenced by: Logstash output, index template pattern, writer/reader roles,
Kibana data view title, `elk.sh` (status/project/snapshot), docs.

## Compatibility policy

- Adding a new optional field: backwards compatible. Add mapping (or rely on dynamic `keyword`).
- Renaming/removing/retyping a field: breaking. Old daily indices keep the old mapping — Kibana shows conflicts
  for the retention period. Avoid; if unavoidable, bump the library major version and document it.
