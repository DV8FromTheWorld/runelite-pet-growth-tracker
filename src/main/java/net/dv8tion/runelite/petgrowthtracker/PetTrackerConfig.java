/*
 * Copyright (c) 2018, Nachtmerrie <https://github.com/Nachtmerrie>
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
package net.dv8tion.runelite.petgrowthtracker;

import net.dv8tion.runelite.petgrowthtracker.cat.CatAttentionType;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * All user-facing toggles, plus the hidden fields {@code CatPetHandler}
 * persists its clock into so growth survives a relog. There is no dog
 * equivalent - see {@code DogTracker} for why. One group, because a player
 * only ever has one follower at a time - there is no scenario where cat
 * state and dog state are both live.
 */
@ConfigGroup(PetTrackerConfig.GROUP)
public interface PetTrackerConfig extends Config
{
    String GROUP = "petTrackerConfig";

    // ------------------------------------------------------------- sections

    @ConfigSection(
            name = "Cat",
            description = "Kitten/cat overlay and notification settings",
            position = 1
    )
    String catSection = "catSection";

    @ConfigSection(
            name = "Dog",
            description = "Puppy/dog overlay and notification settings",
            position = 2
    )
    String dogSection = "dogSection";

    // -------------------------------------------------------------- display

    @ConfigItem(
            keyName = "displaySeconds",
            name = "Display seconds in timers",
            description = "Displays seconds in the timers for your pet",
            position = 0
    )
    default boolean displaySeconds()
    {
        return false;
    }

    // ------------------------------------------------------------------ cat

    @ConfigItem(
            keyName = "catOverlay",
            name = "Show growth countdown",
            description = "Shows how long until your kitten grows into a cat, or your cat into an overgrown cat",
            section = catSection,
            position = 1
    )
    default boolean catOverlay()
    {
        return true;
    }

    @ConfigItem(
            keyName = "catHungryOverlay",
            name = "Show hunger countdown",
            description = "Shows how long until your kitten leaves you for being underfed",
            section = catSection,
            position = 2
    )
    default boolean catHungryOverlay()
    {
        return true;
    }

    @ConfigItem(
            keyName = "catAttentionOverlay",
            name = "Show attention countdown",
            description = "Shows how long until your kitten leaves you for being neglectful",
            section = catSection,
            position = 3
    )
    default boolean catAttentionOverlay()
    {
        return true;
    }

    @ConfigItem(
            keyName = "catNotifications",
            name = "Show notifications",
            description = "Sends a notification when your kitten is hungry or requires attention",
            section = catSection,
            position = 4
    )
    default boolean catNotifications()
    {
        return true;
    }

    @ConfigItem(
            keyName = "catHideWhenGrown",
            name = "Hide once grown",
            description = "Hides the overlay once your cat has finished growing into an overgrown cat",
            section = catSection,
            position = 5
    )
    default boolean catHideWhenGrown()
    {
        return true;
    }

    // ------------------------------------------------------------------ dog

    @ConfigItem(
            keyName = "dogOverlay",
            name = "Show growth countdown",
            description = "Shows how long until your puppy grows into a dog",
            section = dogSection,
            position = 1
    )
    default boolean dogOverlay()
    {
        return true;
    }

    @ConfigItem(
            keyName = "dogFeedOverlay",
            name = "Show feed countdown",
            description = "Shows how long until your puppy's growth pauses from being unfed, and how long since it was last fed",
            section = dogSection,
            position = 2
    )
    default boolean dogFeedOverlay()
    {
        return true;
    }

    @ConfigItem(
            keyName = "dogNotifications",
            name = "Show notifications",
            description = "Sends a notification when your puppy's growth pauses, or it finishes growing into a dog",
            section = dogSection,
            position = 3
    )
    default boolean dogNotifications()
    {
        return true;
    }

    @ConfigItem(
            keyName = "dogHideWhenGrown",
            name = "Hide once grown",
            description = "Hides the overlay once your puppy has finished growing into a dog",
            section = dogSection,
            position = 4
    )
    default boolean dogHideWhenGrown()
    {
        return true;
    }

    // --------------------------------------------------------- cat: hidden

    @ConfigItem(
            keyName = "catFelineId",
            name = "",
            description = "",
            hidden = true
    )
    default int catFelineId()
    {
        return -1;
    }

    @ConfigItem(
            keyName = "catFelineId",
            name = "",
            description = ""
    )
    void catFelineId(int id);

    @ConfigItem(
            keyName = "catLastAttentionType",
            name = "",
            description = "",
            hidden = true
    )
    default CatAttentionType catLastAttentionType()
    {
        return null;
    }

    @ConfigItem(
            keyName = "catLastAttentionType",
            name = "",
            description = ""
    )
    void catLastAttentionType(CatAttentionType lastAttentionType);

    @ConfigItem(
            keyName = "catGrowthTicksAlive",
            name = "",
            description = "",
            hidden = true
    )
    default int catGrowthTicksAlive()
    {
        return -1;
    }

    @ConfigItem(
            keyName = "catGrowthTicksAlive",
            name = "",
            description = ""
    )
    void catGrowthTicksAlive(int ticks);

    @ConfigItem(
            keyName = "catNextHungryTick",
            name = "",
            description = "",
            hidden = true
    )
    default int catNextHungryTick()
    {
        return -1;
    }

    @ConfigItem(
            keyName = "catNextHungryTick",
            name = "",
            description = ""
    )
    void catNextHungryTick(int ticks);

    @ConfigItem(
            keyName = "catNextAttentionTick",
            name = "",
            description = "",
            hidden = true
    )
    default int catNextAttentionTick()
    {
        return -1;
    }

    @ConfigItem(
            keyName = "catNextAttentionTick",
            name = "",
            description = ""
    )
    void catNextAttentionTick(int ticks);

    // Dogs need no equivalent hidden state: their growth lives in a server-side
    // varbit (see net.dv8tion.runelite.petgrowthtracker.dog.DogTracker), so there's nothing to persist.
}
