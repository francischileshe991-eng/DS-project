import api.ChatHandler;
import api.NetworkClient;
import com.sun.net.httpserver.HttpServer;
import models.Clock;
import models.Message;
import models.MessageLog;
import models.Peer;
import models.Scoreboard;
import sync.Election;
import sync.MutualExclusion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.Executors;

public class Node {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            printUsageAndExit();
        }

        int nodeId = Integer.parseInt(args[0]);
        List<Peer> peers = new ArrayList<>();
        int port;
        String host = "localhost";

        File defaultCfg = new File("cluster.cfg");

        if (args.length == 1 && defaultCfg.exists()) {
            // Mode A: java Node <nodeId> (reads cluster.cfg automatically)
            peers = loadPeersFromConfig(defaultCfg);
        } else if (args.length == 2 && (args[1].endsWith(".cfg") || new File(args[1]).exists())) {
            // Mode B: java Node <nodeId> <config_file>
            peers = loadPeersFromConfig(new File(args[1]));
        } else if (args.length >= 2) {
            // Mode C: Legacy CLI or direct arguments: java Node <nodeId> <port> [totalNodes] [basePort]
            try {
                port = Integer.parseInt(args[1]);
                int totalNodes = args.length > 2 ? Integer.parseInt(args[2]) : 10;
                int basePort = args.length > 3 ? Integer.parseInt(args[3]) : 8000;
                for (int i = 0; i < totalNodes; i++) {
                    peers.add(new Peer(i, "localhost", basePort + i));
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid arguments. If passing a config file, make sure it exists.");
                printUsageAndExit();
            }
        } else {
            printUsageAndExit();
        }

        int totalNodes = peers.size();
        if (nodeId < 0 || nodeId >= totalNodes) {
            System.out.println("nodeId " + nodeId + " out of bounds. Cluster has " + totalNodes + " nodes [0 .. " + (totalNodes - 1) + "].");
            System.exit(1);
        }

        Peer self = peers.get(nodeId);
        port = self.port;
        host = self.host;

        Clock clock = new Clock(nodeId, totalNodes);
        MessageLog log = new MessageLog();
        Scoreboard scoreboard = new Scoreboard();
        MutualExclusion mutex = new MutualExclusion(nodeId, peers, nodeId == 0, scoreboard);
        Election election = new Election(nodeId, peers);

        // Bind HTTP server to wildcard (0.0.0.0) on the port so both localhost and LAN connections work
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        ChatHandler handler = new ChatHandler(nodeId, port, host, peers, clock, log, scoreboard, mutex, election);
        server.createContext("/", handler);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        String lanIp = getLanIpAddress();
        Peer nextPeer = peers.get((nodeId + 1) % totalNodes);

        System.out.println("=============================================================");
        System.out.println("  Node " + nodeId + " active on " + host + ":" + port);
        System.out.println("  Local Dashboard:    http://localhost:" + port + "/");
        if (!"localhost".equalsIgnoreCase(host) && !"127.0.0.1".equals(host)) {
            System.out.println("  Host Dashboard:     http://" + host + ":" + port + "/");
        } else if (lanIp != null) {
            System.out.println("  Network Dashboard:  http://" + lanIp + ":" + port + "/");
        }
        System.out.println("  Cluster Peers (" + totalNodes + " nodes):");
        for (Peer p : peers) {
            System.out.println("    " + (p.id == nodeId ? "-> " : "   ") + p
                    + (p.id == nodeId ? " (THIS NODE)" : ""));
        }
        System.out.println("  Next in ring: " + nextPeer);
        System.out.println("  Leader:       Node " + election.getCurrentLeaderId() + " (Bully algorithm)");
        System.out.println("  Token:        Node " + nodeId + (mutex.hasToken() ? " holds the token" : " awaits the token"));
        System.out.println("=============================================================");

        // Bootstrap: if this node holds the initial token, start circulation
        if (mutex.hasToken()) {
            Thread.sleep(500);
            mutex.startTokenCirculation();
        }

        // Leader recovery hook
        election.setOnLeadershipWon(() -> {
            try {
                Thread.sleep(800);
                mutex.ensureTokenExists();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Start periodic failure detection of leader
        election.startHealthMonitoring();
        election.checkAndAssertLeadership();

        startConsole(nodeId, clock, log, scoreboard, mutex, election, peers);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            System.out.println("Node " + nodeId + " stopped.");
        }));

        Thread.currentThread().join();
    }

    private static List<Peer> loadPeersFromConfig(File file) throws Exception {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            props.load(in);
        }
        TreeMap<Integer, String> map = new TreeMap<>();
        for (String key : props.stringPropertyNames()) {
            String val = props.getProperty(key).trim();
            if (val.isBlank()) continue;
            int id = -1;
            if (key.startsWith("node.")) {
                try {
                    id = Integer.parseInt(key.substring(5).trim());
                } catch (NumberFormatException ignore) {}
            } else {
                try {
                    id = Integer.parseInt(key.trim());
                } catch (NumberFormatException ignore) {}
            }
            if (id >= 0) {
                map.put(id, val);
            }
        }
        if (map.isEmpty()) {
            throw new IllegalArgumentException("No valid node configurations found in " + file.getName());
        }
        int maxId = map.lastKey();
        List<Peer> list = new ArrayList<>();
        for (int i = 0; i <= maxId; i++) {
            String addr = map.get(i);
            if (addr == null || addr.isBlank()) {
                throw new IllegalArgumentException("Missing configuration for 'node." + i + "' in " + file.getName() + 
                        ". Expected contiguous nodes 0 to " + maxId + ". Please add: node." + i + "=<host>:<port>");
            }
            list.add(Peer.parse(i, addr));
        }
        return list;
    }

    private static String getLanIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }

    private static void printUsageAndExit() {
        System.out.println("Usage:");
        System.out.println("  1. With cluster config:  java Node <nodeId> [configPath]");
        System.out.println("     Example: java Node 0 cluster.cfg");
        System.out.println("     Example: java Node 0            (auto-detects cluster.cfg if present)");
        System.out.println("  2. Local single-host:    java Node <nodeId> <port> [totalNodes] [basePort]");
        System.out.println("     Example: java Node 0 8000 3 8000");
        System.exit(1);
    }

    private static void startConsole(int nodeId, Clock clock, MessageLog log, Scoreboard scoreboard,
                                     MutualExclusion mutex, Election election, List<Peer> peers) {
        Thread console = new Thread(() -> {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            System.out.print(nodeId + "> ");
            System.out.flush();
            String line;
            try {
                while ((line = in.readLine()) != null) {
                    handleConsoleCommand(nodeId, line.trim(), clock, log, scoreboard, mutex, election, peers, in);
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
                                             Election election, List<Peer> peers, BufferedReader in) throws Exception {
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
                clock.tick();
                Message msg = new Message(nodeId, arg, clock.getLamportTime(), clock.getVectorClock());
                log.add(msg);
                System.out.println("[CHAT] Node " + nodeId + " broadcast \"" + arg + "\" with " + clock);
                int delivered = 0;
                for (Peer p : peers) {
                    if (p.id == nodeId) continue;
                    if (NetworkClient.postTo(p.baseUrl, "/api/chat", msg.toJson())) delivered++;
                }
                System.out.println("[CHAT] Delivered broadcast to " + delivered + " of " + (peers.size() - 1) + " peers.");
                break;
            }
            case "score": {
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
                        + " | endpoint=" + peers.get(nodeId).endpoint()
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
}