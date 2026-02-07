/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Dialog state - matches RS-SDK DialogState
 */
@Data
@Builder
public class DialogState {
    private boolean isOpen;
    private List<DialogOption> options;
    private boolean isWaiting;
    private String text;
}
