/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Inventory item - matches RS-SDK InventoryItem
 */
@Data
@Builder
public class InventoryItem {
    private int slot;
    private int id;
    private String name;
    private int count;
    private List<ItemOption> optionsWithIndex;
}
