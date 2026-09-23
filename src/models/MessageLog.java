package models;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MessageLog {
    private final List<Message> messages = new ArrayList<>();
    private final int maxSize;

    public MessageLog() {
        this(1000);
    }

    public MessageLog(int maxSize) {
        this.maxSize = maxSize;
    }

    public synchronized void add(Message msg) {
        messages.add(msg);
        // Consistent total ordering: sort by Lamport time, then sender id.
        messages.sort(Comparator.comparingInt((Message m) -> m.lamport)
                .thenComparingInt(m -> m.senderId));
        if (messages.size() > maxSize) messages.remove(0);
    }

    public synchronized List<Message> snapshot() {
        return new ArrayList<>(messages);
    }

    public synchronized String toJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(messages.get(i).toJson());
        }
        sb.append("]");
        return sb.toString();
    }

    public synchronized String report() {
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            sb.append("   L").append(m.lamport).append(" N").append(m.senderId)
              .append(" : ").append(m.text).append("\n");
        }
        return sb.toString();
    }
}