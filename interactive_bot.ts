#!/usr/bin/env bun
/**
 * Interactive Bot Client
 *
 * Connects to RuneLite Bot SDK and provides an interactive REPL
 * for sending commands and viewing game state.
 *
 * Usage: bun run interactive_bot.ts
 */

const WS_URL = "ws://localhost:7780";
const readline = require("readline");

interface BotWorldState {
  tick: number;
  inGame: boolean;
  player?: {
    name: string;
    combatLevel: number;
    worldX: number;
    worldZ: number;
    runEnergy: number;
  };
  skills: Array<{ name: string; level: number; baseLevel: number; experience: number }>;
  inventory: Array<{ slot: number; id: number; name: string; count: number }>;
  nearbyNpcs: Array<{ index: number; name: string; id: number; x: number; z: number; distance: number; actions: string[] }>;
  nearbyLocs: Array<{ id: number; name: string; x: number; z: number; distance: number; actions: string[] }>;
  groundItems: Array<{ id: number; name: string; x: number; z: number; count: number }>;
  dialog?: { isOpen: boolean; options: Array<{ index: number; text: string }> };
}

let ws: WebSocket | null = null;
let lastState: BotWorldState | null = null;
let verbose = false;

function connect(): Promise<void> {
  return new Promise((resolve, reject) => {
    console.log(`Connecting to ${WS_URL}...`);
    ws = new WebSocket(WS_URL);

    ws.onopen = () => {
      console.log("Connected to Bot SDK!");
      resolve();
    };

    ws.onmessage = (event) => {
      const msg = JSON.parse(event.data as string);

      if (msg.type === "connected") {
        console.log(`Server: ${msg.message}`);
      } else if (msg.type === "state") {
        lastState = msg.data;
        if (verbose) {
          const p = lastState?.player;
          console.log(`[Tick ${lastState?.tick}] ${p?.name} @ (${p?.worldX}, ${p?.worldZ})`);
        }
      } else if (msg.type === "ack") {
        console.log("Command acknowledged");
      } else if (msg.type === "error") {
        console.error(`Error: ${msg.message}`);
      }
    };

    ws.onclose = () => console.log("Disconnected");
    ws.onerror = (err) => reject(err);
  });
}

function send(data: object) {
  if (ws?.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(data));
  } else {
    console.error("Not connected!");
  }
}

// === COMMANDS ===

function walk(x: number, z: number) {
  send({ type: "walkTo", x, z });
  console.log(`Walking to (${x}, ${z})`);
}

function talkNpc(index: number) {
  send({ type: "talkToNpc", npcIndex: index });
  console.log(`Talking to NPC ${index}`);
}

function interactNpc(index: number, option: number = 1) {
  send({ type: "interactNpc", npcIndex: index, optionIndex: option });
  console.log(`Interacting with NPC ${index} option ${option}`);
}

function interactObj(x: number, z: number, id: number, option: number = 1) {
  send({ type: "interactLoc", x, z, locId: id, optionIndex: option });
  console.log(`Interacting with object ${id} at (${x}, ${z})`);
}

function pickup(x: number, z: number, itemId: number) {
  send({ type: "pickupItem", x, z, itemId });
  console.log(`Picking up item ${itemId}`);
}

function useItem(slot: number, option: number = 1) {
  send({ type: "useInventoryItem", slot, optionIndex: option });
  console.log(`Using item in slot ${slot}`);
}

function dropItem(slot: number) {
  send({ type: "dropItem", slot });
  console.log(`Dropping item in slot ${slot}`);
}

function clickDialog(option: number) {
  send({ type: "clickDialog", optionIndex: option });
  console.log(`Clicking dialog option ${option}`);
}

function continueDialog() {
  send({ type: "continueDialog" });
  console.log(`Continuing dialog`);
}

function typeText(text: string) {
  send({ type: "typeText", text });
  console.log(`Typing: ${text}`);
}

// === STATE QUERIES ===

function pos() {
  const p = lastState?.player;
  console.log(`Position: (${p?.worldX}, ${p?.worldZ})`);
  return { x: p?.worldX, z: p?.worldZ };
}

function npcs(filter?: string) {
  const list = lastState?.nearbyNpcs || [];
  const filtered = filter
    ? list.filter(n => n.name.toLowerCase().includes(filter.toLowerCase()))
    : list;

  console.log(`\n=== NPCs (${filtered.length}) ===`);
  filtered.slice(0, 15).forEach(n => {
    console.log(`  [${n.index}] ${n.name} @ (${n.x}, ${n.z}) dist=${n.distance}`);
    if (n.actions?.length) console.log(`       Actions: ${n.actions.filter(a => a).join(', ')}`);
  });
  return filtered;
}

function objs(filter?: string) {
  const list = lastState?.nearbyLocs || [];
  const filtered = filter
    ? list.filter(l => l.name.toLowerCase().includes(filter.toLowerCase()))
    : list;

  console.log(`\n=== Objects (${filtered.length}) ===`);
  filtered.slice(0, 15).forEach(l => {
    console.log(`  [${l.id}] ${l.name} @ (${l.x}, ${l.z}) dist=${l.distance}`);
    if (l.actions?.length) console.log(`       Actions: ${l.actions.filter(a => a).join(', ')}`);
  });
  return filtered;
}

function inv() {
  const list = lastState?.inventory || [];
  console.log(`\n=== Inventory (${list.length}) ===`);
  list.forEach(i => {
    console.log(`  [${i.slot}] ${i.name} x${i.count} (id=${i.id})`);
  });
  return list;
}

function ground(filter?: string) {
  const list = lastState?.groundItems || [];
  const filtered = filter
    ? list.filter(i => i.name.toLowerCase().includes(filter.toLowerCase()))
    : list;

  console.log(`\n=== Ground Items (${filtered.length}) ===`);
  filtered.slice(0, 15).forEach(i => {
    console.log(`  ${i.name} x${i.count} @ (${i.x}, ${i.z}) (id=${i.id})`);
  });
  return filtered;
}

function skills() {
  const list = lastState?.skills || [];
  console.log(`\n=== Skills ===`);
  list.forEach(s => {
    console.log(`  ${s.name}: ${s.level}/${s.baseLevel} (${s.experience} xp)`);
  });
}

function findNpc(name: string) {
  const npc = lastState?.nearbyNpcs.find(n =>
    n.name.toLowerCase().includes(name.toLowerCase())
  );
  if (npc) {
    console.log(`Found: [${npc.index}] ${npc.name} @ (${npc.x}, ${npc.z})`);
  }
  return npc;
}

function findObj(name: string) {
  const obj = lastState?.nearbyLocs.find(l =>
    l.name.toLowerCase().includes(name.toLowerCase())
  );
  if (obj) {
    console.log(`Found: [${obj.id}] ${obj.name} @ (${obj.x}, ${obj.z})`);
  }
  return obj;
}

// === HELPERS ===

function help() {
  console.log(`
=== Bot Commands ===
  walk(x, z)                    - Walk to coordinates
  talkNpc(index)                - Talk to NPC by index
  interactNpc(index, option)    - Interact with NPC (option 1-5)
  interactObj(x, z, id, option) - Interact with object
  pickup(x, z, itemId)          - Pick up ground item
  useItem(slot, option)         - Use inventory item
  dropItem(slot)                - Drop inventory item
  clickDialog(option)           - Click dialog option
  continueDialog()              - Press space/continue
  typeText("text")              - Type text

=== State Queries ===
  pos()                         - Show current position
  npcs("filter")                - List nearby NPCs
  objs("filter")                - List nearby objects
  inv()                         - List inventory
  ground("filter")              - List ground items
  skills()                      - List skills
  findNpc("name")               - Find NPC by name
  findObj("name")               - Find object by name

=== Other ===
  verbose = true/false          - Toggle state updates
  lastState                     - Raw state object
  help()                        - Show this help
`);
}

// === MAIN ===

async function main() {
  try {
    await connect();

    console.log("\nType 'help()' for available commands.\n");

    const rl = readline.createInterface({
      input: process.stdin,
      output: process.stdout,
      prompt: 'bot> '
    });

    rl.prompt();

    rl.on('line', async (line: string) => {
      const cmd = line.trim();
      if (!cmd) {
        rl.prompt();
        return;
      }

      try {
        // Evaluate the command in context with our functions available
        const result = eval(cmd);
        if (result !== undefined && typeof result !== 'function') {
          // Don't print large arrays/objects unless explicitly requested
          if (Array.isArray(result) && result.length > 0) {
            // Already printed by the function
          } else if (typeof result === 'object' && result !== null) {
            // Already printed or too large
          } else {
            console.log(result);
          }
        }
      } catch (e: any) {
        console.error(`Error: ${e.message}`);
      }

      rl.prompt();
    });

    rl.on('close', () => {
      console.log('\nGoodbye!');
      process.exit(0);
    });

  } catch (err) {
    console.error("Failed to connect:", err);
    process.exit(1);
  }
}

main();
