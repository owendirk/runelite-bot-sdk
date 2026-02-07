/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Canvas;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * Handles incoming commands from SDK clients and executes them on the game
 * client.
 * Uses client.menuAction() for direct interaction invocation.
 */
@Slf4j
@Singleton
public class CommandHandler {
    private final Client client;
    private final ClientThread clientThread;

    @Inject
    public CommandHandler(Client client, ClientThread clientThread) {
        this.client = client;
        this.clientThread = clientThread;
    }

    /**
     * Dispatch a virtual mouse click to the canvas (doesn't move real cursor)
     */
    private void virtualClick(int canvasX, int canvasY, boolean rightClick) {
        Canvas canvas = client.getCanvas();
        if (canvas == null) {
            log.warn("Cannot click: canvas is null");
            return;
        }

        int button = rightClick ? MouseEvent.BUTTON3 : MouseEvent.BUTTON1;
        int mask = rightClick ? MouseEvent.BUTTON3_DOWN_MASK : MouseEvent.BUTTON1_DOWN_MASK;

        log.info("Virtual {} click at canvas ({}, {})", rightClick ? "right" : "left", canvasX, canvasY);

        try {
            long time = System.currentTimeMillis();

            // Dispatch mouse events directly to canvas
            MouseEvent pressed = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    time, mask, canvasX, canvasY, 1, false, button);
            MouseEvent released = new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                    time + 50, mask, canvasX, canvasY, 1, false, button);
            MouseEvent clicked = new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED,
                    time + 50, mask, canvasX, canvasY, 1, false, button);

            canvas.dispatchEvent(pressed);
            canvas.dispatchEvent(released);
            canvas.dispatchEvent(clicked);
        } catch (Exception e) {
            log.error("Error in virtual click: {}", e.getMessage());
        }
    }

    /**
     * Dispatch a virtual left click
     */
    private void virtualLeftClick(int canvasX, int canvasY) {
        virtualClick(canvasX, canvasY, false);
    }

    /**
     * Dispatch a virtual right click
     */
    private void virtualRightClick(int canvasX, int canvasY) {
        virtualClick(canvasX, canvasY, true);
    }

    /**
     * Get canvas point for a local tile
     */
    private net.runelite.api.Point getCanvasPointForTile(int sceneX, int sceneY) {
        LocalPoint localPoint = LocalPoint.fromScene(sceneX, sceneY);
        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null) {
            return null;
        }
        return Perspective.localToCanvas(client, localPoint, worldView.getPlane());
    }

    /**
     * Process incoming command from WebSocket
     */
    public void handleCommand(JsonObject command) {
        String type = command.get("type").getAsString();
        log.debug("Handling command: {}", type);

        // Execute on client thread for safety
        clientThread.invoke(() -> {
            try {
                switch (type) {
                    case "walkTo":
                        handleWalkTo(command);
                        break;
                    case "interactNpc":
                        handleInteractNpc(command);
                        break;
                    case "interactLoc":
                        handleInteractLoc(command);
                        break;
                    case "useInventoryItem":
                        handleUseItem(command);
                        break;
                    case "clickDialog":
                        handleClickDialog(command);
                        break;
                    case "clickComponent":
                        handleClickComponent(command);
                        break;
                    case "talkToNpc":
                        handleTalkToNpc(command);
                        break;
                    case "pickupItem":
                        handlePickupItem(command);
                        break;
                    case "dropItem":
                        handleDropItem(command);
                        break;
                    case "sendKey":
                        handleSendKey(command);
                        break;
                    case "typeText":
                        handleTypeText(command);
                        break;
                    case "continueDialog":
                        handleContinueDialog(command);
                        break;
                    case "bankDeposit":
                        handleBankDeposit(command);
                        break;
                    case "bankDepositAll":
                        handleBankDepositAll(command);
                        break;
                    case "climbUp":
                        handleClimbUp(command);
                        break;
                    case "climbDown":
                        handleClimbDown(command);
                        break;
                    case "openDoor":
                        handleOpenDoor(command);
                        break;
                    default:
                        log.warn("Unknown command type: {}", type);
                }
            } catch (Exception e) {
                log.error("Error executing command {}: {}", type, e.getMessage(), e);
            }
        });
    }

    /**
     * Walk to coordinates using mouse click
     */
    private void handleWalkTo(JsonObject command) {
        int worldX = command.get("x").getAsInt();
        int worldZ = command.get("z").getAsInt();

        // Convert world to scene coordinates
        int sceneX = worldX - client.getBaseX();
        int sceneY = worldZ - client.getBaseY();

        log.info("Walking to world ({}, {}) / scene ({}, {})", worldX, worldZ, sceneX, sceneY);

        // Check if tile is in scene
        if (sceneX < 0 || sceneX >= 104 || sceneY < 0 || sceneY >= 104) {
            log.warn("Target tile is outside scene bounds, walking towards it");
            // Walk towards the target by clicking on the edge of the scene
            sceneX = Math.max(0, Math.min(103, sceneX));
            sceneY = Math.max(0, Math.min(103, sceneY));
        }

        // Get canvas position for the tile
        net.runelite.api.Point canvasPoint = getCanvasPointForTile(sceneX, sceneY);
        if (canvasPoint != null) {
            log.info("Clicking on tile at canvas ({}, {})", canvasPoint.getX(), canvasPoint.getY());
            virtualLeftClick(canvasPoint.getX(), canvasPoint.getY());
        } else {
            log.warn("Could not get canvas position for tile, using menuAction fallback");
            client.menuAction(
                    sceneX,
                    sceneY,
                    MenuAction.WALK,
                    0,
                    -1,
                    "Walk here",
                    ""
            );
        }
    }

    /**
     * Interact with NPC using mouse click
     */
    private void handleInteractNpc(JsonObject command) {
        int npcIndex = command.get("npcIndex").getAsInt();
        int optionIndex = command.has("optionIndex") ? command.get("optionIndex").getAsInt() : 1;

        NPC npc = findNpcByIndex(npcIndex);
        if (npc == null) {
            log.warn("NPC not found with index: {}", npcIndex);
            return;
        }

        NPCComposition comp = npc.getComposition();
        if (comp == null) {
            log.warn("NPC composition is null for index: {}", npcIndex);
            return;
        }

        // Map option index to MenuAction
        MenuAction action = getNpcMenuAction(optionIndex);
        String[] actions = comp.getActions();
        String option = getActionText(actions, optionIndex);

        log.info("Interacting with NPC {} ({}) with option {} ({}) via {}",
                comp.getName(), npcIndex, optionIndex, option, action);

        // For option 1, use left-click; for other options, use menuAction directly
        if (optionIndex == 1) {
            // Try to get NPC's canvas location and left-click on it
            Shape hull = npc.getConvexHull();
            if (hull != null) {
                Rectangle bounds = hull.getBounds();
                int centerX = (int) bounds.getCenterX();
                int centerY = (int) bounds.getCenterY();
                log.info("NPC hull center at canvas ({}, {})", centerX, centerY);
                virtualLeftClick(centerX, centerY);
                return;
            }

            // Fallback: Try to get canvas position from local point
            LocalPoint localPoint = npc.getLocalLocation();
            if (localPoint != null) {
                WorldView wv = client.getTopLevelWorldView();
                net.runelite.api.Point canvasPoint = Perspective.localToCanvas(client, localPoint, wv != null ? wv.getPlane() : 0);
                if (canvasPoint != null) {
                    log.info("NPC at canvas ({}, {})", canvasPoint.getX(), canvasPoint.getY());
                    virtualLeftClick(canvasPoint.getX(), canvasPoint.getY());
                    return;
                }
            }
        }

        // For non-first options or fallback: use menuAction directly
        log.info("Using menuAction for NPC interaction");
        client.menuAction(
                0,
                0,
                action,
                npc.getIndex(),
                -1,
                option,
                "<col=ffff00>" + comp.getName() + "<col=ff00>"
        );
    }

    /**
     * Talk to NPC (uses first option which is typically "Talk-to")
     */
    private void handleTalkToNpc(JsonObject command) {
        int npcIndex = command.get("npcIndex").getAsInt();

        NPC npc = findNpcByIndex(npcIndex);
        if (npc == null) {
            log.warn("NPC not found with index: {}", npcIndex);
            return;
        }

        NPCComposition comp = npc.getComposition();
        String name = comp != null ? comp.getName() : "NPC";

        log.info("Talking to NPC {} ({})", name, npcIndex);

        client.menuAction(
                0,
                0,
                MenuAction.NPC_FIRST_OPTION,
                npc.getIndex(),
                -1,
                "Talk-to",
                "<col=ffff00>" + name + "<col=ff00>");
    }

    /**
     * Interact with game object using mouse click
     */
    private void handleInteractLoc(JsonObject command) {
        int worldX = command.get("x").getAsInt();
        int worldZ = command.get("z").getAsInt();
        int locId = command.get("locId").getAsInt();
        int optionIndex = command.has("optionIndex") ? command.get("optionIndex").getAsInt() : 1;

        // Convert to scene coordinates
        int sceneX = worldX - client.getBaseX();
        int sceneY = worldZ - client.getBaseY();

        ObjectComposition comp = client.getObjectDefinition(locId);
        if (comp == null) {
            log.warn("Object not found with id: {}", locId);
            return;
        }

        // Map option index to MenuAction
        MenuAction action = getObjectMenuAction(optionIndex);
        String[] actions = comp.getActions();
        String option = getActionText(actions, optionIndex);

        log.info("Interacting with {} ({}) at scene ({}, {}) with option {} ({}) via {}",
                comp.getName(), locId, sceneX, sceneY, optionIndex, option, action);

        // For option 1, try virtual left-click; otherwise use menuAction
        if (optionIndex == 1) {
            net.runelite.api.Point canvasPoint = getCanvasPointForTile(sceneX, sceneY);
            if (canvasPoint != null) {
                log.info("Object at canvas ({}, {})", canvasPoint.getX(), canvasPoint.getY());
                virtualLeftClick(canvasPoint.getX(), canvasPoint.getY());
                return;
            }
        }

        // For non-first options or fallback: use menuAction directly
        log.info("Using menuAction for object interaction (option {})", optionIndex);
        client.menuAction(
                sceneX,
                sceneY,
                action,
                locId,
                -1,
                option,
                "<col=ffff>" + comp.getName() + "<col=ff00>"
        );
    }

    /**
     * Pick up ground item using menuAction
     */
    private void handlePickupItem(JsonObject command) {
        int worldX = command.get("x").getAsInt();
        int worldZ = command.get("z").getAsInt();
        int itemId = command.get("itemId").getAsInt();

        // Convert to scene coordinates
        int sceneX = worldX - client.getBaseX();
        int sceneY = worldZ - client.getBaseY();

        log.info("Picking up item {} at scene ({}, {})", itemId, sceneX, sceneY);

        // GROUND_ITEM_THIRD_OPTION is typically "Take"
        client.menuAction(
                sceneX,
                sceneY,
                MenuAction.GROUND_ITEM_THIRD_OPTION,
                itemId,
                -1,
                "Take",
                "");
    }

    /**
     * Use inventory item via widget action
     */
    private void handleUseItem(JsonObject command) {
        int slot = command.get("slot").getAsInt();
        int optionIndex = command.has("optionIndex") ? command.get("optionIndex").getAsInt() : 1;

        // Inventory widget: group 149, child 0
        // Each slot has widget child ID = slot
        int widgetId = (149 << 16) | 0; // Packed widget ID for inventory

        log.info("Using item in slot {} with option {}", slot, optionIndex);

        client.menuAction(
                slot, // p0 - slot index
                widgetId, // p1 - widget ID
                MenuAction.CC_OP, // action type
                optionIndex, // id - option index
                -1, // itemId
                "", // option
                "" // target
        );
    }

    /**
     * Drop inventory item (option index 5 is usually "Drop")
     */
    private void handleDropItem(JsonObject command) {
        int slot = command.get("slot").getAsInt();

        int widgetId = (149 << 16) | 0;

        log.info("Dropping item in slot {}", slot);

        client.menuAction(
                slot,
                widgetId,
                MenuAction.CC_OP,
                5, // Drop is typically option 5
                -1,
                "Drop",
                "");
    }

    /**
     * Click dialog option
     */
    private void handleClickDialog(JsonObject command) {
        int optionIndex = command.get("optionIndex").getAsInt();

        // Dialog options widget: group 219, child 1
        Widget optionWidget = client.getWidget(219, 1);
        if (optionWidget != null && !optionWidget.isHidden()) {
            Widget[] children = optionWidget.getChildren();
            if (children != null && optionIndex < children.length) {
                Widget option = children[optionIndex];
                if (option != null) {
                    int widgetId = option.getId();
                    log.info("Clicking dialog option {} (widget {})", optionIndex, widgetId);

                    client.menuAction(
                            1,
                            widgetId,
                            MenuAction.WIDGET_CONTINUE,
                            -1,
                            -1,
                            "Continue",
                            "");
                    return;
                }
            }
        }

        log.warn("Dialog option {} not found", optionIndex);
    }

    /**
     * Click Continue in dialog (spacebar equivalent)
     */
    private void handleContinueDialog(JsonObject command) {
        // Try common dialog continue widgets
        int[][] continueWidgets = {
                { 229, 2 }, // NPC dialog continue
                { 217, 0 }, // Player dialog continue
                { 193, 0 }, // Level up continue
                { 233, 3 }, // Sprite dialog continue
        };

        for (int[] widgetIds : continueWidgets) {
            Widget widget = client.getWidget(widgetIds[0], widgetIds[1]);
            if (widget != null && !widget.isHidden()) {
                log.info("Clicking continue on widget ({}, {})", widgetIds[0], widgetIds[1]);
                client.menuAction(
                        0,
                        widget.getId(),
                        MenuAction.WIDGET_CONTINUE,
                        -1,
                        -1,
                        "Continue",
                        "");
                return;
            }
        }

        log.warn("No continue dialog widget found");
    }

    /**
     * Click widget component by ID
     */
    private void handleClickComponent(JsonObject command) {
        int componentId = command.get("componentId").getAsInt();

        log.info("Clicking component {}", componentId);

        client.menuAction(
                1,
                componentId,
                MenuAction.CC_OP,
                1,
                -1,
                "",
                "");
    }

    /**
     * Send a keyboard key event
     */
    private void handleSendKey(JsonObject command) {
        int keyCode = command.get("keyCode").getAsInt();

        log.info("Sending key: {}", keyCode);

        java.awt.Canvas canvas = client.getCanvas();
        if (canvas != null) {
            KeyEvent keyPressed = new KeyEvent(canvas, KeyEvent.KEY_PRESSED,
                    System.currentTimeMillis(), 0, keyCode, KeyEvent.CHAR_UNDEFINED);
            KeyEvent keyReleased = new KeyEvent(canvas, KeyEvent.KEY_RELEASED,
                    System.currentTimeMillis(), 0, keyCode, KeyEvent.CHAR_UNDEFINED);

            canvas.dispatchEvent(keyPressed);
            canvas.dispatchEvent(keyReleased);
        }
    }

    /**
     * Type text (sends character events)
     */
    private void handleTypeText(JsonObject command) {
        String text = command.get("text").getAsString();

        log.info("Typing text: {}", text);

        java.awt.Canvas canvas = client.getCanvas();
        if (canvas != null) {
            for (char c : text.toCharArray()) {
                KeyEvent keyTyped = new KeyEvent(canvas, KeyEvent.KEY_TYPED,
                        System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, c);
                canvas.dispatchEvent(keyTyped);
            }
        }
    }

    /**
     * Deposit item from inventory into bank
     * Requires bank to be open
     */
    private void handleBankDeposit(JsonObject command) {
        int slot = command.get("slot").getAsInt();
        int amount = command.has("amount") ? command.get("amount").getAsInt() : 1;

        // Bank inventory widget: 15 (bank group), 3 (inventory container)
        int widgetId = (12 << 16) | 13; // Bank inventory widget

        log.info("Depositing item from slot {} (amount: {})", slot, amount);

        // Option 2 = Deposit-1, Option 3 = Deposit-5, Option 4 = Deposit-10, Option 5 = Deposit-X, Option 8 = Deposit-All
        int option = 2; // Default: Deposit-1
        if (amount >= 28 || amount == -1) {
            option = 8; // Deposit-All
        } else if (amount >= 10) {
            option = 4; // Deposit-10
        } else if (amount >= 5) {
            option = 3; // Deposit-5
        }

        client.menuAction(
                slot,
                widgetId,
                MenuAction.CC_OP,
                option,
                -1,
                "Deposit",
                "");
    }

    /**
     * Click the "Deposit inventory" button in bank
     */
    private void handleBankDepositAll(JsonObject command) {
        // Bank deposit inventory button: group 12, child 42
        Widget depositBtn = client.getWidget(12, 42);
        if (depositBtn != null && !depositBtn.isHidden()) {
            log.info("Clicking deposit inventory button");
            client.menuAction(
                    1,
                    depositBtn.getId(),
                    MenuAction.CC_OP,
                    1,
                    -1,
                    "Deposit inventory",
                    "");
        } else {
            log.warn("Deposit inventory button not found - is bank open?");
        }
    }

    /**
     * Find and climb up stairs/ladder nearby
     */
    private void handleClimbUp(JsonObject command) {
        int targetX = command.has("x") ? command.get("x").getAsInt() : -1;
        int targetZ = command.has("z") ? command.get("z").getAsInt() : -1;
        int targetId = command.has("locId") ? command.get("locId").getAsInt() : -1;

        log.info("Looking for stairs/ladder to climb up...");

        // Search nearby objects for climbable ones
        NearbyClimbable climbable = findClimbable(targetX, targetZ, targetId, true);
        if (climbable != null) {
            int sceneX = climbable.worldX - client.getBaseX();
            int sceneY = climbable.worldZ - client.getBaseY();

            log.info("Climbing up {} at ({}, {})", climbable.name, climbable.worldX, climbable.worldZ);
            client.menuAction(
                    sceneX,
                    sceneY,
                    climbable.action,
                    climbable.id,
                    -1,
                    climbable.option,
                    "<col=ffff>" + climbable.name + "<col=ff00>"
            );
        } else {
            log.warn("No stairs/ladder found to climb up");
        }
    }

    /**
     * Find and climb down stairs/ladder nearby
     */
    private void handleClimbDown(JsonObject command) {
        int targetX = command.has("x") ? command.get("x").getAsInt() : -1;
        int targetZ = command.has("z") ? command.get("z").getAsInt() : -1;
        int targetId = command.has("locId") ? command.get("locId").getAsInt() : -1;

        log.info("Looking for stairs/ladder to climb down...");

        NearbyClimbable climbable = findClimbable(targetX, targetZ, targetId, false);
        if (climbable != null) {
            int sceneX = climbable.worldX - client.getBaseX();
            int sceneY = climbable.worldZ - client.getBaseY();

            log.info("Climbing down {} at ({}, {})", climbable.name, climbable.worldX, climbable.worldZ);
            client.menuAction(
                    sceneX,
                    sceneY,
                    climbable.action,
                    climbable.id,
                    -1,
                    climbable.option,
                    "<col=ffff>" + climbable.name + "<col=ff00>"
            );
        } else {
            log.warn("No stairs/ladder found to climb down");
        }
    }

    /**
     * Find and open a door nearby
     */
    private void handleOpenDoor(JsonObject command) {
        int targetX = command.has("x") ? command.get("x").getAsInt() : -1;
        int targetZ = command.has("z") ? command.get("z").getAsInt() : -1;

        log.info("Looking for door to open...");

        // Search nearby for doors
        Scene scene = client.getScene();
        Tile[][][] tiles = scene.getTiles();
        int plane = client.getPlane();
        Player player = client.getLocalPlayer();
        if (player == null) return;

        WorldPoint playerLoc = player.getWorldLocation();
        int baseX = client.getBaseX();
        int baseY = client.getBaseY();

        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                int sceneX = playerLoc.getX() - baseX + dx;
                int sceneY = playerLoc.getY() - baseY + dy;

                if (sceneX < 0 || sceneX >= 104 || sceneY < 0 || sceneY >= 104) continue;

                Tile tile = tiles[plane][sceneX][sceneY];
                if (tile == null) continue;

                // Check wall objects for doors
                WallObject wall = tile.getWallObject();
                if (wall != null) {
                    ObjectComposition comp = client.getObjectDefinition(wall.getId());
                    if (comp != null && comp.getName() != null) {
                        String name = comp.getName().toLowerCase();
                        String[] actions = comp.getActions();
                        if ((name.contains("door") || name.contains("gate")) && actions != null) {
                            for (int i = 0; i < actions.length; i++) {
                                if (actions[i] != null && actions[i].equalsIgnoreCase("Open")) {
                                    WorldPoint objLoc = wall.getWorldLocation();
                                    if (targetX < 0 || (objLoc.getX() == targetX && objLoc.getY() == targetZ)) {
                                        log.info("Opening {} at ({}, {})", comp.getName(), objLoc.getX(), objLoc.getY());
                                        client.menuAction(
                                                sceneX,
                                                sceneY,
                                                getObjectMenuAction(i + 1),
                                                wall.getId(),
                                                -1,
                                                "Open",
                                                "<col=ffff>" + comp.getName() + "<col=ff00>"
                                        );
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        log.warn("No door found to open");
    }

    // Helper class for climbable objects
    private static class NearbyClimbable {
        int id;
        String name;
        int worldX;
        int worldZ;
        MenuAction action;
        String option;
    }

    /**
     * Find a climbable object (stairs, ladder) nearby
     */
    private NearbyClimbable findClimbable(int targetX, int targetZ, int targetId, boolean goingUp) {
        Scene scene = client.getScene();
        Tile[][][] tiles = scene.getTiles();
        int plane = client.getPlane();
        Player player = client.getLocalPlayer();
        if (player == null) return null;

        WorldPoint playerLoc = player.getWorldLocation();
        int baseX = client.getBaseX();
        int baseY = client.getBaseY();

        String[] upKeywords = {"climb-up", "climb up", "go-up", "go up", "ascend"};
        String[] downKeywords = {"climb-down", "climb down", "go-down", "go down", "descend"};
        String[] keywords = goingUp ? upKeywords : downKeywords;

        for (int dx = -10; dx <= 10; dx++) {
            for (int dy = -10; dy <= 10; dy++) {
                int sceneX = playerLoc.getX() - baseX + dx;
                int sceneY = playerLoc.getY() - baseY + dy;

                if (sceneX < 0 || sceneX >= 104 || sceneY < 0 || sceneY >= 104) continue;

                Tile tile = tiles[plane][sceneX][sceneY];
                if (tile == null) continue;

                // Check game objects
                GameObject[] gameObjects = tile.getGameObjects();
                if (gameObjects != null) {
                    for (GameObject obj : gameObjects) {
                        if (obj == null) continue;
                        NearbyClimbable result = checkObjectForClimb(obj, targetX, targetZ, targetId, keywords);
                        if (result != null) return result;
                    }
                }

                // Check wall objects
                WallObject wall = tile.getWallObject();
                if (wall != null) {
                    NearbyClimbable result = checkObjectForClimb(wall, targetX, targetZ, targetId, keywords);
                    if (result != null) return result;
                }
            }
        }

        return null;
    }

    private NearbyClimbable checkObjectForClimb(TileObject obj, int targetX, int targetZ, int targetId, String[] keywords) {
        ObjectComposition comp = client.getObjectDefinition(obj.getId());
        if (comp == null) return null;

        String name = comp.getName();
        if (name == null || name.equals("null")) return null;
        String nameLower = name.toLowerCase();

        // Check if it's a staircase, ladder, etc.
        if (!nameLower.contains("stair") && !nameLower.contains("ladder") &&
            !nameLower.contains("steps") && !nameLower.contains("trapdoor")) {
            return null;
        }

        String[] actions = comp.getActions();
        if (actions == null) return null;

        WorldPoint objLoc = obj.getWorldLocation();

        // Check if matches target (if specified)
        if (targetId > 0 && obj.getId() != targetId) return null;
        if (targetX > 0 && objLoc.getX() != targetX) return null;
        if (targetZ > 0 && objLoc.getY() != targetZ) return null;

        // Find the right action
        for (int i = 0; i < actions.length; i++) {
            if (actions[i] == null) continue;
            String actionLower = actions[i].toLowerCase();

            for (String keyword : keywords) {
                if (actionLower.contains(keyword) || actionLower.equals("climb")) {
                    NearbyClimbable result = new NearbyClimbable();
                    result.id = obj.getId();
                    result.name = name;
                    result.worldX = objLoc.getX();
                    result.worldZ = objLoc.getY();
                    result.action = getObjectMenuAction(i + 1);
                    result.option = actions[i];
                    return result;
                }
            }
        }

        return null;
    }

    // --- Helper methods ---

    private NPC findNpcByIndex(int index) {
        for (NPC npc : client.getNpcs()) {
            if (npc != null && npc.getIndex() == index) {
                return npc;
            }
        }
        return null;
    }

    private MenuAction getNpcMenuAction(int optionIndex) {
        switch (optionIndex) {
            case 1:
                return MenuAction.NPC_FIRST_OPTION;
            case 2:
                return MenuAction.NPC_SECOND_OPTION;
            case 3:
                return MenuAction.NPC_THIRD_OPTION;
            case 4:
                return MenuAction.NPC_FOURTH_OPTION;
            case 5:
                return MenuAction.NPC_FIFTH_OPTION;
            default:
                return MenuAction.NPC_FIRST_OPTION;
        }
    }

    private MenuAction getObjectMenuAction(int optionIndex) {
        switch (optionIndex) {
            case 1:
                return MenuAction.GAME_OBJECT_FIRST_OPTION;
            case 2:
                return MenuAction.GAME_OBJECT_SECOND_OPTION;
            case 3:
                return MenuAction.GAME_OBJECT_THIRD_OPTION;
            case 4:
                return MenuAction.GAME_OBJECT_FOURTH_OPTION;
            case 5:
                return MenuAction.GAME_OBJECT_FIFTH_OPTION;
            default:
                return MenuAction.GAME_OBJECT_FIRST_OPTION;
        }
    }

    private String getActionText(String[] actions, int optionIndex) {
        if (actions == null || optionIndex < 1 || optionIndex > actions.length) {
            return "Examine";
        }
        String action = actions[optionIndex - 1];
        return action != null ? action : "Examine";
    }
}
