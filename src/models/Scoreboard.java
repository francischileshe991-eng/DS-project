package models;

import util.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Scoreboard {
    private final Map<String, Integer> scores = new LinkedHashMap<>();

    public synchronized void update(String player, int points) {
        scores.merge(player, points, Integer::sum);
    }

    public synchronized void replaceAll(Map<String, Integer> incoming) {
        if (incoming == null) return;
        scores.clear();
        scores.putAll(incoming);
    }

    public synchronized Map<String, Integer> snapshot() {
        return new LinkedHashMap<>(scores);
    }

    public synchronized String toJson() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append('"').append(Json.escape(e.getKey())).append("\":").append(e.getValue());
        }
        sb.append("}");
        return sb.toString();
    }

    public static Map<String, Integer> parseScores(String json) {
        Map<String, Object> raw = Json.parse(json);
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            out.put(e.getKey(), ((Number) e.getValue()).intValue());
        }
        return out;
    }

    public synchronized String report() {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(scores.entrySet());
        list.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed());
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : list) sb.append("   ").append(e.getKey()).append(" = ").append(e.getValue()).append("\n");
        return sb.toString();
    }
}