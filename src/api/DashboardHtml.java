package api;

public final class DashboardHtml {
    private DashboardHtml() {}

    private static final String TEMPLATE = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Node {{NODE_ID}} — P2P Distributed Systems Observatory</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Fira+Code:wght@400;500;600;700&family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #080b11;
      --surface: #0f141f;
      --surface-elevated: #151c2c;
      --surface-hover: #1c263c;
      --border: rgba(255, 255, 255, 0.07);
      --border-focus: rgba(56, 189, 248, 0.5);
      
      --cyan: #38bdf8;
      --cyan-dim: rgba(56, 189, 248, 0.12);
      --emerald: #10b981;
      --emerald-dim: rgba(16, 185, 129, 0.12);
      --amber: #f59e0b;
      --amber-dim: rgba(245, 158, 11, 0.12);
      --purple: #a855f7;
      --purple-dim: rgba(168, 85, 247, 0.12);
      --rose: #f43f5e;
      --rose-dim: rgba(244, 63, 94, 0.12);

      --text: #f1f5f9;
      --text-muted: #8492a6;
      --text-dim: #475569;
      
      --radius-sm: 6px;
      --radius: 10px;
      --radius-lg: 14px;
      --mono: "Fira Code", monospace;
      --sans: "Plus Jakarta Sans", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    }

    * { box-sizing: border-box; margin: 0; padding: 0; }
    
    body {
      background-color: var(--bg);
      background-image: 
        radial-gradient(rgba(255, 255, 255, 0.05) 1px, transparent 1px),
        radial-gradient(ellipse 80% 50% at 50% -20%, rgba(56, 189, 248, 0.12), transparent);
      background-size: 24px 24px, 100% 100%;
      color: var(--text);
      font-family: var(--sans);
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      overflow-x: hidden;
    }

    /* Top Command Header */
    header {
      background: rgba(15, 20, 31, 0.85);
      backdrop-filter: blur(16px);
      border-bottom: 1px solid var(--border);
      padding: 12px 24px;
      position: sticky;
      top: 0;
      z-index: 50;
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 16px;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 14px;
    }
    .brand-icon {
      width: 36px;
      height: 36px;
      border-radius: var(--radius-sm);
      background: linear-gradient(135deg, rgba(56, 189, 248, 0.2), rgba(168, 85, 247, 0.2));
      border: 1px solid rgba(56, 189, 248, 0.3);
      display: flex;
      align-items: center;
      justify-content: center;
      font-family: var(--mono);
      font-weight: 700;
      font-size: 14px;
      color: var(--cyan);
    }
    .brand-text h1 {
      font-size: 0.95rem;
      font-weight: 700;
      letter-spacing: -0.01em;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .brand-text p {
      font-size: 0.72rem;
      font-family: var(--mono);
      color: var(--text-muted);
      letter-spacing: 0.04em;
    }

    .status-strip {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }

    .chip {
      background: var(--surface-elevated);
      border: 1px solid var(--border);
      padding: 6px 12px;
      border-radius: var(--radius-sm);
      font-size: 0.76rem;
      font-family: var(--mono);
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .chip-indicator {
      width: 7px;
      height: 7px;
      border-radius: 50%;
    }
    .chip-alive { background: var(--emerald); box-shadow: 0 0 8px var(--emerald); }
    .chip-leader { border-color: rgba(245, 158, 11, 0.4); background: var(--amber-dim); color: var(--amber); }
    .chip-token { border-color: rgba(168, 85, 247, 0.4); background: var(--purple-dim); color: #d8b4fe; animation: tokenGlow 2s infinite ease-in-out; }
    
    @keyframes tokenGlow {
      0%, 100% { box-shadow: 0 0 0 rgba(168, 85, 247, 0); }
      50% { box-shadow: 0 0 14px rgba(168, 85, 247, 0.45); }
    }

    /* Main Workspace Layout */
    .app-shell {
      max-width: 1440px;
      width: 100%;
      margin: 0 auto;
      padding: 20px 24px;
      display: flex;
      flex-direction: column;
      gap: 20px;
      flex: 1;
    }

    /* Grid Tier 1: Topology, Clocks & Quick Chaos */
    .hero-grid {
      display: grid;
      grid-template-columns: 360px 1fr 320px;
      gap: 18px;
    }
    @media (max-width: 1200px) {
      .hero-grid { grid-template-columns: 1fr 1fr; }
    }
    @media (max-width: 840px) {
      .hero-grid { grid-template-columns: 1fr; }
    }

    .panel {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: var(--radius-lg);
      padding: 18px;
      display: flex;
      flex-direction: column;
      gap: 14px;
      position: relative;
      overflow: hidden;
    }
    .panel-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid var(--border);
      padding-bottom: 12px;
    }
    .panel-tag {
      font-size: 0.68rem;
      font-family: var(--mono);
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: var(--text-muted);
      font-weight: 600;
    }
    .panel-badge {
      font-size: 0.68rem;
      font-family: var(--mono);
      padding: 2px 6px;
      border-radius: 4px;
      background: var(--surface-elevated);
      color: var(--cyan);
      border: 1px solid var(--border);
    }

    /* Circular Ring Topology Canvas */
    .ring-wrapper {
      position: relative;
      height: 230px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    #topology-svg {
      width: 100%;
      height: 100%;
    }
    .ring-orbit {
      stroke: var(--border);
      stroke-width: 1.5;
      stroke-dasharray: 4 4;
      fill: none;
    }
    .ring-active-orbit {
      stroke: var(--purple);
      stroke-width: 2;
      stroke-dasharray: 6 6;
      fill: none;
      animation: rotateRing 20s linear infinite;
      transform-origin: center;
      opacity: 0.4;
    }
    @keyframes rotateRing {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }

    /* Telemetry Counters */
    .metrics-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
    }
    .metric-card {
      background: var(--surface-elevated);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      padding: 14px;
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .metric-title {
      font-size: 0.7rem;
      font-family: var(--mono);
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .metric-value {
      font-size: 1.6rem;
      font-weight: 800;
      font-family: var(--mono);
      color: var(--cyan);
      letter-spacing: -0.03em;
    }
    .metric-sub {
      font-size: 0.7rem;
      color: var(--text-dim);
      font-family: var(--mono);
    }

    .vector-bar-container {
      background: var(--surface-elevated);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      padding: 12px 14px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .vector-cells {
      display: flex;
      gap: 6px;
      flex-wrap: wrap;
    }
    .vector-cell {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      padding: 4px 8px;
      font-family: var(--mono);
      font-size: 0.8rem;
      font-weight: 600;
      color: var(--text);
      display: flex;
      flex-direction: column;
      align-items: center;
      min-width: 38px;
      transition: all 0.2s;
    }
    .vector-cell.mine {
      border-color: var(--cyan);
      background: var(--cyan-dim);
      color: var(--cyan);
    }
    .vector-cell-idx {
      font-size: 0.6rem;
      color: var(--text-dim);
      text-transform: uppercase;
    }

    /* Chaos / Quick Action Buttons */
    .action-group {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .btn {
      background: var(--surface-elevated);
      color: var(--text);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      padding: 10px 14px;
      font-family: var(--sans);
      font-size: 0.82rem;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: space-between;
      transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
    }
    .btn:hover {
      background: var(--surface-hover);
      border-color: rgba(255, 255, 255, 0.18);
      transform: translateY(-1px);
    }
    .btn:active {
      transform: translateY(0);
    }
    .btn-bully {
      border-color: rgba(245, 158, 11, 0.35);
      background: linear-gradient(135deg, rgba(245, 158, 11, 0.12), rgba(244, 63, 94, 0.08));
    }
    .btn-bully:hover {
      border-color: var(--amber);
      box-shadow: 0 0 15px rgba(245, 158, 11, 0.2);
    }
    .btn-cs {
      border-color: rgba(168, 85, 247, 0.35);
      background: linear-gradient(135deg, rgba(168, 85, 247, 0.14), rgba(56, 189, 248, 0.08));
    }
    .btn-cs:hover {
      border-color: var(--purple);
      box-shadow: 0 0 15px rgba(168, 85, 247, 0.2);
    }

    /* Grid Tier 2: Stream & Shared Ledger */
    .stream-grid {
      display: grid;
      grid-template-columns: 1.4fr 1fr;
      gap: 18px;
      flex: 1;
    }
    @media (max-width: 960px) {
      .stream-grid { grid-template-columns: 1fr; }
    }

    /* Chat Stream Terminal */
    .stream-box {
      height: 380px;
      overflow-y: auto;
      background: var(--surface-elevated);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      padding: 12px;
      display: flex;
      flex-direction: column;
      gap: 8px;
      scroll-behavior: smooth;
    }
    .stream-box::-webkit-scrollbar { width: 5px; }
    .stream-box::-webkit-scrollbar-thumb { background: var(--border); border-radius: 4px; }

    .stream-item {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      padding: 10px 12px;
      display: flex;
      flex-direction: column;
      gap: 5px;
      border-left: 3px solid var(--border);
      transition: all 0.15s ease;
    }
    .stream-item.is-me {
      border-left-color: var(--cyan);
      background: rgba(56, 189, 248, 0.03);
    }
    .stream-item-top {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 0.72rem;
      font-family: var(--mono);
    }
    .node-tag {
      font-weight: 700;
      color: var(--cyan);
      display: inline-flex;
      align-items: center;
      gap: 5px;
    }
    .node-tag.is-peer { color: var(--emerald); }
    .stream-meta {
      display: flex;
      gap: 8px;
      align-items: center;
    }
    .pill-lamport {
      background: var(--cyan-dim);
      color: var(--cyan);
      border: 1px solid rgba(56, 189, 248, 0.2);
      padding: 1px 6px;
      border-radius: 4px;
      font-size: 0.68rem;
    }
    .pill-vector {
      color: var(--text-dim);
      font-size: 0.68rem;
    }
    .stream-text {
      font-size: 0.88rem;
      color: var(--text);
      line-height: 1.45;
      word-break: break-word;
    }

    /* Chat Form Bar */
    .stream-input-bar {
      display: flex;
      gap: 8px;
      margin-top: 4px;
    }
    .input-field {
      flex: 1;
      background: var(--surface-elevated);
      border: 1px solid var(--border);
      color: var(--text);
      padding: 11px 14px;
      border-radius: var(--radius);
      font-family: var(--sans);
      font-size: 0.86rem;
      outline: none;
      transition: all 0.2s;
    }
    .input-field:focus {
      border-color: var(--cyan);
      box-shadow: 0 0 12px rgba(56, 189, 248, 0.15);
    }
    .btn-send {
      background: var(--cyan);
      color: #040810;
      font-weight: 700;
      padding: 0 18px;
      border: none;
      border-radius: var(--radius);
      cursor: pointer;
      font-size: 0.82rem;
      transition: all 0.2s;
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .btn-send:hover {
      background: #7dd3fc;
      transform: translateY(-1px);
    }

    /* Scoreboard Table */
    .ledger-table-wrap {
      background: var(--surface-elevated);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      overflow-x: auto;
      max-height: 250px;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
    }
    thead th {
      background: var(--surface);
      font-size: 0.68rem;
      font-family: var(--mono);
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: var(--text-muted);
      padding: 10px 14px;
      border-bottom: 1px solid var(--border);
    }
    tbody td {
      padding: 10px 14px;
      font-size: 0.85rem;
      border-bottom: 1px solid rgba(255, 255, 255, 0.03);
    }
    tbody tr:last-child td { border-bottom: none; }
    .col-rank {
      font-family: var(--mono);
      font-weight: 700;
      color: var(--text-dim);
      width: 44px;
    }
    .col-player { font-weight: 600; }
    .col-pts {
      font-family: var(--mono);
      font-weight: 700;
      color: var(--emerald);
      text-align: right;
    }
    .row-top .col-rank { color: var(--amber); }

    /* Scoreboard Submit Form */
    .cs-form {
      display: grid;
      grid-template-columns: 1fr 100px auto;
      gap: 8px;
      margin-top: 4px;
    }

    .callout-box {
      background: var(--surface-elevated);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      padding: 10px 14px;
      font-size: 0.74rem;
      color: var(--text-muted);
      line-height: 1.45;
      font-family: var(--mono);
      display: flex;
      align-items: flex-start;
      gap: 8px;
    }
    .callout-icon { color: var(--purple); font-size: 14px; }
  </style>
</head>
<body>

  <!-- Command Strip Header -->
  <header>
    <div class="brand">
      <div class="brand-icon">P2P</div>
      <div class="brand-text">
        <h1>UNZA CSC 4722 <span>//</span> Distributed Systems Observatory</h1>
        <p>CAUSAL LOGICAL CLOCKS • TOKEN RING MUTEX • BULLY ELECTION</p>
      </div>
    </div>
    <div class="status-strip">
      <div class="chip">
        <span class="chip-indicator chip-alive"></span>
        <span>Node {{NODE_ID}} : Port {{PORT}}</span>
      </div>
      <div id="leader-chip" class="chip chip-leader">
        <span>👑 LEADER: Node ?</span>
      </div>
      <div id="token-chip" class="chip chip-token" style="display:none;">
        <span>🔑 TOKEN HELD</span>
      </div>
    </div>
  </header>

  <div class="app-shell">
    <!-- Row 1: Topology, Clocks & Chaos Controls -->
    <div class="hero-grid">
      
      <!-- Panel 1: Dynamic Circular Ring Visualizer -->
      <div class="panel">
        <div class="panel-header">
          <span class="panel-tag">Ring Topology Visualizer</span>
          <span id="ring-status-badge" class="panel-badge">Circulating</span>
        </div>
        <div class="ring-wrapper">
          <svg id="topology-svg" viewBox="0 0 280 220">
            <!-- Dynamic SVG nodes and token orbit rendered via JS -->
          </svg>
        </div>
        <div style="font-size:0.7rem; font-family:var(--mono); color:var(--text-dim); text-align:center;">
          Dynamic Peer Skipping: Ring automatically bypasses crashed nodes
        </div>
      </div>

      <!-- Panel 2: Lamport & Vector Clocks HUD -->
      <div class="panel">
        <div class="panel-header">
          <span class="panel-tag">Distributed Logical Clocks</span>
          <span class="panel-badge">Causal Telemetry</span>
        </div>
        <div class="metrics-row">
          <div class="metric-card">
            <span class="metric-title">Lamport Clock (L)</span>
            <span id="lamport-val" class="metric-value">0</span>
            <span class="metric-sub">Monotonic Total Order</span>
          </div>
          <div class="metric-card">
            <span class="metric-title">Messages Ingested</span>
            <span id="msg-count-val" class="metric-value">0</span>
            <span class="metric-sub">Lamport Ordered Buffer</span>
          </div>
        </div>
        <div class="vector-bar-container">
          <div style="display:flex; justify-content:space-between; align-items:center;">
            <span class="metric-title">Vector Clock [0 .. N-1]</span>
            <span id="vector-raw" style="font-size:0.72rem; font-family:var(--mono); color:var(--cyan);">[0, 0, 0]</span>
          </div>
          <div id="vector-cells" class="vector-cells">
            <!-- Vector clock pills populated dynamically -->
          </div>
        </div>
      </div>

      <!-- Panel 3: Cluster Operations & Chaos Testing -->
      <div class="panel">
        <div class="panel-header">
          <span class="panel-tag">Cluster Operations</span>
          <span class="panel-badge">Garcia-Molina Bully</span>
        </div>
        <div class="action-group">
          <button class="btn btn-bully" onclick="triggerElection()">
            <span>⚡ Trigger Bully Election</span>
            <span style="font-size:0.7rem; opacity:0.8;">Run Wave →</span>
          </button>
          <button class="btn btn-cs" onclick="quickIncrement()">
            <span>🔑 Request Critical Section</span>
            <span style="font-size:0.7rem; opacity:0.8;">+1 Pts →</span>
          </button>
        </div>
        <div class="callout-box">
          <span class="callout-icon">ℹ</span>
          <span>Bully algorithm dynamically challenges higher node IDs. Shared scoreboard is mutated strictly while holding the token.</span>
        </div>
      </div>

    </div>

    <!-- Row 2: Chat Stream & Scoreboard Ledger -->
    <div class="stream-grid">

      <!-- Panel 4: Distributed Chat Log Terminal -->
      <div class="panel">
        <div class="panel-header">
          <span class="panel-tag">Causal Message Stream</span>
          <span class="panel-badge" id="stream-sort-badge">Sorted by (L, NodeId)</span>
        </div>
        <div id="stream-box" class="stream-box">
          <!-- Chat messages rendered dynamically -->
        </div>
        <form class="stream-input-bar" onsubmit="broadcastChat(event)">
          <input type="text" id="chat-input" class="input-field" placeholder="Type a message to broadcast across P2P cluster..." autocomplete="off">
          <button type="submit" class="btn-send">
            <span>Broadcast</span>
            <span>↵</span>
          </button>
        </form>
      </div>

      <!-- Panel 5: Shared High Scoreboard -->
      <div class="panel">
        <div class="panel-header">
          <span class="panel-tag">Shared High Scoreboard</span>
          <span class="panel-badge" style="color:var(--emerald);">Mutual Exclusion</span>
        </div>
        <div class="ledger-table-wrap">
          <table>
            <thead>
              <tr>
                <th style="width:40px;">#</th>
                <th>Player</th>
                <th style="text-align:right;">Score</th>
              </tr>
            </thead>
            <tbody id="scores-body">
              <!-- Score rows -->
            </tbody>
          </table>
        </div>
        <form class="cs-form" onsubmit="submitScore(event)">
          <input type="text" id="player-input" class="input-field" placeholder="Player Name" required>
          <input type="number" id="points-input" class="input-field" placeholder="Points" value="10" required>
          <button type="submit" class="btn btn-cs" style="padding:0 14px;">Add Score</button>
        </form>
        <div style="font-size:0.68rem; font-family:var(--mono); color:var(--text-dim);">
          Critical Section safety guarantee: Exactly one node mutates scoreboard at any instant.
        </div>
      </div>

    </div>
  </div>

  <script>
    const MY_NODE_ID = {{NODE_ID}};
    const MY_PORT = {{PORT}};

    let previousLeader = null;
    let lastMsgCount = 0;

    async function pollState() {
      try {
        const [resStatus, resScores, resMsgs] = await Promise.all([
          fetch('/api/status').then(r => r.json()),
          fetch('/api/scoreboard').then(r => r.json()),
          fetch('/api/messages').then(r => r.json())
        ]);

        // 1. Clocks
        document.getElementById('lamport-val').innerText = resStatus.lamport;
        document.getElementById('msg-count-val').innerText = resMsgs.length;
        document.getElementById('vector-raw').innerText = JSON.stringify(resStatus.vector);

        // Vector pills
        renderVectorPills(resStatus.vector);

        // 2. Leader & Token Badges
        const leaderChip = document.getElementById('leader-chip');
        if (resStatus.is_leader) {
          leaderChip.innerHTML = '<span>👑 YOU ARE LEADER (Node ' + MY_NODE_ID + ')</span>';
          leaderChip.style.background = 'rgba(245, 158, 11, 0.2)';
        } else {
          leaderChip.innerHTML = '<span>👑 LEADER: Node ' + resStatus.leader + '</span>';
          leaderChip.style.background = 'var(--surface-elevated)';
        }

        const tokenChip = document.getElementById('token-chip');
        tokenChip.style.display = resStatus.has_token ? 'flex' : 'none';

        // 3. Topology SVG Ring
        renderTopologyRing(resStatus);

        // 4. Scoreboard Table
        renderScoreboard(resScores);

        // 5. Chat Stream
        renderChat(resMsgs);

      } catch (err) {
        console.error("Poll failed:", err);
      }
    }

    function renderVectorPills(vector) {
      const container = document.getElementById('vector-cells');
      if (!vector) return;
      let html = '';
      for (let i = 0; i < vector.length; i++) {
        const isMe = (i === MY_NODE_ID);
        html += `<div class="vector-cell ${isMe ? 'mine' : ''}">
          <span class="vector-cell-idx">N${i}</span>
          <span>${vector[i]}</span>
        </div>`;
      }
      container.innerHTML = html;
    }

    function renderTopologyRing(status) {
      const svg = document.getElementById('topology-svg');
      const totalNodes = (status.vector && status.vector.length) ? status.vector.length : 3;
      const centerX = 140;
      const centerY = 110;
      const radius = 75;

      let svgContent = '';
      // Base orbital track
      svgContent += `<circle cx="${centerX}" cy="${centerY}" r="${radius}" class="ring-orbit" />`;
      svgContent += `<circle cx="${centerX}" cy="${centerY}" r="${radius}" class="ring-active-orbit" />`;

      // Draw connection lines and nodes
      for (let i = 0; i < totalNodes; i++) {
        const angle = (i * (2 * Math.PI / totalNodes)) - (Math.PI / 2);
        const nx = centerX + radius * Math.cos(angle);
        const ny = centerY + radius * Math.sin(angle);

        const isSelf = (i === MY_NODE_ID);
        const isLeader = (i === status.leader);
        const hasToken = (isSelf && status.has_token);

        let nodeColor = '#334155';
        let strokeColor = 'rgba(255,255,255,0.15)';
        let textColor = '#cbd5e1';

        if (isSelf) {
          nodeColor = '#0369a1';
          strokeColor = '#38bdf8';
          textColor = '#ffffff';
        }
        if (isLeader) {
          strokeColor = '#f59e0b';
        }

        // Outer pulse ring if token is here
        if (hasToken) {
          svgContent += `<circle cx="${nx}" cy="${ny}" r="22" fill="none" stroke="#a855f7" stroke-width="2" opacity="0.6">
            <animate attributeName="r" values="18;26;18" dur="1.6s" repeatCount="indefinite" />
            <animate attributeName="opacity" values="0.8;0;0.8" dur="1.6s" repeatCount="indefinite" />
          </circle>`;
        }

        // Main node bubble
        svgContent += `<circle cx="${nx}" cy="${ny}" r="16" fill="${nodeColor}" stroke="${strokeColor}" stroke-width="2.5" />`;
        svgContent += `<text x="${nx}" y="${ny + 4}" font-family="var(--mono)" font-size="11" font-weight="700" fill="${textColor}" text-anchor="middle">N${i}</text>`;

        // Crown on leader
        if (isLeader) {
          svgContent += `<text x="${nx}" y="${ny - 20}" font-size="12" text-anchor="middle">👑</text>`;
        }

        // Token badge
        if (hasToken) {
          svgContent += `<text x="${nx}" y="${ny + 26}" font-size="10" text-anchor="middle">🔑</text>`;
        }
      }

      svg.innerHTML = svgContent;
    }

    function renderScoreboard(scores) {
      const tbody = document.getElementById('scores-body');
      const entries = Object.entries(scores).sort((a, b) => b[1] - a[1]);
      if (entries.length === 0) {
        tbody.innerHTML = '<tr><td colspan="3" style="text-align:center; color:var(--text-dim); padding:20px; font-family:var(--mono);">No scores entered yet. Acquire token to update!</td></tr>';
        return;
      }
      let html = '';
      entries.forEach(([player, pts], idx) => {
        const isTop = idx === 0;
        html += `<tr class="${isTop ? 'row-top' : ''}">
          <td class="col-rank">#${idx + 1}</td>
          <td class="col-player">${escapeHtml(player)}</td>
          <td class="col-pts">${pts} pts</td>
        </tr>`;
      });
      tbody.innerHTML = html;
    }

    function renderChat(msgs) {
      const box = document.getElementById('stream-box');
      if (!msgs || msgs.length === 0) {
        box.innerHTML = '<div style="color:var(--text-dim); text-align:center; padding-top:60px; font-family:var(--mono); font-size:0.8rem;">Stream empty. Broadcast a message below!</div>';
        return;
      }
      let html = '';
      msgs.forEach(m => {
        const isMe = (m.sender_id === MY_NODE_ID);
        html += `<div class="stream-item ${isMe ? 'is-me' : ''}">
          <div class="stream-item-top">
            <span class="node-tag ${!isMe ? 'is-peer' : ''}">
              <span>●</span> Node ${m.sender_id} ${isMe ? '(You)' : ''}
            </span>
            <div class="stream-meta">
              <span class="pill-lamport">L = ${m.lamport}</span>
              <span class="pill-vector">V=${JSON.stringify(m.vector)}</span>
            </div>
          </div>
          <div class="stream-text">${escapeHtml(m.text)}</div>
        </div>`;
      });
      box.innerHTML = html;
      if (msgs.length !== lastMsgCount) {
        box.scrollTop = box.scrollHeight;
        lastMsgCount = msgs.length;
      }
    }

    async function broadcastChat(e) {
      e.preventDefault();
      const input = document.getElementById('chat-input');
      const text = input.value.trim();
      if (!text) return;
      input.value = '';
      await fetch('/api/broadcast', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text: text })
      });
      pollState();
    }

    async function submitScore(e) {
      e.preventDefault();
      const pIn = document.getElementById('player-input');
      const ptsIn = document.getElementById('points-input');
      const player = pIn.value.trim();
      const points = parseInt(ptsIn.value);
      if (!player || isNaN(points)) return;
      await fetch('/api/score', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ player: player, points: points })
      });
      pIn.value = '';
      pollState();
    }

    async function quickIncrement() {
      await fetch('/api/score', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ player: 'Node' + MY_NODE_ID, points: 1 })
      });
      pollState();
    }

    async function triggerElection() {
      await fetch('/api/trigger-election', { method: 'POST' });
      pollState();
    }

    function escapeHtml(str) {
      if (!str) return '';
      return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    setInterval(pollState, 1000);
    pollState();
  </script>
</body>
</html>
""";

    public static String getHtml(int nodeId, int port) {
        return TEMPLATE
                .replace("{{NODE_ID}}", String.valueOf(nodeId))
                .replace("{{PORT}}", String.valueOf(port));
    }
}
