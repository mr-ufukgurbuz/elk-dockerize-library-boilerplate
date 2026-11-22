# Instructions for AI coding agents

Read [`docs/ai/README.md`](docs/ai/README.md) first. It contains the architecture map, the
event-schema contract between the Java library and the ELK stack, invariants you must not
break, step-by-step recipes for common changes, and the verification checklist.

Quick rules:
- `libs/elk-logger` must keep **zero runtime dependencies** and compile with `--release 11`.
- The JSON event schema is a contract: library (`EventSerializer`) ⇄ Logstash (`applogs.conf`)
  ⇄ ES template (`index-template.json`) ⇄ Kibana objects (`generate-saved-objects.py`).
  Change them together and update `docs/ai/EVENT-SCHEMA.md`.
- Logging calls must never throw and never block (except overflow policy `BLOCK`).
- Run `mvn -B verify` and, if you touched `elk-stack/`, `tests/e2e.sh` before finishing.
- Human docs are Turkish (`docs/*.md`); keep them in sync with behavior changes.
