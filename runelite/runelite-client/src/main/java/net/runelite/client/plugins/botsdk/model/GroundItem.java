/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;

/**
 * Ground item - matches RS-SDK GroundItem
 */
@Data
@Builder
public class GroundItem {
    private int id;
    private String name;
    private int count;
    private int x;
    private int z;
    private int distance;
}
