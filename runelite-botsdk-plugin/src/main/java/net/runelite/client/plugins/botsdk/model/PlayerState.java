/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;

/**
 * Player state - matches RS-SDK PlayerState
 */
@Data
@Builder
public class PlayerState {
    private String name;
    private int combatLevel;

    /** Local X coordinate within the scene */
    private int x;

    /** Local Z coordinate within the scene (Y in OSRS terms) */
    private int z;

    /** World X coordinate */
    private int worldX;

    /** World Z coordinate (Y in OSRS terms) */
    private int worldZ;

    /** Map plane/floor: 0=ground, 1=first floor, 2=second floor, 3=third floor */
    private int level;

    private int runEnergy;
    private int runWeight;

    /** Current animation ID (-1 = idle/none) */
    private int animId;

    /** Current spot animation ID (-1 = none) */
    private int spotanimId;

    /** Combat state tracking */
    private PlayerCombatState combat;
}
