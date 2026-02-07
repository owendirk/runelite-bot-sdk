/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Location/object option - matches RS-SDK LocOption
 */
@Data
@Builder
public class LocOption {
    private String text;
    private int opIndex;
}
