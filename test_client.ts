#!/usr/bin/env bun
/**
 * Bot SDK Test Client
 * 
 * Connects to RuneLite Bot SDK WebSocket server and demonstrates:
 * - Receiving game state broadcasts
 * - Sending automation commands
 * 
 * Usage:
 *   bun run test_client.ts
 * 
 * Prerequisites:
 *   1. RuneLite running with Bot SDK plugin enabled
 *   2. Logged into OSRS
 */

const WS_URL = "ws://localhost:7780";

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
  nearbyNpcs: Array<{ index: number; name: string; x: number; z: number; distance: number }>;
  nearbyLocs: Array<{ id: number; name: string; x: number; z: number; distance: number }>;
  groundItems: Array<{ id: number; name: string; x: number; z: number; count: number }>;
  dialog?: { isOpen: boolean; options: Array<{ index: number; text: string }> };
}

class BotSDKClient {
  private ws: WebSocket | null = null;
  private lastState: BotWorldState | null = null;

  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      console.log(`Connecting to ${WS_URL}...`);
      this.ws = new WebSocket(WS_URL);

      this.ws.onopen = () => {
        console.log("✅ Connected to Bot SDK!");
        resolve();
      };

      this.ws.onmessage = (event) => {
        const msg = JSON.parse(event.data as string);
        
        if (msg.type === "connected") {
          console.log(`📡 ${msg.message}`);
        } else if (msg.type === "state") {
          this.lastState = msg.data;
          this.onStateUpdate(msg.data);
        } else if (msg.type === "ack") {
          console.log(`✓ Command acknowledged`);
        } else if (msg.type === "error") {
          console.error(`❌ Error: ${msg.message}`);
        }
      };

      this.ws.onclose = () => {
        console.log("Disconnected from Bot SDK");
      };

      this.ws.onerror = (err) => {
        console.error("WebSocket error:", err);
        reject(err);
      };
    });
  }

  private onStateUpdate(state: BotWorldState) {
    if (!state.inGame) {
      console.log(`[Tick ${state.tick}] Not logged in`);
      return;
    }

    const p = state.player;
    console.log(
      `[Tick ${state.tick}] ${p?.name} @ (${p?.worldX}, ${p?.worldZ}) | ` +
      `HP: ${state.skills.find(s => s.name === "Hitpoints")?.level}/${state.skills.find(s => s.name === "Hitpoints")?.baseLevel} | ` +
      `NPCs: ${state.nearbyNpcs.length} | Items: ${state.inventory.length}`
    );
  }

  // === COMMANDS ===

  walkTo(x: number, z: number) {
    this.send({ type: "walkTo", x, z });
    console.log(`🚶 Walking to (${x}, ${z})`);
  }

  interactNpc(npcIndex: number, optionIndex: number = 1) {
    this.send({ type: "interactNpc", npcIndex, optionIndex });
    console.log(`👤 Interacting NPC ${npcIndex} option ${optionIndex}`);
  }

  talkToNpc(npcIndex: number) {
    this.send({ type: "talkToNpc", npcIndex });
    console.log(`💬 Talking to NPC ${npcIndex}`);
  }

  interactObject(x: number, z: number, locId: number, optionIndex: number = 1) {
    this.send({ type: "interactLoc", x, z, locId, optionIndex });
    console.log(`🏠 Interacting object ${locId} at (${x}, ${z})`);
  }

  pickupItem(x: number, z: number, itemId: number) {
    this.send({ type: "pickupItem", x, z, itemId });
    console.log(`📦 Picking up item ${itemId} at (${x}, ${z})`);
  }

  useItem(slot: number, optionIndex: number = 1) {
    this.send({ type: "useInventoryItem", slot, optionIndex });
    console.log(`🎒 Using item in slot ${slot}`);
  }

  dropItem(slot: number) {
    this.send({ type: "dropItem", slot });
    console.log(`🗑️ Dropping item in slot ${slot}`);
  }

  clickDialog(optionIndex: number) {
    this.send({ type: "clickDialog", optionIndex });
    console.log(`💬 Clicking dialog option ${optionIndex}`);
  }

  continueDialog() {
    this.send({ type: "continueDialog" });
    console.log(`⏭️ Continuing dialog`);
  }

  typeText(text: string) {
    this.send({ type: "typeText", text });
    console.log(`⌨️ Typing: ${text}`);
  }

  sendKey(keyCode: number) {
    this.send({ type: "sendKey", keyCode });
    console.log(`🔑 Sending key: ${keyCode}`);
  }

  // === STATE QUERIES ===

  getState(): BotWorldState | null {
    return this.lastState;
  }

  findNpcByName(name: string): number | null {
    const npc = this.lastState?.nearbyNpcs.find(n => 
      n.name.toLowerCase().includes(name.toLowerCase())
    );
    return npc?.index ?? null;
  }

  findObjectByName(name: string): { id: number; x: number; z: number } | null {
    const obj = this.lastState?.nearbyLocs.find(l => 
      l.name.toLowerCase().includes(name.toLowerCase())
    );
    return obj ? { id: obj.id, x: obj.x, z: obj.z } : null;
  }

  private send(data: object) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data));
    } else {
      console.error("Not connected!");
    }
  }

  close() {
    this.ws?.close();
  }
}

// === DEMO ===
async function main() {
  const client = new BotSDKClient();
  
  try {
    await client.connect();
    
    console.log("\n📋 Available commands:");
    console.log("  client.walkTo(x, z)");
    console.log("  client.talkToNpc(npcIndex)");
    console.log("  client.interactNpc(npcIndex, optionIndex)");
    console.log("  client.interactObject(x, z, locId, optionIndex)");
    console.log("  client.pickupItem(x, z, itemId)");
    console.log("  client.useItem(slot, optionIndex)");
    console.log("  client.dropItem(slot)");
    console.log("  client.clickDialog(optionIndex)");
    console.log("  client.continueDialog()");
    console.log("  client.typeText('hello')");
    console.log("");
    console.log("📊 State queries:");
    console.log("  client.getState()");
    console.log("  client.findNpcByName('banker')");
    console.log("  client.findObjectByName('tree')");
    console.log("\nListening for game state updates...\n");

    // Keep running - press Ctrl+C to exit
    await new Promise(() => {});
    
  } catch (err) {
    console.error("Failed to connect:", err);
    process.exit(1);
  }
}

main();
