package net.dv8tion.runelite.petgrowthtracker;

import net.runelite.api.GameState;
import net.runelite.api.events.MenuOptionClicked;

/**
 * The state machine for one family of pet (cat or dog).
 *
 * {@link PetTrackerPlugin} owns the RuneLite subscriptions and decides which
 * single handler currently owns the player's follower; everything species
 * specific - what counts as growth, what the game's chat lines say, how a
 * feeding or a stroke changes the clocks - lives behind this interface so
 * the plugin and the overlay never need a species check of their own.
 */
public interface PetHandler
{
    /**
     * The tracked NPC id changed. {@code npcId == 0} means the player no
     * longer has a follower at all; any other value is a follower this
     * handler has been asked to own (the plugin has already decided this
     * handler is the right one, or - for dogs - that it's worth a provisional
     * look).
     */
    void onFollowerChanged(int npcId);

    /** Fires every game tick while this handler owns the current follower. */
    void onGameTick();

    /**
     * A game-message chat line arrived. Returns {@code true} if the line was
     * recognised as belonging to this species, purely so the plugin can log
     * when neither handler claims a line worth investigating.
     */
    boolean onChatMessage(String message);

    /**
     * Text read from a dialog/messagebox/objectbox widget this tick (guess
     * age replies, "has grown into a cat", turning a pet in, etc). Called
     * with {@code null} when no such widget is open.
     */
    void onDialogText(String text);

    void onMenuOptionClicked(MenuOptionClicked event);

    void onGameStateChanged(GameState state);

    /**
     * A plugin config value changed. {@code key} is the raw config key, e.g.
     * {@code "catAttentionOverlay"}.
     */
    void onConfigChanged(String key, String newValue);

    /** Whether this handler currently has anything worth showing or saving. */
    boolean isActive();

    /** {@code null} when there is nothing worth painting right now. */
    PetStatus status();

    /** Persist whatever clocks this handler is keeping, e.g. on shutdown. */
    void saveState();
}
