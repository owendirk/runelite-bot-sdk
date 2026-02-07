/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Interface option for InterfaceState
 */
@Data
@Builder
public class InterfaceOption {
    private int index;
    private String text;
    private int componentId;
}
