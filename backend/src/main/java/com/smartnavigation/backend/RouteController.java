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
    private final Resource graphDataResource;
    private final JsonParser jsonParser = JsonParserFactory.getJsonParser();

    public RouteController(@Value("classpath:graph-data.json") Resource graphDataResource) {
        this.graphDataResource = graphDataResource;
        loadGraphFromJson();
    }

    // Edge class with type
    static class Edge {
        String from;
        String to;
        double weight;
        String type; // INDOOR / OUTDOOR

        Edge(String from, String to, double weight, String type) {
            this.from = from;
            this.to = to;
            this.weight = weight;
            this.type = type;
        }
    }

    // Node class
    static class Node {
        String name;
        double x, y;
        String category;

        Node(String name, String category, double x, double y) {
            this.name = name;
            this.category = category;
            this.x = x;
            this.y = y;
        }
    }

    void addEdge(String u, String v, double w, String type) {
        graph.putIfAbsent(u, new ArrayList<>());
        graph.putIfAbsent(v, new ArrayList<>());
        graph.get(u).add(new Edge(u, v, w, type));
        graph.get(v).add(new Edge(v, u, w, type));
    }

    double distance(Node a, Node b) {
        return Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2));
    }

    private void loadGraphFromJson() {
        nodes.clear();
        graph.clear();

        Map<String, Object> graphData;
        try (InputStream inputStream = graphDataResource.getInputStream()) {
            String raw = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            graphData = jsonParser.parseMap(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load graph-data.json", ex);
        }

        if (graphData == null) {
            throw new IllegalStateException("graph-data.json must contain at least one node");
        }

        Object rawNodes = graphData.get("nodes");
        if (!(rawNodes instanceof List<?> nodeList) || nodeList.isEmpty()) {
            throw new IllegalStateException("graph-data.json must contain at least one node");
        }

        for (Object rawNode : nodeList) {
            if (!(rawNode instanceof Map<?, ?> nodeMapRaw)) {
                continue;
            }
            Map<?, ?> nodeMap = nodeMapRaw;
            String id = getString(nodeMap.get("id"));
            Double x = getDouble(nodeMap.get("x"));
            Double y = getDouble(nodeMap.get("y"));
            if (id == null || id.isBlank() || x == null || y == null) {
                continue;
            }

            String category = getString(nodeMap.get("category"));
            if (category == null || category.isBlank()) {
                String type = getString(nodeMap.get("type"));
                category = (type == null || type.isBlank()) ? "Path" : type;
            }
            nodes.add(new Node(id, category, x, y));
        }

        if (nodes.isEmpty()) {
            throw new IllegalStateException("graph-data.json produced zero valid nodes");
        }

        Object rawEdges = graphData.get("edges");
        if (rawEdges instanceof List<?> edgeList && !edgeList.isEmpty()) {
            Map<String, Node> nodeByName = new HashMap<>();
            for (Node node : nodes) {
                nodeByName.put(node.name, node);
            }

            for (Object rawEdge : edgeList) {
                if (!(rawEdge instanceof Map<?, ?> edgeMapRaw)) {
                    continue;
                }
                Map<?, ?> edgeMap = edgeMapRaw;
                String from = getString(edgeMap.get("from"));
                String to = getString(edgeMap.get("to"));
                if (from == null || to == null) {
                    continue;
                }

                Node fromNode = nodeByName.get(from);
                Node toNode = nodeByName.get(to);
                if (fromNode == null || toNode == null) {
                    continue;
                }

                Double weight = getDouble(edgeMap.get("weight"));
                String edgeType = getString(edgeMap.get("type"));

                double w = weight != null ? weight : distance(fromNode, toNode);
                String type = (edgeType == null || edgeType.isBlank())
                        ? inferEdgeType(fromNode, toNode)
                        : edgeType.toUpperCase();

                addEdge(from, to, w, type);
            }
            return;
        }

        buildAutoEdges();
    }

    private String inferEdgeType(Node a, Node b) {
        if ("Building".equals(a.category) && "Building".equals(b.category)) {
            return "INDOOR";
        }
        return "OUTDOOR";
    }

    private void buildAutoEdges() {
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node a = nodes.get(i);
                Node b = nodes.get(j);
                double d = distance(a, b);

                if (d < 0.35) {
                    addEdge(a.name, b.name, d, inferEdgeType(a, b));
                }
            }
        }
    }

    private String getString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private Double getDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @GetMapping("/nodes")
    public List<Map<String, Object>> getNodes() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Node n : nodes) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", n.name);
            item.put("type", n.category.toUpperCase());
            item.put("category", n.category);
            item.put("x", n.x);
            item.put("y", n.y);
            item.put("floor", 1);
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
                String a = e.from;
                String b = e.to;
                String key = (a.compareTo(b) < 0) ? a + "||" + b : b + "||" + a;
                if (seen.contains(key)) {
                    continue;
                }
                seen.add(key);

                Map<String, Object> edgeMap = new HashMap<>();
                edgeMap.put("from", a);
                edgeMap.put("to", b);
                edgeMap.put("weight", e.weight);
                edgeMap.put("type", e.type);
                edges.add(edgeMap);
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
            @RequestParam(defaultValue = "false") boolean avoidStairs
    ) {
        return runDijkstra(source, destination, mode, avoidStairs);
    }

    @GetMapping("/route-unweighted")
    public Map<String, Object> getRouteUnweighted(
            @RequestParam String source,
            @RequestParam String destination,
            @RequestParam(defaultValue = "MIXED") String mode,
            @RequestParam(defaultValue = "false") boolean avoidStairs
    ) {
        return runBfs(source, destination, mode, avoidStairs);
    }

    @GetMapping("/navigate")
    public Map<String, Object> navigate(
            @RequestParam String source,
            @RequestParam String destination,
            @RequestParam(defaultValue = "shortest") String type,
            @RequestParam(defaultValue = "MIXED") String mode,
            @RequestParam(defaultValue = "false") boolean avoidStairs
    ) {
        if ("hops".equalsIgnoreCase(type)) {
            return runBfs(source, destination, mode, avoidStairs);
        }
        return runDijkstra(source, destination, mode, avoidStairs);
    }

    private Map<String, Object> invalid(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        return error;
    }

    private boolean edgeAllowed(Edge e, String mode, boolean avoidStairs) {
        if (mode.equalsIgnoreCase("INDOOR") && e.type.equals("OUTDOOR")) {
            return false;
        }
        if (mode.equalsIgnoreCase("OUTDOOR") && e.type.equals("INDOOR")) {
            return false;
        }
        if (avoidStairs) {
            String edgeLabel = (e.from + " " + e.to).toUpperCase();
            if (edgeLabel.contains("STAIR")) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> runDijkstra(String source, String destination, String mode, boolean avoidStairs) {

        if (!graph.containsKey(source) || !graph.containsKey(destination)) {
            return invalid("Invalid source or destination");
        }

        Map<String, Double> dist = new HashMap<>();
        Map<String, String> parent = new HashMap<>();

        for (String node : graph.keySet()) {
            dist.put(node, Double.MAX_VALUE);
        }

        PriorityQueue<Pair> pq = new PriorityQueue<>(
                Comparator.comparingDouble(p -> p.dist)
        );

        dist.put(source, 0.0);
        pq.add(new Pair(source, 0));

        while (!pq.isEmpty()) {
            Pair cur = pq.poll();
            String u = cur.node;

            if (cur.dist > dist.get(u)) {
                continue;
            }

            for (Edge e : graph.getOrDefault(u, new ArrayList<>())) {
                if (!edgeAllowed(e, mode, avoidStairs)) {
                    continue;
                }

                String v = e.to;
                double newDist = dist.get(u) + e.weight;

                if (newDist < dist.get(v)) {
                    dist.put(v, newDist);
                    parent.put(v, u);
                    pq.add(new Pair(v, newDist));
                }
            }
        }

        List<String> path = new ArrayList<>();
        String cur = destination;

        while (cur != null) {
            path.add(cur);
            cur = parent.get(cur);
        }

        Collections.reverse(path);

        if (path.isEmpty() || !path.get(0).equals(source)) {
            return invalid("No route found for current filters");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("path", path);
        result.put("distance", dist.get(destination));
        result.put("algorithm", "DIJKSTRA");
        result.put("mode", mode.toUpperCase());
        result.put("avoidStairs", avoidStairs);

        return result;
    }

    private Map<String, Object> runBfs(String source, String destination, String mode, boolean avoidStairs) {
        if (!graph.containsKey(source) || !graph.containsKey(destination)) {
            return invalid("Invalid source or destination");
        }

        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        Map<String, String> parent = new HashMap<>();

        queue.add(source);
        visited.add(source);

        while (!queue.isEmpty()) {
            String u = queue.poll();
            if (u.equals(destination)) {
                break;
            }

            for (Edge e : graph.getOrDefault(u, new ArrayList<>())) {
                if (!edgeAllowed(e, mode, avoidStairs)) {
                    continue;
                }
                if (!visited.contains(e.to)) {
                    visited.add(e.to);
                    parent.put(e.to, u);
                    queue.add(e.to);
                }
            }
        }

        if (!visited.contains(destination)) {
            return invalid("No route found for current filters");
        }

        List<String> path = new ArrayList<>();
        String cur = destination;
        while (cur != null) {
            path.add(cur);
            cur = parent.get(cur);
        }
        Collections.reverse(path);

        double distance = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            String from = path.get(i);
            String to = path.get(i + 1);
            for (Edge e : graph.getOrDefault(from, new ArrayList<>())) {
                if (e.to.equals(to)) {
                    distance += e.weight;
                    break;
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("path", path);
        result.put("distance", distance);
        result.put("hops", Math.max(0, path.size() - 1));
        result.put("algorithm", "BFS");
        result.put("mode", mode.toUpperCase());
        result.put("avoidStairs", avoidStairs);

        return result;
    }

    static class Pair {
        String node;
        double dist;

        Pair(String node, double dist) {
            this.node = node;
            this.dist = dist;
        }
    }
}