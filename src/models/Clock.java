package models;

import java.util.Arrays;

public class Clock {
    private int lamportTime = 0;
    private final int[] vectorClock;
    private final int nodeId;

    public Clock(int nodeId, int totalNodes) {
        this.nodeId = nodeId;
        this.vectorClock = new int[totalNodes];
    }

    // Local event tick
    public synchronized void tick() {
        lamportTime++;
        vectorClock[nodeId]++;
    }

    // Update clocks upon receiving a message
    public synchronized void updateOnReceive(int incomingLamport, int[] incomingVector) {
        // Lamport clock update: max(local, incoming) + 1
        this.lamportTime = Math.max(this.lamportTime, incomingLamport) + 1;
        // Vector clock update: max(local[i], incoming[i]) for all indices
        if (incomingVector != null) {
            for (int i = 0; i < vectorClock.length; i++) {
                int inc = i < incomingVector.length ? incomingVector[i] : 0;
                vectorClock[i] = Math.max(vectorClock[i], inc);
            }
        }
        // Always increment local node position after merge
        vectorClock[nodeId]++;
    }

    public synchronized int getLamportTime() {
        return lamportTime;
    }

    public synchronized int[] getVectorClock() {
        return Arrays.copyOf(vectorClock, vectorClock.length);
    }

    /**
     * Determines causality between two vector clocks:
     * Returns:
     *  -1 if v1 causally precedes v2 (v1 -> v2)
     *   1 if v2 causally precedes v1 (v2 -> v1)
     *   0 if v1 and v2 are concurrent (v1 || v2) or identical
     */
    public static int compareCausality(int[] v1, int[] v2) {
        if (v1 == null || v2 == null) return 0;
        boolean lessOrEqual = true;
        boolean greaterOrEqual = true;
        int len = Math.max(v1.length, v2.length);
        for (int i = 0; i < len; i++) {
            int val1 = i < v1.length ? v1[i] : 0;
            int val2 = i < v2.length ? v2[i] : 0;
            if (val1 > val2) lessOrEqual = false;
            if (val1 < val2) greaterOrEqual = false;
        }
        if (lessOrEqual && !greaterOrEqual) return -1; // v1 strictly precedes v2
        if (greaterOrEqual && !lessOrEqual) return 1;  // v2 strictly precedes v1
        return 0; // concurrent or identical
    }

    public static String causalityLabel(int[] v1, int[] v2) {
        int c = compareCausality(v1, v2);
        if (c < 0) return "CAUSALLY_PRECEDES";
        if (c > 0) return "CAUSALLY_FOLLOWS";
        return "CONCURRENT";
    }

    @Override
    public synchronized String toString() {
        return "lamport=" + lamportTime + " vector=" + Arrays.toString(vectorClock);
    }
}