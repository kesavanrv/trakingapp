from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, RedirectResponse
from pydantic import BaseModel
import sqlite3
from datetime import datetime
from pathlib import Path

# ---------- Paths & DB ----------

BASE_DIR = Path(__file__).resolve().parent
DB_PATH = BASE_DIR / "tracking.db"   # absolute path, safer on servers


def init_db():
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS locations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            vehicle_id TEXT,
            lat REAL,
            lon REAL,
            speed REAL,
            fuel REAL,
            timestamp TEXT
        )
        """
    )
    conn.commit()
    conn.close()


def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


# ---------- FastAPI app ----------

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# Make sure DB is initialized on server startup
@app.on_event("startup")
def on_startup():
    init_db()


# ---------- Pydantic model ----------

class LocationIn(BaseModel):
    vehicle_id: str
    lat: float
    lon: float
    speed: float
    fuel: float = 0.0


# ---------- Routes ----------

@app.get("/")
def root():
    # redirect root to /map (for browser)
    return RedirectResponse(url="/map")


@app.post("/api/location")
def create_location(loc: LocationIn):
    conn = get_db()
    cur = conn.cursor()
    cur.execute(
        """
        INSERT INTO locations (vehicle_id, lat, lon, speed, fuel, timestamp)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        (loc.vehicle_id, loc.lat, loc.lon, loc.speed, loc.fuel, datetime.now().isoformat()),
    )
    conn.commit()
    conn.close()
    return {"status": "ok"}


@app.get("/api/locations/latest")
def get_latest_location(vehicle_id: str = "ANDROID01"):
    conn = get_db()
    cur = conn.cursor()
    cur.execute(
        """
        SELECT id, vehicle_id, lat, lon, speed, fuel, timestamp
        FROM locations
        WHERE vehicle_id = ?
        ORDER BY id DESC
        LIMIT 1
        """,
        (vehicle_id,),
    )
    row = cur.fetchone()
    conn.close()

    if not row:
        return {"error": "no data"}

    return dict(row)


@app.get("/api/locations")
def get_all_locations(vehicle_id: str = "ANDROID01", limit: int = 500):
    """
    Get up to `limit` points for a vehicle, in ascending time order.
    Used mainly for live / recent history.
    """
    conn = get_db()
    cur = conn.cursor()
    cur.execute(
        """
        SELECT id, vehicle_id, lat, lon, speed, fuel, timestamp
        FROM locations
        WHERE vehicle_id = ?
        ORDER BY id ASC
        LIMIT ?
        """,
        (vehicle_id, limit),
    )
    rows = cur.fetchall()
    conn.close()
    return [dict(r) for r in rows]


@app.get("/api/locations/by-date")
def get_locations_by_date(date: str, vehicle_id: str = "ANDROID01"):
    """
    Get all points for a given vehicle on a specific day.
    date format: YYYY-MM-DD
    """
    start = f"{date}T00:00:00"
    end = f"{date}T23:59:59"

    conn = get_db()
    cur = conn.cursor()
    cur.execute(
        """
        SELECT id, vehicle_id, lat, lon, speed, fuel, timestamp
        FROM locations
        WHERE vehicle_id = ?
          AND timestamp BETWEEN ? AND ?
        ORDER BY id ASC
        """,
        (vehicle_id, start, end),
    )
    rows = cur.fetchall()
    conn.close()
    return [dict(r) for r in rows]


@app.get("/api/vehicles")
def get_vehicles():
    """
    Return a list of distinct vehicle IDs that have sent data.
    Used to populate the dropdown in map.html.
    """
    conn = get_db()
    cur = conn.cursor()
    cur.execute("SELECT DISTINCT vehicle_id FROM locations ORDER BY vehicle_id")
    rows = cur.fetchall()
    conn.close()
    return [r["vehicle_id"] for r in rows]


# ---------- Map page ----------

@app.get("/map")
def map_page():
    # make sure the file in the repo is really named "map.html"
    return FileResponse(BASE_DIR / "map.html")
