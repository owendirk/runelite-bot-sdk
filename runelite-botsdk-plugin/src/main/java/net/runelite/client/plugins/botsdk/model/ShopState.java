/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Shop state - matches RS-SDK ShopState
 */
@Data
@Builder
public class ShopState {
    private boolean isOpen;
    private String title;
    private List<ShopItem> shopItems;
    private List<ShopItem> playerItems;
}
