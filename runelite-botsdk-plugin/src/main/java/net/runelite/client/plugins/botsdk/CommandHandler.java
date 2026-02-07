/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.event.KeyEvent;

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
                    default:
                        log.warn("Unknown command type: {}", type);
                }
            } catch (Exception e) {
                log.error("Error executing command {}: {}", type, e.getMessage(), e);
            }
        });
    }

    /**
     * Walk to coordinates using menuAction
     */
    private void handleWalkTo(JsonObject command) {
        int worldX = command.get("x").getAsInt();
        int worldZ = command.get("z").getAsInt();

        // Convert world to scene coordinates
        int sceneX = worldX - client.getBaseX();
        int sceneY = worldZ - client.getBaseY();

        log.info("Walking to world ({}, {}) / scene ({}, {})", worldX, worldZ, sceneX, sceneY);

        client.menuAction(
                sceneX, // p0 - scene X
                sceneY, // p1 - scene Y
                MenuAction.WALK, // action type
                0, // id
                -1, // itemId
                "Walk here", // option
                "" // target
        );
    }

    /**
     * Interact with NPC using menuAction
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

        client.menuAction(
                0, // p0
                0, // p1
                action, // action type
                npc.getIndex(), // id - NPC index
                -1, // itemId
                option, // option text
                "<col=ffff00>" + comp.getName() + "<col=ff00>" // colored target
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
     * Interact with game object using menuAction
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

        log.info("Interacting with {} ({}) at ({}, {}) with option {} ({}) via {}",
                comp.getName(), locId, sceneX, sceneY, optionIndex, option, action);

        client.menuAction(
                sceneX, // p0 - scene X
                sceneY, // p1 - scene Y
                action, // action type
                locId, // id - object ID
                -1, // itemId
                option, // option text
                "<col=ffff>" + comp.getName() + "<col=ff00>" // colored target
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
