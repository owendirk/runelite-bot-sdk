/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Interface state - matches RS-SDK InterfaceState
 */
@Data
@Builder
public class InterfaceState {
    private boolean isOpen;
    private int interfaceId;
    private List<InterfaceOption> options;
}
