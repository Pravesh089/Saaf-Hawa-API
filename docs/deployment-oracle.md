# Deployment Guide — Oracle Cloud Always Free (₹0/month)

This puts the whole stack (API + TimescaleDB/PostGIS + raw archive) on an **Oracle Cloud
"Always Free"** ARM server. It's genuinely free forever — Oracle never charges Always Free
resources. A debit card is required **only for identity verification** at signup; no money is
taken.

The server is a normal Ubuntu box, so once it's up this is identical to the generic VPS guide
(`docs/deployment.md`). The Oracle-specific parts are: creating the account, creating the
instance, and opening the firewall (Oracle has *two* firewalls — see step 5, this trips up
everyone).

---

## What you get on Always Free

- Up to **4 ARM (Ampere A1) cores + 24 GB RAM**, split however you like. We'll use a single
  VM with ~2 cores / 12 GB — plenty for this whole stack with room to spare.
- 200 GB block storage.
- A public IP.
- Cost: **₹0**, indefinitely.

## 1. Create the Oracle Cloud account

1. Go to <https://www.oracle.com/cloud/free/> → **Start for free**.
2. Fill in your details. For **Home Region** pick the one closest to you that has Always Free
   ARM capacity — for India, **India South (Hyderabad)** or **India West (Mumbai)**.
   > ⚠️ Your home region is permanent. ARM capacity in Mumbai is often full; if instance
   > creation fails later with "out of host capacity", that's why — see the workaround in step 4.
3. Verify your email and phone.
4. Enter your **debit card** for verification. Oracle places a tiny temporary hold (≈ ₹1–100)
   that is refunded. **Always Free stays free** even after the trial credits expire — just
   don't click "Upgrade to Paid".

## 2. Create the Ubuntu server (Compute instance)

1. In the console, hamburger menu → **Compute → Instances → Create instance**.
2. **Name**: `saafhawa`.
3. **Image and shape** → **Edit**:
   - Image: **Canonical Ubuntu 22.04** (or 24.04).
   - Shape: click **Change shape → Ampere → VM.Standard.A1.Flex**. Set **2 OCPUs** and
     **12 GB memory**.
4. **Networking**: leave the defaults (it creates a VCN). Make sure **"Assign a public IPv4
   address"** is checked.
5. **Add SSH keys**: choose **Generate a key pair for me** and **download both** the private
   and public key. Keep the private key safe — it's how you log in.
6. Click **Create**. Wait until the instance state is **Running**, then copy its
   **Public IP address**.

> 💡 **"Out of host capacity" error?** ARM free capacity comes and goes. Either: (a) try a
> different Availability Domain in the dropdown, (b) try again every few hours (it frees up),
> or (c) temporarily request 1 OCPU / 6 GB which is easier to place. There are also community
> scripts that retry for you, but manual retrying usually works within a day.

## 3. Connect via SSH

From your laptop terminal (adjust the path to the private key you downloaded):

```bash
chmod 600 ~/Downloads/ssh-key-*.key
ssh -i ~/Downloads/ssh-key-*.key ubuntu@YOUR_SERVER_IP
```

The default user on Ubuntu images is **`ubuntu`**.

## 4. Install Docker

```bash
sudo apt-get update
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && newgrp docker
docker --version && docker compose version   # both should print versions
```

## 5. Open BOTH firewalls (the #1 gotcha)

Oracle blocks inbound traffic in **two** places. You must open port 80 in both or you'll get
"connection timed out" from outside.

**(a) Oracle Security List** (cloud firewall):
1. Console → **Networking → Virtual Cloud Networks** → your VCN → **Security Lists** →
   *Default Security List*.
2. **Add Ingress Rule**:
   - Source CIDR: `0.0.0.0/0`
   - IP Protocol: **TCP**, Destination Port Range: **80**
   - Save. (Port 22 is usually already open.)
   - *(Later, for HTTPS, add another rule for port **443**.)*

**(b) The instance's own `iptables`** (Ubuntu images ship with it locked down):

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo netfilter-persistent save
```

## 6. Get the code and configure

```bash
git clone https://github.com/Pravesh089/Saaf-Hawa-API.git
cd Saaf-Hawa-API/deploy
cp .env.example .env
nano .env
```

Fill in `.env` — generate strong values with `openssl rand -hex 24`:

```
DB_NAME=saafhawa
DB_USER=saafhawa
DB_PASSWORD=<openssl rand -hex 24>

S3_ACCESS_KEY=<openssl rand -hex 24>
S3_SECRET_KEY=<openssl rand -hex 24>
S3_BUCKET=saafhawa-raw

SAAFHAWA_ADMIN_TOKEN=<openssl rand -hex 24>

DATAGOVIN_API_KEY=<your verified data.gov.in key>

CONTACT_URL=https://github.com/Pravesh089/Saaf-Hawa-API
```

## 7. Build and start

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
docker compose -f docker-compose.prod.yml logs -f app
```

You're up when you see `Started SaafHawaApplication`. Flyway runs the TimescaleDB hypertable +
continuous-aggregate migrations automatically on first boot — the ARM build of
`timescale/timescaledb-ha:pg16` supports both TimescaleDB and PostGIS, so nothing needs to
change for ARM.

## 8. Verify and pull live data

From your laptop:

```bash
curl http://YOUR_SERVER_IP/v1/status
```

Trigger an immediate ingest (otherwise it runs hourly on its own):

```bash
curl -X POST http://YOUR_SERVER_IP/admin/ingest/cpcb-datagovin \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"

curl "http://YOUR_SERVER_IP/v1/latest?city=Delhi"
```

Open `http://YOUR_SERVER_IP/docs` in a browser for the interactive API.

## 9. (Optional) Domain + HTTPS

Same as the generic guide: point an A record at the server IP, add port 443 to the Oracle
Security List **and** iptables, then enable the Caddy service in `docker-compose.prod.yml`
(see `docs/deployment.md` step 8).

---

## Oracle-specific troubleshooting

| Symptom | Fix |
|---|---|
| `curl` from outside times out | You opened only one firewall. Do **both** parts of step 5 (Security List *and* iptables). |
| Instance won't create: "out of host capacity" | ARM free capacity is full in your AD. Try another AD, ask for 1 OCPU/6 GB, or retry later (step 2). |
| `Permission denied (publickey)` on SSH | Wrong key or user. User is `ubuntu`; key is the private file you downloaded; `chmod 600` it. |
| ARM image pull errors | All images used here (`timescale/timescaledb-ha:pg16`, `minio`, the app build) are multi-arch and run on ARM. Just re-run `up`. |
| Build killed / OOM | You set the shape too small. Recreate with ≥ 6 GB, or add swap. |

After this, everything else (updating, backups) is identical to `docs/deployment.md`
sections 7 and 9.
