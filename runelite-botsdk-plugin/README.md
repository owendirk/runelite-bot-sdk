# RuneLite Bot SDK Plugin (Standalone Project)

Standalone Java project containing the `botsdk` RuneLite plugin sources.

## What this is

This repo contains the plugin code used to expose game state over WebSocket and execute automation commands.

## Source Path

- `src/main/java/net/runelite/client/plugins/botsdk/`

## Build

```bash
./gradlew build
```

Built jar output:

- `build/libs/botsdk-plugin-1.0.0.jar`

## Related Repos

- Bot client scripts + monitor: `https://github.com/owendirk/runelite-bot-sdk`
- RuneLite fork branch with integrated plugin: `https://github.com/owendirk/runelite` (`botsdk-integration`)
