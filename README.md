# runelite-bot-sdk Monorepo

Single repository containing:

- `smart_fishing_bot.ts` + monitor server (Bun TypeScript bot client)
- `runelite/` (RuneLite fork with integrated Bot SDK plugin work)
- `runelite-botsdk-plugin/` (standalone plugin project)
- `rs-sdk/` (RS-SDK workspace copy)

## Repo Layout

- `smart_fishing_bot.ts`: main fishing bot (defaults to Lumbridge Swamp)
- `monitor/server.ts`: dashboard + bot status server (`http://localhost:8080`)
- `runelite/`: build and run RuneLite client from source
- `runelite-botsdk-plugin/`: standalone Java plugin sources
- `rs-sdk/`: optional SDK tooling and experiments

## Quick Start (Clone And Run)

```bash
git clone https://github.com/owendirk/runelite-bot-sdk.git
cd runelite-bot-sdk
```

### 1) Build RuneLite

```bash
cd runelite
./gradlew :runelite-client:shadowJar
```

### 2) Launch RuneLite

```bash
java -jar runelite-client/build/libs/client-*-SNAPSHOT-shaded.jar
```

In RuneLite plugin settings:

- Enable `Bot SDK`
- Set `Include NPCs = true`
- Set `Include Objects = true`

### 3) Start Monitor (optional)

Open a second terminal in repo root:

```bash
cd runelite-bot-sdk
bun run monitor/server.ts
```

### 4) Run Bot

Open another terminal in repo root:

```bash
cd runelite-bot-sdk
bun run smart_fishing_bot.ts
```

## Bot Defaults

- Location: Lumbridge Swamp
- Exact anchored fishing spot logic
- Drops shrimp when inventory is full
- Keeps required fishing tools in inventory

## Useful Env Vars

```bash
BOT_DEBUG=1 BOT_DROP_SHRIMP_WHEN_FULL=1 bun run smart_fishing_bot.ts
```

- `BOT_DEBUG=0|1`
- `BOT_DEBUG_SNAPSHOT_TICKS=10`
- `BOT_STUCK_TICKS=25`
- `BOT_FISHING_RETRY_MS=7000`
- `BOT_MAX_FISH_SPOT_DISTANCE=2`
- `BOT_STRICT_SPOT_ONLY=0|1`
- `BOT_FISH_TILE_RADIUS=1`
- `BOT_DROP_SHRIMP_WHEN_FULL=0|1`

## Notes

- This repo is intentionally monorepo-style so one clone contains everything needed.
- `runelite/` and `rs-sdk/` are included as source copies in this repository.
