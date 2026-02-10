# WWT Warehouse Monitor — Setup Guide

## How It All Connects

```
computer-vision.py  ──writes──▶  alerts-compvis.csv  ──reads──▶  server.js
WWT7.java           ──writes──▶  wwt7.csv             ──reads──▶  server.js
                                                                      │
                                                               http://localhost:3001
                                                                      │
                                                              dashboard (browser)
```

The Node server watches your CSV files and exposes a live API.
The dashboard polls that API every 5 seconds and updates in real time.

---

## Step 1 — Install Node.js
Download from https://nodejs.org (LTS version)

---

## Step 2 — Set up the server

Put these files in the SAME folder as your CSV files:
  - server.js
  - package.json
  - public/index.html   ← the dashboard

Your folder should look like:
```
your-folder/
  server.js
  package.json
  public/
    index.html
  items_with_area.csv
  locations_with_areas.csv
  alerts-compvis.csv
  inventory.csv
  WWT7.java
  computer-vision.py
  ... etc
```

---

## Step 3 — Install dependencies & start the server

Open a terminal in your folder and run:

```bash
npm install
node server.js
```

You should see:
```
  WWT Warehouse Monitor running at http://localhost:3001
  Watching CSVs in: /your/folder/path
```

Open http://localhost:3001 in your browser — the dashboard is live.

---

## Step 4 — Run your Java and Python programs normally

WWT7.java and computer-vision.py work exactly as before.
The server reads their CSV output files automatically — no changes needed.

- WWT7.java  →  writes wwt7.csv on exit  →  dashboard shows locations
- computer-vision.py  →  appends to alerts-compvis.csv  →  dashboard shows alerts live

---

## What Updates Live (every 5 seconds)

| Panel              | Data Source           |
|--------------------|-----------------------|
| Work area timers   | In-memory (via API)   |
| CV alerts          | alerts-compvis.csv    |
| Location capacity  | wwt7.csv + locations  |
| Item inventory     | items_with_area.csv   |
| Stats              | All of the above      |

---

## Checkout / Checkin via Dashboard

You can check items in/out directly from the browser — no need to run WWT7 interactively.
The server tracks work area state in memory and applies the same 15-minute timer logic.

Note: the dashboard's work area state is separate from WWT7's in-memory state when both
are running simultaneously. For a single source of truth, use one or the other.

---

## API Endpoints (for reference)

```
GET  /api/status          — full system snapshot (stats, work area, alerts, locations)
GET  /api/items?search=   — item list with optional search
GET  /api/alerts          — full CV alert log
POST /api/checkout        — body: { "partNumber": "M5XDY" }
POST /api/checkin         — body: { "partNumber": "M5XDY" }
```
