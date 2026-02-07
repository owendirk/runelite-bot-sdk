/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Dialog option - matches RS-SDK DialogOption
 */
@Data
@Builder
public class DialogOption {
    private int index;
    private String text;
    private Integer componentId;
    private Integer buttonType;
}
