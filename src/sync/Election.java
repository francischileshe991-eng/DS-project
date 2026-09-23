package sync;

import api.NetworkClient;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Election {
    public static final String MSG_ELECTION = "ELECTION";
    public static final String MSG_OK = "OK";
    public static final String MSG_COORDINATOR = "COORDINATOR";

    private static final long ELECTION_TIMEOUT_MS = 1500;
    private static final long COORDINATOR_TIMEOUT_MS = 3000;
    private static final long HEALTH_INTERVAL_MS = 2000;
    private static final int MAX_ELECTION_ATTEMPTS = 3;
    private static final long ELECTION_COOLDOWN_MS = 15000;

    private final int nodeId;
    private final List<Integer> peerPorts;
    private volatile int currentLeaderId;
    private volatile boolean isElectionInProgress = false;
    private volatile int electionAttempts = 0;
    private final AtomicBoolean okReceived = new AtomicBoolean(false);
    private final AtomicBoolean coordinatorReceived = new AtomicBoolean(false);
    private Runnable onLeadershipWon;

    private final ExecutorService execution;
    private final ScheduledExecutorService monitor;

    public Election(int nodeId, List<Integer> peerPorts) {
        this.nodeId = nodeId;
        this.peerPorts = peerPorts;
        // Highest node ID is the default initial host
        this.currentLeaderId = peerPorts.size() - 1;
        this.execution = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "election-run-" + nodeId);
            t.setDaemon(true);
            return t;
        });
        this.monitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "health-monitor-" + nodeId);
            t.setDaemon(true);
            return t;
        });
    }

    public void setOnLeadershipWon(Runnable callback) {
        this.onLeadershipWon = callback;
    }

    public synchronized void startElection() {
        if (isElectionInProgress) {
            System.out.println("[ELECTION] Node " + nodeId + " election already in progress. Ignoring duplicate trigger.");
            return;
        }
        if (electionAttempts >= MAX_ELECTION_ATTEMPTS) {
            System.out.println("[ELECTION] Node " + nodeId + " exhausted " + MAX_ELECTION_ATTEMPTS
                    + " attempts; cooling down for " + ELECTION_COOLDOWN_MS + "ms before retrying.");
            monitor.schedule(() -> electionAttempts = 0, ELECTION_COOLDOWN_MS, TimeUnit.MILLISECONDS);
            return;
        }
        isElectionInProgress = true;
        electionAttempts++;
        okReceived.set(false);
        coordinatorReceived.set(false);
        System.out.println("[ELECTION] Node " + nodeId + " starting Bully election (attempt " + electionAttempts + ")...");
        execution.submit(this::runElection);
    }

    private void runElection() {
        int sent = 0;
        int higherPeers = 0;
        for (int id = nodeId + 1; id < peerPorts.size(); id++) {
            higherPeers++;
            int targetPort = peerPorts.get(id);
            String payload = msg(MSG_ELECTION);
            if (NetworkClient.postTo(targetPort, "/api/election", payload)) {
                sent++;
                okReceived.set(true);
            }
        }

        if (higherPeers == 0) {
            declareVictory();
            return;
        }

        System.out.println("[ELECTION] Node " + nodeId + " sent ELECTION to " + sent + " of " + higherPeers + " higher peers.");

        // Wait for OK replies
        sleep(ELECTION_TIMEOUT_MS);

        if (coordinatorReceived.get() || !isElectionInProgress) {
            System.out.println("[ELECTION] Node " + nodeId + " election resolved by received COORDINATOR.");
            return;
        }

        if (!okReceived.get()) {
            // No higher node responded -> this node declares victory!
            declareVictory();
            return;
        }

        // At least one higher node responded; wait for its COORDINATOR announcement
        System.out.println("[ELECTION] Higher node acknowledged. Node " + nodeId + " awaiting COORDINATOR broadcast...");
        sleep(COORDINATOR_TIMEOUT_MS);

        if (coordinatorReceived.get() || !isElectionInProgress) {
            System.out.println("[ELECTION] Node " + nodeId + " recognized new coordinator.");
            return;
        }

        // Coordinator announcement never arrived within timeout -> restart election
        System.out.println("[ELECTION] Node " + nodeId + " timed out waiting for COORDINATOR; restarting Bully election.");
        isElectionInProgress = false;
        startElection();
    }

    private synchronized void declareVictory() {
        if (coordinatorReceived.get() && currentLeaderId > nodeId) {
            return;
        }
        this.currentLeaderId = nodeId;
        this.isElectionInProgress = false;
        this.electionAttempts = 0;
        System.out.println("=============================================================");
        System.out.println("  [ELECTION WON] Node " + nodeId + " is now the primary LEADER / Room Host!");
        System.out.println("=============================================================");
        execution.submit(this::broadcastCoordinator);

        if (onLeadershipWon != null) {
            execution.submit(onLeadershipWon);
        }
    }

    public void handleElectionMessage(int senderId) {
        System.out.println("[ELECTION] Node " + nodeId + " received ELECTION message from Node " + senderId);
        // Reply OK to sender asynchronously so we never block incoming HTTP thread
        execution.submit(() -> {
            if (senderId >= 0 && senderId < peerPorts.size()) {
                NetworkClient.postTo(peerPorts.get(senderId), "/api/election", msg(MSG_OK));
            }
        });

        // Bully Algorithm rule: Since this node has higher ID than sender, start own election
        if (!isElectionInProgress) {
            startElection();
        }
    }

    public void handleOkMessage(int senderId) {
        System.out.println("[ELECTION] Node " + nodeId + " received explicit OK from Node " + senderId);
        okReceived.set(true);
    }

    public void handleCoordinatorMessage(int newLeaderId) {
        if (newLeaderId < nodeId) {
            // A lower node is claiming to be leader, but we are alive and have a higher ID! Bully it!
            System.out.println("[ELECTION] Node " + nodeId + " rejected COORDINATOR from lower Node "
                    + newLeaderId + "; asserting leadership!");
            startElection();
            return;
        }
        this.currentLeaderId = newLeaderId;
        this.isElectionInProgress = false;
        this.coordinatorReceived.set(true);
        this.electionAttempts = 0;
        System.out.println("=============================================================");
        System.out.println("  [LEADER RECOGNIZED] New Leader confirmed: Node " + newLeaderId);
        System.out.println("=============================================================");
    }

    public void broadcastCoordinator() {
        String payload = msg(MSG_COORDINATOR);
        for (int id = 0; id < peerPorts.size(); id++) {
            if (id == nodeId) continue;
            int port = peerPorts.get(id);
            boolean ok = NetworkClient.postTo(port, "/api/election", payload);
            System.out.println("[ELECTION] Node " + nodeId + " announced COORDINATOR to Node " + id
                    + " (port " + port + ")" + (ok ? " -> ACK" : " -> UNREACHABLE"));
        }
    }

    public void startHealthMonitoring() {
        monitor.scheduleAtFixedRate(this::monitorLeaderHealth, HEALTH_INTERVAL_MS, HEALTH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void monitorLeaderHealth() {
        if (currentLeaderId == nodeId) return;      // We are currently the leader
        if (isElectionInProgress) return;           // Already running an election

        if (currentLeaderId < 0 || currentLeaderId >= peerPorts.size()) {
            System.out.println("[HEALTH] Node " + nodeId + " detected invalid leader ID " + currentLeaderId + ". Triggering election.");
            startElection();
            return;
        }

        int leaderPort = peerPorts.get(currentLeaderId);
        if (!NetworkClient.isAlive(leaderPort)) {
            System.out.println("[HEALTH] Node " + nodeId + " detected Leader Node " + currentLeaderId
                    + " (port " + leaderPort + ") is DOWN via /api/health.");
            startElection();
        }
    }

    public void checkAndAssertLeadership() {
        if (nodeId == peerPorts.size() - 1) {
            this.currentLeaderId = nodeId;
            this.isElectionInProgress = false;
            execution.submit(this::broadcastCoordinator);
        } else {
            monitorLeaderHealth();
        }
    }

    private String msg(String type) {
        return "{\"type\":\"" + type + "\",\"sender_id\":" + nodeId + "}";
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public int getCurrentLeaderId() {
        return currentLeaderId;
    }

    public boolean isLeader() {
        return nodeId == currentLeaderId;
    }

    public boolean isElectionInProgress() {
        return isElectionInProgress;
    }
}