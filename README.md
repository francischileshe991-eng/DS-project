## 🚀 Quick Start Guide

### 1. Prerequisites
- Java Development Kit (JDK 11 or higher). Java 22 is installed and verified.
- PowerShell 7 or Windows PowerShell.

### 2. Automated Compilation & Cluster Launch
To compile all classes and launch a 3-node distributed cluster:
```powershell
# In PowerShell:
.\scripts\start-cluster.ps1 -Nodes 3 -BasePort 8000
```
This starts 3 autonomous nodes:
- **Node 0**: `http://localhost:8000/`
- **Node 1**: `http://localhost:8001/`
- **Node 2**: `http://localhost:8002/`

You can also launch up to 10 nodes (e.g., `-Nodes 10`).

### 3. Open the Interactive Visual Web Dashboard
Open any browser to:
- **http://localhost:8000/** (Node 0)
- **http://localhost:8001/** (Node 1)
- **http://localhost:8002/** (Node 2)

The dashboard provides:
- **Live Cluster Topology Ring**: Dynamic status of all nodes with real-time indicators for who holds the token and who is the elected leader.
- **Lamport & Vector Clock HUD**: Live counters tracking logical time and vector arrays $[v_0, v_1, \dots, v_{N-1}]$.
- **Distributed Chat Log**: Displays chat messages sorted deterministically by Lamport timestamp, showing sender ID, vector clocks, and causality tags.
- **Shared Scoreboard (Mutual Exclusion)**: High-score table updated atomically only when holding the circulating token.
- **Cluster Controls**: Broadcast messages, submit scores, or manually trigger Bully elections.

### 4. Running the Automated Test Suite (100 Marks Verification)
To execute the automated verification test harness covering all 4 grading rubric criteria:
```powershell
pwsh -ExecutionPolicy Bypass -File scripts\test-scenarios.ps1
```

### 5. Stopping the Cluster
To gracefully shut down all background cluster nodes:
```powershell
.\scripts\stop-cluster.ps1
```

---

## 📂 Project Architecture

```
DS project/
├── src/
│   ├── Node.java                 # Main application runner & console CLI
│   ├── models/
│   │   ├── Clock.java            # Lamport clock & Vector clock algorithms
│   │   ├── Message.java          # Message representation & JSON serialization
│   │   ├── MessageLog.java       # Chronological Lamport total order sorting
│   │   └── Scoreboard.java       # Shared scoreboard data model
│   ├── sync/
│   │   ├── MutualExclusion.java  # Token Ring CS coordination & peer skipping
│   │   └── Election.java         # Bully Leader Election & failure recovery
│   ├── api/
│   │   ├── ChatHandler.java      # Lightweight HTTP REST endpoint router
│   │   ├── DashboardHtml.java    # Embedded responsive dark-mode Web UI
│   │   └── NetworkClient.java    # Non-blocking HTTP client helper
│   └── util/
│       └── Json.java             # Pure Java JSON parser & serializer
├── scripts/
│   ├── compile.ps1               # Source compiler script
│   ├── start-cluster.ps1         # Launches N nodes and validates health
│   ├── stop-cluster.ps1          # Gracefully terminates cluster processes
│   └── test-scenarios.ps1        # Automated rubric test suite
├── docs/
│   ├── REPORT.md                 # Project report, theoretical models, team matrix
│   └── UML_DIAGRAMS.md           # UML Sequence Diagrams for all 4 concepts
└── README.md                     # This file
```

---

## 📊 Core Concepts & Evaluation Rubric (100 Marks)

| Area | Marks | Concepts & Implementation Highlights |
| :--- | :---: | :--- |
| **i) Logical Clocks** | **25** | Monotonic Lamport timestamps ($L = \max(L, L_{in}) + 1$) and Vector Clocks ($V[i] = \max(V[i], V_{in}[i]), V[local]++$). Total order sorting with $(L, sender\_id)$. |
| **ii) Mutual Exclusion** | **25** | Token Ring distributed mutual exclusion over ports $(id + 1) \pmod N$. Critical Section (scoreboard update) executes strictly only while holding token. |
| **iii) Bully Election** | **25** | Garcia-Molina Bully Algorithm: Failure detected via periodic `/api/health` heartbeat. `ELECTION` $\to$ `OK` $\to$ `COORDINATOR` hierarchy. |
| **iv) Code Quality & API** | **15** | Pure Java without third-party frameworks. Strict separation of concerns (`models`, `sync`, `api`, `util`). Non-blocking cached thread pools. |
| **v) Report & UML** | **10** | Comprehensive sequence diagrams in [`docs/UML_DIAGRAMS.md`](docs/UML_DIAGRAMS.md) and 10-person task allocation matrix in [`docs/REPORT.md`](docs/REPORT.md). |
