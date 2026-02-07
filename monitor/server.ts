#!/usr/bin/env bun
/**
 * Bot Farm Monitor Server
 *
 * Aggregates state from multiple bot clients and serves a web dashboard.
 * Each bot connects to this server, which tracks:
 * - Account name
 * - Current activity (fishing, banking, idle)
 * - Skill levels and XP
 * - Position
 * - Status (online, offline, error)
 */

const WEB_PORT = 8080;
const WS_PORT = 7781;

interface BotStatus {
  id: string;
  accountName: string;
  connected: boolean;
  lastUpdate: number;
  gameState: string;
  inGame: boolean;
  currentWorld: number;
  currentPlane: number;
  position: { x: number; z: number } | null;
  skills: Record<string, { level: number; xp: number }>;
  inventory: { count: number; items: string[] };
  activity: string;
  fishingLevel: number;
  xpGained: number;
  startTime: number;
}

const bots: Map<string, BotStatus> = new Map();

// Fishing level requirements
const FISHING_METHODS = [
  { level: 1, name: "Shrimp/Anchovies", method: "Net", location: "Various" },
  { level: 5, name: "Sardine/Herring", method: "Bait", location: "Various" },
  { level: 20, name: "Trout/Salmon", method: "Fly", location: "Barbarian Village" },
  { level: 35, name: "Tuna/Swordfish", method: "Harpoon", location: "Karamja" },
  { level: 40, name: "Lobster", method: "Cage", location: "Karamja" },
  { level: 62, name: "Monkfish", method: "Net", location: "Piscatoris" },
  { level: 76, name: "Sharks", method: "Harpoon", location: "Fishing Guild" },
  { level: 82, name: "Anglerfish", method: "Bait", location: "Piscarilius" },
];

// WebSocket server for bot connections
const botWsServer = Bun.serve({
  port: WS_PORT,
  fetch(req, server) {
    if (server.upgrade(req)) {
      return;
    }
    return new Response("Upgrade failed", { status: 500 });
  },
  websocket: {
    open(ws) {
      const id = crypto.randomUUID().slice(0, 8);
      (ws as any).botId = id;

      bots.set(id, {
        id,
        accountName: "Unknown",
        connected: true,
        lastUpdate: Date.now(),
        gameState: "UNKNOWN",
        inGame: false,
        currentWorld: 0,
        currentPlane: 0,
        position: null,
        skills: {},
        inventory: { count: 0, items: [] },
        activity: "Connecting...",
        fishingLevel: 1,
        xpGained: 0,
        startTime: Date.now(),
      });

      console.log(`Bot connected: ${id}`);
    },

    message(ws, message) {
      try {
        const data = JSON.parse(message.toString());
        const id = (ws as any).botId;
        const bot = bots.get(id);
        if (!bot) return;

        // Update bot status from state broadcast
        if (data.type === "state" && data.data) {
          const state = data.data;
          bot.lastUpdate = Date.now();
          bot.gameState = state.gameState || "UNKNOWN";
          bot.inGame = state.inGame || false;
          bot.currentWorld = state.currentWorld || 0;
          bot.currentPlane = state.currentPlane || 0;
          bot.accountName = state.accountName || state.player?.name || "Unknown";

          if (state.player) {
            bot.position = { x: state.player.worldX, z: state.player.worldZ };
          }

          // Track skills
          if (state.skills) {
            const prevFishingXp = bot.skills["Fishing"]?.xp || 0;
            for (const skill of state.skills) {
              bot.skills[skill.name] = { level: skill.level, xp: skill.experience };
            }
            // Track XP gained
            const newFishingXp = bot.skills["Fishing"]?.xp || 0;
            if (prevFishingXp > 0 && newFishingXp > prevFishingXp) {
              bot.xpGained += (newFishingXp - prevFishingXp);
            }
            bot.fishingLevel = bot.skills["Fishing"]?.level || 1;
          }

          // Track inventory
          if (state.inventory) {
            bot.inventory.count = state.inventory.length;
            bot.inventory.items = state.inventory.map((i: any) => i.name);
          }
        }

        // Activity updates from bot script
        if (data.type === "activity") {
          bot.activity = data.activity;
        }

      } catch (e) {
        console.error("Error parsing bot message:", e);
      }
    },

    close(ws) {
      const id = (ws as any).botId;
      const bot = bots.get(id);
      if (bot) {
        bot.connected = false;
        bot.activity = "Disconnected";
      }
      console.log(`Bot disconnected: ${id}`);
    },
  },
});

console.log(`Bot WebSocket server running on port ${WS_PORT}`);

// Web server for dashboard
const webServer = Bun.serve({
  port: WEB_PORT,
  fetch(req) {
    const url = new URL(req.url);

    // API endpoint for bot status
    if (url.pathname === "/api/bots") {
      const botList = Array.from(bots.values()).map(bot => ({
        ...bot,
        uptime: Math.floor((Date.now() - bot.startTime) / 1000),
        xpPerHour: bot.xpGained > 0
          ? Math.floor(bot.xpGained / ((Date.now() - bot.startTime) / 3600000))
          : 0,
        recommendedMethod: FISHING_METHODS.filter(m => m.level <= bot.fishingLevel).pop(),
      }));

      return new Response(JSON.stringify(botList), {
        headers: { "Content-Type": "application/json" },
      });
    }

    // API endpoint for fishing methods
    if (url.pathname === "/api/fishing-methods") {
      return new Response(JSON.stringify(FISHING_METHODS), {
        headers: { "Content-Type": "application/json" },
      });
    }

    // Serve dashboard HTML
    return new Response(DASHBOARD_HTML, {
      headers: { "Content-Type": "text/html" },
    });
  },
});

console.log(`Dashboard running at http://localhost:${WEB_PORT}`);

const DASHBOARD_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Bot Farm Monitor</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      background: #1a1a2e;
      color: #eee;
      padding: 20px;
    }
    h1 { color: #00d4ff; margin-bottom: 20px; }
    .stats {
      display: flex;
      gap: 20px;
      margin-bottom: 20px;
    }
    .stat-card {
      background: #16213e;
      padding: 15px 25px;
      border-radius: 8px;
      border-left: 4px solid #00d4ff;
    }
    .stat-card h3 { color: #888; font-size: 12px; text-transform: uppercase; }
    .stat-card .value { font-size: 28px; font-weight: bold; color: #00d4ff; }
    .bot-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
      gap: 20px;
    }
    .bot-card {
      background: #16213e;
      border-radius: 8px;
      padding: 20px;
      border: 1px solid #333;
    }
    .bot-card.online { border-left: 4px solid #00ff88; }
    .bot-card.offline { border-left: 4px solid #ff4444; opacity: 0.7; }
    .bot-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 15px;
    }
    .bot-name { font-size: 18px; font-weight: bold; }
    .bot-status {
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: bold;
    }
    .bot-status.online { background: #00ff8833; color: #00ff88; }
    .bot-status.offline { background: #ff444433; color: #ff4444; }
    .bot-info { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .info-item { }
    .info-label { color: #888; font-size: 11px; text-transform: uppercase; }
    .info-value { font-size: 14px; }
    .activity {
      margin-top: 15px;
      padding: 10px;
      background: #0f0f23;
      border-radius: 4px;
      font-size: 13px;
    }
    .skills { margin-top: 15px; }
    .skill-bar {
      display: flex;
      align-items: center;
      margin-bottom: 8px;
    }
    .skill-name { width: 80px; font-size: 12px; }
    .skill-level {
      width: 30px;
      text-align: right;
      margin-right: 10px;
      font-weight: bold;
      color: #00d4ff;
    }
    .skill-progress {
      flex: 1;
      height: 8px;
      background: #333;
      border-radius: 4px;
      overflow: hidden;
    }
    .skill-progress-fill {
      height: 100%;
      background: linear-gradient(90deg, #00d4ff, #00ff88);
    }
  </style>
</head>
<body>
  <h1>Bot Farm Monitor</h1>

  <div class="stats" id="stats">
    <div class="stat-card">
      <h3>Total Bots</h3>
      <div class="value" id="totalBots">0</div>
    </div>
    <div class="stat-card">
      <h3>Online</h3>
      <div class="value" id="onlineBots">0</div>
    </div>
    <div class="stat-card">
      <h3>Total XP/hr</h3>
      <div class="value" id="totalXpHr">0</div>
    </div>
  </div>

  <div class="bot-grid" id="botGrid"></div>

  <script>
    function formatTime(seconds) {
      const h = Math.floor(seconds / 3600);
      const m = Math.floor((seconds % 3600) / 60);
      const s = seconds % 60;
      return h > 0 ? \`\${h}h \${m}m\` : \`\${m}m \${s}s\`;
    }

    function formatNumber(n) {
      return n.toLocaleString();
    }

    async function refresh() {
      try {
        const res = await fetch('/api/bots');
        const bots = await res.json();

        // Update stats
        document.getElementById('totalBots').textContent = bots.length;
        document.getElementById('onlineBots').textContent = bots.filter(b => b.connected).length;
        document.getElementById('totalXpHr').textContent = formatNumber(
          bots.reduce((sum, b) => sum + (b.xpPerHour || 0), 0)
        );

        // Update bot cards
        const grid = document.getElementById('botGrid');
        grid.innerHTML = bots.map(bot => \`
          <div class="bot-card \${bot.connected ? 'online' : 'offline'}">
            <div class="bot-header">
              <div class="bot-name">\${bot.accountName || 'Unknown'}</div>
              <div class="bot-status \${bot.connected ? 'online' : 'offline'}">
                \${bot.connected ? 'Online' : 'Offline'}
              </div>
            </div>

            <div class="bot-info">
              <div class="info-item">
                <div class="info-label">World</div>
                <div class="info-value">\${bot.currentWorld || '-'}</div>
              </div>
              <div class="info-item">
                <div class="info-label">Position</div>
                <div class="info-value">\${bot.position ? \`\${bot.position.x}, \${bot.position.z}\` : '-'}</div>
              </div>
              <div class="info-item">
                <div class="info-label">Uptime</div>
                <div class="info-value">\${formatTime(bot.uptime)}</div>
              </div>
              <div class="info-item">
                <div class="info-label">XP/hr</div>
                <div class="info-value">\${formatNumber(bot.xpPerHour || 0)}</div>
              </div>
            </div>

            <div class="activity">
              <strong>Activity:</strong> \${bot.activity || 'Unknown'}
            </div>

            <div class="skills">
              <div class="skill-bar">
                <div class="skill-name">Fishing</div>
                <div class="skill-level">\${bot.fishingLevel}</div>
                <div class="skill-progress">
                  <div class="skill-progress-fill" style="width: \${Math.min(bot.fishingLevel, 99)}%"></div>
                </div>
              </div>
            </div>

            \${bot.recommendedMethod ? \`
              <div style="margin-top: 10px; font-size: 12px; color: #888;">
                <strong>Recommended:</strong> \${bot.recommendedMethod.name} at \${bot.recommendedMethod.location}
              </div>
            \` : ''}
          </div>
        \`).join('');

      } catch (e) {
        console.error('Error refreshing:', e);
      }
    }

    // Refresh every 2 seconds
    refresh();
    setInterval(refresh, 2000);
  </script>
</body>
</html>`;
