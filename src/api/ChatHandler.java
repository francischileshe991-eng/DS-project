package api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import models.Clock;
import models.Message;
import models.MessageLog;
import models.Peer;
import models.Scoreboard;
import sync.Election;
import sync.MutualExclusion;
import util.Json;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatHandler implements HttpHandler {
    private final int nodeId;
    private final int port;
    private final String host;
    private final List<Peer> peers;
    private final Clock clock;
    private final MessageLog log;
    private final Scoreboard scoreboard;
    private final MutualExclusion mutex;
    private final Election election;

    public ChatHandler(int nodeId, int port, String host, List<Peer> peers, Clock clock, MessageLog log,
                       Scoreboard scoreboard, MutualExclusion mutex, Election election) {
        this.nodeId = nodeId;
        this.port = port;
        this.host = (host == null || host.isBlank()) ? "localhost" : host.trim();
        this.peers = new ArrayList<>(peers);
        this.clock = clock;
        this.log = log;
        this.scoreboard = scoreboard;
        this.mutex = mutex;
        this.election = election;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        try {
            if ("GET".equals(method) && ("/".equals(path) || "/dashboard".equals(path))) {
                sendHtmlResponse(exchange, 200, DashboardHtml.getHtml(nodeId, host, port));
            } else if ("POST".equals(method) && "/api/chat".equals(path)) {
                handleChat(exchange);
            } else if ("POST".equals(method) && "/api/broadcast".equals(path)) {
                handleBroadcast(exchange);
            } else if ("POST".equals(method) && "/api/token".equals(path)) {
                handleToken(exchange);
            } else if ("POST".equals(method) && "/api/election".equals(path)) {
                handleElection(exchange);
            } else if ("POST".equals(method) && "/api/score".equals(path)) {
                handleScore(exchange);
            } else if ("POST".equals(method) && "/api/trigger-election".equals(path)) {
                election.startElection();
                sendResponse(exchange, 200, "{\"status\":\"Election Triggered\"}");
            } else if ("GET".equals(method) && "/api/health".equals(path)) {
                sendResponse(exchange, 200, "{\"status\":\"ALIVE\"}");
            } else if ("GET".equals(method) && "/api/messages".equals(path)) {
                sendResponse(exchange, 200, log.toJson());
            } else if ("GET".equals(method) && "/api/scoreboard".equals(path)) {
                sendResponse(exchange, 200, scoreboard.toJson());
            } else if ("GET".equals(method) && "/api/status".equals(path)) {
                sendResponse(exchange, 200, statusJson());
            } else {
                sendResponse(exchange, 404, "{\"error\":\"Not Found\"}");
            }
        } catch (Exception e) {
            sendResponse(exchange, 500, "{\"error\":\"Server Error: " + Json.escape(String.valueOf(e.getMessage())) + "\"}");
        } finally {
            exchange.close();
        }
    }

    private void handleChat(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Map<String, Object> payload = Json.parse(body);
        Message msg = new Message(
                Json.asInt(payload, "sender_id"),
                Json.asString(payload, "text"),
                Json.asInt(payload, "lamport"),
                Json.asIntArray(payload, "vector")
        );
        clock.updateOnReceive(msg.lamport, msg.vector);
        log.add(msg);
        System.out.println("[CHAT] Node " + nodeId + " received from Node " + msg.senderId
                + " -> \"" + msg.text + "\" | " + clock);
        sendResponse(exchange, 200, "{\"status\":\"Message Received\"}");
    }

    private void handleBroadcast(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Map<String, Object> payload = Json.parse(body);
        String text = Json.asString(payload, "text");
        if (text == null || text.isBlank()) {
            sendResponse(exchange, 400, "{\"error\":\"Empty message\"}");
            return;
        }
        clock.tick();
        Message msg = new Message(nodeId, text, clock.getLamportTime(), clock.getVectorClock());
        log.add(msg);
        System.out.println("[CHAT BROADCAST] Node " + nodeId + " broadcast: \"" + text + "\" | " + clock);
        for (Peer p : peers) {
            if (p.id == nodeId) continue;
            NetworkClient.postTo(p.baseUrl, "/api/chat", msg.toJson());
        }
        sendResponse(exchange, 200, "{\"status\":\"Broadcasted\",\"message\":" + msg.toJson() + "}");
    }

    private void handleToken(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        String scoresJson = null;
        int gen = -1;
        int lastCs = -1;
        int activeCs = -1;
        try {
            Map<String, Object> payload = Json.parse(body);
            Object scores = payload.get("scores");
            if (scores != null) scoresJson = Json.stringify(scores);
            Object g = payload.get("gen");
            if (g instanceof Number) gen = ((Number) g).intValue();
            Object lcs = payload.get("last_cs_node");
            if (lcs instanceof Number) lastCs = ((Number) lcs).intValue();
            Object acs = payload.get("active_cs_node");
            if (acs instanceof Number) activeCs = ((Number) acs).intValue();
        } catch (Exception ignore) {
            // No scores/generation payload carried
        }
        mutex.receiveToken(scoresJson, gen, lastCs, activeCs);
        sendResponse(exchange, 200, "{\"status\":\"Token Handled\"}");
    }

    private void handleElection(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Map<String, Object> payload = Json.parse(body);
        String type = Json.asString(payload, "type");
        int senderId = Json.asInt(payload, "sender_id");
        if ("ELECTION".equalsIgnoreCase(type)) {
            election.handleElectionMessage(senderId);
        } else if ("OK".equalsIgnoreCase(type)) {
            election.handleOkMessage(senderId);
        } else if ("COORDINATOR".equalsIgnoreCase(type)) {
            election.handleCoordinatorMessage(senderId);
        } else {
            sendResponse(exchange, 400, "{\"error\":\"Unknown election message type\"}");
            return;
        }
        sendResponse(exchange, 200, "{\"status\":\"OK\"}");
    }

    private void handleScore(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Map<String, Object> payload = Json.parse(body);
        String player = Json.asString(payload, "player");
        int points = Json.asInt(payload, "points");
        mutex.requestCriticalSection(player, points);
        sendResponse(exchange, 200, "{\"status\":\"Queued for Critical Section\"}");
    }

    private String statusJson() {
        return "{"
                + "\"node_id\":" + nodeId + ","
                + "\"host\":\"" + Json.escape(host) + "\","
                + "\"port\":" + port + ","
                + "\"lamport\":" + clock.getLamportTime() + ","
                + "\"vector\":" + Json.stringify(clock.getVectorClock()) + ","
                + "\"leader\":" + election.getCurrentLeaderId() + ","
                + "\"is_leader\":" + election.isLeader() + ","
                + "\"is_electing\":" + election.isElectionInProgress() + ","
                + "\"has_token\":" + mutex.hasToken() + ","
                + "\"token_holder\":" + mutex.getCurrentTokenHolder() + ","
                + "\"active_cs_node\":" + mutex.getActiveCsNode() + ","
                + "\"last_cs_node\":" + mutex.getLastCsNode() + ","
                + "\"pending_cs\":" + mutex.getPendingCount() + ","
                + "\"token_gen\":" + mutex.getTokenGeneration() + ","
                + "\"token_seen_ms\":" + mutex.getLastTokenActivityMs() + ","
                + "\"messages\":" + log.snapshot().size()
                + "}";
    }

    private static final int MAX_BODY_BYTES = 64 * 1024;

    private String readBody(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES);
        return new String(body, StandardCharsets.UTF_8);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private void sendHtmlResponse(HttpExchange exchange, int statusCode, String html) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}