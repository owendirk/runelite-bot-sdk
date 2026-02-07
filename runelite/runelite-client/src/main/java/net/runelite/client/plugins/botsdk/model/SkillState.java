/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;

/**
 * Skill state - matches RS-SDK SkillState
 */
@Data
@Builder
public class SkillState {
    private String name;
    private int level;
    private int baseLevel;
    private int experience;
}
