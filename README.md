# Smart Campus Navigation System – GEU

A hybrid indoor + outdoor campus routing platform for Graphic Era (Deemed to be) University, Dehradun. Built with Spring Boot and Leaflet.js, using real OpenStreetMap data for accurate campus mapping.

## Features

- **Real OSM Map Data** — Node coordinates extracted from `map.osm` (OpenStreetMap export of GEU campus)
- **Leaflet Map** — Interactive map with OpenStreetMap tiles, clickable markers, route polylines
- **Dijkstra + BFS Routing** — Shortest path (weighted) and minimum hops (unweighted) algorithms
- **Turn-by-Turn Directions** — Step-by-step navigation with left/right/straight/U-turn detection using GPS bearings
- **Indoor Navigation** — Floor-plan canvas rendering for CSIT Building, Library, and Mechanical Block with room-level routing
- **Multi-Floor Support** — Ground, 1st, and 2nd floor tabs with staircase edge traversal
- **Accessibility** — Avoid-stairs toggle to skip staircase edges
- **Three Routing Modes** — Auto (mixed), Outdoor-only, Indoor-only
- **Route Summary** — Distance, estimated walk time, number of stops

## Project Structure

```
smart-navigation-system/
├── backend/                              Spring Boot application (Java 17)
│   └── src/main/
│       ├── java/.../backend/
│       │   ├── BackendApplication.java   Entry point
│       │   └── RouteController.java      Graph loading, Dijkstra, BFS, turn-by-turn
│       └── resources/
│           ├── application.properties
│           ├── graph-data.json           Campus graph (92 nodes, 216 edges)
│           └── static/index.html         Frontend (served by Spring Boot)
├── docs/
│   └── PV_EVALUATION_GUIDE.md
├── map.osm                              OpenStreetMap data for GEU campus
├── render.yaml                          Render.com deployment config
└── .gitignore
```

## Prerequisites

- Java 17 or higher
- Maven (or use the included `mvnw` wrapper)

## Running Locally

```bash
cd backend
./mvnw spring-boot:run          # Linux / macOS
mvnw.cmd spring-boot:run        # Windows
```

Open **http://localhost:8080** in your browser.

## API Endpoints

| Endpoint | Method | Parameters | Description |
|---|---|---|---|
| `/nodes` | GET | — | All campus nodes with GPS coordinates |
| `/graph` | GET | — | Full graph (nodes + edges) |
| `/navigate` | GET | `source`, `destination`, `type`, `mode`, `avoidStairs` | Main routing endpoint with turn-by-turn directions |
| `/route` | GET | `source`, `destination`, `mode`, `avoidStairs` | Dijkstra shortest path |
| `/route-unweighted` | GET | `source`, `destination`, `mode`, `avoidStairs` | BFS minimum hops |

**Parameters:**
- `source` / `destination` — Node IDs (e.g. `GEU Main Gate`, `CSIT-2-HODOffice`)
- `type` — `shortest` (Dijkstra) or `hops` (BFS)
- `mode` — `MIXED`, `OUTDOOR`, or `INDOOR`
- `avoidStairs` — `true` or `false`

**Example:**
```
GET /navigate?source=GEU Main Gate&destination=CSIT-2-HODOffice&type=shortest&mode=MIXED&avoidStairs=false
```

## Campus Graph

The graph data in `graph-data.json` is generated from `map.osm` using real coordinates:

**Outdoor Nodes (58):** Buildings, landmarks, gates, cafes, grounds, medical facilities, road junctions

**Indoor Nodes (34):** Room-level nodes across 3 buildings (CSIT, Library, Mechanical Block) with floor information

**Edges (216):** Outdoor walking paths with haversine-calculated distances (meters), indoor corridors with staircase annotations

### Indoor Buildings

| Building | Floors | Sample Rooms |
|---|---|---|
| GEU CSIT Building | G, 1, 2 | Computer Lab, Network Lab, AI Lab, Seminar Hall, Data Center, HOD Office |
| GEU Santoshanand Library | G, 1 | Reception, Issuing Counter, Reading Hall, Digital Section, Silent Zone |
| GEU Mechanical Block | G, 1 | Workshop, Drawing Hall, CAD/CAM Lab, HOD Office |

## Graph Data Format

**Node:**
```json
{
  "id": "GEU CSIT Building",
  "category": "Building",
  "lat": 30.2689524,
  "lng": 77.9931805,
  "x": 0.1339,
  "y": 0.3555,
  "floor": 0
}
```

**Indoor Node:**
```json
{
  "id": "CSIT-1-Room201",
  "category": "IndoorNode",
  "building": "GEU CSIT Building",
  "lat": 30.2689524,
  "lng": 77.9931805,
  "x": 0.1339,
  "y": 0.3555,
  "floor": 1,
  "indoor": true,
  "label": "Room 201 - AI Lab"
}
```

**Edge:**
```json
{
  "from": "CSIT-G-Stairs",
  "to": "CSIT-1-Landing",
  "weight": 15,
  "type": "INDOOR",
  "isStairs": true
}
```

## Deployment

The project includes a `render.yaml` for one-click deployment to [Render](https://render.com):

```bash
# Build
cd backend && ./mvnw clean package -DskipTests

# Run
java -jar backend/target/navigation-0.0.1-SNAPSHOT.jar
```

## Tech Stack

- **Backend:** Spring Boot 4.0, Java 17
- **Frontend:** Vanilla HTML/CSS/JS, Leaflet.js
- **Map Data:** OpenStreetMap
- **Algorithms:** Dijkstra (weighted shortest path), BFS (minimum hops)
- **Deployment:** Render.com
