/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * NPC option - matches RS-SDK NpcOption
 */
@Data
@Builder
public class NpcOption {
    private String text;
    private int opIndex;
}
