# Bot SDK Client (OSRS + RuneLite)

TypeScript clients and bots for the RuneLite `botsdk` plugin, using WebSocket commands/state.

## Contents

- `smart_fishing_bot.ts`: Main fishing bot (default: Lumbridge Swamp, exact spot anchors, optional shrimp dropping on full inventory)
- `smart_fishing_bot_live.ts`: Mirror of `smart_fishing_bot.ts`
- `test_client.ts`: Minimal command/state test client
- `interactive_bot.ts`: Interactive REPL client
- `monitor/server.ts`: Local monitor dashboard and bot status API

## Prerequisites

- Bun `1.3+`
- RuneLite client with `botsdk` plugin available
- OSRS account logged in-game

## 1) Start RuneLite with Bot SDK

Launch RuneLite (example local path):

```bash
java -jar /home/skier/Documents/osrs/runelite/runelite-client/build/libs/client-1.12.17-SNAPSHOT-shaded.jar
```

In RuneLite plugin config:

- Enable `Bot SDK`
- Ensure:
- `Include NPCs = true`
- `Include Objects = true`
- `Max NPC Distance` high enough for your route (15+ is typical)

## 2) Start the Monitor (optional)

```bash
cd /home/skier/Documents/osrs/botsdk_client
bun run monitor/server.ts
```

Monitor URLs:

- Dashboard: `http://localhost:8080`
- Bot monitor WS: `ws://localhost:7781`

## 3) Run the Bot

Default run:

```bash
cd /home/skier/Documents/osrs/botsdk_client
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

## Publish to GitHub

`botsdk_client` is currently a plain folder (not yet a git repo). To publish:

1. Initialize and commit:

```bash
cd /home/skier/Documents/osrs/botsdk_client
git init
git add .
git commit -m "Initial botsdk_client bot + monitor setup"
```

2. Authenticate GitHub CLI (required once per machine):

```bash
gh auth login -h github.com
```

3. Create repo and push:

```bash
gh repo create botsdk_client --private --source=. --remote=origin --push
```

If you already have a repo URL:

```bash
git remote add origin <YOUR_GITHUB_REPO_URL>
git branch -M main
git push -u origin main
```
