from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, RedirectResponse
from pydantic import BaseModel
import sqlite3
from datetime import datetime
from pathlib import Path

# ---------- Paths & DB ----------

BASE_DIR = Path(__file__).resolve().parent
DB_PATH = BASE_DIR / "tracking.db"   # absolute path, safer on Render


def init_db():
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    cur.execute("""
        CREATE TABLE IF NOT EXISTS locations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            vehicle_id TEXT,
            lat REAL,
            lon REAL,
            speed REAL,
            fuel REAL,
            timestamp TEXT
        )
    """)
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
        (loc.vehicle_id, loc.lat, loc.lon, loc.speed, loc.fuel, datetime.now().isoformat())
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
def get_all_locations():
    conn = get_db()
    cur = conn.cursor()
    cur.execute("SELECT * FROM locations ORDER BY id DESC LIMIT 50")
    rows = cur.fetchall()
    conn.close()
    return [dict(r) for r in rows]


# ---------- Map page ----------

@app.get("/map")
def map_page():
    # make sure the file in the repo is really named "map.html"
    return FileResponse(BASE_DIR / "map.html")
