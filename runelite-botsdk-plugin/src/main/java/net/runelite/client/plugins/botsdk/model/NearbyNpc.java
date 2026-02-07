/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Nearby NPC - matches RS-SDK NearbyNpc
 */
@Data
@Builder
public class NearbyNpc {
    private int index;
    private String name;
    private int combatLevel;
    private int x;
    private int z;
    private int distance;
    private int hp;
    private int maxHp;
    private Integer healthPercent;
    private int targetIndex;
    private boolean inCombat;
    private int combatCycle;
    private int animId;
    private int spotanimId;
    private List<NpcOption> optionsWithIndex;
    private List<String> options;
}
