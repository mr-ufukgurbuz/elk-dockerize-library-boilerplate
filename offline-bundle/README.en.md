# offline-bundle — Air-gapped Installation Bundle

🇬🇧 English | [🇹🇷 Türkçe](README.md)

A **single-file bundle** that installs the ELK environment on an RHEL 8/9 server with no internet access
and nothing pre-installed (not even Docker).

## Download the ready-made bundle

GitHub Actions builds the bundle and uploads it to
**[Releases](https://github.com/mr-ufukgurbuz/elk-dockerize-library-boilerplate/releases)**:

| File | Target |
|---|---|
| `elk-offline-bundle-8.19.25-x86_64.tar.gz` (+ `.sha256`) | Intel/AMD servers |
| `elk-offline-bundle-8.19.25-aarch64.tar.gz` (+ `.sha256`) | ARM servers |

> At ~1.6 GB the bundle is not stored in the git repository (GitHub's 100 MB per-file limit);
> it is published as a Release asset (2 GB limit).

## Install

```bash
sha256sum -c elk-offline-bundle-8.19.25-x86_64.tar.gz.sha256
tar xzf elk-offline-bundle-8.19.25-x86_64.tar.gz
cd elk-offline-bundle-8.19.25-x86_64
sudo ./install.sh --open-firewall
```

When it finishes:

![elk summary](../docs/images/terminal-elk-summary.png)

| `install.sh` option | Description |
|---|---|
| `--prefix DIR` | Where the ELK files go (default `/opt/elk-stack`) |
| `--docker-data-root DIR` | Docker images + **log data** directory (default `/var/lib/docker`); only applied when Docker is installed for the first time |
| `--es-heap 2g` / `--ls-heap 1g` | JVM heap sizes |
| `--retention-days N` | How many days to keep logs (ILM) |
| `--open-firewall` | Opens ports 5601, 9200, 5000, 8080 in firewalld |
| `--skip-docker` | Skip Docker installation |
| `--no-start` | Install but do not start |

What it does: verifies SHA256 checksums → installs static Docker binaries + systemd unit (if Docker is missing)
→ sets `vm.max_map_count` → `docker load`s the images → copies the stack to `/opt/elk-stack` with random passwords
(an existing `.env` is kept on upgrades) → creates the `elk` command → optionally opens the firewall → starts and waits until healthy.

## Building the bundle yourself

On a machine with internet access (Docker or Podman + JDK 11 + Maven):

```bash
./offline-bundle/build-bundle.sh                 # x86_64
./offline-bundle/build-bundle.sh --arch aarch64  # ARM
```

To publish a new Release on GitHub, bump `BUNDLE_TAG` in [`RELEASE`](RELEASE) (e.g. `bundle-8.19.25-r2`) and push;
`.github/workflows/offline-bundle.yml` builds both architectures and uploads them to that Release.
Manual trigger: **Actions → Offline Bundle → Run workflow**.

## Files

| File | Purpose |
|---|---|
| `build-bundle.sh` | Builds the bundle: downloads static Docker + Compose, `docker save`s the ELK images, builds the jars, writes SHA256SUMS |
| `install.sh` | On the offline server: integrity check, Docker install (systemd), `vm.max_map_count`, `docker load`, `/opt/elk-stack`, firewall, start |
| `uninstall.sh` | Removal (`--purge-data`, `--remove-docker`) |
| `docker-files/` | `docker.service`, `daemon.json` |
| `RELEASE` | Tag and versions of the bundle to publish |
