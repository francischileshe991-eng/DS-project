package sync;

import api.NetworkClient;
import models.Peer;
import models.Scoreboard;
import util.Json;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MutualExclusion {
    private final int nodeId;
    private final List<Peer> peers;
    private final Scoreboard scoreboard;
    private volatile boolean hasToken = false;
    private final Queue<ScoreUpdate> pendingUpdates = new LinkedList<>();
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private static final int IDLE_CIRCULATION_DELAY_MS = 350;
    private static final int ISOLATED_RETRY_DELAY_MS = 1000;
    private static final int TOKEN_CHECK_INTERVAL_MS = 1000;

    // --- Token generation fencing -------------------------------------------------
    // Every token carries a monotonically increasing generation number. A stale token
    // (older generation than the generation already in circulation) is dropped on
    // arrival instead of being re-entered into the ring. This prevents a duplicate
    // token from ever being introduced by the recovery mechanism (ensureTokenExists).
    // lastTokenActivityMs tracks when this node last handled the token so the recovery
    // routine only regenerates once the ring has been genuinely silent for a grace period.
    private volatile int tokenGeneration = 0;
    private volatile long lastTokenActivityMs = System.currentTimeMillis();

    public static class ScoreUpdate {
        public final String player;
        public final int points;

        public ScoreUpdate(String player, int points) {
            this.player = player;
            this.points = points;
        }
    }

    public MutualExclusion(int nodeId, List<Peer> peers, boolean startsWithToken, Scoreboard scoreboard) {
        this.nodeId = nodeId;
        this.peers = new ArrayList<>(peers);
        this.hasToken = startsWithToken;
        this.scoreboard = scoreboard;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "token-passer-" + nodeId);
            t.setDaemon(true);
            return t;
        });
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "token-recovery-" + nodeId);
            t.setDaemon(true);
            return t;
        });
    }

    public static MutualExclusion fromPorts(int nodeId, List<Integer> peerPorts, boolean startsWithToken, Scoreboard scoreboard) {
        List<Peer> list = new ArrayList<>();
        for (int i = 0; i < peerPorts.size(); i++) {
            list.add(new Peer(i, "localhost", peerPorts.get(i)));
        }
        return new MutualExclusion(nodeId, list, startsWithToken, scoreboard);
    }

    public synchronized void requestCriticalSection(String player, int points) {
        pendingUpdates.add(new ScoreUpdate(player, points));
        System.out.println("[MUTEX] Node " + nodeId + " requested CS: add " + points + " to '" + player
                + "' (pending queue size: " + pendingUpdates.size() + ", token held locally: " + hasToken + ")");
        if (hasToken) {
            tryRunCriticalSectionAndPass();
        }
    }

    public synchronized void requestCriticalSection() {
        requestCriticalSection("Node" + nodeId, 1);
    }

    public synchronized void receiveToken(String scoresJson) {
        receiveToken(scoresJson, -1);
    }

    public synchronized void receiveToken(String scoresJson, int incomingGen) {
        if (incomingGen >= 0) {
            if (incomingGen < tokenGeneration) {
                // A stale token from an older generation: drop it to avoid duplicates.
                System.out.println("[TOKEN] Node " + nodeId + " dropped STALE token (gen " + incomingGen
                        + " < current gen " + tokenGeneration + ").");
                return;
            }
            tokenGeneration = incomingGen;
        }
        this.hasToken = true;
        lastTokenActivityMs = System.currentTimeMillis();
        System.out.println("[TOKEN] Node " + nodeId + " received the token (gen " + tokenGeneration + ").");
        if (scoresJson != null && !scoresJson.isBlank()) {
            try {
                // Incoming ring board IS authoritative; local board is only mutated inside the CS.
                scoreboard.replaceAll(Scoreboard.parseScores(scoresJson));
            } catch (Exception e) {
                System.out.println("[MUTEX] Ignoring malformed scoreboard payload: " + e.getMessage());
            }
        }
        tryRunCriticalSectionAndPass();
    }

    private synchronized void tryRunCriticalSectionAndPass() {
        if (!hasToken) return;
        boolean didWork = false;
        if (!pendingUpdates.isEmpty()) {
            // Critical Section: atomic execution on shared resource
            while (!pendingUpdates.isEmpty()) {
                ScoreUpdate u = pendingUpdates.poll();
                scoreboard.update(u.player, u.points);
                System.out.println("[CS ENTERED] Node " + nodeId + " updated '" + u.player + "' +" + u.points);
                didWork = true;
            }
            System.out.println("[CS EXITED] Node " + nodeId + " completed all pending CS operations. Updated Board:");
            System.out.print(scoreboard.report());
        }
        passToken(didWork);
    }

    private void passToken(boolean didWork) {
        if (!hasToken) return;
        hasToken = false;
        String payload = "{\"token_holder\":" + nodeId + ",\"gen\":" + tokenGeneration
                + ",\"scores\":" + scoreboard.toJson() + "}";
        executor.submit(() -> sendTokenAroundRing(payload, didWork));
    }

    private void sendTokenAroundRing(String payload, boolean didWork) {
        // If no work was done in CS, pace the circulation so we don't saturate network/CPU
        if (!didWork && IDLE_CIRCULATION_DELAY_MS > 0) {
            try {
                Thread.sleep(IDLE_CIRCULATION_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        int total = peers.size();
        if (total <= 1) {
            // Single node standalone mode
            synchronized (this) {
                hasToken = true;
                lastTokenActivityMs = System.currentTimeMillis();
            }
            return;
        }

        // Dynamically probe the next alive successor around the ring (nodeId + step) % total
        for (int step = 1; step < total; step++) {
            int targetId = (nodeId + step) % total;
            Peer target = peers.get(targetId);

            if (NetworkClient.postTo(target.baseUrl, "/api/token", payload)) {
                synchronized (this) {
                    lastTokenActivityMs = System.currentTimeMillis();
                }
                if (step > 1) {
                    System.out.println("[TOKEN] Node " + nodeId + " bypassed offline nodes and passed token to "
                            + target + ".");
                } else {
                    System.out.println("[TOKEN] Node " + nodeId + " passed token to "
                            + target + ".");
                }
                return;
            }
        }

        // If all other peers are unreachable, keep token locally and retry after a delay
        System.out.println("[TOKEN] Node " + nodeId + " found no active peers in ring. Retaining token locally; will retry in "
                + ISOLATED_RETRY_DELAY_MS + "ms.");
        synchronized (this) {
            hasToken = true;
            lastTokenActivityMs = System.currentTimeMillis();
        }
        try {
            Thread.sleep(ISOLATED_RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        synchronized (this) {
            if (hasToken) {
                tryRunCriticalSectionAndPass();
            }
        }
    }

    public synchronized boolean hasToken() {
        return hasToken;
    }

    public synchronized int getPendingCount() {
        return pendingUpdates.size();
    }

    public void startTokenCirculation() {
        synchronized (this) {
            if (hasToken) tryRunCriticalSectionAndPass();
        }
    }

    public synchronized void ensureTokenExists() {
        if (hasToken) return;

        // Ask every alive peer: do YOU hold the token, and when did you last touch it?
        boolean tokenHeldByPeer = false;
        long newestActivity = lastTokenActivityMs;
        int alivePeers = 0;
        for (int i = 0; i < peers.size(); i++) {
            if (i == nodeId) continue;
            Peer p = peers.get(i);
            String status = NetworkClient.getBody(p.baseUrl, "/api/status");
            if (status == null) continue;
            alivePeers++;
            try {
                Map<String, Object> s = Json.parse(status);
                if (Boolean.TRUE.equals(s.get("has_token"))) {
                    tokenHeldByPeer = true;
                    System.out.println("[TOKEN HEALTH] Circulating token confirmed active at " + p + ".");
                    break;
                }
                Object seen = s.get("token_seen_ms");
                if (seen instanceof Number) {
                    newestActivity = Math.max(newestActivity, ((Number) seen).longValue());
                }
            } catch (Exception ignore) {
                // malformed status payload; ignore this peer
            }
        }

        if (tokenHeldByPeer) return;

        // Grace / quiescence: if any peer (or we) handled a token within one full ring-rotation
        // window, the token is simply mid-flight BETWEEN nodes -- not lost. Keep re-checking
        // instead of regenerating, so we never mint a duplicate while the ring is still alive.
        long grantedGrace = ringGraceMs();
        long idleMs = System.currentTimeMillis() - newestActivity;
        if (alivePeers > 0 && idleMs < grantedGrace) {
            System.out.println("[TOKEN HEALTH] No holder; last token activity " + idleMs
                    + "ms ago (< grace " + grantedGrace + "ms). Re-checking in " + TOKEN_CHECK_INTERVAL_MS + "ms...");
            scheduler.schedule(this::ensureTokenExists, TOKEN_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
            return;
        }

        // The ring has been silent for a full rotation: the token was genuinely lost (e.g. its
        // holder crashed mid-pass). Regenerate it with a NEW generation so any stale copy that
        // might still arrive is dropped by the receiving node (generation fencing).
        System.out.println("=============================================================");
        System.out.println("  [TOKEN RECOVERY] Circulating token lost due to node crash.");
        System.out.println("  Node " + nodeId + " (Leader) regenerates the token (gen " + (tokenGeneration + 1) + ")!");
        System.out.println("=============================================================");
        tokenGeneration++;
        hasToken = true;
        lastTokenActivityMs = System.currentTimeMillis();
        tryRunCriticalSectionAndPass();
    }

    private long ringGraceMs() {
        return (long) peers.size() * (IDLE_CIRCULATION_DELAY_MS + 600L) + 1500L;
    }

    public int getTokenGeneration() {
        return tokenGeneration;
    }

    public long getLastTokenActivityMs() {
        return lastTokenActivityMs;
    }
}