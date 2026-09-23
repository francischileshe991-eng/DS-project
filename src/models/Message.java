package models;

import util.Json;

import java.util.Map;

public class Message {
    public final int senderId;
    public final String text;
    public final int lamport;
    public final int[] vector;
    public final long receivedAt = System.currentTimeMillis();

    public Message(int senderId, String text, int lamport, int[] vector) {
        this.senderId = senderId;
        this.text = text;
        this.lamport = lamport;
        this.vector = vector.clone();
    }

    public static Message fromJson(String json) {
        Map<String, Object> m = Json.parse(json);
        return new Message(
                Json.asInt(m, "sender_id"),
                Json.asString(m, "text"),
                Json.asInt(m, "lamport"),
                Json.asIntArray(m, "vector")
        );
    }

    public String toJson() {
        StringBuilder v = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) v.append(",");
            v.append(vector[i]);
        }
        v.append("]");
        return "{\"sender_id\":" + senderId
                + ",\"text\":\"" + Json.escape(text) + "\""
                + ",\"lamport\":" + lamport
                + ",\"vector\":" + v + "}";
    }

    @Override
    public String toString() {
        return "N" + senderId + " [L" + lamport + "] :: " + text;
    }
}