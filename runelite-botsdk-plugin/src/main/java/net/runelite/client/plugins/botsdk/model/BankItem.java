/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;

/**
 * Bank item - matches RS-SDK BankItem
 */
@Data
@Builder
public class BankItem {
    private int slot;
    private int id;
    private String name;
    private int count;
}
