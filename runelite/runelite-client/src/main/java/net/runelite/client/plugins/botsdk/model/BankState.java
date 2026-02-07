/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Bank state - matches RS-SDK BankState
 */
@Data
@Builder
public class BankState {
    private boolean isOpen;
    private List<BankItem> items;
}
