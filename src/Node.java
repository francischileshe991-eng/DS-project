import api.ChatHandler;
import api.NetworkClient;
import com.sun.net.httpserver.HttpServer;
import models.Clock;
import models.Message;
import models.MessageLog;
import models.Scoreboard;
import sync.Election;
import sync.MutualExclusion;
import util.Json;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class Node {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java Node <nodeId> <port> [totalNodes] [basePort]");
            System.out.println("Example: java Node 0 8000        (10 nodes, ports 8000-8009)");
            System.out.println("Example: java Node 0 8000 3       (3 nodes, ports 8000-8002)");
            System.exit(1);
        }

        int nodeId = Integer.parseInt(args[0]);
        int port = Integer.parseInt(args[1]);
        int totalNodes = args.length > 2 ? Integer.parseInt(args[2]) : 10;
        int basePort = args.length > 3 ? Integer.parseInt(args[3]) : 8000;

        if (nodeId < 0 || nodeId >= totalNodes) {
            System.out.println("nodeId must be in [0, " + (totalNodes - 1) + "] for " + totalNodes + " nodes.");
            System.exit(1);
        }

        List<Integer> peerPorts = new ArrayList<>();
        for (int i = 0; i < totalNodes; i++) peerPorts.add(basePort + i);

        int nextPeerPort = peerPorts.get((nodeId + 1) % totalNodes);

        Clock clock = new Clock(nodeId, totalNodes);
        MessageLog log = new MessageLog();
        Scoreboard scoreboard = new Scoreboard();
        MutualExclusion mutex = new MutualExclusion(nodeId, peerPorts, nodeId == 0, scoreboard);
        Election election = new Election(nodeId, peerPorts);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        ChatHandler handler = new ChatHandler(nodeId, port, peerPorts, clock, log, scoreboard, mutex, election);
        server.createContext("/", handler);
        // Non-blocking execution: cached thread pool so concurrent requests never serialize.
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("=============================================================");
        System.out.println("  Node " + nodeId + " running on port " + port);
        System.out.println("  Dashboard:  http://localhost:" + port + "/");
        System.out.println("  Peers:      " + peerPorts);
        System.out.println("  Next token: port " + nextPeerPort);
        System.out.println("  Leader:     Node " + election.getCurrentLeaderId() + " (Bully algorithm)");
        System.out.println("  Token:      Node " + nodeId + (mutex.hasToken() ? " holds the token" : " awaits the token"));
        System.out.println("=============================================================");

        // Bootstrap: if this node holds the initial token, start the ring after a short delay.
        if (mutex.hasToken()) {
            Thread.sleep(500);
            mutex.startTokenCirculation();
        }

        // Register recovery callback: if this node is elected leader, verify token health and regenerate if lost
        election.setOnLeadershipWon(() -> {
            try {
                Thread.sleep(800);
                mutex.ensureTokenExists();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Periodic failure detection of the elected leader via /api/health.
        election.startHealthMonitoring();
        election.checkAndAssertLeadership();

        startConsole(nodeId, clock, log, scoreboard, mutex, election, peerPorts);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            System.out.println("Node " + nodeId + " stopped.");
        }));

        // Keep main thread alive so server runs persistently until stopped
        Thread.currentThread().join();
    }

    private static void startConsole(int nodeId, Clock clock, MessageLog log, Scoreboard scoreboard,
                                     MutualExclusion mutex, Election election, List<Integer> peerPorts) {
        Thread console = new Thread(() -> {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            System.out.print(nodeId + "> ");
            System.out.flush();
            String line;
            try {
                while ((line = in.readLine()) != null) {
                    handleConsoleCommand(nodeId, line.trim(), clock, log, scoreboard, mutex, election, peerPorts, in);
                    System.out.print(nodeId + "> ");
                    System.out.flush();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "console-" + nodeId);
        console.setDaemon(true);
        console.start();
    }

    private static void handleConsoleCommand(int nodeId, String line, Clock clock, MessageLog log,
                                             Scoreboard scoreboard, MutualExclusion mutex,
                                             Election election, List<Integer> peerPorts, BufferedReader in) throws Exception {
        if (line.isEmpty()) return;

        String[] parts = line.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "chat": {
                if (arg.isEmpty()) {
                    System.out.println("Usage: chat <text>");
                    return;
                }
                // Local send event advances own clocks.
                clock.tick();
                Message msg = new Message(nodeId, arg, clock.getLamportTime(), clock.getVectorClock());
                log.add(msg);
                System.out.println("[CHAT] Node " + nodeId + " broadcast \"" + arg + "\" with " + clock);
                int delivered = 0;
                for (int p : peerPorts) {
                    if (p == portFor(nodeId, peerPorts)) continue;
                    if (NetworkClient.postTo(p, "/api/chat", msg.toJson())) delivered++;
                }
                System.out.println("[CHAT] Delivered broadcast to " + delivered + " of " + (peerPorts.size() - 1) + " peers.");
                break;
            }
            case "score": {
                // score <player> <points> -> requests the token ring critical section.
                String[] parts2 = arg.split("\\s+", 2);
                if (parts2.length < 2) {
                    System.out.println("Usage: score <player> <points>");
                    return;
                }
                int points = Integer.parseInt(parts2[1]);
                mutex.requestCriticalSection(parts2[0], points);
                break;
            }
            case "scores":
                System.out.println("[BOARD] Local scoreboard:");
                System.out.print(scoreboard.report().isEmpty() ? "   (empty)\n" : scoreboard.report());
                break;
            case "msgs":
                System.out.println("[LOG] Messages (sorted by Lamport time):");
                System.out.print(log.report().isEmpty() ? "   (empty)\n" : log.report());
                break;
            case "state":
                System.out.println("[STATE] node=" + nodeId
                        + " | port=" + portFor(nodeId, peerPorts)
                        + " | " + clock
                        + " | leader=" + election.getCurrentLeaderId()
                        + " | token=" + (mutex.hasToken() ? "HOLDING" : "not holding"));
                break;
            case "leader":
                System.out.println("[LEADER] current leader = Node " + election.getCurrentLeaderId()
                        + (election.isLeader() ? " (you)" : ""));
                break;
            case "election":
                System.out.println("[CMD] Manually triggering an election.");
                election.startElection();
                break;
            case "help":
                System.out.println("chat <text>          broadcast a chat message");
                System.out.println("score <p> <pts>      request scoreboard update (critical section)");
                System.out.println("scores               print local scoreboard");
                System.out.println("msgs                 print message log (Lamport-sorted)");
                System.out.println("state                print clocks, leader, token status");
                System.out.println("leader               print current leader");
                System.out.println("election             manually trigger a Bully election");
                System.out.println("quit                 exit");
                break;
            case "quit":
            case "exit":
                System.out.println("Stopping node " + nodeId + ".");
                System.exit(0);
                break;
            default:
                System.out.println("Unknown command. Type 'help'.");
        }
    }

    private static int portFor(int nodeId, List<Integer> peerPorts) {
        return peerPorts.get(nodeId);
    }
}