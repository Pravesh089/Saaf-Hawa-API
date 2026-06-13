# Saaf Hawa — Project Guide (plain language)

A simple explanation of what this project is, who uses it, your role, which endpoints matter,
and what to build next.

---

## 1. What is this project? (the one-line version)

**Saaf Hawa ("clean air") is a free service that collects India's air-pollution data from the
government, cleans it up, and hands it out through a simple web API.**

Think of it like a **water filter for data**:
- Dirty water goes in → clean water comes out.
- Here: messy government air-quality data goes in → clean, organized, trustworthy data comes out.

### Why does it need to exist?

India's pollution data (from CPCB, the government's pollution board) is technically public, but
painful to use:
- There's **no easy way to download history** — the official portal limits you to about one week
  of data per request, with lots of manual clicking.
- The raw data is **messy** — it contains impossible values (negatives, zeros, fake "999"
  readings, sensors stuck on the same number for days), and the government attaches **no warnings**
  about which numbers to trust.
- When the government servers go down (which happens), the public loses access entirely.

So every journalist, researcher, and student ends up building their own scraper and cleaning the
data their own way — wasting effort and producing results nobody can compare.

**Saaf Hawa solves this once, for everyone:** it keeps a permanent, growing copy of the data,
flags every suspicious reading openly (it never silently deletes or "fixes" numbers), and serves
it all through one documented API that anyone can use in minutes.

---

## 2. What does it actually do? (the moving parts)

```
   Government sources                Saaf Hawa (this app)               People who need data
 ┌─────────────────────┐      ┌──────────────────────────────┐      ┌──────────────────────┐
 │ CPCB via data.gov.in │ ───► │ 1. Fetch data every hour     │      │ Journalists          │
 │ (live air-quality    │      │ 2. Save the raw copy         │ ───► │ Researchers          │
 │  readings)           │      │ 3. Clean + flag bad values   │      │ Students             │
 └─────────────────────┘      │ 4. Store it forever          │      │ App developers       │
                               │ 5. Serve it via web API      │      └──────────────────────┘
                               └──────────────────────────────┘
```

1. **Collects** — every hour it automatically pulls the latest readings from the government feed.
2. **Archives** — it saves an untouched copy of the raw data first, so nothing is ever lost.
3. **Cleans & flags** — it checks each reading and attaches quality flags (e.g. "this value is
   negative", "this sensor looks stuck", "this is a fake placeholder number"). It **keeps the
   original number** and just labels it — it never hides or changes data.
4. **Stores** — everything goes into a time-series database built to hold years of readings.
5. **Serves** — it exposes the data through a clean, documented web API (REST/JSON), with
   built-in fairness limits so no single user can overload it.

---

## 3. Who is the end user?

The end users are **people who need air-quality data but don't want to fight the government
portal**. The app is the *product for them* — they consume the data, they don't run the system.

| End user | What they want | Example |
|---|---|---|
| **Data journalist** | Pollution numbers as CSV to write a story | "PM2.5 in Delhi vs Lucknow every winter since 2017" |
| **Researcher / think-tank** | Clean datasets with documented quality flags | A pollution-tracking report across 161 cities |
| **Academic / student** | Stable, citable data for a paper | A dataset for an economics study |
| **App developer** | A lightweight endpoint to power a widget | A neighbourhood "current AQI" display |

They are **technical enough to call an API or open a CSV**, but they are not running servers. The
whole point is to save them weeks of work.

### How do they access it?

They make web requests to your API (no login needed for light use; a free API key for more).
Examples of how an end user accesses it today:

- In a **browser**: open `http://your-server:8080/docs` and click around the interactive docs.
- With **curl** (command line): `curl "http://your-server:8080/v1/latest?city=Delhi"`
- From **code** (Python, JavaScript, R, etc.): a normal HTTP GET request.
- Download a **CSV** by adding `&format=csv` to a query and opening it in Excel.

Right now "your-server" is your own machine (`localhost`). To let outsiders reach it, you'd deploy
it to the internet (see Section 6).

---

## 4. What is YOUR role?

You are the **operator / maintainer** of the service — the person who runs and looks after the
"factory", not a regular data consumer.

Your responsibilities:
- **Run and host** the application (and its database) so it stays online.
- **Hold the keys** — set the data.gov.in API key (so the app can fetch data) and the admin token.
- **Watch its health** — make sure ingestion keeps working and the data stays fresh.
- **Operate it** — trigger historical backfills, fix station naming mix-ups, adjust quality-check
  thresholds, issue or revoke API keys.

You use a **separate, private set of endpoints** (the `/admin` ones) that end users never touch.

---

## 5. Which endpoints do YOU use vs. which do END USERS use?

### 🟢 End-user (public) endpoints — the product

These are what your users call. All are under `/v1` and need no special privileges (just an
optional API key for higher limits).

| Endpoint | What it gives the user |
|---|---|
| `GET /v1/stations` | List/search monitoring stations (by state, city, map area) |
| `GET /v1/stations/{id}` | Details about one station |
| `GET /v1/stations/nearest?lat=&lon=` | Find the closest stations to a location |
| `GET /v1/measurements?station=&pollutant=&from=&to=` | The core one: pollution readings over a time range (raw, daily, or monthly; JSON or CSV) |
| `GET /v1/latest?city=` | The most recent reading per pollutant |
| `GET /v1/aqi/cities/{city}` | A city's daily Air Quality Index history |
| `GET /v1/status` | Is the data fresh? Health/freshness page |
| `GET /v1/qc/methodology` | Plain explanation of the quality flags |
| `GET /docs` | Interactive API documentation (Swagger UI) |
| `POST /v1/keys` | Sign up for a free API key |

### 🔒 Admin endpoints — for YOU only

These need your secret admin token (`Authorization: Bearer <token>`). End users never see these.

| Endpoint | What it lets you do |
|---|---|
| `POST /admin/ingest/{source}` | Manually pull data / backfill history for a date range |
| `POST /admin/stations/merge` | Fix duplicate stations (merge two into one) |
| `POST /admin/qc/reload` | Apply new quality-check settings without a restart |
| `POST /admin/keys/{id}/revoke` | Disable an abusive API key |

**Simple way to remember:** `/v1/...` = customers; `/admin/...` = you, the owner.

---

## 6. The way ahead — what to build next

The API (the engine) works. Here are the natural next steps, roughly in order of value.

### Step 1 — Put it online (so it's not just on your laptop)
Right now only you can reach it at `localhost`. Deploy it to a host so it has a public web address
(e.g. `https://api.saafhawa.in`). Options: a cheap VPS (DigitalOcean/Hetzner), or a platform like
Railway/Render/Fly.io. You'll also need a hosted TimescaleDB (e.g. Timescale Cloud's free tier).
**This is the highest-impact next step** — without it, nobody else can use the service.

### Step 2 — Build a UI (a website / dashboard) 👈 you asked about this
The API returns JSON, which is great for developers but unfriendly for the general public. A simple
website on top of the API would make it usable by everyone:
- A **map of India** with coloured dots for each station's current AQI.
- A **city search** → show a chart of pollution over time.
- A **"download CSV" button** for the data you're viewing.
- A **status page** showing whether the government feed is up.

The UI would be a **separate front-end app** (e.g. React/Next.js, or even plain HTML + a charting
library) that simply *calls the same public `/v1` endpoints* your end users use. The API doesn't
change — the UI is just a friendly face on top of it. This is the most visible, "wow" improvement.

> Note: the original project plan calls a web dashboard a *later, optional* add-on — "the API is
> the product." So a UI is valuable for reach, but it's a presentation layer, not core plumbing.

### Step 3 — Add more data sources (more history, cross-checking)
The plan (milestones M2–M3) adds:
- **Historical city AQI back to 2015** (from public archives) — so users get a decade of data.
- **OpenAQ cross-checking** — compare against another source to catch disagreements.
- **Smarter quality checks** ("stuck sensor" and "spike" detection over time).
- **Bulk downloads** — pre-made CSV/Parquet files per state/year for researchers.

### Step 4 — Polish for launch (milestone M4)
- Self-service API-key signup page, finalize rate limits.
- Metrics/monitoring so you get alerted if the government feed breaks.
- Announce it to the community (e.g. DataMeet) so journalists and researchers start using it.

### Suggested order
1. **Deploy it** (Step 1) — make it reachable.
2. **Build a basic UI** (Step 2) — make it usable by non-developers; great for demos.
3. **Backfill history** (Step 3) — make the data richer.
4. **Polish & announce** (Step 4).

---

## TL;DR

- **What:** a free, clean, always-on API for India's air-quality data, with honest quality flags.
- **End users:** journalists, researchers, students, developers — they *read* data via `/v1/...`.
- **You:** the operator — you *run* it and manage it via `/admin/...`.
- **Access today:** `http://localhost:8080/docs` on your machine.
- **Next:** put it online → build a simple website/dashboard on top → add historical data → launch.
