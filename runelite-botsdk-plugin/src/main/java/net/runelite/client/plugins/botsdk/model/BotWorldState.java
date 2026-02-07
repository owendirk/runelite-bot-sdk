/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Complete bot world state - matches RS-SDK BotWorldState
 * This is the root object broadcast to SDK clients
 */
@Data
@Builder
public class BotWorldState {
    private int tick;
    private boolean inGame;
    private PlayerState player;
    private List<SkillState> skills;
    private List<InventoryItem> inventory;
    private List<InventoryItem> equipment;
    private List<NearbyNpc> nearbyNpcs;
    private List<NearbyPlayer> nearbyPlayers;
    private List<NearbyLoc> nearbyLocs;
    private List<GroundItem> groundItems;
    private List<GameMessage> gameMessages;
    private DialogState dialog;
    private InterfaceState interfaceState;
    private ShopState shop;
    private BankState bank;
    private boolean modalOpen;
    private int modalInterface;
}
