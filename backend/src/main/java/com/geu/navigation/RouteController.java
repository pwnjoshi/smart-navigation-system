package com.geu.navigation;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/route")
public class RouteController {

    Map<String, List<Edge>> graph = new HashMap<>();

    // Edge class with type
    static class Edge {
        String to;
        double weight;
        String type; // INDOOR / OUTDOOR

        Edge(String to, double weight, String type) {
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

    List<Node> nodes = new ArrayList<>();

    public RouteController() {
        buildGraph();
    }

    void addEdge(String u, String v, double w, String type) {
        graph.putIfAbsent(u, new ArrayList<>());
        graph.putIfAbsent(v, new ArrayList<>());
        graph.get(u).add(new Edge(v, w, type));
        graph.get(v).add(new Edge(u, w, type));
    }

    double distance(Node a, Node b) {
        return Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2));
    }

    void buildGraph() {

        // 🔹 ADD DATA (with category)
        nodes.add(new Node("GEU Santoshanand Library", "Building", 0.35, 0.35));
        nodes.add(new Node("GEU CSIT Building", "Building", 0.10, 0.05));
        nodes.add(new Node("GEU Civil Block", "Building", 0.30, 0.40));
        nodes.add(new Node("GEU Quick Bite", "Cafe", 0.40, 0.45));
        nodes.add(new Node("GEU Mechanical Block", "Building", 0.55, 0.60));
        nodes.add(new Node("GEU Convention Centre", "Building", 0.50, 0.55));
        nodes.add(new Node("GEU Chanakya Block", "Building", 0.70, 0.50));
        nodes.add(new Node("GEU KP Nautiyal Block", "Building", 0.75, 0.45));
        nodes.add(new Node("GEU Petroleum Block", "Building", 0.78, 0.40));
        nodes.add(new Node("GEU Param Computer Centre", "Building", 0.60, 0.37));
        nodes.add(new Node("GEU Aryabhatta Computer Center", "Building", 0.55, 0.35));
        nodes.add(new Node("GEU Admission Office", "Building", 0.50, 0.30));
        nodes.add(new Node("GEU International Office", "Building", 0.52, 0.31));
        nodes.add(new Node("GEU Btech Block", "Building", 0.45, 0.28));
        nodes.add(new Node("GEU CSIT through tunnel", "Path", 0.35, 0.38));

        nodes.add(new Node("GEU Gate 2", "Landmark", 0.90, 0.70));
        nodes.add(new Node("GEU Bus Parking", "Landmark", 0.88, 0.65));

        nodes.add(new Node("GEU Boys Hostel", "Building", 0.35, 0.28));
        nodes.add(new Node("GEU Girls Hostel", "Building", 0.25, 0.40));
        nodes.add(new Node("GEU Priyadarshini Hostel", "Building", 0.65, 0.48));
        nodes.add(new Node("GEU President Estate", "Building", 0.60, 0.42));

        nodes.add(new Node("GEU Biotech Block", "Building", 0.60, 0.55));
        nodes.add(new Node("GEU Paramedical Block", "Building", 0.50, 0.40));

        nodes.add(new Node("GEU Ravi Canteen", "Cafe", 0.48, 0.32));
        nodes.add(new Node("GEU Happiness Cafe", "Cafe", 0.50, 0.315));
        nodes.add(new Node("GEU Cafe and Gym", "Cafe", 0.42, 0.30));

        nodes.add(new Node("GEU Gym", "Building", 0.50, 0.325));

        nodes.add(new Node("GEU Main Ground", "Ground", 0.40, 0.25));
        nodes.add(new Node("GEU Indoor Badminton Court", "Ground", 0.45, 0.22));
        nodes.add(new Node("GEU Basketball Court", "Ground", 0.55, 0.26));

        nodes.add(new Node("GEU Dispensary", "Medical", 0.50, 0.43));

        nodes.add(new Node("GEU Bell Road", "Path", 0.45, 0.50));
        nodes.add(new Node("GEU Internal Road 1", "Path", 0.50, 0.40));
        nodes.add(new Node("GEU Internal Road 2", "Path", 0.60, 0.35));

        nodes.add(new Node("St Paul High School", "External", 0.85, 0.75));
        nodes.add(new Node("St Paul Hospital", "External", 0.92, 0.78));
        nodes.add(new Node("St Mary Church", "External", 0.90, 0.72));
        nodes.add(new Node("Post Office", "External", 0.88, 0.70));

        // 🔥 AUTO CONNECT WITH TYPE
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {

                double d = distance(nodes.get(i), nodes.get(j));

                if (d < 0.35) {

                    String type = "OUTDOOR";

                    // If both are buildings → indoor
                    if (nodes.get(i).category.equals("Building") &&
                        nodes.get(j).category.equals("Building")) {
                        type = "INDOOR";
                    }

                    addEdge(nodes.get(i).name, nodes.get(j).name, d, type);
                }
            }
        }
    }

    // 🔥 DIJKSTRA WITH MODE FILTERING
    @GetMapping
    public Map<String, Object> getRoute(
            @RequestParam String source,
            @RequestParam String destination,
            @RequestParam(defaultValue = "MIXED") String mode
    ) {

        if (!graph.containsKey(source) || !graph.containsKey(destination)) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid source or destination");
            return error;
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

            for (Edge e : graph.getOrDefault(u, new ArrayList<>())) {

                // 🔥 MODE FILTERING
                if (mode.equalsIgnoreCase("INDOOR") && e.type.equals("OUTDOOR")) continue;
                if (mode.equalsIgnoreCase("OUTDOOR") && e.type.equals("INDOOR")) continue;

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

        Map<String, Object> result = new HashMap<>();
        result.put("path", path);
        result.put("distance", dist.get(destination));
        result.put("mode", mode.toUpperCase());

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