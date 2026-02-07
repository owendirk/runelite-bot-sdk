/*
 * Copyright (c) 2024
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.botsdk;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.botsdk.model.BotWorldState;

import javax.inject.Inject;

/**
 * Bot SDK Plugin - Exposes game state and accepts automation commands via
 * WebSocket
 * 
 * This plugin enables integration with the RS-SDK TypeScript framework for bot
 * development.
 * 
 * WARNING: Using automation on official OSRS servers violates Jagex Terms of
 * Service
 * and will result in account bans. This is for educational purposes only.
 */
@Slf4j
@PluginDescriptor(name = "Bot SDK", description = "Exposes game state and accepts automation commands via WebSocket", tags = {
        "bot", "sdk", "automation", "developer" }, enabledByDefault = true)
public class BotSDKPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private BotSDKConfig config;

    @Inject
    private ItemManager itemManager;

    private StateCollector stateCollector;
    private CommandHandler commandHandler;
    private BotSDKWebSocketServer wsServer;

    private int ticksSinceLastBroadcast = 0;

    @Provides
    BotSDKConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BotSDKConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        log.info("Bot SDK Plugin starting up...");

        // Create state collector
        stateCollector = new StateCollector(client, itemManager, config);

        // Create command handler
        commandHandler = injector.getInstance(CommandHandler.class);

        // Start WebSocket server
        int port = config.port();
        wsServer = new BotSDKWebSocketServer(port);
        wsServer.setCommandHandler(commandHandler::handleCommand);

        try {
            wsServer.start();
            log.info("Bot SDK WebSocket server started on port {}", port);
        } catch (Exception e) {
            log.error("Failed to start WebSocket server on port {}: {}", port, e.getMessage());
        }
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("Bot SDK Plugin shutting down...");

        if (wsServer != null) {
            wsServer.shutdown();
            wsServer = null;
        }

        stateCollector = null;
        commandHandler = null;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (wsServer == null || !wsServer.isRunning()) {
            return;
        }

        // Only broadcast at configured interval
        ticksSinceLastBroadcast++;
        if (ticksSinceLastBroadcast < config.broadcastInterval()) {
            return;
        }
        ticksSinceLastBroadcast = 0;

        // Collect and broadcast state
        try {
            BotWorldState state = stateCollector.collectState();
            String json = stateCollector.toJson(state);
            wsServer.broadcastState(json);

            if (log.isTraceEnabled()) {
                log.trace("Broadcast state: tick={}, players={}, npcs={}, locs={}",
                        state.getTick(),
                        state.getNearbyPlayers() != null ? state.getNearbyPlayers().size() : 0,
                        state.getNearbyNpcs() != null ? state.getNearbyNpcs().size() : 0,
                        state.getNearbyLocs() != null ? state.getNearbyLocs().size() : 0);
            }
        } catch (Exception e) {
            log.error("Error collecting/broadcasting state: {}", e.getMessage());
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            log.debug("Player logged in, SDK will start broadcasting state");
        } else if (event.getGameState() == GameState.LOGIN_SCREEN) {
            log.debug("Player logged out");
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        // Track when local player takes damage
        if (event.getActor() == client.getLocalPlayer() && stateCollector != null) {
            stateCollector.onDamageTaken();
        }
    }
}
