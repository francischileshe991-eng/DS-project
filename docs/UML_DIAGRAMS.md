# UML Sequence Diagrams: Distributed Chat & Scoreboard System
**Course**: CSC 4722 – Distributed Systems  
**Evaluation Rubric v**: Accurate UML sequence diagrams [5 marks]

---

## 1. Logical Clock Chat Broadcast & Delivery Sequence

This sequence shows how Node 0 broadcasts a message, advancing its local Lamport and Vector clocks, and how recipient nodes (Node 1 and Node 2) update their clocks upon receiving the message:
$$L_{local} = \max(L_{local}, L_{incoming}) + 1$$
$$V_{local}[i] = \max(V_{local}[i], V_{incoming}[i]) \quad \forall i, \quad \text{and } V_{local}[local]++$$

```mermaid
sequenceDiagram
    autonumber
    participant U as User / Client
    participant N0 as Node 0 (Port 8000)
    participant N1 as Node 1 (Port 8001)
    participant N2 as Node 2 (Port 8002)

    Note over N0: Local State: L=0, V=[0,0,0]
    U->>N0: POST /api/broadcast {"text": "Hello"}
    Note over N0: clock.tick()<br/>L = 0 + 1 = 1<br/>V = [1, 0, 0]
    Note over N0: Append to local log sorted by (L, nodeId)

    par Broadcast to Node 1
        N0->>N1: POST /api/chat {"sender_id": 0, "text": "Hello", "lamport": 1, "vector": [1,0,0]}
        Note over N1: clock.updateOnReceive(1, [1,0,0])<br/>L = max(0, 1) + 1 = 2<br/>V[0]=1, V[1]=1, V[2]=0 -> [1,1,0]
        Note over N1: Append to local log sorted by (L, nodeId)
        N1-->>N0: 200 OK {"status": "Message Received"}
    and Broadcast to Node 2
        N0->>N2: POST /api/chat {"sender_id": 0, "text": "Hello", "lamport": 1, "vector": [1,0,0]}
        Note over N2: clock.updateOnReceive(1, [1,0,0])<br/>L = max(0, 1) + 1 = 2<br/>V[0]=1, V[1]=0, V[2]=1 -> [1,0,1]
        Note over N2: Append to local log sorted by (L, nodeId)
        N2-->>N0: 200 OK {"status": "Message Received"}
    end
    N0-->>U: 200 OK {"status": "Broadcasted"}
```

---

## 2. Token Ring Mutual Exclusion & Shared Scoreboard Sequence

This sequence demonstrates mutual exclusion. Node 0 requests a score update (`wantsToUpdateScore = true`), which enters the critical section strictly upon receiving the circulating token. It applies the mutation and then safely passes the token around the ring.

```mermaid
sequenceDiagram
    autonumber
    participant U as Client
    participant N0 as Node 0 (Port 8000)
    participant N1 as Node 1 (Port 8001)
    participant N2 as Node 2 (Port 8002)

    U->>N0: POST /api/score {"player": "Alice", "points": 15}
    Note over N0: mutex.requestCriticalSection("Alice", 15)<br/>Enqueued in pendingUpdates<br/>(Awaiting token)
    N0-->>U: 200 OK {"status": "Queued for Critical Section"}

    Note over N2: Node 2 holds token
    N2->>N0: POST /api/token {"token_holder": 2, "scores": {"Alice": 0}}
    Note over N0: mutex.receiveToken()<br/>Merge incoming scoreboard
    
    rect rgb(30, 45, 60)
        Note over N0: ════ CRITICAL SECTION (MUTEX) ════<br/>De-queue "Alice" +15<br/>scoreboard.update("Alice", 15)<br/>Local Board: Alice = 15
    end

    Note over N0: Prepare Token Payload with updated scores
    N0->>N1: POST /api/token {"token_holder": 0, "scores": {"Alice": 15}}
    N1-->>N0: 200 OK {"status": "Token Handled"}
    Note over N0: hasToken = false

    Note over N1: Node 1 checks pending CS (none)<br/>Idle delay (350ms)
    N1->>N2: POST /api/token {"token_holder": 1, "scores": {"Alice": 15}}
    N2-->>N1: 200 OK {"status": "Token Handled"}
```

---

## 3. Bully Leader Election Algorithm on Node Failure

This sequence illustrates host failure detection via periodic `/api/health` polling, the initiation of the Bully election, and the coordinator announcement.

```mermaid
sequenceDiagram
    autonumber
    participant N0 as Node 0 (Port 8000)
    participant N1 as Node 1 (Port 8001)
    participant N2 as Node 2 (Port 8002 - Crashed Leader)

    Note over N2: Node 2 crashes / terminates
    Note over N0: health-monitor checks Node 2 (Port 8002)
    N0-xN2: GET /api/health (Connection Refused)
    Note over N0: Leader Node 2 is DOWN!<br/>Trigger startElection()

    Note over N0: Step 1: Send ELECTION to peers with higher ID (>0)
    N0->>N1: POST /api/election {"type": "ELECTION", "sender_id": 0}
    N1-->>N0: 200 OK {"status": "OK"}
    
    Note over N1: Node 1 received ELECTION from lower node 0<br/>Sends explicit OK reply and starts own election
    N1-)N0: POST /api/election {"type": "OK", "sender_id": 1}

    Note over N1: Step 2: Node 1 sends ELECTION to higher peers (>1)
    N1-xN2: POST /api/election {"type": "ELECTION", "sender_id": 1} (Unreachable)
    
    Note over N1: Timeout (1800ms) expires with no higher node OK.<br/>Node 1 declares victory!
    Note over N1: 👑 Node 1 is now the LEADER / COORDINATOR

    Note over N1: Step 3: Broadcast COORDINATOR to all active peers
    N1->>N0: POST /api/election {"type": "COORDINATOR", "sender_id": 1}
    Note over N0: handleCoordinatorMessage(1)<br/>New Leader recognized: Node 1
    N0-->>N1: 200 OK {"status": "OK"}
```

---

## 4. Fault-Tolerant Token Ring (Peer Skipping on Failure)

This sequence demonstrates dynamic skipping when a peer crashes. The token is never dropped or frozen.

```mermaid
sequenceDiagram
    autonumber
    participant N0 as Node 0 (Port 8000)
    participant N1 as Node 1 (Port 8001 - Offline)
    participant N2 as Node 2 (Port 8002)

    Note over N0: Node 0 finishes Critical Section
    Note over N0: Attempt hop 1: (0 + 1) % 3 = Node 1
    N0-xN1: POST /api/token (Connection Refused)
    Note over N0: Node 1 is offline.<br/>Dynamically probe hop 2: (0 + 2) % 3 = Node 2
    N0->>N2: POST /api/token {"token_holder": 0, "scores": {...}}
    N2-->>N0: 200 OK {"status": "Token Handled"}
    Note over N0: Token successfully bypassed dead Node 1!
```
