# Self-hosting StravaFit (Docker Compose)

This runs a complete StravaFit backend on one machine:

| Service | What it is |
|---|---|
| Supabase stack (`db`, `auth`, `rest`, `realtime`, `storage`, `api-gw`, `studio`, …) | The official Supabase self-host compose, vendored unchanged at a pinned commit |
| `migrate` | One-shot job that applies `backend/supabase/migrations` |
| `tiles` | Static [PMTiles](https://docs.protomaps.com/) server for self-hosted OpenStreetMap base maps |
| `caddy` | HTTPS (automatic certificates) and the single public entry point |
| `worker` | Kotlin worker for uploads, segment matching, heatmaps and exports. **Not published yet**, so it is behind the `worker` profile |

Until the worker exists, uploads stay in the `queued` state. Everything else (accounts, privacy, follows, the feed, comments, high-fives) works.

## Requirements

- A Linux host with 2 vCPU, 4 GB RAM and 20 GB of disk. Add disk for map tiles.
- Docker Engine 24 or newer with Compose v2.20 or newer (`docker compose version`).
- `git` and `openssl`.
- For a public instance, a DNS name pointing at the host, with ports 80 and 443 open.

## Install (about 15 minutes)

Run all commands from `infra/docker/`.

1. **Get the code.**
   ```sh
   git clone https://github.com/warpirate/stravafit.git
   cd stravafit/infra/docker
   ```

2. **Vendor the Supabase stack** at the pinned commit. It goes into `./supabase/`, which git ignores.
   ```sh
   ./scripts/fetch-supabase.sh
   ```

3. **Create `.env` and generate secrets.** This joins the upstream template with our settings, then writes fresh random secrets and signed API keys into `.env`.
   ```sh
   cat supabase/.env.example .env.example > .env
   sh supabase/utils/generate-keys.sh --update-env && rm -f .env.old
   ```

4. **Set your public address.** Edit `.env`:
   - `DOMAIN`: your host name, for example `fit.example.org`. Use `localhost` for a local trial.
   - `PUBLIC_URL`, `SITE_URL`, `API_EXTERNAL_URL` and `SUPABASE_PUBLIC_URL`: all `https://<DOMAIN>`.
   - `ADDITIONAL_REDIRECT_URLS=stravafit://auth-callback`, so email links open the app.
   - `SMTP_*`: a real mail server. Sign-up confirmations and one-time codes are sent by email.

5. **Start the stack and apply the schema.**
   ```sh
   docker compose up -d
   docker compose --profile migrate run --rm migrate
   ```
   The migrate job is safe to re-run. It records what it applied in `supabase_migrations.schema_migrations` and only applies new files. Add `-e SEED=1` to load three demo accounts. Do this on development machines only.

6. **Check it.**
   ```sh
   curl -s https://<DOMAIN>/auth/v1/health -H "apikey: $(grep ^ANON_KEY= .env | cut -d= -f2)"
   ```
   A JSON health response means auth, the gateway and TLS all work. Put `https://<DOMAIN>` and the `ANON_KEY` from `.env` into the app's server settings.

## Map tiles

Put one or more `.pmtiles` files in `./volumes/tiles/`. They are served at `https://<DOMAIN>/tiles/<name>/{z}/{x}/{y}.mvt`. For a single region you can cut an extract from the Protomaps daily build with the `pmtiles` CLI:
```sh
pmtiles extract https://build.protomaps.com/<YYYYMMDD>.pmtiles volumes/tiles/region.pmtiles --bbox=<minLon>,<minLat>,<maxLon>,<maxLat>
```
Map data is © OpenStreetMap contributors (ODbL). The app shows the attribution.

## Security notes

- **Studio is not exposed through Caddy.** Reach it with an SSH tunnel: `ssh -L 3000:localhost:3000 <host>`. Studio's port binding comes from the upstream file. Firewall every port except 80 and 443 (see `docker compose ps` for what is published).
- Keep `.env` private and back it up. `SERVICE_ROLE_KEY` bypasses row level security.
- Back up the `db` volume and the storage volume, which holds original uploads and photos. Activity files are personal health and location data.

## Upgrading

```sh
git pull
./scripts/fetch-supabase.sh            # or pass a newer upstream commit; read its CHANGELOG first
docker compose pull && docker compose up -d
docker compose --profile migrate run --rm migrate
```

## Enabling the worker (later)

When the worker image is published:
```sh
docker compose --profile worker up -d worker
```
It connects straight to Postgres and processes the queues `ingest`, `post_process`, `privacy_reprocess`, `media` and `cleanup` (see `backend/supabase/migrations/20261029000700_uploads_jobs.sql`). It uses pgmq, which the Supabase Postgres image includes.
