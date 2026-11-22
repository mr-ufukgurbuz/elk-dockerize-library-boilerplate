# Recipes

Each recipe lists files to touch in order and how to verify. Always finish with the checklist in TESTING.md.

## R1. Add a new library configuration key

Example: `elk.message.prefix`.
1. `config/ElkConfig.java`: add `public static final String KEY_MESSAGE_PREFIX = "elk.message.prefix";`, a private final field,
   parse it in the constructor with `str/bool/intVal/longVal/enumVal` (always with a default and clamping), add a getter.
2. Use it (usually in `internal/LoggerContext` or `internal/EventSerializer`).
3. Add it with its default to `libs/elk-logger/elk-logger.properties.example`.
4. Document it in `docs/07-KONFIGURASYON-REFERANSI.md` (correct table, Turkish).
5. Add a test in `ConfigLoaderTest` (default + override) and a behavior test.
Env var mapping is automatic: `ELK_MESSAGE_PREFIX`.

## R2. Add a new field to every event

1. `internal/EventSerializer.toJson()`: emit it (respect reserved names: no `host`, `port`, `type`, `headers`, `@version`).
2. `elk-stack/setup/es/index-template.json`: add mapping under `properties`.
3. Optional: Kibana column/visualization in `generate-saved-objects.py`, then
   `python3 elk-stack/setup/kibana/generate-saved-objects.py > elk-stack/setup/kibana/applogs-saved-objects.ndjson`.
4. Update `docs/ai/EVENT-SCHEMA.md` and `docs/03-ELASTICSEARCH.md`.
5. Assert it in `TransportIntegrationTest.tcpJsonLines`.
6. Template changes apply to **new** indices (next UTC day) — mention this in the change notes.

## R3. Add a new transport (e.g. direct Elasticsearch `_bulk`)

1. Add enum value in `config/TransportType`.
2. Create `internal/XxxTransport implements Transport` (package-private). `send()` must throw `IOException` on any failure
   and must not retry internally (the dispatcher does). It is called from one thread only.
3. Map it in `internal/Transports.create()`.
4. Add config keys (R1) for its endpoint/credentials.
5. Test with a fake server in `TransportIntegrationTest` (see `httpJsonArray` using `com.sun.net.httpserver.HttpServer`).
6. If it bypasses Logstash, it must produce the same index name and the fields Logstash would add (`client_ip`, `transport`), and needs an ES user with `applogs_writer` role.

## R4. Change Elastic Stack version (8.x)

1. `elk-stack/.env.example` → `ELK_VERSION`.
2. `.github/workflows/offline-bundle.yml` default input, `README.md` "Doğrulanmış Sürümler", `docs/01`, `docs/10` (certutil image tag), `docs/ai/README.md`.
3. `grep -rn "8\.19\.25" --exclude-dir=target .` and update remaining references.
4. Run `tests/e2e.sh --down`, then open Kibana and check the dashboard renders (TESTING.md §3).

## R5. Upgrade to Elastic 9.x (breaking)

Known differences to handle:
- Internal monitoring collection removed: delete `xpack.monitoring.collection.enabled` (ES), `xpack.monitoring.*` (logstash.yml),
  `monitoring.*` (kibana.yml); add a Metricbeat or Elastic Agent container (+ its image in the bundle) if Stack Monitoring is needed.
- Logstash 9: `pipeline.buffer.type` default changes (we set `direct`; review), `http.host` alias removed (already using `api.http.host`).
- Check Kibana legacy visualization types still load (`histogram`, `pie`, `metric`, `table`); otherwise convert generator to Lens.
- Run Upgrade Assistant on the 8.19 cluster first; take a snapshot.

## R6. Add a Kibana visualization to the default dashboard

1. In `generate-saved-objects.py` add `vis(DV + "-vis-<name>", "<Title> (Tum Projeler)", <builder>(...), "<optional KQL>")` to `objects`.
   Keep the id prefix `DV` and the `(Tum Projeler)` suffix (project spaces rely on sed replacement).
2. Add it to `layout` with grid coordinates (48 columns wide).
3. Regenerate ndjson (command in R2.3). CI fails if you forget.
4. `sudo elk up` (or `docker compose up kibana-setup`) and check in browser. For existing project spaces re-run `elk project create <p>`.

## R7. Change the index name pattern

High-risk; touches: Logstash output, index template `index_patterns`, `role-applogs-*.json`, Kibana data view title in generator,
`elk.sh` (status, test-log hint, project create sed, project list awk, delete-data, snapshot), `tests/e2e.sh`, all docs.
Prefer not to do this.

## R8. Add authentication to the Logstash HTTP input

1. `applogs.conf` http input: `user => "${LS_HTTP_USER}" password => "${LS_HTTP_PASSWORD}"`.
2. Add both vars to `.env.example` (with `CHANGE_ME` handled by `elk.sh init-secrets` loop) and to logstash `environment` in compose.
3. Clients: `elk.logstash.http.username/password` (already supported).
4. Docs: `docs/04-LOGSTASH.md`, `docs/10-GUVENLIK.md`.

## R9. Add a new `elk.sh` subcommand

Add `cmd_<name>()` function, a `case` entry in `main`, a line in `cmd_help`, document in `docs/02-KURULUM-RHEL.md` §3.
Use `load_env` to get `$ES`, `$KB`, `$AUTH`. Use `die` for errors. Keep it idempotent where possible.

## R10. Release a new library version

1. Bump `<version>` in root `pom.xml` and every module `pom.xml` (parent reference too) and `internal/Version.VERSION`.
2. Update version strings in: `examples/*/pom.xml` dependency, `examples/run-sample.sh`, `libs/install-to-local-maven-repo.sh` (`V=`), `tests/e2e.sh` jar names, docs (`grep -rn "1\.0\.0"`).
3. `mvn -B verify`, copy jars to `dist/` (`cp libs/*/target/elk-logger*.jar dist/` and remove old ones).
