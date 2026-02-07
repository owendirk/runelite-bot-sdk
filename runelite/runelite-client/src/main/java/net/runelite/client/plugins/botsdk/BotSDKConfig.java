/*
 * Copyright (c) 2024
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.botsdk;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("botsdk")
public interface BotSDKConfig extends Config {
    @ConfigItem(keyName = "port", name = "WebSocket Port", description = "Port for the WebSocket server to listen on", position = 1)
    @Range(min = 1024, max = 65535)
    default int port() {
        return 7780;
    }

    @ConfigItem(keyName = "broadcastInterval", name = "Broadcast Interval", description = "Number of game ticks between state broadcasts (1 tick ≈ 600ms)", position = 2)
    @Range(min = 1, max = 10)
    default int broadcastInterval() {
        return 1;
    }

    @ConfigItem(keyName = "includeNpcs", name = "Include NPCs", description = "Include nearby NPCs in state broadcasts", position = 3)
    default boolean includeNpcs() {
        return true;
    }

    @ConfigItem(keyName = "includeLocs", name = "Include Objects", description = "Include nearby game objects in state broadcasts", position = 4)
    default boolean includeLocs() {
        return true;
    }

    @ConfigItem(keyName = "includeGroundItems", name = "Include Ground Items", description = "Include ground items in state broadcasts", position = 5)
    default boolean includeGroundItems() {
        return true;
    }

    @ConfigItem(keyName = "maxNpcDistance", name = "Max NPC Distance", description = "Maximum distance (in tiles) for NPCs to be included", position = 6)
    @Range(min = 5, max = 50)
    default int maxNpcDistance() {
        return 15;
    }

    @ConfigItem(keyName = "maxLocDistance", name = "Max Object Distance", description = "Maximum distance (in tiles) for objects to be included", position = 7)
    @Range(min = 5, max = 50)
    default int maxLocDistance() {
        return 10;
    }
}
