# WWT Warehouse Monitor

## Dashboard

**https://averageprogrammer78.github.io/AutoFlagger/**

No install needed — opens in the browser with live data. Everyone with the link can see everything.

This is an **MVP** — there are no logins or role-based access controls. The sidebar views are just there to help you find what's relevant to you faster.

### Views

| View | What it shows |
|------|---------------|
| **Overview** | Stats, active work area items, recent CV alerts |
| **CV Alerts** | Full alert log, search/filter, breakdown by location |
| **Work Area** | Checked-out items, timers, checkout/checkin forms |
| **Inventory** | Full item catalog with search |
| **Locations** | Storage capacity bars by location |

Data refreshes every 5 seconds.

---

## How It Works

```
computer-vision.py  ──writes──▶  alerts-compvis.csv  ──reads──▶  server (Render)
WWT7.java           ──writes──▶  wwt7.csv             ──reads──▶  server (Render)
                                                                        │
                                                              autoflagger.onrender.com/api
                                                                        │
                                                                   dashboard
```

| Data | Source |
|------|--------|
| CV alerts | alerts-compvis.csv |
| Location capacity | wwt7.csv + locations |
| Item inventory | items_with_area.csv |
| Work area timers | In-memory (via API) |

---

## API Endpoints

Base URL: `https://autoflagger.onrender.com/api`

```
GET  /api/status          — full system snapshot (stats, work area, alerts, locations)
GET  /api/items?search=   — item list with optional search
GET  /api/alerts          — full CV alert log
POST /api/checkout        — body: { "partNumber": "M5XDY" }
POST /api/checkin         — body: { "partNumber": "M5XDY" }
```

---

## Server Maintenance

This section is only for whoever manages the backend.

The API server runs on **Render** at `https://autoflagger.onrender.com`. It reads CSV files written by `WWT7.java` and `computer-vision.py` and serves them to the dashboard.

To run locally:

```bash
npm install
node server.js
```

**Note:** The dashboard checkout/checkin state is in-memory on the server and is separate from WWT7's state. If both are running, use one or the other for work area operations.
