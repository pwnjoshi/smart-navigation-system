# 🧭 Smart Spatial Navigation System – GEU Campus

A **hybrid indoor + outdoor** campus routing platform with turn-by-turn directions and an interactive Leaflet map.

---

## ✨ New Features (Enhanced)

| Feature | Description |
|---|---|
| 🗺️ **Leaflet Map** | Real OpenStreetMap tiles with campus overlay — zoom, pan, click markers |
| ↩️ **Turn-by-Turn Directions** | Left / Right / Straight / U-turn steps with emoji indicators |
| 🏢 **Indoor Navigation** | Floor-plan canvas for CSIT Building, Library, Mechanical Block |
| 📐 **Multi-floor Support** | Floor tabs — Ground / 1st / 2nd floors with room-level routing |
| 🌐 **Real GPS Coordinates** | All outdoor nodes have `lat`/`lng` for accurate map placement |
| ♿ **Avoid Stairs Toggle** | Skip staircase edges for accessibility |
| 📊 **Route Summary** | Distance (m / km), estimated walk time, number of stops |
| 🔀 **Hybrid Mode** | Auto / Outdoor-only / Indoor-only routing |

---

## 🏗️ Architecture

```
smart-navigation-system/
├── backend/                          # Spring Boot (Java 17)
│   └── src/main/
│       ├── java/com/smartnavigation/backend/
│       │   ├── BackendApplication.java
│       │   └── RouteController.java   ← Dijkstra + BFS + turn-by-turn
│       └── resources/
│           ├── application.properties
│           ├── graph-data.json        ← Campus graph (outdoor + indoor)
│           └── static/index.html      ← Leaflet frontend
└── frontend/
    └── index.html                     ← Standalone copy of frontend
```

---

## 🚀 Running the Project

### Prerequisites
- **Java 17+**
- **Maven** (or use the included `mvnw` wrapper)

### Steps

```bash
cd backend
./mvnw spring-boot:run          # Linux/Mac
mvnw.cmd spring-boot:run        # Windows
```

Open your browser: **http://localhost:8080**

---

## 🗺️ API Reference

| Endpoint | Params | Description |
|---|---|---|
| `GET /nodes` | — | All campus nodes (outdoor + indoor) with GPS coords |
| `GET /graph` | — | Full graph with nodes and edges |
| `GET /navigate` | `source`, `destination`, `type` (shortest/hops), `mode` (MIXED/OUTDOOR/INDOOR), `avoidStairs` | **Main routing endpoint** – returns path, directions, turn-by-turn |
| `GET /route` | `source`, `destination`, `mode`, `avoidStairs` | Dijkstra shortest path |
| `GET /route-unweighted` | `source`, `destination`, `mode`, `avoidStairs` | BFS minimum hops |

### Example
```
GET /navigate?source=GEU Main Gate&destination=CSIT-2-HODOffice&type=shortest&mode=MIXED
```

Returns:
```json
{
  "path": ["GEU Main Gate", "GEU CSIT Building", "CSIT-G-Entrance", "CSIT-G-Lobby", ...],
  "directions": [
    { "step": 1, "instruction": "Head North towards GEU Bell Road Junction", "turn": "straight", "emoji": "⬆️", "distanceText": "120 m" },
    { "step": 2, "instruction": "Turn left towards GEU CSIT Building", "turn": "left", "emoji": "⬅️", "distanceText": "60 m" },
    { "step": 5, "instruction": "Go up stairs to Floor 2", "type": "stairs", "emoji": "↩️" },
    ...
  ],
  "distanceText": "245 m",
  "estimatedTime": "3 min",
  "hasIndoor": true
}
```

---

## 🏢 Indoor Buildings (Sample Data)

| Building | Floors | Rooms |
|---|---|---|
| GEU CSIT Building | G, 1st, 2nd | Lab 101/102, Room 201/202/203, AI Lab, HOD Office, Data Center |
| GEU Santoshanand Library | G, 1st | Reception, Issuing Counter, Reading Hall, Digital Section, Silent Zone |
| GEU Mechanical Block | G, 1st | Workshop, Drawing Hall, CAD/CAM Lab, HOD Office |

---

## 📐 Graph Data Format (`graph-data.json`)

### Node
```json
{ "id": "Room ID", "category": "IndoorNode", "lat": 30.275, "lng": 77.998,
  "x": 0.23, "y": 0.55, "floor": 1, "indoor": true,
  "building": "GEU CSIT Building", "label": "Room 201 - AI Lab" }
```

### Edge
```json
{ "from": "CSIT-G-Stairs", "to": "CSIT-1-Landing",
  "weight": 15, "type": "INDOOR", "isStairs": true }
```

---

## 🔮 Future Scope

- 📡 Real-time user location via GPS / BLE beacons
- 🗺️ CAD-quality indoor floor plan uploads (SVG/DXF)
- 🚌 Campus shuttle integration
- 🧑‍🦽 Full accessibility routing with elevator data
- 📱 Progressive Web App (PWA) for mobile offline use
