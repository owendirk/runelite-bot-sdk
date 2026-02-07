/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.botsdk.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Nearby location/game object - matches RS-SDK NearbyLoc
 */
@Data
@Builder
public class NearbyLoc {
    private int id;
    private String name;
    private int x;
    private int z;
    private int distance;
    private List<LocOption> optionsWithIndex;
    private List<String> options;
}
