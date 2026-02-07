/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;

/**
 * Nearby player - matches RS-SDK NearbyPlayer
 */
@Data
@Builder
public class NearbyPlayer {
    private int index;
    private String name;
    private int combatLevel;
    private int x;
    private int z;
    private int distance;
}
