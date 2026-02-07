/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Item option - matches RS-SDK InventoryItemOption
 */
@Data
@Builder
public class ItemOption {
    private String text;
    private int opIndex;
}
