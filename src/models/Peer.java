package models;

public class Peer {
    public final int id;
    public final String host;
    public final int port;
    public final String baseUrl;

    public Peer(int id, String host, int port) {
        this.id = id;
        this.host = (host == null || host.isBlank()) ? "localhost" : host.trim();
        this.port = port;
        this.baseUrl = "http://" + this.host + ":" + port;
    }

    public String endpoint() {
        return host + ":" + port;
    }

    public static Peer parse(int id, String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Address cannot be empty for peer " + id);
        }
        String clean = address.trim();
        if (clean.startsWith("http://")) {
            clean = clean.substring(7);
        } else if (clean.startsWith("https://")) {
            clean = clean.substring(8);
        }
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }

        String host = "localhost";
        int port;
        if (clean.contains(":")) {
            int colonIdx = clean.lastIndexOf(':');
            host = clean.substring(0, colonIdx).trim();
            port = Integer.parseInt(clean.substring(colonIdx + 1).trim());
        } else {
            port = Integer.parseInt(clean);
        }

        return new Peer(id, host, port);
    }

    @Override
    public String toString() {
        return "Node " + id + " [" + host + ":" + port + "]";
    }
}
