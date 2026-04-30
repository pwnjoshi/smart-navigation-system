# Smart Campus Navigation System — PV Evaluation Guide

This document is written to help you explain the project clearly to an evaluator. It covers the problem, the idea, the architecture, the important code files, the algorithms used, the API endpoints, and the best way to present the demo.


## 1. One-line introduction

Smart Campus Navigation System is a hybrid indoor-outdoor navigation application for large institutional environments that finds the best route between any two locations and provides turn-by-turn directions, route summaries, and indoor floor-level guidance.


## 2. Short viva explanation

If the evaluator asks you to explain the project in simple words, say:

"This project is a smart navigation system for a large institutional campus. It helps a user find the shortest path from one place to another, whether the path is outside on campus roads or inside a building across multiple floors. The system uses a graph-based model where every location is a node and every walkable connection is an edge. It runs Dijkstra or BFS to find the best route, then generates human-readable turn-by-turn directions and shows the result on an interactive map."


## 3. Why this project was made

The problem: large campuses are hard to navigate, especially for new students, visitors, and staff. Outdoor roads, building entrances, staircases, corridors, and individual rooms all require different kinds of navigation. No single tool handles all of these together.

This project solves that by combining:

- outdoor campus navigation using real OpenStreetMap coordinates,
- indoor building navigation with floor-level room routing,
- two route optimization algorithms (shortest distance and minimum hops),
- accessibility support through the avoid-stairs option,
- a visual map interface with turn-by-turn guidance.


## 4. What the project contains

The project has two main parts:

1. A **Spring Boot backend** that stores the campus graph in memory and calculates routes using graph algorithms.
2. A **Leaflet.js frontend** that renders the interactive map, draws the route, and displays step-by-step directions.

Both are served from a single Spring Boot application. The frontend is a static HTML file served from the backend's classpath.


## 5. Architecture explanation

Explain the architecture in this order:

1. When the application starts, `RouteController` reads `graph-data.json` and builds an in-memory adjacency list graph.
2. The frontend loads in the browser and fetches all nodes from `GET /nodes` to populate the map markers and dropdown selectors.
3. When the user selects a source and destination and clicks "Get Directions", the frontend sends a request to `GET /navigate`.
4. The backend checks the routing mode (MIXED, OUTDOOR, INDOOR) and the algorithm choice (shortest or hops).
5. The backend runs Dijkstra (for shortest weighted path) or BFS (for minimum hops).
6. The backend reconstructs the path using parent pointers, calculates total distance, estimates walking time at 80 m/min, and generates turn-by-turn instructions using GPS bearing calculations.
7. The backend returns a JSON response containing the path, path details, directions, distance, time, and whether the route includes indoor segments.
8. The frontend draws the route as a polyline on the Leaflet map, shows the directions in the sidebar, and opens the indoor floor-plan panel if the route enters a building.

```
User Browser                         Spring Boot Backend
    |                                       |
    |--- GET /nodes ----------------------->|
    |<-- JSON: all 92 nodes ----------------|
    |                                       |
    |--- GET /navigate?source=...&dest=... >|
    |                                       |--- load graph from memory
    |                                       |--- run Dijkstra or BFS
    |                                       |--- build turn-by-turn directions
    |<-- JSON: path + directions + summary -|
    |                                       |
    |--- render on Leaflet map              |
    |--- show directions in sidebar         |
    |--- show indoor floor plan if needed   |
```


## 6. Important files to mention

| File | Purpose |
|---|---|
| `backend/src/main/java/.../RouteController.java` | Core logic: graph loading, Dijkstra, BFS, turn-by-turn generation, all REST endpoints |
| `backend/src/main/java/.../BackendApplication.java` | Spring Boot entry point |
| `backend/src/main/resources/graph-data.json` | Campus graph data: 92 nodes and 216 edges |
| `backend/src/main/resources/static/index.html` | Frontend UI served by the backend |
| `backend/src/main/resources/application.properties` | Server configuration (port 8080) |
| `map.osm` | Raw OpenStreetMap export of the campus, used to generate graph-data.json |
| `render.yaml` | Deployment configuration for Render.com |


## 7. Backend logic in detail

### a) Graph loading — `loadGraphFromJson()`

When the application starts, the constructor of `RouteController` calls `loadGraphFromJson()`. This method:

1. Reads `graph-data.json` from the classpath using Spring's `@Value("classpath:graph-data.json")` resource injection.
2. Parses the JSON into a `Map<String, Object>` using Spring's `JsonParserFactory`.
3. Iterates over the `nodes` array and creates `Node` objects with fields: `name`, `category`, `lat`, `lng`, `x`, `y`, `floor`, `indoor`, `building`, `label`.
4. Stores each node in three structures:
   - `nodes` — an `ArrayList<Node>` for ordered access,
   - `nodeByName` — a `HashMap<String, Node>` for O(1) lookup by ID,
   - `graph` — a `HashMap<String, List<Edge>>` adjacency list.
5. Iterates over the `edges` array and creates bidirectional `Edge` objects. Each edge is added to both `graph.get(from)` and `graph.get(to)` via the `addEdge()` method.
6. If an edge has no explicit weight, the backend calculates it using the Haversine formula on the GPS coordinates of the two nodes.

### b) Data structures in memory

```
graph:        HashMap<String, List<Edge>>    — adjacency list
nodes:        ArrayList<Node>                — all nodes in order
nodeByName:   HashMap<String, Node>          — node lookup by ID
```

The `Node` class holds: `name`, `category`, `x`, `y`, `lat`, `lng`, `floor`, `indoor`, `building`, `label`.

The `Edge` class holds: `from`, `to`, `weight`, `type` (INDOOR/OUTDOOR), `isStairs`.

### c) Dijkstra algorithm — `runDijkstra()`

Implementation details:

1. Initialize a distance map with `Double.MAX_VALUE` for all nodes, set source distance to 0.
2. Use a `PriorityQueue<Object[]>` as a min-heap, ordered by distance.
3. For each node polled from the queue, iterate over its edges.
4. Check `edgeAllowed()` — skip edges that violate the routing mode or the avoid-stairs flag.
5. If `currentDist + edge.weight < knownDist`, update the distance and record the parent.
6. After the loop, reconstruct the path by following parent pointers from destination back to source.
7. Reverse the path and return it. Return `null` if the destination was never reached.

Time complexity: O((V + E) log V) where V is the number of nodes and E is the number of edges.

### d) BFS algorithm — `runBfs()`

Implementation details:

1. Use an `ArrayDeque<String>` as the queue and a `HashSet<String>` for visited tracking.
2. Start from the source node.
3. For each node dequeued, iterate over its edges.
4. Check `edgeAllowed()` — same filtering as Dijkstra.
5. If the neighbor has not been visited, mark it visited, record its parent, and enqueue it.
6. Stop when the destination is reached.
7. Reconstruct the path using parent pointers.

Time complexity: O(V + E).

### e) Edge filtering — `edgeAllowed()`

This method enforces three rules:

- If mode is `INDOOR`, reject edges with type `OUTDOOR`.
- If mode is `OUTDOOR`, reject edges with type `INDOOR`.
- If `avoidStairs` is true, reject edges where `isStairs` is true.

This is how the system supports accessibility and mode-specific routing without changing the algorithm.

### f) Turn-by-turn generation — `buildRouteResponse()`

After the path is found, the backend generates navigation instructions:

1. For each consecutive pair of nodes in the path, it calculates the segment distance using `edgeWeight()`.
2. It determines the turn direction by comparing GPS bearings:
   - Compute bearing from previous node to current node (bearing1).
   - Compute bearing from current node to next node (bearing2).
   - The difference `bearing2 - bearing1` tells the turn angle.
   - Ranges: straight (-15 to +15), slight left/right (15-45), left/right (45-160), U-turn (160+).
3. It detects floor transitions by checking the `isStairs` flag on edges.
4. It generates human-readable instructions like "Head North towards...", "Turn left towards...", "Go up stairs then go up to Floor 2", "Arrive at...".
5. It calculates estimated walking time at 80 meters per minute.

### g) Haversine formula — `haversineMeters()`

Used to calculate real-world distance in meters between two GPS coordinates. The formula accounts for the curvature of the Earth using:

```
a = sin²(dLat/2) + cos(lat1) * cos(lat2) * sin²(dLng/2)
distance = 2 * R * arcsin(sqrt(a))
```

Where R = 6,371,000 meters (Earth's radius).

### h) Bearing calculation — `bearing()`

Computes the compass direction from one GPS point to another:

```
y = sin(dLng) * cos(lat2)
x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
bearing = atan2(y, x) converted to degrees
```

The result is normalized to 0-360 degrees and mapped to compass directions (North, NE, East, SE, South, SW, West, NW).


## 8. Algorithms — what to say

### Dijkstra

"Dijkstra's algorithm finds the shortest weighted path in a graph. It uses a priority queue (min-heap) to always process the node with the smallest known distance first. Each edge has a weight representing walking distance in meters. The algorithm guarantees the optimal shortest path. Time complexity is O((V + E) log V)."

### BFS

"Breadth-First Search finds the path with the fewest edges (minimum hops). It uses a FIFO queue and visits nodes level by level. It does not consider edge weights, so it is useful when the user wants the route with the fewest stops rather than the shortest physical distance. Time complexity is O(V + E)."

### When to use which

"Dijkstra is used when the user selects 'Shortest' — it minimizes total walking distance. BFS is used when the user selects 'Min Hops' — it minimizes the number of intermediate stops. Both algorithms respect the routing mode and the avoid-stairs flag."


## 9. Data model explanation

### Node

Each node represents a physical location on campus. There are 92 nodes total.

**Outdoor nodes (58):**
- 18 Buildings (CSIT, Civil, Mechanical, Chanakya, KP Nautiyal, Petroleum, Convention Centre, etc.)
- 3 Landmarks (Main Gate, Gate 2, Bus Parking)
- 2 Cafes (Cafe and Gym, Mess)
- 2 Grounds (Basketball Court, Indoor Badminton Court)
- 2 Medical (Dispensary, St Paul Hospital)
- 2 External (St Paul High School, Global School)
- 29 Path/Junction nodes (road intersections derived from OSM data)

**Indoor nodes (34):**
- GEU CSIT Building: 16 nodes across 3 floors (Ground, 1st, 2nd)
- GEU Santoshanand Library: 10 nodes across 2 floors (Ground, 1st)
- GEU Mechanical Block: 8 nodes across 2 floors (Ground, 1st)

Node fields:

| Field | Type | Description |
|---|---|---|
| `id` | String | Unique identifier, e.g. "GEU Main Gate" or "CSIT-1-Room201" |
| `category` | String | Building, Landmark, Cafe, Medical, Ground, External, Path, IndoorNode |
| `lat` / `lng` | Double | GPS coordinates from OpenStreetMap |
| `x` / `y` | Double | Normalized 0-1 coordinates for map positioning |
| `floor` | Integer | Floor number (0 = ground) |
| `indoor` | Boolean | True for indoor nodes |
| `building` | String | Parent building name (indoor nodes only) |
| `label` | String | Human-readable display name |

### Edge

Each edge represents a walkable connection. There are 216 edges total.

- 181 outdoor edges with haversine-calculated distances in meters.
- 35 indoor edges with manually assigned weights.

Edge fields:

| Field | Type | Description |
|---|---|---|
| `from` / `to` | String | Connected node IDs |
| `weight` | Double | Walking distance in meters |
| `type` | String | INDOOR or OUTDOOR |
| `isStairs` | Boolean | True if this edge represents a staircase |


## 10. API endpoints

### GET /nodes

Returns all 92 campus nodes with their coordinates and metadata. Used by the frontend to populate the map and dropdowns.

### GET /graph

Returns the full graph (nodes + edges). Useful for debugging or visualization.

### GET /navigate (main endpoint)

Parameters:

| Parameter | Required | Default | Values |
|---|---|---|---|
| `source` | Yes | — | Any node ID |
| `destination` | Yes | — | Any node ID |
| `type` | No | `shortest` | `shortest` (Dijkstra) or `hops` (BFS) |
| `mode` | No | `MIXED` | `MIXED`, `OUTDOOR`, `INDOOR` |
| `avoidStairs` | No | `false` | `true` or `false` |

Example request:
```
GET /navigate?source=GEU Main Gate&destination=CSIT-2-HODOffice&type=shortest&mode=MIXED&avoidStairs=false
```

Response fields:

| Field | Description |
|---|---|
| `path` | Ordered list of node IDs from source to destination |
| `pathDetails` | Array of objects with lat, lng, floor, indoor, category for each node |
| `directions` | Array of turn-by-turn instruction objects |
| `distance` | Total distance in meters (number) |
| `distanceText` | Formatted distance string, e.g. "245 m" or "1.2 km" |
| `estimatedTime` | Walking time estimate, e.g. "3 min" |
| `algorithm` | "DIJKSTRA" or "BFS" |
| `mode` | The routing mode used |
| `avoidStairs` | Whether stairs were avoided |
| `hops` | Number of edges in the path |
| `hasIndoor` | Whether the route passes through indoor nodes |

Each direction object contains:

| Field | Description |
|---|---|
| `step` | Step number |
| `from` / `to` | Labels of the two nodes |
| `instruction` | Human-readable text like "Turn left towards..." |
| `turn` | straight, left, right, slight left, slight right, U-turn |
| `distance` | Segment distance in meters |
| `distanceText` | Formatted segment distance |
| `type` | start, straight, turn, stairs, arrive |
| `isStairs` | Whether this step involves stairs |
| `isIndoor` | Whether this step is indoors |

### GET /route

Dijkstra-only endpoint. Same parameters as `/navigate` except no `type` parameter.

### GET /route-unweighted

BFS-only endpoint. Same parameters as `/navigate` except no `type` parameter.


## 11. Frontend explanation

The frontend is a single HTML file with embedded CSS and JavaScript. It uses:

- **Leaflet.js** for the interactive map with OpenStreetMap tiles.
- **Vanilla JavaScript** for all logic (no frameworks).

What it does:

1. On load, fetches all nodes from `GET /nodes`.
2. Places colored dot markers on the map for each outdoor node.
3. Populates source and destination dropdowns, grouped by category (outdoor) and building (indoor).
4. When the user clicks a marker, a popup appears with "From here" and "To here" buttons.
5. When the user clicks "Get Directions", it sends a request to `GET /navigate`.
6. It draws the route as a polyline (blue for outdoor, orange dashed for indoor).
7. It shows a summary card with distance, walk time, and number of stops.
8. It renders numbered step-by-step directions in the sidebar.
9. If the route enters a building, it opens an indoor floor-plan panel at the bottom of the map.
10. The floor-plan panel uses an HTML Canvas to draw room boxes, path lines, and step numbers.

If the evaluator asks what makes the UI useful, say: "It does not just draw a line on a map. It gives human-readable guidance like 'Head North towards...', 'Turn left towards...', 'Go up stairs to Floor 2', and 'Arrive at Room 201'. It also shows an indoor floor plan with numbered steps when the route goes inside a building."


## 12. How the route is built — step by step

This is the internal flow from user click to displayed result:

1. User selects source (e.g. "GEU Main Gate") and destination (e.g. "CSIT-2-HODOffice").
2. User selects mode (Auto) and algorithm (Shortest).
3. Frontend sends: `GET /navigate?source=GEU Main Gate&destination=CSIT-2-HODOffice&type=shortest&mode=MIXED`.
4. Backend calls `runDijkstra("GEU Main Gate", "CSIT-2-HODOffice", "MIXED", false)`.
5. Dijkstra explores the graph, respecting edge types and stair flags.
6. Dijkstra finds the optimal path, e.g.: Main Gate → Junction → CSIT Building → CSIT-G-Entrance → Lobby → Stairs → 1st Floor → Stairs → 2nd Floor → Corridor → HOD Office.
7. Backend calls `buildRouteResponse()` which:
   - calculates total distance by summing edge weights,
   - estimates walking time at 80 m/min,
   - generates turn instructions using bearing comparisons,
   - detects indoor/outdoor transitions and floor changes.
8. Backend returns JSON with path, directions, and summary.
9. Frontend draws the polyline on the map.
10. Frontend shows the direction steps in the sidebar.
11. Frontend detects `hasIndoor: true` and opens the indoor floor-plan panel for CSIT Building.


## 13. What to say about the graph data

"The graph is stored in a JSON file so that it can be edited and extended without changing any Java code. Outdoor campus locations are stored as graph nodes with real GPS coordinates extracted from OpenStreetMap. Buildings are connected to internal nodes like lobbies, stairs, corridors, and rooms. This makes the system scalable — new buildings or locations can be added by editing the JSON file. The outdoor edge weights are calculated using the Haversine formula on real GPS coordinates, so the distances are accurate."


## 14. What to say about the OSM data

"The file `map.osm` is a raw OpenStreetMap export of the GEU campus area. It contains real-world geographic data including building outlines, road networks, gates, and amenities. We parsed this file to extract building centroids, road intersections, and named features, then used those coordinates to build the graph-data.json file. This means all the GPS coordinates in the system are real, not made up."


## 15. Special features to highlight

These are the features that make the project stronger in a viva:

1. **Hybrid routing** — seamlessly transitions between outdoor paths and indoor corridors in a single route.
2. **Real GPS coordinates** — extracted from OpenStreetMap, not hardcoded approximations.
3. **Two algorithms** — Dijkstra for shortest distance, BFS for minimum hops. User can switch between them.
4. **Turn-by-turn directions** — uses GPS bearing math to determine left/right/straight/U-turn at each step.
5. **Multi-floor indoor navigation** — floor tabs, staircase detection, room-level routing across 3 buildings.
6. **Accessibility** — avoid-stairs toggle that filters staircase edges from the graph traversal.
7. **Three routing modes** — Auto (mixed), Outdoor-only, Indoor-only.
8. **Route summary** — distance, estimated walking time, number of stops.
9. **Indoor floor-plan rendering** — Canvas-based room layout with path visualization and step numbers.
10. **Scalable data model** — JSON-based graph that can be extended without code changes.


## 16. Example viva answers

### "What does the backend do?"

"The backend is built with Spring Boot. On startup, it loads the campus graph from a JSON file into an in-memory adjacency list. It exposes REST APIs for route planning. When a request comes in, it runs Dijkstra for shortest distance or BFS for minimum hops, respects the routing mode and accessibility flags, then builds a response with the path, total distance, estimated walking time, and step-by-step turn directions using GPS bearing calculations."

### "What does the frontend do?"

"The frontend is a single-page application built with Leaflet.js and vanilla JavaScript. It shows an interactive OpenStreetMap of the campus with clickable markers for each location. The user selects source and destination, chooses a routing mode and algorithm, and gets the route drawn on the map with numbered turn-by-turn directions in the sidebar. If the route goes inside a building, it also shows an indoor floor plan with room-level path visualization."

### "Why did you use a graph?"

"A graph is the natural data structure for navigation because campus locations are points (nodes) connected by walkable paths (edges). Each edge has a weight representing walking distance. This structure lets us directly apply well-known shortest-path algorithms like Dijkstra and BFS. It also cleanly separates indoor and outdoor navigation by tagging edges with types."

### "What is the difference between Dijkstra and BFS?"

"Dijkstra considers edge weights and finds the path with the minimum total distance. It uses a priority queue to always expand the closest unvisited node. BFS ignores weights and finds the path with the fewest edges. It uses a FIFO queue and expands level by level. Dijkstra is better when you want the shortest walk. BFS is better when you want the fewest stops."

### "How do you handle indoor navigation?"

"Indoor navigation works by adding floor-level nodes inside buildings. Each building has an entrance node connected to the outdoor graph. Inside, there are lobby, corridor, staircase, and room nodes connected by indoor edges. Staircase edges connect nodes on different floors and are marked with `isStairs: true`. The same Dijkstra or BFS algorithm works on both indoor and outdoor nodes because they are all part of the same graph."

### "How do you handle accessibility?"

"When the user enables 'Avoid stairs', the `edgeAllowed()` method rejects any edge where `isStairs` is true. This forces the algorithm to find an alternative path that does not use staircases. The filtering happens at the algorithm level, so it works with both Dijkstra and BFS."

### "How are the turn directions generated?"

"After finding the path, the backend looks at three consecutive nodes: previous, current, and next. It calculates the GPS bearing from previous to current, and from current to next. The difference between these two bearings gives the turn angle. Based on the angle range, it classifies the turn as straight, slight left, slight right, left, right, or U-turn. For the first step, it uses the absolute bearing to give a compass direction like 'Head North'."

### "Why JSON for graph data?"

"JSON is human-readable, easy to edit, and does not require a database. New buildings, rooms, or paths can be added by editing the file. The backend parses it once at startup and keeps the graph in memory, so there is no runtime I/O cost. This makes the system easy to maintain and extend."

### "How accurate are the distances?"

"The outdoor distances are calculated using the Haversine formula on real GPS coordinates extracted from OpenStreetMap. The Haversine formula accounts for the curvature of the Earth and gives accurate results for short distances. Indoor distances are manually estimated based on typical building dimensions."

### "What is the time complexity?"

"Dijkstra runs in O((V + E) log V) where V is 92 nodes and E is 216 edges. BFS runs in O(V + E). Both are very fast for a graph of this size — the response is essentially instant."

### "Can this scale to a larger campus?"

"Yes. The graph is loaded from JSON, so adding more buildings, roads, or rooms only requires editing the data file. The algorithms scale well — Dijkstra and BFS can handle thousands of nodes efficiently. For very large campuses, the JSON could be replaced with a database, but the algorithm and API design would remain the same."


## 17. Demo script

Follow this sequence during the demo:

1. Open `http://localhost:8080` in the browser.
2. Show the map — point out that it uses real OpenStreetMap tiles of the GEU campus area.
3. Click on a few markers to show the popup with location name and category.
4. Select "GEU Main Gate" as source and "GEU CSIT Building" as destination.
5. Click "Get Directions" — show the outdoor route on the map and the directions in the sidebar.
6. Point out the route summary: distance, walk time, stops.
7. Now change the destination to "CSIT-2-HODOffice" (an indoor room on the 2nd floor).
8. Click "Get Directions" — show how the route goes from outdoor to indoor.
9. Point out the indoor floor-plan panel that appears at the bottom.
10. Click through the floor tabs (Ground, Floor 1, Floor 2) to show room-level navigation.
11. Switch the algorithm to "Min Hops" and show how the route changes.
12. Enable "Avoid stairs" and show how the route adapts (or fails if no stair-free path exists).
13. Switch to "Outdoor" mode and show that indoor edges are excluded.
14. Summarize: "This system combines graph algorithms, real map data, and indoor floor plans to provide complete campus navigation."


## 18. Likely questions and short answers

| Question | Answer |
|---|---|
| What is the main goal? | Accurate campus navigation across outdoor and indoor spaces. |
| Why Spring Boot? | Simple to build REST APIs, handles JSON parsing, serves static files, production-ready. |
| Why JSON for graph data? | Easy to maintain, readable, no database needed, editable without code changes. |
| How is the route calculated? | Dijkstra for shortest distance, BFS for minimum hops, on an adjacency list graph. |
| How do you handle indoor navigation? | Floor-level nodes (lobby, stairs, rooms) connected by indoor edges within buildings. |
| How do you handle accessibility? | The edgeAllowed() method skips staircase edges when avoidStairs is true. |
| How are directions generated? | GPS bearing comparison between consecutive node triplets to determine turn angles. |
| What is the Haversine formula? | Calculates real-world distance between two GPS points accounting for Earth's curvature. |
| How many nodes and edges? | 92 nodes (58 outdoor, 34 indoor) and 216 edges (181 outdoor, 35 indoor). |
| Which buildings have indoor data? | CSIT Building (3 floors), Library (2 floors), Mechanical Block (2 floors). |
| What is the time complexity? | Dijkstra: O((V+E) log V). BFS: O(V+E). Both instant for 92 nodes. |
| Can it scale? | Yes. Add nodes/edges to JSON. Algorithms handle thousands of nodes efficiently. |
| Where does the map data come from? | OpenStreetMap export (map.osm) parsed to extract real GPS coordinates. |
| What frontend framework? | No framework. Vanilla HTML/CSS/JS with Leaflet.js for the map. |
| How is the frontend served? | As a static file from Spring Boot's classpath (resources/static/index.html). |


## 19. Future scope

If the evaluator asks what can be improved:

- Real-time GPS position tracking using the browser's Geolocation API.
- Bluetooth Low Energy (BLE) beacon support for precise indoor positioning.
- Higher-fidelity floor plans using SVG or CAD imports instead of Canvas rendering.
- Elevator-based accessible routing as an alternative to stairs.
- Progressive Web App (PWA) support for offline mobile use.
- Live path updates for blocked or crowded routes.
- Multi-destination routing (visit multiple places in optimal order).
- Integration with campus shuttle schedules for combined walking + transit routes.


## 20. Closing statement

You can close your explanation with:

"This project combines graph algorithms, real OpenStreetMap data, and indoor floor-plan visualization to make campus navigation easier, faster, and more accessible. It demonstrates practical application of data structures, shortest-path algorithms, GPS mathematics, and full-stack web development."
