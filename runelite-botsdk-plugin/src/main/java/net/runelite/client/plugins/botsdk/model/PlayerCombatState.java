/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;

/**
 * Combat state tracking for player - matches RS-SDK PlayerCombatState
 */
@Data
@Builder
public class PlayerCombatState {
    /** Currently engaged in combat (has a target) */
    private boolean inCombat;

    /** Index of NPC/player we're targeting (-1 if none) */
    private int targetIndex;

    /** Tick when we last took damage (-1 if never) */
    private int lastDamageTick;
}
