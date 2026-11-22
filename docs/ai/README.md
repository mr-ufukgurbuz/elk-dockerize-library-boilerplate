# AI Agent Guide — start here

This folder is written for AI coding agents (including smaller models) that need to modify,
extend or refactor this repository safely. It is in English on purpose; the human-facing
documentation in `docs/*.md` is Turkish and must be kept in sync when behavior changes.

## Read in this order

| File | Why |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | What each component does and which file owns which responsibility |
| [EVENT-SCHEMA.md](EVENT-SCHEMA.md) | **The contract** between the Java library and the ELK stack. Most bugs come from breaking it |
| [INVARIANTS.md](INVARIANTS.md) | Rules that must stay true after any change + known pitfalls |
| [RECIPES.md](RECIPES.md) | Step-by-step instructions for the most common change requests |
| [TESTING.md](TESTING.md) | How to verify your change (unit, e2e, manual) and the final checklist |

## 30-second summary

- `libs/elk-logger`: zero-dependency Java 11 logging library. Apps call `ElkLoggerFactory.getLogger(X.class).info("..{}..", arg)`.
  Events are serialized to one-line JSON and shipped asynchronously to Logstash (TCP json_lines on 5000 or HTTP JSON array on 8080).
- `libs/elk-logger-slf4j`: optional SLF4J 2.x provider that delegates to `elk-logger`.
- `elk-stack`: docker compose with Elasticsearch, Logstash, Kibana + two one-shot setup containers.
  Logstash writes to index `applogs-<project>-YYYY.MM.dd`. `project` comes from `elk.project.name`.
- `offline-bundle`: `build-bundle.sh` (online machine) creates a tarball with static Docker binaries, `docker save`d images,
  the stack, jars and docs. `install.sh` installs everything on an offline RHEL 8/9 host.
- Versions: Elastic Stack 8.19.25, Docker static 29.10.0, Compose v5.7.1, Java release 11, SLF4J 2.0.13.

## Golden rules

1. Never add runtime dependencies to `libs/elk-logger` (no Jackson, no SLF4J, no Guava). JDK 11 API only — compile with `--release 11`.
2. A logging call must never throw and must never block the caller (except `OverflowPolicy.BLOCK`, bounded by a timeout).
3. If you change the JSON event fields, update **all four** places listed in EVENT-SCHEMA.md in the same change.
4. Keep shell scripts POSIX-ish bash that runs on RHEL 8 (bash 4.4) and needs no internet. No `jq`, no `python` on the target host.
5. Every one-shot setup script must stay idempotent (it re-runs on every `elk up`).
6. Do not rename `applogs-` prefix, the `applogs-all` Kibana id prefix or the `(Tum Projeler)` title suffix without updating `bin/elk.sh project create` (it uses sed on them).
7. Run `mvn -B verify` (always) and `tests/e2e.sh --down` (if you touched `elk-stack/`, the serializer or transports).
