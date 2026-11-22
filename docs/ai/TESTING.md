# Testing and verification

## 1. Unit + integration tests (fast, no Docker)

```bash
mvn -B verify
```
- `MessageFormatterTest` — `{}` semantics, escaping, trailing throwable, broken `toString`.
- `JsonWriterTest` — escaping, nested values, Turkish characters, NaN.
- `ConfigLoaderTest` — layering (file → env → sysprop), placeholders, invalid values, project sanitization.
- `LevelResolverTest` — longest-prefix level resolution.
- `Slf4jBridgeTest` (slf4j module) — provider binding, shared MDC, fluent key/values → fields, markers → tags.
- `TransportIntegrationTest` — real sockets: TCP json_lines fake server, HTTP fake server with basic auth,
  unreachable Logstash → fallback file, queue overflow is non-blocking, runtime level change.

Also verify the no-Maven path still compiles: `./libs/build-without-maven.sh /tmp/out`.

## 2. End-to-end (Docker required, ~3-5 min)

```bash
sudo sysctl -w vm.max_map_count=262144
tests/e2e.sh --down
```
Starts the real stack from `elk-stack/`, runs both sample apps with a unique `APP_ENV`, asserts:
documents exist in `applogs-order-service-*` and `applogs-payment-service-*`, no cross-project leakage,
ERROR events carry `exception.stacktrace`, the Kibana dashboard object exists.

## 3. Manual checks for UI-affecting changes

```bash
cd elk-stack && ./bin/elk.sh up
./bin/elk.sh project create order-service --user order_viewer --password OrderView123
```
- Browser `http://localhost:5601` (elastic / `.env` password): Dashboards → "Uygulama Loglari (Tum Projeler)": all panels render, no error toasts.
- Log in as `order_viewer`: only the `order-service` space and data is visible.
- Stack Monitoring shows Elasticsearch, Kibana, Logstash.
Headless alternative: Playwright (`chromium.launch()`), log in at `/login`, open `/app/dashboards#/view/applogs-all-dashboard`, screenshot.

## 4. Offline bundle

```bash
./offline-bundle/build-bundle.sh --output /tmp/bundle            # online machine
# simulate offline install on a docker host:
docker compose -f elk-stack/docker-compose.yml down -v; docker rmi elasticsearch:8.19.25 kibana:8.19.25 logstash:8.19.25
cd /tmp/bundle/elk-offline-bundle-*/ && sudo ./install.sh --skip-docker
```
The Docker-install part of `install.sh` can only be fully tested on a real RHEL 8/9 VM.

## 5. Final checklist before you finish

- [ ] `mvn -B verify` green
- [ ] `bash -n` on every changed `.sh`
- [ ] `cd elk-stack && cp -n .env.example .env && docker compose config -q`
- [ ] ndjson regenerated if generator changed (CI diff check)
- [ ] Event schema change → all four places + EVENT-SCHEMA.md + docs/03
- [ ] New config key → ElkConfig + properties.example + docs/07 + test
- [ ] Turkish docs updated for user-visible behavior
- [ ] `tests/e2e.sh --down` green if stack/serializer/transport changed
- [ ] No new runtime dependency in `libs/elk-logger/pom.xml`
