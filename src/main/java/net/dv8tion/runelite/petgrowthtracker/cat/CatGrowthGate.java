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
package net.dv8tion.runelite.petgrowthtracker.cat;

import net.runelite.api.Client;
import net.runelite.api.HashTable;
import net.runelite.api.WidgetNode;
import net.runelite.api.gameval.InterfaceID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

/**
 * The one piece of cat growth detection that genuinely needs {@code Client}:
 * whether a currently-open interface is one of the ones known to pause
 * kitten/cat growth. Pulled out on its own so it isn't buried in the middle
 * of {@link CatPetHandler} - the dog side's equivalent question ("is growth
 * currently allowed to progress?") lives just as visibly in
 * {@code DogTracker.tick()}, just answered by elapsed-since-fed time instead
 * of open interfaces, because that's the mechanic the game actually uses for
 * each species.
 *
 * This is only ever consulted as a fallback, when overhead text can't be
 * seen (a crowded area, or a teleport landing on the same tick as a growth
 * tick) - see {@link CatPetHandler#onGameTick()}.
 */
class CatGrowthGate
{
    /* InterfaceIDs for interfaces that stall kitten growth.  This is not all-inclusive.  It's just a makeshift
    replacement for watching for overhead text that would normally indicate kitten growth when the player count
    is very high and cant load all entities and your kitten might not be rendered.
    Rule of thumb: if you can't move around and keep the interface open, it will delay kitten growth if it is open.
     */
    private static final List<Integer> UNSAFE_IDS = new ArrayList<>(Arrays.asList(InterfaceID.JOURNALSCROLL,
            InterfaceID.GE_PRICECHECKER, InterfaceID.DEATHKEEP, InterfaceID.COLLECTION,
            InterfaceID.CHAT_RIGHT, InterfaceID.CHAT_LEFT, InterfaceID.CHATMENU,
            InterfaceID.OBJECTBOX_DOUBLE, InterfaceID.OBJECTBOX,
            InterfaceID.QUESTJOURNAL, InterfaceID.HOSIDIUS_SEEDBOX, InterfaceID.RUNE_POUCH,
            InterfaceID.TRAIL_CLUETEXT, InterfaceID.TRAIL_REWARDSCREEN,
            InterfaceID.TRAIL_MAP01, InterfaceID.TRAIL_MAP02, InterfaceID.TRAIL_MAP03,
            InterfaceID.TRAIL_MAP06, InterfaceID.TRAIL_MAP11,
            InterfaceID.EQUIPMENT,
            InterfaceID.CA_OVERVIEW, InterfaceID.CA_TASKS,
            InterfaceID.CA_BOSSES, InterfaceID.CA_REWARDS, InterfaceID.CRM_SURPRISEPOPUP_SIDE,
            InterfaceID.DISPLAYNAME, // believe it or not this does pause kitten growth
            InterfaceID.FAVOUR_KEYRING,
            InterfaceID.BOOKOFSCROLLS,
            InterfaceID.FORESTRY_KIT_MAIN, InterfaceID.FORESTRY_KIT_SIDE, // forestry main and side simultaneously open
            InterfaceID.GE_OFFERS, InterfaceID.BANK_DEPOSITBOX, InterfaceID.GE_COLLECT,
            InterfaceID.ITEMSETS, InterfaceID.ITEMSETS_SIDE, InterfaceID.GE_HISTORY,
            InterfaceID.SEED_VAULT, InterfaceID.QUESTSCROLL, InterfaceID.LEVELUP_DISPLAY,
            InterfaceID.FAIRYRINGS, InterfaceID.SHOPMAIN, InterfaceID.SLAYER_REWARDS,
            InterfaceID.CONFIRMDESTROY, InterfaceID.BANKPIN_KEYPAD, InterfaceID.BANKMAIN,
            InterfaceID.XPREWARD // same for book of knowledge from dunce random event
    ));

    private final Client client;

    @Inject
    CatGrowthGate(Client client)
    {
        this.client = client;
    }

    /** True if a known growth-pausing interface is currently open. */
    boolean isBlocked()
    {
        HashTable<WidgetNode> componentTable = client.getComponentTable();
        for (WidgetNode widgetNode : componentTable)
        {
            for (Integer unsafe : UNSAFE_IDS)
            {
                if (widgetNode.getId() == unsafe)
                {
                    return true;
                }
            }
        }
        return false;
    }
}
