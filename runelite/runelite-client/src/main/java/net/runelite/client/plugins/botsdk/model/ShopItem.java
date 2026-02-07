/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;

/**
 * Shop item - matches RS-SDK ShopItem
 */
@Data
@Builder
public class ShopItem {
    private int slot;
    private int id;
    private String name;
    private int count;
    private int baseCost;
    private int buyPrice;
    private int sellPrice;
}
