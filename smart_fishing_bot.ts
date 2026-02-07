#!/usr/bin/env bun
/**
 * Smart Fishing Bot with Level Progression
 *
 * Features:
 * - Tracks fishing level and switches methods automatically
 * - Handles stairs/ladders for navigation
 * - Reports status to monitor server
 * - Supports multiple fishing locations
 */

const WS_URL = "ws://localhost:7780";        // RuneLite Bot SDK
const MONITOR_URL = "ws://localhost:7781";   // Monitor server (optional)
const BOT_BUILD = "2026-02-07-lumbridge-drop-shrimp-v5";

// === FISHING CONFIGURATIONS ===
const FISHING_CONFIGS = {
  // Lumbridge Swamp - Level 1-20
  lumbridge: {
    name: "Lumbridge Swamp",
    minLevel: 1,
    maxLevel: 20,
    fishSpot: "Net",
    requiredTools: ["small fishing net", "fishing net"],
    fishingArea: { x: 3245, z: 3155 },
    fishingTiles: [
      { x: 3245, z: 3155 },
      { x: 3244, z: 3154 },
      { x: 3246, z: 3156 },
    ],
    bankArea: { x: 3208, z: 3220 },
    needsStairs: true,
    stairsUp: { x: 3206, z: 3208 },   // Lumbridge castle stairs
    stairsDown: { x: 3206, z: 3208 },
  },
  // Draynor Village - Level 1-30
  draynor: {
    name: "Draynor Village",
    minLevel: 1,
    maxLevel: 30,
    fishSpot: "Net",
    requiredTools: ["small fishing net", "fishing net"],
    fishingArea: { x: 3087, z: 3228 }, // Draynor Village fishing spot (shrimp/anchovies)
    fishingTiles: [
      { x: 3086, z: 3227 },
      { x: 3086, z: 3228 },
      { x: 3087, z: 3228 },
    ],
    bankArea: { x: 3092, z: 3243 },
    needsStairs: false,
  },
  // Barbarian Village - Level 20-50 (fly fishing)
  barbarian: {
    name: "Barbarian Village",
    minLevel: 20,
    maxLevel: 50,
    fishSpot: "Lure",
    requiredTools: ["fly fishing rod"],
    fishingArea: { x: 3104, z: 3424 },
    bankArea: { x: 3092, z: 3243 }, // Draynor bank
    needsStairs: false,
  },
};

// Current config - default to Lumbridge Swamp
let currentConfig = FISHING_CONFIGS.lumbridge;

// === CONFIG ===
const CLICK_COOLDOWN = 3000;
const DEBUG_ENABLED = Bun.env.BOT_DEBUG !== "0";
const DEBUG_SNAPSHOT_EVERY_TICKS = Number(Bun.env.BOT_DEBUG_SNAPSHOT_TICKS || 10);
const STUCK_TICK_THRESHOLD = Number(Bun.env.BOT_STUCK_TICKS || 25);
const FISHING_RETRY_MS = Number(Bun.env.BOT_FISHING_RETRY_MS || 7000);
const MAX_FISH_SPOT_CLICK_DISTANCE = Number(Bun.env.BOT_MAX_FISH_SPOT_DISTANCE || 2);
const STRICT_SPOT_ONLY = Bun.env.BOT_STRICT_SPOT_ONLY !== "0";
const FISH_TILE_RADIUS = Number(Bun.env.BOT_FISH_TILE_RADIUS || 1);
const DROP_SHRIMP_WHEN_FULL = Bun.env.BOT_DROP_SHRIMP_WHEN_FULL !== "0";
const FISH_KEYWORDS = [
  "raw shrimp",
  "raw anchovies",
  "raw sardine",
  "raw herring",
  "raw trout",
  "raw salmon",
  "raw pike",
  "raw cod",
  "raw mackerel",
  "raw bass",
  "raw tuna",
  "raw lobster",
  "raw swordfish",
  "raw monkfish",
  "raw shark",
  "raw karambwan",
];
const DROP_SHRIMP_KEYWORDS = ["raw shrimp", "shrimp"];

// === TYPES ===
type BotState = "IDLE" | "WALKING_TO_FISH" | "FISHING" | "DROPPING" | "WALKING_TO_BANK" | "CLIMBING_STAIRS" | "BANKING";

interface GameState {
  tick: number;
  inGame: boolean;
  gameState: string;
  currentPlane: number;
  currentWorld: number;
  accountName?: string;
  player?: {
    name: string;
    worldX: number;
    worldZ: number;
    animation: number;
  };
  skills?: Array<{ name: string; level: number; baseLevel: number; experience: number }>;
  inventory: Array<{ slot: number; id: number; name: string; count: number }>;
  nearbyNpcs: Array<{ index: number; id: number; name: string; x: number; z: number; distance: number; options?: string[] }>;
  nearbyLocs: Array<{ id: number; name: string; x: number; z: number; distance: number; options?: string[] }>;
  dialog?: {
    isOpen: boolean;
    isWaiting?: boolean;
    text?: string;
    options: Array<{ index: number; text: string; componentId?: number }>;
  };
  bank?: { isOpen: boolean; items: Array<{ slot: number; id: number; name: string; count: number }> };
}

// === GLOBALS ===
let ws: WebSocket | null = null;
let monitorWs: WebSocket | null = null;
let game: GameState | null = null;
let botState: BotState = "IDLE";
let lastTick = 0;
let lastClickTime = 0;
let lastPos = { x: 0, z: 0 };
let hasLastPos = false;
let movedThisTick = false;
let fishingLevel = 1;
let startXp = 0;
let currentXp = 0;
let fishCaught = 0;
let stableStateTicks = 0;
let samePosTicks = 0;
let lastDebugSnapshotTick = 0;
let lastStuckWarnTick = -1;
let lastCommand: { type: string; reason: string; at: number } | null = null;
let lastProgressTick = 0;
let lastObservedFishCount = 0;
let lastObservedXp = 0;
let lastFishingInteractAt = 0;
let lastFishingProgressAt = 0;

// === CONNECTION ===
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
      if (msg.type === "state") {
        game = msg.data;
        updateSkillTracking();
      } else if (msg.type === "ack") {
        debugLog("ACK", msg.commandType ? `for ${msg.commandType}` : "");
      } else if (msg.type === "error") {
        console.error(`[SDK ERROR] ${msg.message || "Unknown error"}`);
        debugLog("SDK_ERROR_PAYLOAD", JSON.stringify(msg));
      }
    };

    ws.onclose = () => {
      console.log("\nDisconnected");
      process.exit(0);
    };

    ws.onerror = reject;
  });
}

function connectMonitor() {
  try {
    monitorWs = new WebSocket(MONITOR_URL);
    monitorWs.onopen = () => console.log("Connected to Monitor");
    monitorWs.onerror = () => {}; // Ignore errors - monitor is optional
  } catch (e) {
    // Monitor connection is optional
  }
}

function send(cmd: object) {
  ws?.send(JSON.stringify(cmd));
}

function sendToMonitor(data: object) {
  if (monitorWs?.readyState === WebSocket.OPEN) {
    monitorWs.send(JSON.stringify(data));
  }
}

function debugLog(label: string, message: string) {
  if (!DEBUG_ENABLED) return;
  console.log(`[DEBUG:${label}] ${message}`);
}

function trackCommand(type: string, reason: string) {
  lastCommand = { type, reason, at: Date.now() };
  debugLog("CMD", `${type} | ${reason}`);
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
function playerPlane(): number { return game?.currentPlane || 0; }
function animation(): number { return game?.player?.animation || -1; }
function invCount(): number { return game?.inventory?.length || 0; }
function invFull(): boolean { return invCount() >= 28; }
function bankOpen(): boolean { return game?.bank?.isOpen || false; }

function hasAnyKeyword(name: string, keywords: string[]): boolean {
  const lower = name.toLowerCase();
  return keywords.some(k => lower.includes(k));
}

function getFishItems() {
  return (game?.inventory || []).filter(i => hasAnyKeyword(i.name, FISH_KEYWORDS));
}

function getShrimpItems() {
  return (game?.inventory || []).filter(i => hasAnyKeyword(i.name, DROP_SHRIMP_KEYWORDS));
}

function getFishCount(): number {
  return getFishItems().reduce((sum, i) => sum + i.count, 0);
}

function hasFishInInventory(): boolean {
  return getFishItems().length > 0;
}

function hasRequiredTool(): boolean {
  const tools = currentConfig.requiredTools || [];
  if (tools.length === 0) return true;
  return (game?.inventory || []).some(i => hasAnyKeyword(i.name, tools));
}

function shouldBankForFish(): boolean {
  return invFull() && hasFishInInventory();
}

function shouldDropShrimp(): boolean {
  return DROP_SHRIMP_WHEN_FULL && invFull() && getShrimpItems().length > 0;
}

function isMoving(): boolean {
  return movedThisTick;
}

function canClick(): boolean {
  return Date.now() - lastClickTime >= CLICK_COOLDOWN;
}

function click() {
  lastClickTime = Date.now();
}

function isFishing(): boolean {
  const a = animation();
  return a === 621 || a === 619 || a === 623 || a === 620 || a === 622 || a === 618;
}

function updateSkillTracking() {
  if (!game?.skills) return;

  const fishing = game.skills.find(s => s.name === "Fishing");
  if (fishing) {
    fishingLevel = fishing.level;
    if (startXp === 0) {
      startXp = fishing.experience;
    }
    currentXp = fishing.experience;
  }

  // Update config based on level
  if (fishingLevel >= 20 && currentConfig === FISHING_CONFIGS.lumbridge) {
    console.log(`\n*** Level ${fishingLevel} reached! Consider switching to Barbarian Village ***\n`);
  }
}

function getFishingLevel(): number {
  return fishingLevel;
}

function getXpGained(): number {
  return currentXp - startXp;
}

function getFishingTiles(): Array<{ x: number; z: number }> {
  const tiles = (currentConfig as any).fishingTiles as Array<{ x: number; z: number }> | undefined;
  if (tiles && tiles.length > 0) return tiles;
  return [{ x: currentConfig.fishingArea.x, z: currentConfig.fishingArea.z }];
}

function minDistToFishingTile(x: number, z: number): number {
  const tiles = getFishingTiles();
  let best = Number.POSITIVE_INFINITY;
  for (const tile of tiles) {
    const d = dist(x, z, tile.x, tile.z);
    if (d < best) best = d;
  }
  return best;
}

function nearestFishingTileToPlayer() {
  const px = playerX();
  const pz = playerZ();
  const tiles = getFishingTiles();
  let best = tiles[0];
  let bestDist = dist(px, pz, best.x, best.z);
  for (let i = 1; i < tiles.length; i++) {
    const d = dist(px, pz, tiles[i].x, tiles[i].z);
    if (d < bestDist) {
      best = tiles[i];
      bestDist = d;
    }
  }
  return { ...best, distance: bestDist };
}

function findFishingSpot() {
  const spots = (game?.nearbyNpcs?.filter(n =>
    n.name?.toLowerCase().includes("fishing spot")
  ) || []).map(s => ({ ...s, anchorDist: minDistToFishingTile(s.x, s.z) }));

  if (spots.length === 0) return null;
  let targetSpots = spots;
  const anchoredSpots = spots.filter(s => s.anchorDist <= FISH_TILE_RADIUS);

  if (STRICT_SPOT_ONLY) {
    if (anchoredSpots.length === 0) return null;
    targetSpots = anchoredSpots;
  } else if (anchoredSpots.length > 0) {
    targetSpots = anchoredSpots;
  }

  targetSpots.sort((a, b) => a.distance - b.distance);

  const desiredOption = currentConfig.fishSpot.toLowerCase();
  for (const spot of targetSpots) {
    const optIdx = spot.options?.findIndex(o => o?.toLowerCase() === desiredOption) ?? -1;
    if (optIdx >= 0) {
      return { spot, optionIndex: optIdx + 1, matchedOption: currentConfig.fishSpot, strict: true };
    }
  }

  // Fallback: still click closest fishing spot even if option metadata is missing.
  return { spot: targetSpots[0], optionIndex: 1, matchedOption: null, strict: false };
}

function findBankBooth() {
  return game?.nearbyLocs?.find(l =>
    l.name.toLowerCase().includes("bank booth") ||
    l.name.toLowerCase().includes("bank chest")
  );
}

function findStairs(direction: "up" | "down") {
  const keyword = direction === "up" ? "climb-up" : "climb-down";
  return game?.nearbyLocs?.find(l =>
    (l.name.toLowerCase().includes("stair") || l.name.toLowerCase().includes("ladder")) &&
    l.options?.some(o => o?.toLowerCase().includes(keyword) || o?.toLowerCase() === "climb")
  );
}

function findDialogOption(keywords: string[]) {
  if (!game?.dialog?.isOpen || !game.dialog.options?.length) return null;

  const options = game.dialog.options;
  for (const option of options) {
    const text = option.text?.toLowerCase() || "";
    if (!text) continue;
    if (keywords.some(k => text.includes(k))) {
      return option;
    }
  }
  return null;
}

function getDialogChoiceNumber(targetIndex: number): number | null {
  const options = game?.dialog?.options || [];
  const idx = options.findIndex(o => o.index === targetIndex);
  if (idx < 0) return null;
  const choice = idx + 1;
  return choice >= 1 && choice <= 9 ? choice : null;
}

function normalizeConfig() {
  // Self-heal obviously wrong Draynor coords (pre-fix config was in Lumbridge area).
  if (currentConfig === FISHING_CONFIGS.draynor) {
    const d = dist(
      currentConfig.fishingArea.x,
      currentConfig.fishingArea.z,
      currentConfig.bankArea.x,
      currentConfig.bankArea.z
    );
    if (d > 120) {
      console.warn(`[CONFIG FIX] Draynor fishing coords looked wrong (${currentConfig.fishingArea.x}, ${currentConfig.fishingArea.z}); correcting to (3087, 3228).`);
      currentConfig.fishingArea = { x: 3087, z: 3228 };
    }
  }
}

// === ACTIONS ===
function walkTo(x: number, z: number, reason: string) {
  if (!canClick()) return;
  console.log(`  >> Walk to (${x}, ${z}) - ${reason}`);
  trackCommand("walkTo", reason);
  send({ type: "walkTo", x, z });
  click();
}

function clickNpc(index: number, option: number, reason: string) {
  if (!canClick()) return;
  console.log(`  >> Click NPC ${index} opt ${option} - ${reason}`);
  trackCommand("interactNpc", reason);
  send({ type: "interactNpc", npcIndex: index, optionIndex: option });
  click();
}

function clickLoc(x: number, z: number, id: number, option: number, reason: string) {
  if (!canClick()) return;
  console.log(`  >> Click Loc ${id} at (${x},${z}) opt ${option} - ${reason}`);
  trackCommand("interactLoc", reason);
  send({ type: "interactLoc", x, z, locId: id, optionIndex: option });
  click();
}

function clickDialogOption(optionIndex: number, reason: string) {
  if (!canClick()) return;
  console.log(`  >> Click Dialog ${optionIndex} - ${reason}`);
  trackCommand("clickDialog", reason);
  send({ type: "clickDialog", optionIndex });
  click();
}

function clickComponent(componentId: number, reason: string) {
  if (!canClick()) return;
  console.log(`  >> Click Component ${componentId} - ${reason}`);
  trackCommand("clickComponent", reason);
  send({ type: "clickComponent", componentId });
  click();
}

function sendKey(keyCode: number, reason: string) {
  if (!canClick()) return;
  console.log(`  >> Send Key ${keyCode} - ${reason}`);
  trackCommand("sendKey", reason);
  send({ type: "sendKey", keyCode });
  click();
}

function climbUp() {
  if (!canClick()) return;
  console.log(`  >> Climbing up...`);
  trackCommand("climbUp", "Climb stairs up");
  send({ type: "climbUp" });
  click();
}

function climbDown() {
  if (!canClick()) return;
  console.log(`  >> Climbing down...`);
  trackCommand("climbDown", "Climb stairs down");
  send({ type: "climbDown" });
  click();
}

// === STATE MACHINE ===

function decideState(): BotState {
  if (!currentConfig.needsStairs && playerPlane() > 0) {
    return "CLIMBING_STAIRS";
  }

  if (shouldDropShrimp()) {
    return "DROPPING";
  }

  if (shouldBankForFish()) {
    return "WALKING_TO_BANK";
  }
  return "WALKING_TO_FISH";
}

function handleIdle() {
  const target = decideState();
  console.log(`[IDLE] -> ${target}`);
  botState = target;
  sendToMonitor({ type: "activity", activity: target });
}

function handleWalkingToFish() {
  const d = dist(playerX(), playerZ(), currentConfig.fishingArea.x, currentConfig.fishingArea.z);
  const fishTarget = findFishingSpot();
  console.log(`[WALKING_TO_FISH] Dist: ${d.toFixed(0)} | Plane: ${playerPlane()}`);

  // Runtime self-heal for stale Draynor config: never path 150+ tiles from Draynor bank.
  if (currentConfig === FISHING_CONFIGS.draynor && d > 120) {
    console.warn(`[CONFIG FIX] Draynor fish target looked stale (${currentConfig.fishingArea.x}, ${currentConfig.fishingArea.z}); correcting to (3087, 3228).`);
    currentConfig.fishingArea = { x: 3087, z: 3228 };
    botState = "FISHING";
    return;
  }

  // If we can already see a valid fishing spot nearby, start fishing immediately.
  if (fishTarget && fishTarget.spot.distance <= 15) {
    debugLog("PATH", `Fishing spot already nearby at (${fishTarget.spot.x}, ${fishTarget.spot.z}) dist=${fishTarget.spot.distance}`);
    botState = "FISHING";
    return;
  }

  // Ground-floor routes (e.g. Draynor/Barbarian) must descend first if we spawn upstairs.
  if (!currentConfig.needsStairs && playerPlane() > 0) {
    botState = "CLIMBING_STAIRS";
    return;
  }

  // Check for stairs if needed
  if (currentConfig.needsStairs && playerPlane() > 0) {
    botState = "CLIMBING_STAIRS";
    return;
  }

  // Check if at fishing area
  if (d < 15) {
    botState = "FISHING";
    return;
  }

  // Walk towards fishing area
  if (canClick() && !isMoving()) {
    walkTo(currentConfig.fishingArea.x, currentConfig.fishingArea.z, "Go to fishing area");
  }

  sendToMonitor({ type: "activity", activity: `Walking to fish (${d.toFixed(0)} tiles)` });
}

function handleFishing() {
  const pos = `(${playerX()}, ${playerZ()})`;
  const fishTarget = findFishingSpot();
  const fishCount = getFishCount();
  const hasTool = hasRequiredTool();
  const now = Date.now();

  console.log(`[FISHING] Pos: ${pos} | Inv: ${invCount()}/28 | Fish: ${fishCount} | Tool: ${hasTool ? "yes" : "no"} | Level: ${fishingLevel} | XP: +${getXpGained()}`);

  if (!hasTool) {
    console.log(`  Missing required fishing tool (${currentConfig.requiredTools.join(", ")})`);
    sendToMonitor({ type: "activity", activity: "Missing fishing tool" });
    return;
  }

  if (shouldBankForFish()) {
    if (shouldDropShrimp()) {
      console.log(`  Inventory full with shrimp, dropping`);
      botState = "DROPPING";
      sendToMonitor({ type: "activity", activity: "Inventory full, dropping shrimp" });
      return;
    }

    console.log(`  Inventory full with fish, going to bank`);
    botState = "WALKING_TO_BANK";
    sendToMonitor({ type: "activity", activity: "Inventory full, going to bank" });
    return;
  }

  if (isFishing()) {
    console.log(`  Currently fishing...`);
    sendToMonitor({ type: "activity", activity: `Fishing (Level ${fishingLevel})` });
    return;
  }

  if (lastFishingInteractAt > 0 && now - lastFishingInteractAt < FISHING_RETRY_MS) {
    console.log(`  Waiting for fishing interaction to resolve...`);
    return;
  }

  if (lastFishingProgressAt > 0 && now - lastFishingProgressAt < FISHING_RETRY_MS) {
    console.log(`  Recent catch detected, continuing current cycle...`);
    return;
  }

  if (!canClick()) {
    console.log(`  Waiting for action...`);
    return;
  }

  if (isMoving()) {
    console.log(`  Moving...`);
    return;
  }

  if (fishTarget) {
    const fishSpot = fishTarget.spot;
    if (fishSpot.distance > MAX_FISH_SPOT_CLICK_DISTANCE) {
      console.log(`  Fishing spot too far (${fishSpot.distance} tiles), repositioning...`);
      const anchor = nearestFishingTileToPlayer();
      walkTo(anchor.x, anchor.z, "Reposition on exact fishing tile");
      return;
    }

    if (fishTarget.strict) {
      console.log(`  Found: "${fishSpot.name}" @ (${fishSpot.x}, ${fishSpot.z}) [${fishTarget.matchedOption}]`);
    } else {
      console.log(`  Found fishing spot but option metadata missing; using option 1 fallback`);
    }
    clickNpc(fishSpot.index, fishTarget.optionIndex, fishTarget.matchedOption || currentConfig.fishSpot);
    lastFishingInteractAt = Date.now();
    sendToMonitor({ type: "activity", activity: `Clicking fishing spot` });
  } else {
    const npcCount = game?.nearbyNpcs?.length || 0;
    if (npcCount === 0) {
      console.log(`  No NPC data visible (plugin npc collection may be disabled/range-limited)`);
      sendToMonitor({ type: "activity", activity: "No NPC data visible" });
      return;
    }

    const anchor = nearestFishingTileToPlayer();
    if (anchor.distance > 1.5) {
      console.log(`  No anchored fishing spot visible, moving to exact tile...`);
      walkTo(anchor.x, anchor.z, "Move to exact fishing tile");
    } else {
      console.log(`  No anchored fishing spot visible, waiting on exact tile...`);
    }
  }
}

function handleClimbingStairs() {
  const plane = playerPlane();
  console.log(`[CLIMBING_STAIRS] Current plane: ${plane}`);

  if (plane === 0) {
    // On ground floor, continue to destination
    if (shouldDropShrimp()) {
      botState = "DROPPING";
    } else if (shouldBankForFish()) {
      botState = "WALKING_TO_BANK";
    } else {
      botState = "WALKING_TO_FISH";
    }
    return;
  }

  // Some stairs open a dialog ("Climb up or down the stairs?").
  const downOption = findDialogOption(["climb down", "go down", "descend"]);
  if (downOption) {
    if (!canClick()) {
      console.log(`  Dialog open, waiting...`);
      return;
    }

    console.log(`  Selecting: "${downOption.text}"`);

    // Prefer numeric dialog key (1-9), fallback to component/index clicking.
    const choiceNumber = getDialogChoiceNumber(downOption.index);
    if (choiceNumber !== null) {
      // Java KeyEvent digits: '1' => 49, '2' => 50, etc.
      sendKey(48 + choiceNumber, `Choose option ${choiceNumber} (climb down)`);
    } else if (typeof downOption.componentId === "number") {
      clickComponent(downOption.componentId, "Choose climb down");
    } else {
      clickDialogOption(downOption.index, "Choose climb down");
    }

    sendToMonitor({ type: "activity", activity: "Selecting climb-down dialog" });
    return;
  }

  if (!canClick()) {
    console.log(`  Waiting...`);
    return;
  }

  if (isMoving()) {
    console.log(`  Moving...`);
    return;
  }

  // Find and climb down stairs
  const stairs = findStairs("down");
  if (stairs) {
    console.log(`  Found stairs at (${stairs.x}, ${stairs.z})`);
    climbDown();
    sendToMonitor({ type: "activity", activity: "Climbing down stairs" });
  } else {
    console.log(`  Looking for stairs...`);
    // Walk towards known stair location
    if (currentConfig.stairsDown) {
      walkTo(currentConfig.stairsDown.x, currentConfig.stairsDown.z, "Find stairs");
    }
  }
}

async function handleDropping() {
  const shrimpItems = getShrimpItems();
  console.log(`[DROPPING] Inv: ${invCount()}/28 | Shrimp stacks: ${shrimpItems.length}`);

  // Once inventory is no longer full, continue fishing.
  if (!invFull()) {
    botState = "WALKING_TO_FISH";
    sendToMonitor({ type: "activity", activity: "Dropped shrimp, back to fishing" });
    return;
  }

  // Fallback for full inventory with no shrimp left.
  if (shrimpItems.length === 0) {
    if (shouldBankForFish()) {
      console.log(`  Full inventory but no shrimp to drop, going to bank`);
      botState = "WALKING_TO_BANK";
      sendToMonitor({ type: "activity", activity: "No shrimp to drop, going to bank" });
    } else {
      botState = "WALKING_TO_FISH";
    }
    return;
  }

  if (!canClick()) {
    console.log(`  Waiting to drop...`);
    return;
  }

  if (isMoving()) {
    console.log(`  Moving...`);
    return;
  }

  const item = shrimpItems[0];
  console.log(`  Dropping ${item.name} from slot ${item.slot}`);
  trackCommand("dropItem", `Drop ${item.name}`);
  send({ type: "dropItem", slot: item.slot });
  click();
  sendToMonitor({ type: "activity", activity: `Dropping ${item.name}` });
}

async function handleWalkingToBank() {
  const d = dist(playerX(), playerZ(), currentConfig.bankArea.x, currentConfig.bankArea.z);
  console.log(`[WALKING_TO_BANK] Dist: ${d.toFixed(0)} | Plane: ${playerPlane()}`);

  // Ground-floor routes (e.g. Draynor/Barbarian) must descend first if we spawn upstairs.
  if (!currentConfig.needsStairs && playerPlane() > 0) {
    botState = "CLIMBING_STAIRS";
    return;
  }

  // Check for stairs if needed (going up to bank)
  if (currentConfig.needsStairs && playerPlane() === 0 && d < 20) {
    // Find and climb up stairs
    const stairs = findStairs("up");
    if (stairs && canClick() && !isMoving()) {
      console.log(`  Climbing up to bank...`);
      climbUp();
      sendToMonitor({ type: "activity", activity: "Climbing to bank" });
      return;
    }
  }

  // Check if at bank
  if (d < 5 || bankOpen()) {
    botState = "BANKING";
    return;
  }

  // Walk to bank
  if (canClick() && !isMoving()) {
    walkTo(currentConfig.bankArea.x, currentConfig.bankArea.z, "Go to bank");
    sendToMonitor({ type: "activity", activity: `Walking to bank (${d.toFixed(0)} tiles)` });
  }
}

async function handleBanking() {
  const fishItems = getFishItems();
  const hasFish = fishItems.length > 0;
  console.log(`[BANKING] Bank open: ${bankOpen()} | Fish stacks: ${fishItems.length} | Tool: ${hasRequiredTool() ? "yes" : "no"}`);

  if (!hasFish && !bankOpen()) {
    console.log(`  No fish, back to fishing`);
    botState = "WALKING_TO_FISH";
    return;
  }

  if (bankOpen()) {
    if (hasFish) {
      if (!canClick()) {
        console.log(`  Waiting to deposit...`);
        return;
      }

      const item = fishItems[0];
      console.log(`  Depositing ${item.name} x${item.count} from slot ${item.slot}...`);
      send({ type: "bankDeposit", slot: item.slot, amount: -1 });
      fishCaught += item.count;
      click();
      sendToMonitor({ type: "activity", activity: `Depositing ${item.name}` });
      return;
    }

    if (!canClick()) {
      console.log(`  Fish deposited, waiting to close bank...`);
      return;
    }

    if (!hasRequiredTool()) {
      console.log(`  Warning: fishing tool not in inventory after banking`);
    }

    sendKey(27, "Close bank");
    botState = "WALKING_TO_FISH";
    sendToMonitor({ type: "activity", activity: "Deposited fish, returning" });
    return;
  }

  // Open bank
  const booth = findBankBooth();
  if (booth && canClick()) {
    clickLoc(booth.x, booth.z, booth.id, 2, "Open bank");
    sendToMonitor({ type: "activity", activity: "Opening bank" });
  } else if (!booth) {
    console.log(`  No bank booth found`);
  }
}

// === MAIN LOOP ===

async function tick() {
  if (!game?.inGame) {
    console.log(`[WAITING] Game state: ${game?.gameState || "unknown"}`);
    sendToMonitor({ type: "activity", activity: `Waiting (${game?.gameState})` });
    return;
  }

  // Forward full state to monitor
  if (monitorWs?.readyState === WebSocket.OPEN) {
    monitorWs.send(JSON.stringify({ type: "state", data: game }));
  }

  if (game.tick === lastTick) return;

  const stateAtTickStart = botState;
  if (lastProgressTick === 0) {
    lastProgressTick = game.tick;
    lastObservedFishCount = getFishCount();
    lastObservedXp = currentXp;
  }

  const currentPos = { x: playerX(), z: playerZ() };
  if (!hasLastPos) {
    movedThisTick = false;
    hasLastPos = true;
  } else {
    movedThisTick = currentPos.x !== lastPos.x || currentPos.z !== lastPos.z;
  }
  lastPos = currentPos;
  samePosTicks = movedThisTick ? 0 : samePosTicks + 1;
  lastTick = game.tick;

  const fishNow = getFishCount();
  const xpNow = currentXp;
  if (fishNow > lastObservedFishCount || xpNow > lastObservedXp) {
    lastProgressTick = game.tick;
    lastFishingProgressAt = Date.now();
    debugLog("PROGRESS", `fish ${lastObservedFishCount}->${fishNow}, xp ${lastObservedXp}->${xpNow}`);
  }
  lastObservedFishCount = fishNow;
  lastObservedXp = xpNow;

  switch (botState) {
    case "IDLE": handleIdle(); break;
    case "WALKING_TO_FISH": handleWalkingToFish(); break;
    case "FISHING": handleFishing(); break;
    case "DROPPING": await handleDropping(); break;
    case "CLIMBING_STAIRS": handleClimbingStairs(); break;
    case "WALKING_TO_BANK": await handleWalkingToBank(); break;
    case "BANKING": await handleBanking(); break;
  }

  if (botState === stateAtTickStart) {
    stableStateTicks++;
  } else {
    debugLog("STATE", `${stateAtTickStart} -> ${botState}`);
    stableStateTicks = 0;
  }

  const cooldownMs = Math.max(0, CLICK_COOLDOWN - (Date.now() - lastClickTime));
  const dialogOpen = game.dialog?.isOpen === true;
  const actionableNow = canClick() && !isMoving() && !dialogOpen;
  const looksStuck =
    stableStateTicks >= STUCK_TICK_THRESHOLD &&
    samePosTicks >= STUCK_TICK_THRESHOLD &&
    game.tick - lastProgressTick > STUCK_TICK_THRESHOLD &&
    !isFishing() &&
    !bankOpen();

  if (looksStuck && lastStuckWarnTick !== game.tick) {
    lastStuckWarnTick = game.tick;
    const lastCmdAge = lastCommand ? Date.now() - lastCommand.at : -1;
    const stuckSummary =
      `state=${botState} pos=(${playerX()},${playerZ()},p${playerPlane()}) ` +
      `stableTicks=${stableStateTicks} samePosTicks=${samePosTicks} ` +
      `dialog=${dialogOpen} cooldownMs=${cooldownMs} actionable=${actionableNow} ` +
      `lastCmd=${lastCommand ? `${lastCommand.type}/${lastCommand.reason}` : "none"} ` +
      `lastCmdAgeMs=${lastCmdAge}`;
    console.log(`[STUCK?] ${stuckSummary}`);
    sendToMonitor({ type: "activity", activity: `Stuck? ${botState} @ (${playerX()},${playerZ()},p${playerPlane()})` });
  }

  if (DEBUG_ENABLED && (lastDebugSnapshotTick === 0 || game.tick - lastDebugSnapshotTick >= DEBUG_SNAPSHOT_EVERY_TICKS)) {
    lastDebugSnapshotTick = game.tick;
    const snapshot =
      `tick=${game.tick} state=${botState} pos=(${playerX()},${playerZ()},p${playerPlane()}) ` +
      `moved=${movedThisTick} anim=${animation()} inv=${invCount()} fish=${getFishCount()} ` +
      `bankOpen=${bankOpen()} dialogOpen=${dialogOpen} cooldownMs=${cooldownMs} ` +
      `canAct=${actionableNow} lastCmd=${lastCommand ? `${lastCommand.type}` : "none"}`;
    debugLog("SNAPSHOT", snapshot);
  }
}

async function main() {
  await connect();
  connectMonitor();
  normalizeConfig();

  console.log("\n=== SMART FISHING BOT ===");
  console.log(`Build: ${BOT_BUILD}`);
  console.log(`Location: ${(currentConfig as any).name || "Configured route"}`);
  console.log(`Fish area: (${currentConfig.fishingArea.x}, ${currentConfig.fishingArea.z})`);
  console.log(`Bank area: (${currentConfig.bankArea.x}, ${currentConfig.bankArea.z})`);
  const fishBankDist = dist(
    currentConfig.fishingArea.x,
    currentConfig.fishingArea.z,
    currentConfig.bankArea.x,
    currentConfig.bankArea.z
  );
  if (fishBankDist > 120) {
    console.warn(`[CONFIG WARN] Fishing and bank areas are far apart (${fishBankDist.toFixed(0)} tiles). Verify coordinates for ${currentConfig.fishSpot}.`);
  }
  console.log(`Click cooldown: ${CLICK_COOLDOWN}ms`);
  console.log(`Fishing retry wait: ${FISHING_RETRY_MS}ms | Max spot click distance: ${MAX_FISH_SPOT_CLICK_DISTANCE}`);
  console.log(`Drop shrimp when full: ${DROP_SHRIMP_WHEN_FULL ? "ON" : "OFF"}`);
  console.log(`Strict spots: ${STRICT_SPOT_ONLY ? "ON" : "OFF"} | Tile radius: ${FISH_TILE_RADIUS}`);
  console.log(`Debug: ${DEBUG_ENABLED ? "ON" : "OFF"} (BOT_DEBUG=0 to disable)`);
  console.log("Ctrl+C to stop\n");

  while (true) {
    await tick();
    await sleep(600);
  }
}

main();
