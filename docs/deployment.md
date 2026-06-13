# Deployment Guide — single VPS with Docker

This deploys the whole stack (API + TimescaleDB/PostGIS + MinIO) onto one Linux server using
Docker Compose. Files referenced live in `deploy/`.

> ⚠️ **Before you deploy:** make sure your `main` branch has the **clean** `V5__continuous_aggregates.sql`.
> An earlier copy on `main` was corrupted by a copy-paste (it contains stray text) and will make the
> database migrations fail on startup. The fixed version is on branch `claude/youthful-cori-l390ze`.
> Confirm `main` is clean with:
> ```
> grep -nE "KB|good news|----" src/main/resources/db/migration/V5__continuous_aggregates.sql
> ```
> That should print **nothing**. If it prints lines, replace the file with the clean version first.

---

## 0. What you need

- A VPS running Ubuntu 22.04/24.04. **2 GB RAM minimum** (the image build needs it); 4 GB
  comfortable. 2 vCPU, ~25 GB disk to start (the raw archive grows over time).
  Good cheap options: Hetzner CX22, DigitalOcean Basic, Vultr.
- A free **data.gov.in API key** (register at https://data.gov.in) for live data.
- SSH access to the server.
- *(Optional, later)* a domain name for HTTPS.

## 1. Provision the server & install Docker

SSH in as root (or a sudo user), then:

```bash
# Install Docker Engine + Compose plugin
curl -fsSL https://get.docker.com | sh

# (optional) run docker without sudo
sudo usermod -aG docker $USER && newgrp docker

docker --version && docker compose version   # both should print versions
```

## 2. Get the code onto the server

```bash
git clone https://github.com/Pravesh089/Saaf-Hawa-API.git
cd Saaf-Hawa-API/deploy
```

## 3. Configure secrets

```bash
cp .env.example .env
nano .env        # fill in strong passwords, your DATAGOVIN_API_KEY, and an admin token
```

Generate strong values, e.g.:

```bash
openssl rand -hex 24     # use for SAAFHAWA_ADMIN_TOKEN and the passwords
```

## 4. Build and start

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

The first build takes a few minutes (it compiles the app). Watch the logs:

```bash
docker compose -f docker-compose.prod.yml logs -f app
```

You're up when you see `Started SaafHawaApplication`. The Flyway migrations (including the
TimescaleDB hypertable and continuous aggregates) run automatically on first boot.

## 5. Open the firewall & verify

```bash
# allow HTTP (and SSH); adjust if you use ufw
sudo ufw allow 22/tcp && sudo ufw allow 80/tcp && sudo ufw enable
```

From your laptop (replace with your server's IP):

```bash
curl http://YOUR_SERVER_IP/v1/status
```

Then open `http://YOUR_SERVER_IP/docs` in a browser for the interactive API.

## 6. Pull live data

The hourly scheduler will fetch automatically (since you set `DATAGOVIN_API_KEY`). To trigger an
immediate fetch:

```bash
curl -X POST http://YOUR_SERVER_IP/admin/ingest/cpcb-datagovin \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"

curl "http://YOUR_SERVER_IP/v1/latest?city=Delhi"
```

## 7. Updating after you push new code

```bash
cd ~/Saaf-Hawa-API && git pull
cd deploy && docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

Your data is preserved (it lives in Docker volumes `dbdata` and `miniodata`, not in the container).

## 8. (Optional) Add a domain + HTTPS

1. Buy a domain; create an **A record** pointing `api.yourdomain.com` → your server IP.
2. Put that domain in `deploy/Caddyfile`.
3. In `docker-compose.prod.yml`: remove the `app` service's `ports:` block, then uncomment the
   `caddy` service and the `caddydata`/`caddyconfig` volumes.
4. Re-run the `up -d` command. Caddy fetches a free Let's Encrypt certificate automatically.
   Open the firewall for 443: `sudo ufw allow 443/tcp`.

You'll then be live at `https://api.yourdomain.com/docs`.

## 9. Backups (recommended)

```bash
# nightly database dump (add to cron)
docker compose -f docker-compose.prod.yml exec -T db \
  pg_dump -U saafhawa -Fc saafhawa > backup-$(date +%F).dump
```

The raw-payload archive in MinIO is your ultimate recovery path — the dataset can be rebuilt by
replaying it. Copy the `miniodata` volume off-server periodically too.

## Common issues

| Symptom | Fix |
|---|---|
| Build killed / out of memory | Server needs ≥ 2 GB RAM. Add swap or resize. |
| Migrations fail on V5 | `main` has the corrupted V5 — replace with the clean version (see top of this doc). |
| `extension "timescaledb" ... ` error | You used a plain `postgres` image. Use `timescale/timescaledb-ha:pg16` (already set in the compose file). |
| Port 80 in use | Another web server is running; stop it or change the published port. |
| Can't reach it from outside | Open the firewall (step 5) and check your VPS provider's cloud firewall too. |
