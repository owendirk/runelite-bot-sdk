# Bot SDK Client (OSRS + RuneLite)

TypeScript clients and bots for the RuneLite `botsdk` plugin, using WebSocket commands/state.

## Repos

- Client/bots (this repo): `https://github.com/owendirk/runelite-bot-sdk`
- RuneLite fork with integrated `botsdk` plugin branch: `https://github.com/owendirk/runelite` (`botsdk-integration`)
- Standalone plugin source: `https://github.com/owendirk/runelite-botsdk-plugin`
- RS-SDK fork branch used in this workspace: `https://github.com/owendirk/rs-sdk` (`botsdk-integration`)

## Contents

- `smart_fishing_bot.ts`: Main fishing bot (default: Lumbridge Swamp, exact spot anchors, optional shrimp dropping on full inventory)
- `smart_fishing_bot_live.ts`: Mirror of `smart_fishing_bot.ts`
- `test_client.ts`: Minimal command/state test client
- `interactive_bot.ts`: Interactive REPL client
- `monitor/server.ts`: Local monitor dashboard and bot status API

## Prerequisites

- Bun `1.3+`
- Java `17+`
- RuneLite client with `botsdk` plugin (from `owendirk/runelite` branch `botsdk-integration`)
- OSRS account logged in-game

## Quickstart (Fresh Machine)

Clone:

```bash
mkdir -p ~/osrs && cd ~/osrs
git clone https://github.com/owendirk/runelite-bot-sdk.git
git clone --branch botsdk-integration https://github.com/owendirk/runelite.git
```

Build RuneLite with Bot SDK plugin:

```bash
cd ~/osrs/runelite
./gradlew :runelite-client:shadowJar
```

Start RuneLite:

```bash
java -jar ~/osrs/runelite/runelite-client/build/libs/client-*-SNAPSHOT-shaded.jar
```

Then continue with steps below.

## 1) Enable Bot SDK in RuneLite

In RuneLite plugin config:

- Enable `Bot SDK`
- Ensure:
- `Include NPCs = true`
- `Include Objects = true`
- `Max NPC Distance` high enough for your route (15+ is typical)

## 2) Start the Monitor (optional)

```bash
cd ~/osrs/runelite-bot-sdk
bun run monitor/server.ts
```

Monitor URLs:

- Dashboard: `http://localhost:8080`
- Bot monitor WS: `ws://localhost:7781`

## 3) Run the Bot

Default run:

```bash
cd ~/osrs/runelite-bot-sdk
bun run smart_fishing_bot.ts
```

You should see startup lines including:

- `Build: ...`
- `Location: ...`
- `Fish area: (...)`
- `Strict spots: ON ...`

## Bot Behavior (Current)

- Defaults to **Lumbridge Swamp**
- Uses **exact anchored fishing tiles** (no general-area roaming)
- Keeps required fishing tools in inventory
- When inventory is full and shrimp exist, **drops shrimp** instead of banking
- Falls back to banking only when full inventory has no shrimp to drop

## Useful Env Vars

Run with env vars inline, for example:

```bash
BOT_DEBUG=1 BOT_MAX_FISH_SPOT_DISTANCE=2 bun run smart_fishing_bot.ts
```

Supported flags:

- `BOT_DEBUG=0|1`: Toggle debug output
- `BOT_DEBUG_SNAPSHOT_TICKS=10`: Snapshot interval
- `BOT_STUCK_TICKS=25`: Stuck warning threshold
- `BOT_FISHING_RETRY_MS=7000`: Wait window after fish interactions/progress
- `BOT_MAX_FISH_SPOT_DISTANCE=2`: Max allowed distance to click fishing spot NPC
- `BOT_STRICT_SPOT_ONLY=0|1`: Restrict clicks to anchored fishing spots only
- `BOT_FISH_TILE_RADIUS=1`: Tile radius for anchored spot filtering
- `BOT_DROP_SHRIMP_WHEN_FULL=0|1`: Drop shrimp instead of banking when inventory is full

## Quick Troubleshooting

- Bot connects but never sees fishing spots:
- Verify plugin `Include NPCs = true`
- Increase `Max NPC Distance`
- Bot keeps walking to wrong place:
- Confirm startup `Location` and `Fish area` lines
- Bot not moving after walk command:
- Confirm game window is active and SDK plugin is enabled
- Watch for `[SDK ERROR]` lines in logs
