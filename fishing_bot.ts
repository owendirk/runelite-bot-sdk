#!/usr/bin/env bun
/**
 * Net Fishing Bot - Clean State Machine
 *
 * Tracks movement by position changes, not isMoving flag.
 * Adds cooldowns to prevent spam clicking.
 */

const WS_URL = "ws://localhost:7780";

// === CONFIG ===
// Lumbridge Swamp fishing (shrimp/anchovies) - south of Lumbridge castle
const FISHING_AREA = { x: 3239, z: 3147 };
// Lumbridge castle bank (top floor) - requires climbing stairs
const BANK_AREA = { x: 3208, z: 3220 };
const CLICK_COOLDOWN = 3000;  // ms between clicks

// Note: For simplicity, this bot assumes you're already at the fishing area.
// Complex navigation (stairs, doors) requires pathfinding which isn't implemented yet.

// === TYPES ===
type BotState = "IDLE" | "WALKING" | "FISHING" | "BANKING";

interface GameState {
  tick: number;
  inGame: boolean;
  player?: {
    name: string;
    worldX: number;
    worldZ: number;
    animation: number;
  };
  inventory: Array<{ slot: number; id: number; name: string; count: number }>;
  nearbyNpcs: Array<{ index: number; id: number; name: string; x: number; z: number; distance: number; actions?: string[] }>;
  nearbyLocs: Array<{ id: number; name: string; x: number; z: number; distance: number; actions?: string[] }>;
  bank?: { isOpen: boolean; items: Array<{ slot: number; id: number; name: string; count: number }> };
}

// === GLOBALS ===
let ws: WebSocket | null = null;
let game: GameState | null = null;
let botState: BotState = "IDLE";
let lastTick = 0;
let lastClickTime = 0;
let lastPos = { x: 0, z: 0 };
let walkTarget: { x: number; z: number; reason: string } | null = null;

// === CONNECTION ===
function connect(): Promise<void> {
  return new Promise((resolve, reject) => {
    console.log(`Connecting to ${WS_URL}...`);
    ws = new WebSocket(WS_URL);

    ws.onopen = () => {
      console.log("Connected!\n");
      resolve();
    };

    ws.onmessage = (event) => {
      const msg = JSON.parse(event.data as string);
      if (msg.type === "state") {
        game = msg.data;
      }
    };

    ws.onclose = () => {
      console.log("\nDisconnected");
      process.exit(0);
    };

    ws.onerror = reject;
  });
}

function send(cmd: object) {
  ws?.send(JSON.stringify(cmd));
}

// === UTILITIES ===
function sleep(ms: number): Promise<void> {
  return new Promise(r => setTimeout(r, ms));
}

function dist(x1: number, z1: number, x2: number, z2: number): number {
  return Math.sqrt((x2 - x1) ** 2 + (z2 - z1) ** 2);
}

function playerX(): number { return game?.player?.worldX || 0; }
function playerZ(): number { return game?.player?.worldZ || 0; }
function animation(): number { return game?.player?.animation || -1; }
function invCount(): number { return game?.inventory?.length || 0; }
function invFull(): boolean { return invCount() >= 28; }
function bankOpen(): boolean { return game?.bank?.isOpen || false; }

function isMoving(): boolean {
  // Detect movement by position change
  const moved = playerX() !== lastPos.x || playerZ() !== lastPos.z;
  return moved;
}

function canClick(): boolean {
  return Date.now() - lastClickTime >= CLICK_COOLDOWN;
}

function click() {
  lastClickTime = Date.now();
}

function isFishing(): boolean {
  const a = animation();
  return a === 621 || a === 619 || a === 623 || a === 620 || a === 622;
}

function findFishingSpot() {
  // Just look for any NPC named "Fishing spot"
  const spots = game?.nearbyNpcs?.filter(n =>
    n.name.toLowerCase().includes("fishing spot")
  ) || [];

  // Return closest one
  if (spots.length > 0) {
    spots.sort((a, b) => a.distance - b.distance);
    return spots[0];
  }
  return null;
}

function findBankBooth() {
  return game?.nearbyLocs?.find(l =>
    l.name.toLowerCase().includes("bank booth")
  );
}

// === ACTIONS ===
function walkTo(x: number, z: number, reason: string) {
  if (!canClick()) return;

  console.log(`  >> Walk to (${x}, ${z}) - ${reason}`);
  send({ type: "walkTo", x, z });
  walkTarget = { x, z, reason };
  click();
}

function clickNpc(index: number, option: number, reason: string) {
  if (!canClick()) return;

  console.log(`  >> Click NPC ${index} opt ${option} - ${reason}`);
  send({ type: "interactNpc", npcIndex: index, optionIndex: option });
  click();
}

function clickLoc(x: number, z: number, id: number, option: number, reason: string) {
  if (!canClick()) return;

  console.log(`  >> Click Loc ${id} at (${x},${z}) opt ${option} - ${reason}`);
  send({ type: "interactLoc", x, z, locId: id, optionIndex: option });
  click();
}

// === STATE MACHINE ===

function decideState() {
  // What should we be doing?
  if (invFull()) {
    return "BANKING";
  }
  return "FISHING";
}

function handleIdle() {
  const target = decideState();
  console.log(`[IDLE] -> ${target}`);
  botState = target;
}

function handleFishing() {
  const pos = `(${playerX()}, ${playerZ()})`;
  const fishSpot = findFishingSpot();

  console.log(`[FISHING] Pos: ${pos} | Inv: ${invCount()}/28 | Anim: ${animation()}`);

  // Check if inv full
  if (invFull()) {
    console.log(`  Inventory full!`);
    botState = "BANKING";
    return;
  }

  // Already fishing?
  if (isFishing()) {
    console.log(`  Currently fishing...`);
    return;
  }

  // Recently clicked? Wait for action
  if (!canClick()) {
    console.log(`  Waiting for action... (${Math.ceil((CLICK_COOLDOWN - (Date.now() - lastClickTime)) / 1000)}s)`);
    return;
  }

  // Moving? Wait to arrive
  if (isMoving()) {
    console.log(`  Moving...`);
    return;
  }

  // Find and click fishing spot
  if (fishSpot) {
    console.log(`  Found: "${fishSpot.name}" idx=${fishSpot.index} @ (${fishSpot.x}, ${fishSpot.z}) dist=${fishSpot.distance}`);
    clickNpc(fishSpot.index, 1, "Net fishing spot");
  } else {
    console.log(`  No fishing spot visible`);
    // Walk to fishing area if far
    const d = dist(playerX(), playerZ(), FISHING_AREA.x, FISHING_AREA.z);
    if (d > 10) {
      walkTo(FISHING_AREA.x, FISHING_AREA.z, "Go to fishing area");
    }
  }
}

async function handleBanking() {
  const pos = `(${playerX()}, ${playerZ()})`;
  const d = dist(playerX(), playerZ(), BANK_AREA.x, BANK_AREA.z);

  console.log(`[BANKING] Pos: ${pos} | Dist to bank: ${d.toFixed(0)} | Bank open: ${bankOpen()}`);

  // If no fish in inventory, go back to fishing
  const hasFish = game?.inventory?.some(i =>
    i.name.toLowerCase().includes("raw") ||
    i.name.toLowerCase().includes("shrimp") ||
    i.name.toLowerCase().includes("anchov")
  );

  if (!hasFish && !bankOpen()) {
    console.log(`  No fish to deposit, back to fishing`);
    botState = "FISHING";
    return;
  }

  // Bank is open - deposit all inventory
  if (bankOpen()) {
    console.log(`  Depositing all inventory...`);
    send({ type: "bankDepositAll" });

    // Wait and close bank
    await sleep(500);
    send({ type: "sendKey", keyCode: 27 }); // ESC to close
    botState = "FISHING";
    return;
  }

  // Not at bank yet
  if (d > 5) {
    if (canClick() && !isMoving()) {
      walkTo(BANK_AREA.x, BANK_AREA.z, "Go to bank");
    } else if (isMoving()) {
      console.log(`  Walking to bank...`);
    }
    return;
  }

  // At bank - open it
  const booth = findBankBooth();
  if (booth && canClick()) {
    clickLoc(booth.x, booth.z, booth.id, 2, "Open bank");
  } else if (!booth) {
    console.log(`  No bank booth found`);
  }
}

// === MAIN LOOP ===

async function tick() {
  if (!game?.inGame) {
    console.log("[WAITING] Not logged in...");
    return;
  }

  // Skip duplicate ticks
  if (game.tick === lastTick) return;

  // Update position tracking
  const posChanged = playerX() !== lastPos.x || playerZ() !== lastPos.z;
  lastPos = { x: playerX(), z: playerZ() };
  lastTick = game.tick;

  // Run state handler
  switch (botState) {
    case "IDLE": handleIdle(); break;
    case "FISHING": handleFishing(); break;
    case "BANKING": await handleBanking(); break;
  }
}

async function main() {
  await connect();

  console.log("=== NET FISHING BOT ===");
  console.log(`Fish at: (${FISHING_AREA.x}, ${FISHING_AREA.z})`);
  console.log(`Bank at: (${BANK_AREA.x}, ${BANK_AREA.z})`);
  console.log(`Click cooldown: ${CLICK_COOLDOWN}ms`);
  console.log("Ctrl+C to stop\n");

  while (true) {
    await tick();
    await sleep(600);
  }
}

main();
