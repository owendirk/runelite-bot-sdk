/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.botsdk.model.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Collects game state from RuneLite API and converts to RS-SDK compatible
 * format
 */
@Slf4j
@Singleton
public class StateCollector {
    private final Client client;
    private final ItemManager itemManager;
    private final BotSDKConfig config;
    private final Gson gson;

    private int lastDamageTick = -1;

    @Inject
    public StateCollector(Client client, ItemManager itemManager, BotSDKConfig config) {
        this.client = client;
        this.itemManager = itemManager;
        this.config = config;
        this.gson = new GsonBuilder().create();
    }

    /**
     * Collect complete game state
     */
    public BotWorldState collectState() {
        GameState gameState = client.getGameState();
        boolean inGame = gameState == GameState.LOGGED_IN;

        // Get account name if available
        String accountName = null;
        if (inGame && client.getLocalPlayer() != null) {
            accountName = client.getLocalPlayer().getName();
        }

        return BotWorldState.builder()
                .tick(client.getTickCount())
                .inGame(inGame)
                .gameState(gameState.name())
                .currentPlane(inGame ? client.getPlane() : 0)
                .currentWorld(client.getWorld())
                .accountName(accountName)
                .player(inGame ? collectPlayerState() : null)
                .skills(inGame ? collectSkills() : new ArrayList<>())
                .inventory(inGame ? collectInventory() : new ArrayList<>())
                .equipment(inGame ? collectEquipment() : new ArrayList<>())
                .nearbyNpcs(inGame && config.includeNpcs() ? collectNpcs() : new ArrayList<>())
                .nearbyPlayers(inGame ? collectPlayers() : new ArrayList<>())
                .nearbyLocs(inGame && config.includeLocs() ? collectLocs() : new ArrayList<>())
                .groundItems(inGame && config.includeGroundItems() ? collectGroundItems() : new ArrayList<>())
                .gameMessages(new ArrayList<>()) // TODO: implement message tracking
                .dialog(collectDialogState())
                .interfaceState(collectInterfaceState())
                .shop(collectShopState())
                .bank(collectBankState())
                .modalOpen(client.isMenuOpen())
                .modalInterface(-1)
                .build();
    }

    /**
     * Serialize state to JSON
     */
    public String toJson(BotWorldState state) {
        return gson.toJson(state);
    }

    private PlayerState collectPlayerState() {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return null;
        }

        WorldPoint worldLoc = localPlayer.getWorldLocation();
        LocalPoint localLoc = localPlayer.getLocalLocation();

        // Track combat state
        Actor interacting = localPlayer.getInteracting();
        boolean inCombat = interacting != null;
        int targetIndex = -1;
        if (interacting instanceof NPC) {
            targetIndex = ((NPC) interacting).getIndex();
        }

        return PlayerState.builder()
                .name(localPlayer.getName())
                .combatLevel(localPlayer.getCombatLevel())
                .x(localLoc != null ? localLoc.getSceneX() : 0)
                .z(localLoc != null ? localLoc.getSceneY() : 0)
                .worldX(worldLoc.getX())
                .worldZ(worldLoc.getY())
                .level(worldLoc.getPlane())
                .runEnergy(client.getEnergy() / 100)
                .runWeight(client.getWeight())
                .animId(localPlayer.getAnimation())
                .spotanimId(localPlayer.getGraphic())
                .combat(PlayerCombatState.builder()
                        .inCombat(inCombat)
                        .targetIndex(targetIndex)
                        .lastDamageTick(lastDamageTick)
                        .build())
                .build();
    }

    private List<SkillState> collectSkills() {
        List<SkillState> skills = new ArrayList<>();
        for (Skill skill : Skill.values()) {
            if (skill == Skill.OVERALL) {
                continue;
            }
            skills.add(SkillState.builder()
                    .name(skill.getName())
                    .level(client.getBoostedSkillLevel(skill))
                    .baseLevel(client.getRealSkillLevel(skill))
                    .experience(client.getSkillExperience(skill))
                    .build());
        }
        return skills;
    }

    private List<InventoryItem> collectInventory() {
        List<InventoryItem> items = new ArrayList<>();
        ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
        if (container == null) {
            return items;
        }

        Item[] containerItems = container.getItems();
        for (int i = 0; i < containerItems.length; i++) {
            Item item = containerItems[i];
            if (item.getId() == -1 || item.getId() == 0) {
                continue;
            }

            ItemComposition comp = itemManager.getItemComposition(item.getId());
            List<ItemOption> options = new ArrayList<>();
            String[] actions = comp.getInventoryActions();
            if (actions != null) {
                for (int j = 0; j < actions.length; j++) {
                    if (actions[j] != null) {
                        options.add(ItemOption.builder()
                                .text(actions[j])
                                .opIndex(j + 1)
                                .build());
                    }
                }
            }

            items.add(InventoryItem.builder()
                    .slot(i)
                    .id(item.getId())
                    .name(comp.getName())
                    .count(item.getQuantity())
                    .optionsWithIndex(options)
                    .build());
        }
        return items;
    }

    private List<InventoryItem> collectEquipment() {
        List<InventoryItem> items = new ArrayList<>();
        ItemContainer container = client.getItemContainer(InventoryID.EQUIPMENT);
        if (container == null) {
            return items;
        }

        Item[] containerItems = container.getItems();
        for (int i = 0; i < containerItems.length; i++) {
            Item item = containerItems[i];
            if (item.getId() == -1 || item.getId() == 0) {
                continue;
            }

            ItemComposition comp = itemManager.getItemComposition(item.getId());
            items.add(InventoryItem.builder()
                    .slot(i)
                    .id(item.getId())
                    .name(comp.getName())
                    .count(item.getQuantity())
                    .optionsWithIndex(new ArrayList<>())
                    .build());
        }
        return items;
    }

    private List<NearbyNpc> collectNpcs() {
        List<NearbyNpc> npcs = new ArrayList<>();
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return npcs;
        }

        WorldPoint playerLoc = localPlayer.getWorldLocation();
        int maxDist = config.maxNpcDistance();

        for (NPC npc : client.getNpcs()) {
            if (npc == null) {
                continue;
            }

            WorldPoint npcLoc = npc.getWorldLocation();
            int distance = playerLoc.distanceTo(npcLoc);
            if (distance > maxDist) {
                continue;
            }

            NPCComposition comp = npc.getComposition();
            if (comp == null) {
                continue;
            }

            // Get NPC options
            List<NpcOption> optionsWithIndex = new ArrayList<>();
            List<String> options = new ArrayList<>();
            String[] actions = comp.getActions();
            if (actions != null) {
                for (int i = 0; i < actions.length; i++) {
                    if (actions[i] != null) {
                        optionsWithIndex.add(NpcOption.builder()
                                .text(actions[i])
                                .opIndex(i + 1)
                                .build());
                        options.add(actions[i]);
                    }
                }
            }

            // Get health info
            int healthRatio = npc.getHealthRatio();
            int healthScale = npc.getHealthScale();
            Integer healthPercent = null;
            if (healthRatio >= 0 && healthScale > 0) {
                healthPercent = (healthRatio * 100) / healthScale;
            }

            // Check if in combat
            Actor interacting = npc.getInteracting();
            boolean inCombat = interacting != null;
            int targetIndex = -1;
            if (interacting instanceof Player) {
                targetIndex = ((Player) interacting).getId();
            }

            npcs.add(NearbyNpc.builder()
                    .index(npc.getIndex())
                    .name(comp.getName())
                    .combatLevel(comp.getCombatLevel())
                    .x(npcLoc.getX())
                    .z(npcLoc.getY())
                    .distance(distance)
                    .hp(healthRatio)
                    .maxHp(healthScale)
                    .healthPercent(healthPercent)
                    .targetIndex(targetIndex)
                    .inCombat(inCombat)
                    .combatCycle(0)
                    .animId(npc.getAnimation())
                    .spotanimId(npc.getGraphic())
                    .optionsWithIndex(optionsWithIndex)
                    .options(options)
                    .build());
        }

        return npcs;
    }

    private List<NearbyPlayer> collectPlayers() {
        List<NearbyPlayer> players = new ArrayList<>();
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return players;
        }

        WorldPoint playerLoc = localPlayer.getWorldLocation();

        for (Player player : client.getPlayers()) {
            if (player == null || player == localPlayer) {
                continue;
            }

            WorldPoint otherLoc = player.getWorldLocation();
            int distance = playerLoc.distanceTo(otherLoc);

            players.add(NearbyPlayer.builder()
                    .index(player.getId())
                    .name(player.getName())
                    .combatLevel(player.getCombatLevel())
                    .x(otherLoc.getX())
                    .z(otherLoc.getY())
                    .distance(distance)
                    .build());
        }

        return players;
    }

    private List<NearbyLoc> collectLocs() {
        List<NearbyLoc> locs = new ArrayList<>();
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return locs;
        }

        WorldPoint playerLoc = localPlayer.getWorldLocation();
        int maxDist = config.maxLocDistance();
        Scene scene = client.getScene();
        Tile[][][] tiles = scene.getTiles();
        int plane = client.getPlane();

        // Iterate through nearby tiles
        int baseX = client.getBaseX();
        int baseY = client.getBaseY();
        int playerSceneX = playerLoc.getX() - baseX;
        int playerSceneY = playerLoc.getY() - baseY;

        for (int dx = -maxDist; dx <= maxDist; dx++) {
            for (int dy = -maxDist; dy <= maxDist; dy++) {
                int sceneX = playerSceneX + dx;
                int sceneY = playerSceneY + dy;

                if (sceneX < 0 || sceneX >= 104 || sceneY < 0 || sceneY >= 104) {
                    continue;
                }

                Tile tile = tiles[plane][sceneX][sceneY];
                if (tile == null) {
                    continue;
                }

                // Check game objects
                GameObject[] gameObjects = tile.getGameObjects();
                if (gameObjects != null) {
                    for (GameObject obj : gameObjects) {
                        if (obj == null) {
                            continue;
                        }
                        addLocFromObject(locs, obj, playerLoc);
                    }
                }

                // Check wall objects
                WallObject wallObj = tile.getWallObject();
                if (wallObj != null) {
                    addLocFromObject(locs, wallObj, playerLoc);
                }

                // Check decorative objects
                DecorativeObject decObj = tile.getDecorativeObject();
                if (decObj != null) {
                    addLocFromObject(locs, decObj, playerLoc);
                }

                // Check ground objects
                GroundObject groundObj = tile.getGroundObject();
                if (groundObj != null) {
                    addLocFromObject(locs, groundObj, playerLoc);
                }
            }
        }

        return locs;
    }

    private void addLocFromObject(List<NearbyLoc> locs, TileObject obj, WorldPoint playerLoc) {
        ObjectComposition comp = client.getObjectDefinition(obj.getId());
        if (comp == null || comp.getName() == null || comp.getName().equals("null")) {
            return;
        }

        WorldPoint objLoc = obj.getWorldLocation();
        int distance = playerLoc.distanceTo(objLoc);

        // Get options
        List<LocOption> optionsWithIndex = new ArrayList<>();
        List<String> options = new ArrayList<>();
        String[] actions = comp.getActions();
        if (actions != null) {
            for (int i = 0; i < actions.length; i++) {
                if (actions[i] != null) {
                    optionsWithIndex.add(LocOption.builder()
                            .text(actions[i])
                            .opIndex(i + 1)
                            .build());
                    options.add(actions[i]);
                }
            }
        }

        locs.add(NearbyLoc.builder()
                .id(obj.getId())
                .name(comp.getName())
                .x(objLoc.getX())
                .z(objLoc.getY())
                .distance(distance)
                .optionsWithIndex(optionsWithIndex)
                .options(options)
                .build());
    }

    private List<net.runelite.client.plugins.botsdk.model.GroundItem> collectGroundItems() {
        List<net.runelite.client.plugins.botsdk.model.GroundItem> items = new ArrayList<>();
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return items;
        }

        WorldPoint playerLoc = localPlayer.getWorldLocation();
        Scene scene = client.getScene();
        Tile[][][] tiles = scene.getTiles();
        int plane = client.getPlane();
        int maxDist = config.maxLocDistance();

        int baseX = client.getBaseX();
        int baseY = client.getBaseY();
        int playerSceneX = playerLoc.getX() - baseX;
        int playerSceneY = playerLoc.getY() - baseY;

        for (int dx = -maxDist; dx <= maxDist; dx++) {
            for (int dy = -maxDist; dy <= maxDist; dy++) {
                int sceneX = playerSceneX + dx;
                int sceneY = playerSceneY + dy;

                if (sceneX < 0 || sceneX >= 104 || sceneY < 0 || sceneY >= 104) {
                    continue;
                }

                Tile tile = tiles[plane][sceneX][sceneY];
                if (tile == null) {
                    continue;
                }

                List<TileItem> tileItems = tile.getGroundItems();
                if (tileItems == null) {
                    continue;
                }

                WorldPoint tileLoc = tile.getWorldLocation();
                int distance = playerLoc.distanceTo(tileLoc);

                for (TileItem tileItem : tileItems) {
                    ItemComposition comp = itemManager.getItemComposition(tileItem.getId());
                    items.add(net.runelite.client.plugins.botsdk.model.GroundItem.builder()
                            .id(tileItem.getId())
                            .name(comp.getName())
                            .count(tileItem.getQuantity())
                            .x(tileLoc.getX())
                            .z(tileLoc.getY())
                            .distance(distance)
                            .build());
                }
            }
        }

        return items;
    }

    private DialogState collectDialogState() {
        // Check common dialog widgets
        // Widget IDs for dialogs: 229 (NPC chat), 217 (player chat), 219 (options)
        boolean isOpen = false;
        String text = null;
        List<DialogOption> options = new ArrayList<>();

        // Check option dialog (ID 219)
        Widget optionWidget = client.getWidget(219, 1);
        if (optionWidget != null && !optionWidget.isHidden()) {
            isOpen = true;
            Widget[] children = optionWidget.getChildren();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    Widget child = children[i];
                    if (child != null && child.getText() != null && !child.getText().isEmpty()) {
                        options.add(DialogOption.builder()
                                .index(i)
                                .text(child.getText())
                                .componentId(child.getId())
                                .build());
                    }
                }
            }
        }

        // Check NPC dialog (ID 229)
        Widget npcDialog = client.getWidget(229, 1);
        if (npcDialog != null && !npcDialog.isHidden()) {
            isOpen = true;
            text = npcDialog.getText();
        }

        return DialogState.builder()
                .isOpen(isOpen)
                .options(options)
                .isWaiting(isOpen && options.isEmpty())
                .text(text)
                .build();
    }

    private InterfaceState collectInterfaceState() {
        // Placeholder - would need to track specific interfaces
        return InterfaceState.builder()
                .isOpen(false)
                .interfaceId(-1)
                .options(new ArrayList<>())
                .build();
    }

    private ShopState collectShopState() {
        // Check if shop is open (widget group 300)
        Widget shopWidget = client.getWidget(300, 0);
        boolean isOpen = shopWidget != null && !shopWidget.isHidden();

        return ShopState.builder()
                .isOpen(isOpen)
                .title(isOpen ? "Shop" : "")
                .shopItems(new ArrayList<>())
                .playerItems(new ArrayList<>())
                .build();
    }

    private BankState collectBankState() {
        // Check if bank is open (widget group 12)
        Widget bankWidget = client.getWidget(12, 0);
        boolean isOpen = bankWidget != null && !bankWidget.isHidden();

        List<BankItem> items = new ArrayList<>();
        if (isOpen) {
            ItemContainer container = client.getItemContainer(InventoryID.BANK);
            if (container != null) {
                Item[] bankItems = container.getItems();
                for (int i = 0; i < bankItems.length; i++) {
                    Item item = bankItems[i];
                    if (item.getId() == -1 || item.getId() == 0) {
                        continue;
                    }
                    ItemComposition comp = itemManager.getItemComposition(item.getId());
                    items.add(BankItem.builder()
                            .slot(i)
                            .id(item.getId())
                            .name(comp.getName())
                            .count(item.getQuantity())
                            .build());
                }
            }
        }

        return BankState.builder()
                .isOpen(isOpen)
                .items(items)
                .build();
    }

    /**
     * Called when player takes damage
     */
    public void onDamageTaken() {
        lastDamageTick = client.getTickCount();
    }
}
