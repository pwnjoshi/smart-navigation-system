package com.smartnavigation.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@CrossOrigin(origins = "*")
public class RouteController {

    private final Map<String, List<Edge>> graph = new HashMap<>();
    private final List<Node> nodes = new ArrayList<>();
    private final Map<String, Node> nodeByName = new HashMap<>();
    private final Resource graphDataResource;
    private final JsonParser jsonParser = JsonParserFactory.getJsonParser();

    public RouteController(@Value("classpath:graph-data.json") Resource graphDataResource) {
        this.graphDataResource = graphDataResource;
        loadGraphFromJson();
    }

    static class Edge {
        String from, to;
        double weight;
        String type;
        boolean isStairs;

        Edge(String from, String to, double weight, String type, boolean isStairs) {
            this.from = from;
            this.to = to;
            this.weight = weight;
            this.type = type;
            this.isStairs = isStairs;
        }
    }

    static class Node {
        String name, category;
        double x, y, lat, lng;
        int floor;
        boolean indoor;
        String building;
        String label;

        Node(String name, String category, double x, double y, double lat, double lng,
             int floor, boolean indoor, String building, String label) {
            this.name = name;
            this.category = category;
            this.x = x;
            this.y = y;
            this.lat = lat;
            this.lng = lng;
            this.floor = floor;
            this.indoor = indoor;
            this.building = building;
            this.label = label;
        }
    }

    void addEdge(String u, String v, double w, String type, boolean isStairs) {
        graph.putIfAbsent(u, new ArrayList<>());
        graph.putIfAbsent(v, new ArrayList<>());
        graph.get(u).add(new Edge(u, v, w, type, isStairs));
        graph.get(v).add(new Edge(v, u, w, type, isStairs));
    }

    double haversineMeters(Node a, Node b) {
        if (a.lat == 0 || b.lat == 0) return euclidean(a, b) * 1000;
        double R = 6371000;
        double dLat = Math.toRadians(b.lat - a.lat);
        double dLng = Math.toRadians(b.lng - a.lng);
        double sinLat = Math.sin(dLat / 2);
        double sinLng = Math.sin(dLng / 2);
        double h = sinLat * sinLat + Math.cos(Math.toRadians(a.lat)) *
                   Math.cos(Math.toRadians(b.lat)) * sinLng * sinLng;
        return 2 * R * Math.asin(Math.sqrt(h));
    }

    double euclidean(Node a, Node b) {
        return Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2));
    }

    private void loadGraphFromJson() {
        nodes.clear();
        graph.clear();
        nodeByName.clear();

        Map<String, Object> graphData;
        try (InputStream inputStream = graphDataResource.getInputStream()) {
            String raw = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            graphData = jsonParser.parseMap(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load graph-data.json", ex);
        }

        Object rawNodes = graphData.get("nodes");
        if (!(rawNodes instanceof List<?> nodeList) || nodeList.isEmpty()) {
            throw new IllegalStateException("graph-data.json must contain at least one node");
        }

        for (Object rawNode : nodeList) {
            if (!(rawNode instanceof Map<?, ?> nodeMap)) continue;

            String id       = getString(nodeMap.get("id"));
            Double x        = getDouble(nodeMap.get("x"));
            Double y        = getDouble(nodeMap.get("y"));
            if (id == null || id.isBlank() || x == null || y == null) continue;

            String cat      = getString(nodeMap.get("category"));
            if (cat == null || cat.isBlank()) cat = "Path";
            double lat      = getDoubleOrDefault(nodeMap.get("lat"), 0.0);
            double lng      = getDoubleOrDefault(nodeMap.get("lng"), 0.0);
            int floor       = getIntOrDefault(nodeMap.get("floor"), 0);
            boolean indoor  = Boolean.TRUE.equals(nodeMap.get("indoor")) ||
                              "true".equalsIgnoreCase(getString(nodeMap.get("indoor")));
            String building = getString(nodeMap.get("building"));
            String label    = getString(nodeMap.get("label"));
            if (label == null || label.isBlank()) label = id;

            Node n = new Node(id, cat, x, y, lat, lng, floor, indoor, building, label);
            nodes.add(n);
            nodeByName.put(id, n);
        }

        Object rawEdges = graphData.get("edges");
        if (rawEdges instanceof List<?> edgeList && !edgeList.isEmpty()) {
            for (Object rawEdge : edgeList) {
                if (!(rawEdge instanceof Map<?, ?> edgeMap)) continue;
                String from    = getString(edgeMap.get("from"));
                String to      = getString(edgeMap.get("to"));
                if (from == null || to == null) continue;
                Node fNode = nodeByName.get(from);
                Node tNode = nodeByName.get(to);
                if (fNode == null || tNode == null) continue;

                Double weight  = getDouble(edgeMap.get("weight"));
                String etype   = getString(edgeMap.get("type"));
                boolean stairs = Boolean.TRUE.equals(edgeMap.get("isStairs")) ||
                                 "true".equalsIgnoreCase(getString(edgeMap.get("isStairs")));

                double w = weight != null ? weight : haversineMeters(fNode, tNode);
                String t = (etype == null || etype.isBlank()) ? inferType(fNode, tNode) : etype.toUpperCase();
                addEdge(from, to, w, t, stairs);
            }
        } else {
            buildAutoEdges();
        }
    }

    private String inferType(Node a, Node b) {
        if (a.indoor || b.indoor) return "INDOOR";
        return "OUTDOOR";
    }

    private void buildAutoEdges() {
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node a = nodes.get(i), b = nodes.get(j);
                double d = haversineMeters(a, b);
                if (d < 350) addEdge(a.name, b.name, d, inferType(a, b), false);
            }
        }
    }

    private String getString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Double getDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException e) { return null; }
    }

    private double getDoubleOrDefault(Object value, double def) {
        Double d = getDouble(value);
        return d != null ? d : def;
    }

    private int getIntOrDefault(Object value, int def) {
        Double d = getDouble(value);
        return d != null ? d.intValue() : def;
    }

    // ─── API Endpoints ──────────────────────────────────────────────────────────

    @GetMapping("/nodes")
    public List<Map<String, Object>> getNodes() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Node n : nodes) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", n.name);
            item.put("label", n.label);
            item.put("type", n.category.toUpperCase());
            item.put("category", n.category);
            item.put("x", n.x);
            item.put("y", n.y);
            item.put("lat", n.lat);
            item.put("lng", n.lng);
            item.put("floor", n.floor);
            item.put("indoor", n.indoor);
            if (n.building != null) item.put("building", n.building);
            result.add(item);
        }
        return result;
    }

    @GetMapping("/graph")
    public Map<String, Object> getGraph() {
        Map<String, Object> result = new HashMap<>();
        result.put("nodes", getNodes());
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, List<Edge>> entry : graph.entrySet()) {
            for (Edge e : entry.getValue()) {
                String key = e.from.compareTo(e.to) < 0 ? e.from + "||" + e.to : e.to + "||" + e.from;
                if (seen.contains(key)) continue;
                seen.add(key);
                Map<String, Object> em = new HashMap<>();
                em.put("from", e.from);
                em.put("to", e.to);
                em.put("weight", e.weight);
                em.put("type", e.type);
                em.put("isStairs", e.isStairs);
                edges.add(em);
            }
        }
        result.put("edges", edges);
        return result;
    }

    @GetMapping("/route")
    public Map<String, Object> getRoute(
            @RequestParam String source,
            @RequestParam String destination,
            @RequestParam(defaultValue = "MIXED") String mode,
            @RequestParam(defaultValue = "false") boolean avoidStairs) {
        return buildRouteResponse(runDijkstra(source, destination, mode, avoidStairs), mode, avoidStairs, "DIJKSTRA");
    }

    @GetMapping("/route-unweighted")
    public Map<String, Object> getRouteUnweighted(
            @RequestParam String source,
            @RequestParam String destination,
            @RequestParam(defaultValue = "MIXED") String mode,
            @RequestParam(defaultValue = "false") boolean avoidStairs) {
        return buildRouteResponse(runBfs(source, destination, mode, avoidStairs), mode, avoidStairs, "BFS");
    }

    @GetMapping("/navigate")
    public Map<String, Object> navigate(
            @RequestParam String source,
            @RequestParam String destination,
            @RequestParam(defaultValue = "shortest") String type,
            @RequestParam(defaultValue = "MIXED") String mode,
            @RequestParam(defaultValue = "false") boolean avoidStairs) {
        List<String> path;
        String algo;
        if ("hops".equalsIgnoreCase(type)) {
            path = runBfs(source, destination, mode, avoidStairs);
            algo = "BFS";
        } else {
            path = runDijkstra(source, destination, mode, avoidStairs);
            algo = "DIJKSTRA";
        }
        return buildRouteResponse(path, mode, avoidStairs, algo);
    }

    // ─── Route Building ──────────────────────────────────────────────────────────

    private Map<String, Object> buildRouteResponse(List<String> path, String mode, boolean avoidStairs, String algo) {
        if (path == null) return invalid("No route found");

        double totalDist = 0;
        List<Map<String, Object>> pathDetails = new ArrayList<>();
        List<Map<String, Object>> directions  = new ArrayList<>();

        for (int i = 0; i < path.size(); i++) {
            Node n = nodeByName.get(path.get(i));
            if (n == null) continue;

            Map<String, Object> pd = new HashMap<>();
            pd.put("id", n.name);
            pd.put("label", n.label);
            pd.put("lat", n.lat);
            pd.put("lng", n.lng);
            pd.put("x", n.x);
            pd.put("y", n.y);
            pd.put("floor", n.floor);
            pd.put("indoor", n.indoor);
            pd.put("category", n.category);
            if (n.building != null) pd.put("building", n.building);
            pathDetails.add(pd);

            if (i < path.size() - 1) {
                Node next = nodeByName.get(path.get(i + 1));
                if (next == null) continue;

                double segDist = edgeWeight(path.get(i), path.get(i + 1));
                totalDist += segDist;

                // Determine turn direction
                String turn = "straight";
                String turnEmoji = "⬆️";
                if (i > 0) {
                    Node prev = nodeByName.get(path.get(i - 1));
                    if (prev != null) {
                        double bearing1 = bearing(prev.lat, prev.lng, n.lat, n.lng);
                        double bearing2 = bearing(n.lat, n.lng, next.lat, next.lng);
                        double diff = normalizeBearing(bearing2 - bearing1);
                        if (diff < -160 || diff > 160) {
                            turn = "U-turn"; turnEmoji = "↩️";
                        } else if (diff < -45) {
                            turn = "left"; turnEmoji = "⬅️";
                        } else if (diff > 45) {
                            turn = "right"; turnEmoji = "➡️";
                        } else if (diff < -15) {
                            turn = "slight left"; turnEmoji = "↖️";
                        } else if (diff > 15) {
                            turn = "slight right"; turnEmoji = "↗️";
                        }
                    }
                }

                // Detect floor transitions
                boolean isStairs = isStairsEdge(path.get(i), path.get(i + 1));
                String floorInstruction = "";
                if (isStairs) {
                    if (next.floor > n.floor) floorInstruction = " then go up to Floor " + next.floor;
                    else floorInstruction = " then go down to Floor " + next.floor;
                }

                // Build instruction text
                String distText = segDist < 1000
                        ? String.format("%.0f m", segDist)
                        : String.format("%.1f km", segDist / 1000);

                String instruction;
                String stepType;
                if (isStairs) {
                    instruction = (next.floor > n.floor ? "Go up stairs" : "Go down stairs") + floorInstruction;
                    stepType = "stairs";
                } else if (i == 0) {
                    double headBearing = bearing(n.lat, n.lng, next.lat, next.lng);
                    instruction = "Head " + bearingToCompass(headBearing) + " towards " + next.label;
                    stepType = "start";
                } else if (i == path.size() - 2) {
                    instruction = "Arrive at " + next.label;
                    stepType = "arrive";
                } else if ("straight".equals(turn)) {
                    instruction = "Continue straight to " + next.label + " (" + distText + ")";
                    stepType = "straight";
                } else {
                    instruction = "Turn " + turn + " towards " + next.label + " (" + distText + ")";
                    stepType = "turn";
                }

                Map<String, Object> dir = new HashMap<>();
                dir.put("step", i + 1);
                dir.put("from", n.label);
                dir.put("to", next.label);
                dir.put("instruction", instruction);
                dir.put("turn", turn);
                dir.put("emoji", turnEmoji);
                dir.put("distance", segDist);
                dir.put("distanceText", distText);
                dir.put("type", stepType);
                dir.put("floor", n.floor);
                dir.put("isStairs", isStairs);
                dir.put("isIndoor", next.indoor || n.indoor);
                directions.add(dir);
            }
        }

        // Summary
        int estTime = (int) Math.ceil(totalDist / 80.0); // ~80 m/min walking
        String timeText = estTime < 60 ? estTime + " min" : String.format("%d hr %d min", estTime / 60, estTime % 60);

        Map<String, Object> result = new HashMap<>();
        result.put("path", path);
        result.put("pathDetails", pathDetails);
        result.put("directions", directions);
        result.put("distance", totalDist);
        result.put("distanceText", totalDist < 1000
                ? String.format("%.0f m", totalDist)
                : String.format("%.1f km", totalDist / 1000));
        result.put("estimatedTime", timeText);
        result.put("algorithm", algo);
        result.put("mode", mode.toUpperCase());
        result.put("avoidStairs", avoidStairs);
        result.put("hops", Math.max(0, path.size() - 1));
        result.put("hasIndoor", path.stream().anyMatch(p -> {
            Node n = nodeByName.get(p);
            return n != null && n.indoor;
        }));
        return result;
    }

    // Compute bearing between two GPS points (degrees)
    private double bearing(double lat1, double lng1, double lat2, double lng2) {
        if (lat1 == 0 && lat2 == 0) return 0;
        double dLng = Math.toRadians(lng2 - lng1);
        double rlat1 = Math.toRadians(lat1), rlat2 = Math.toRadians(lat2);
        double y = Math.sin(dLng) * Math.cos(rlat2);
        double x = Math.cos(rlat1) * Math.sin(rlat2) - Math.sin(rlat1) * Math.cos(rlat2) * Math.cos(dLng);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private double normalizeBearing(double b) {
        while (b > 180) b -= 360;
        while (b < -180) b += 360;
        return b;
    }

    private String bearingToCompass(double b) {
        String[] dirs = {"North", "NE", "East", "SE", "South", "SW", "West", "NW"};
        return dirs[(int) Math.round(b / 45) % 8];
    }

    private double edgeWeight(String from, String to) {
        for (Edge e : graph.getOrDefault(from, new ArrayList<>())) {
            if (e.to.equals(to)) return e.weight;
        }
        Node a = nodeByName.get(from), b = nodeByName.get(to);
        return (a != null && b != null) ? haversineMeters(a, b) : 0;
    }

    private boolean isStairsEdge(String from, String to) {
        for (Edge e : graph.getOrDefault(from, new ArrayList<>())) {
            if (e.to.equals(to)) return e.isStairs;
        }
        return false;
    }

    private boolean edgeAllowed(Edge e, String mode, boolean avoidStairs) {
        if (mode.equalsIgnoreCase("INDOOR") && "OUTDOOR".equals(e.type)) return false;
        if (mode.equalsIgnoreCase("OUTDOOR") && "INDOOR".equals(e.type)) return false;
        if (avoidStairs && e.isStairs) return false;
        return true;
    }

    private List<String> runDijkstra(String source, String destination, String mode, boolean avoidStairs) {
        if (!graph.containsKey(source) || !graph.containsKey(destination)) return null;
        Map<String, Double> dist = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        for (String n : graph.keySet()) dist.put(n, Double.MAX_VALUE);
        PriorityQueue<double[]> pq = new PriorityQueue<>(Comparator.comparingDouble(p -> p[0]));
        dist.put(source, 0.0);
        pq.add(new double[]{0, source.hashCode()});
        Map<Integer, String> hashToNode = new HashMap<>();
        for (String n : graph.keySet()) hashToNode.put(n.hashCode(), n);
        hashToNode.put(source.hashCode(), source);

        // Use name-indexed map directly
        Map<String, Double> dists2 = new HashMap<>();
        Map<String, String> parents2 = new HashMap<>();
        for (String n : graph.keySet()) dists2.put(n, Double.MAX_VALUE);
        PriorityQueue<Object[]> pq2 = new PriorityQueue<>(Comparator.comparingDouble(p -> (Double) p[0]));
        dists2.put(source, 0.0);
        pq2.add(new Object[]{0.0, source});

        while (!pq2.isEmpty()) {
            Object[] cur = pq2.poll();
            double d = (Double) cur[0];
            String u = (String) cur[1];
            if (d > dists2.getOrDefault(u, Double.MAX_VALUE)) continue;
            for (Edge e : graph.getOrDefault(u, new ArrayList<>())) {
                if (!edgeAllowed(e, mode, avoidStairs)) continue;
                double nd = dists2.get(u) + e.weight;
                if (nd < dists2.getOrDefault(e.to, Double.MAX_VALUE)) {
                    dists2.put(e.to, nd);
                    parents2.put(e.to, u);
                    pq2.add(new Object[]{nd, e.to});
                }
            }
        }

        List<String> path = new ArrayList<>();
        String cur = destination;
        while (cur != null) { path.add(cur); cur = parents2.get(cur); }
        Collections.reverse(path);
        if (path.isEmpty() || !path.get(0).equals(source)) return null;
        return path;
    }

    private List<String> runBfs(String source, String destination, String mode, boolean avoidStairs) {
        if (!graph.containsKey(source) || !graph.containsKey(destination)) return null;
        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        Map<String, String> parent = new HashMap<>();
        queue.add(source);
        visited.add(source);
        while (!queue.isEmpty()) {
            String u = queue.poll();
            if (u.equals(destination)) break;
            for (Edge e : graph.getOrDefault(u, new ArrayList<>())) {
                if (!edgeAllowed(e, mode, avoidStairs) || visited.contains(e.to)) continue;
                visited.add(e.to);
                parent.put(e.to, u);
                queue.add(e.to);
            }
        }
        if (!visited.contains(destination)) return null;
        List<String> path = new ArrayList<>();
        String cur = destination;
        while (cur != null) { path.add(cur); cur = parent.get(cur); }
        Collections.reverse(path);
        return path;
    }

    private Map<String, Object> invalid(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        return error;
    }
}
