/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * WebSocket server that broadcasts game state and receives commands from SDK
 * clients
 */
@Slf4j
public class BotSDKWebSocketServer extends WebSocketServer {
    private final ConcurrentHashMap<WebSocket, String> clients = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().create();
    private Consumer<JsonObject> commandHandler;
    private volatile boolean running = false;

    public BotSDKWebSocketServer(int port) {
        super(new InetSocketAddress(port));
        setReuseAddr(true);
    }

    /**
     * Set the command handler for incoming messages
     */
    public void setCommandHandler(Consumer<JsonObject> handler) {
        this.commandHandler = handler;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String clientId = conn.getRemoteSocketAddress().toString();
        clients.put(conn, clientId);
        log.info("Bot SDK client connected: {}", clientId);

        // Send welcome message
        JsonObject welcome = new JsonObject();
        welcome.addProperty("type", "connected");
        welcome.addProperty("message", "Connected to RuneLite Bot SDK");
        conn.send(gson.toJson(welcome));
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String clientId = clients.remove(conn);
        log.info("Bot SDK client disconnected: {} (code={}, reason={})", clientId, code, reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JsonObject json = new JsonParser().parse(message).getAsJsonObject();
            log.debug("Received command from {}: {}", clients.get(conn), json.get("type"));

            if (commandHandler != null) {
                commandHandler.accept(json);
            }

            // Send acknowledgment
            JsonObject ack = new JsonObject();
            ack.addProperty("type", "ack");
            ack.addProperty("success", true);
            conn.send(gson.toJson(ack));
        } catch (Exception e) {
            log.error("Error processing command: {}", e.getMessage());

            JsonObject error = new JsonObject();
            error.addProperty("type", "error");
            error.addProperty("message", e.getMessage());
            conn.send(gson.toJson(error));
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn != null) {
            log.error("WebSocket error for {}: {}", clients.get(conn), ex.getMessage());
        } else {
            log.error("WebSocket server error: {}", ex.getMessage());
        }
    }

    @Override
    public void onStart() {
        running = true;
        log.info("Bot SDK WebSocket server started on port {}", getPort());
    }

    /**
     * Broadcast state JSON to all connected clients
     */
    public void broadcastState(String stateJson) {
        if (!running || clients.isEmpty()) {
            return;
        }

        // Wrap in state message
        JsonObject message = new JsonObject();
        message.addProperty("type", "state");
        message.add("data", new JsonParser().parse(stateJson));
        String messageJson = gson.toJson(message);

        for (WebSocket client : clients.keySet()) {
            if (client.isOpen()) {
                try {
                    client.send(messageJson);
                } catch (Exception e) {
                    log.warn("Failed to send state to {}: {}", clients.get(client), e.getMessage());
                }
            }
        }
    }

    /**
     * Check if server is running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get number of connected clients
     */
    public int getClientCount() {
        return clients.size();
    }

    /**
     * Stop the server
     */
    public void shutdown() {
        running = false;
        try {
            stop(1000);
            log.info("Bot SDK WebSocket server stopped");
        } catch (InterruptedException e) {
            log.warn("Interrupted while stopping WebSocket server");
            Thread.currentThread().interrupt();
        }
    }
}
