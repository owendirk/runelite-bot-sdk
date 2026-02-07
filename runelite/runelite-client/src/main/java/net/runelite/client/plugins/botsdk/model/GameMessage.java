/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;

/**
 * Game message - matches RS-SDK GameMessage
 */
@Data
@Builder
public class GameMessage {
    private int type;
    private String text;
    private String sender;
    private int tick;
}
