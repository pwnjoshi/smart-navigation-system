# Smart Campus Navigation System - PV Evaluation Guide

This document is written to help you explain the project clearly to an evaluator. It covers the problem, the idea, the architecture, the important code files, the algorithms used, the API endpoints, and the best way to present the demo.

## 1. One-line introduction

Smart Campus Navigation System is a hybrid indoor-outdoor navigation app for the large institutions environment that finds the best route between different locations and gives turn-by-turn directions, route summaries, and indoor floor guidance.

## 2. Short viva explanation

If the evaluator asks you to explain the project in simple words, you can say:

"This project is a smart navigation system for a college campus. It helps a user find the shortest path from one place to another, whether the path is outside on campus roads or inside a building across multiple floors. The system uses a graph-based model, runs path-finding algorithms, and shows the route on a map with clear directions."

## 3. Why this project was made

The problem is that large campuses are hard to navigate, especially for new students, visitors, and staff. Outdoor routes, building entrances, stairs, and rooms all need different kinds of navigation.

This project solves that by combining:

- outdoor campus navigation,
- indoor building navigation,
- route optimization,
- accessibility support with the avoid-stairs option,
- and a simple visual map interface.

## 4. What the project contains

The project has two main parts:

- a Spring Boot backend that stores the campus graph and calculates routes,
- a Leaflet-based frontend that shows the map, route, and turn-by-turn guidance.

The backend code is in [backend/src/main/java/com/smartnavigation/backend/RouteController.java](../backend/src/main/java/com/smartnavigation/backend/RouteController.java), and the main application starts from [backend/src/main/java/com/smartnavigation/backend/BackendApplication.java](../backend/src/main/java/com/smartnavigation/backend/BackendApplication.java).

## 5. Architecture explanation

You can explain the architecture like this:

1. The frontend sends the source and destination to the backend.
2. The backend loads the campus graph from [backend/src/main/resources/graph-data.json](../backend/src/main/resources/graph-data.json).
3. The backend runs Dijkstra for shortest weighted path or BFS for minimum hops.
4. The backend builds a response containing the path, distance, estimated time, and turn-by-turn instructions.
5. The frontend renders the path on the map and shows the directions in the sidebar.

## 6. Important files to mention

- [README.md](../README.md) - project summary and running instructions.
- [backend/src/main/java/com/smartnavigation/backend/BackendApplication.java](../backend/src/main/java/com/smartnavigation/backend/BackendApplication.java) - Spring Boot entry point.
- [backend/src/main/java/com/smartnavigation/backend/RouteController.java](../backend/src/main/java/com/smartnavigation/backend/RouteController.java) - graph loading, routing logic, and API endpoints.
- [backend/src/main/resources/graph-data.json](../backend/src/main/resources/graph-data.json) - campus nodes and edges.
- [backend/src/main/resources/static/index.html](../backend/src/main/resources/static/index.html) - UI served by the backend.
- [frontend/index.html](../frontend/index.html) - standalone frontend copy.

## 7. Backend logic in simple terms

The backend follows this flow:

### a) Load the campus graph

When the application starts, `RouteController` reads `graph-data.json` and loads:

- nodes: locations like gates, buildings, floors, rooms, and landmarks,
- edges: connections between nodes with distances and type information.

### b) Store the graph in memory

The controller keeps:

- `nodes` - all nodes in a list,
- `nodeByName` - quick lookup by node id,
- `graph` - adjacency list of edges.

### c) Choose a route algorithm

The project supports two path-finding algorithms:

- Dijkstra for shortest distance,
- BFS for minimum number of stops.

### d) Respect routing mode

The user can choose:

- `MIXED` - use both indoor and outdoor,
- `OUTDOOR` - avoid indoor edges,
- `INDOOR` - avoid outdoor edges.

### e) Support accessibility

If `avoidStairs=true`, the backend skips staircase edges.

## 8. Algorithms used

### Dijkstra

Used in [RouteController.java](../backend/src/main/java/com/smartnavigation/backend/RouteController.java) for shortest weighted path.

Why it matters:

- each edge has a weight,
- the algorithm chooses the route with the smallest total distance,
- it is best when you want the actual shortest walking path.

### BFS

Used for minimum hops.

Why it matters:

- it finds the route with the fewest number of edges,
- it is useful when you care about fewer stops instead of physical distance.

### Bearing-based turn directions

The backend also calculates turn instructions by comparing bearings between consecutive GPS points. That is how it decides whether the user should go straight, left, right, slight left, slight right, or U-turn.

## 9. Data model explanation

### Node

Each node represents a place on campus. A node can be a gate, building, floor landing, corridor, lab, room, cafe, or road point.

Important fields:

- `id` - unique name of the place,
- `category` - label such as Building, Landmark, IndoorNode,
- `lat` / `lng` - GPS coordinates,
- `x` / `y` - local map coordinates,
- `floor` - floor number,
- `indoor` - whether the node is inside a building,
- `building` - building name,
- `label` - display name shown to the user.

### Edge

Each edge connects two nodes.

Important fields:

- `from` and `to` - connected nodes,
- `weight` - walking distance or cost,
- `type` - indoor or outdoor,
- `isStairs` - marks staircase paths.

## 10. API endpoints to explain

These are the main endpoints exposed by the backend:

- `GET /nodes` - returns all campus nodes,
- `GET /graph` - returns the full graph with nodes and edges,
- `GET /route` - shortest weighted route using Dijkstra,
- `GET /route-unweighted` - minimum-hop route using BFS,
- `GET /navigate` - main API that selects the algorithm based on query parameters.

### Main example

`/navigate?source=GEU Main Gate&destination=CSIT-2-HODOffice&type=shortest&mode=MIXED`

This returns:

- `path` - ordered list of nodes,
- `pathDetails` - detailed metadata for each node,
- `directions` - turn-by-turn instructions,
- `distanceText` - total route length,
- `estimatedTime` - walking time estimate,
- `hasIndoor` - whether the route enters a building.

## 11. Frontend explanation

The frontend uses Leaflet with OpenStreetMap tiles. It shows:

- a map of the campus,
- source and destination selectors,
- Auto / Outdoor / Indoor mode buttons,
- Shortest / Min Hops selection,
- an avoid-stairs checkbox,
- a route summary card,
- turn-by-turn directions,
- and an indoor floor-plan panel.

If the evaluator asks what makes the UI useful, mention that it is not only drawing a line on a map. It also gives human-readable guidance like:

- head north,
- turn left,
- go up stairs,
- arrive at the room.

## 12. How the route is built

This is a good way to explain the internal flow:

1. The user selects source and destination.
2. The frontend sends them to the backend.
3. The backend checks if the path is allowed in the selected mode.
4. The backend runs Dijkstra or BFS.
5. The backend reconstructs the path using parent links.
6. The backend computes total distance and estimated walking time.
7. The backend creates step-by-step navigation instructions.
8. The frontend shows the result visually.

## 13. What to say about the graph data

The graph is stored in JSON so that it is easy to edit and extend without changing Java code.

You can say:

- outdoor campus locations are stored as graph nodes,
- buildings are connected to internal nodes such as lobbies, stairs, and rooms,
- this makes the project scalable,
- new buildings can be added by editing the JSON file.

## 14. Special features to highlight

These are the features that make the project stronger in a viva:

- hybrid indoor-outdoor routing,
- multi-floor indoor navigation,
- shortest-path and minimum-hop modes,
- accessibility option for stairs avoidance,
- turn-by-turn directions,
- route summary with distance and time,
- map-based visualization.

## 15. Example viva answer for the backend

If asked what the backend does, say:

"The backend is built using Spring Boot. It loads the graph from JSON, stores campus locations as nodes and connections as edges, and provides REST APIs for route planning. It uses Dijkstra for shortest distance and BFS for minimum hops, then converts the final path into user-friendly directions."

## 16. Example viva answer for the frontend

If asked what the frontend does, say:

"The frontend is a map interface built with Leaflet. It allows the user to select a source and destination, choose routing mode, and view the path on the campus map. It also shows step-by-step navigation and an indoor floor plan when the route goes inside a building."

## 17. Example viva answer for the data structure

If asked why graph is used, say:

"A graph is the right data structure because campus locations are connected as points and paths. Each place becomes a node and each walkable connection becomes an edge. This makes route planning efficient and makes shortest-path algorithms easy to apply."

## 18. Example viva answer for Dijkstra vs BFS

If asked the difference, say:

"Dijkstra gives the shortest weighted route, so it is used when distance matters. BFS gives the route with the fewest edges, so it is used when the number of stops matters."

## 19. Demo script you can follow

Use this sequence in the demo:

1. Open the app in the browser.
2. Show the map and point out that it covers GEU campus.
3. Choose a source and destination.
4. Explain the three modes: Auto, Outdoor, Indoor.
5. Show the shortest route first.
6. Show the min-hops option.
7. Toggle avoid stairs to show accessibility support.
8. Open an indoor destination and show the floor panel.
9. Explain the summary values and directions.

## 20. Likely questions and short answers

### What is the main goal?

To provide accurate campus navigation across outdoor and indoor spaces.

### Why Spring Boot?

It is simple to build REST APIs and handle backend logic quickly.

### Why JSON for graph data?

It is easy to maintain, readable, and flexible for future updates.

### How is the route calculated?

By using Dijkstra or BFS on the campus graph.

### How do you handle indoor navigation?

By adding floor-level nodes, stairs, and room nodes inside buildings.

### How do you handle accessibility?

By skipping stair edges when the avoid-stairs option is enabled.

## 21. Future scope

If the evaluator asks what can be improved, mention:

- real-time GPS position tracking,
- Bluetooth beacon support for indoor accuracy,
- better floor-plan rendering,
- elevator-based accessible routing,
- mobile PWA support,
- live updates for crowded or blocked paths.

## 22. Final closing statement

You can close your explanation with:

"This project combines graph algorithms, map visualization, and indoor-outdoor route planning to make campus navigation easier, faster, and more accessible."
