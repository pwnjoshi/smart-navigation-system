# smart-navigation-system

## Project Overview
Smart Navigation System is an MVP campus routing platform that helps users find the best route between two locations.

The campus map is modeled as a graph:
- Locations are nodes
- Paths are edges
- A routing algorithm finds the best route

Example route request:
- Input: `A101 -> PARK`
- Output: `A101 -> STAIRS -> EXIT -> GATE -> PARK`

## MVP Goal
Build a working end-to-end navigation flow where users select a source and destination, then receive an optimized route with useful travel context.

## How It Works
1. User selects source and destination in the frontend.
2. Frontend sends route request to backend API.
3. Backend runs routing algorithm on graph data.
4. Backend returns computed path and route metadata.
5. Frontend displays the final route to the user.

## Core Capabilities
- Campus graph modeling with indoor and outdoor nodes
- Weighted shortest-path routing
- Optional unweighted (minimum-hop) routing
- Mode-aware route filtering (indoor/outdoor/mixed)
- Clear route result display for users

## Benefits
- Reduces time spent navigating unfamiliar campus areas
- Improves accessibility with adaptable route preferences
- Supports faster movement between classrooms, offices, and public spaces
- Creates a foundation for future smart-campus features

## Unique Selling Points (USP)
- Hybrid indoor + outdoor navigation in a single flow
- Multi-algorithm routing support (shortest distance and minimum hops)
- Flexible route controls for user-specific needs
- Modular architecture ready for scaling across larger campuses

## Initial Project Structure
```text
smart-navigation-system/
|-- backend/
|-- frontend/
|-- docs/
|-- .gitignore
`-- README.md
```

## Next Development Milestones
- Set up Spring Boot backend service and graph API contracts
- Build a minimal frontend UI for route requests
- Connect frontend and backend through navigation endpoints
- Add test data and validate sample campus routes

## Deploy To Render
This repository includes files needed for Render deployment:
- `render.yaml` (Render Blueprint)
- `backend/Dockerfile` (container build)

### Option 1: Blueprint Deploy (recommended)
1. Push this repository to GitHub.
2. In Render dashboard, click **New** -> **Blueprint**.
3. Select this repository.
4. Render reads `render.yaml` and creates service `smart-navigation-backend`.

### Option 2: Manual Web Service
1. In Render dashboard, click **New** -> **Web Service**.
2. Connect this repository.
3. Runtime: **Docker**.
4. Root/Context: `backend`.
5. Dockerfile path: `backend/Dockerfile`.

### Runtime Notes
- Spring Boot port is configured as `server.port=${PORT:8080}` for Render compatibility.
- Health check endpoint: `/api/hello`.
- App endpoints example:
	- `/index.html`
	- `/api/hello`
	- `/route?source=A101&destination=PARK`
