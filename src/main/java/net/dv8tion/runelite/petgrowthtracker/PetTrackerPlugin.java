package net.dv8tion.runelite.petgrowthtracker;

import com.google.inject.Provides;
import net.dv8tion.runelite.petgrowthtracker.cat.CatPetHandler;
import net.dv8tion.runelite.petgrowthtracker.dog.DogPetHandler;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

/**
 * Tracks a cat/kitten or a puppy/dog follower, showing growth, hunger and
 * (for cats) attention countdowns in one overlay.
 *
 * This is a fork of the Kitten Tracker plugin, restructured around a
 * {@link PetHandler} per species so the same plugin can track either family
 * of pet: {@code CatPetHandler} carries over the original's growth-tick
 * engine unchanged, and {@code DogPetHandler} adds puppy/dog growth
 * tracking built around the account's own growth/feed varbits. This class's
 * job is just routing: work out which handler owns the current follower,
 * and forward every RuneLite event to it.
 *
 * Routing a follower is symmetric: both {@code CatPetHandler} and
 * {@code DogPetHandler} classify an NPC id via their own {@code FollowerKind}
 * enum (contiguous, known id ranges for every life stage of that species),
 * and whichever one claims the id owns every event for that follower. A
 * follower that neither recognises (a boss pet, a skilling pet) gets no
 * handler at all - {@link #species} stays {@code null} and the overlay shows
 * nothing, rather than guessing.
 */
@Slf4j
@PluginDescriptor(
        name = "Pet Growth Tracker",
        description = "Tracks the growth and hunger of your kitten/cat or puppy/dog, and warns you before it runs away or stops growing",
        tags = {"kitten", "cat", "puppy", "dog", "pet", "growth", "hunger", "status", "alert", "timer"}
)
public class PetTrackerPlugin extends Plugin
{
    private static final int VAR_PLAYER_FOLLOWER = 447;

    private static final String DIALOG_HAND_OVER_CAT_CIVILIAN = "You hand over the cat.You are given";
    private static final String DIALOG_GERTRUDE_GIVES_YOU_ANOTHER_KITTEN = "Gertrude gives you another kitten.";

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private PetOverlay overlay;
    @Inject
    private CatPetHandler catHandler;
    @Inject
    private DogPetHandler dogHandler;

    private int followerID = 0;
    private PetSpecies species;

    @Provides
    PetTrackerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(PetTrackerConfig.class);
    }

    @Override
    public void startUp()
    {
        overlayManager.add(overlay);
        // Deferred to the client thread, matching the original: startUp() isn't guaranteed
        // to already be running there, and Client state shouldn't be touched off it.
        clientThread.invokeLater(this::checkForFollower);
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        catHandler.saveState();
        dogHandler.saveState();
    }

    private void checkForFollower()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        if (playerHasFollower())
        {
            followerID = getCurrentFollowerId();
            if (followerID > 0)
            {
                classifyAndDispatch(followerID);
            }
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        if (playerHasFollower() && followerID != getCurrentFollowerId())
        {
            followerID = getCurrentFollowerId();
            if (followerID > 0) // Varbit needs to fill up first after logging in
            {
                classifyAndDispatch(followerID);
            }
        }

        if (!playerHasFollower() && followerID != 0)
        {
            if (species != null)
            {
                activeHandler().onFollowerChanged(0);
            }
            followerID = 0;
            species = null;
        }
    }

    private void classifyAndDispatch(int npcId)
    {
        PetSpecies newSpecies;
        if (catHandler.isTrackedId(npcId))
        {
            newSpecies = PetSpecies.CAT;
        }
        else if (dogHandler.isTrackedId(npcId))
        {
            newSpecies = PetSpecies.DOG;
        }
        else
        {
            // Some other pet entirely (a boss pet, a skilling pet) - not ours to track.
            if (species != null)
            {
                activeHandler().onFollowerChanged(0);
            }
            species = null;
            return;
        }

        if (species != null && species != newSpecies)
        {
            activeHandler().onFollowerChanged(0);
        }

        species = newSpecies;
        activeHandler().onFollowerChanged(npcId);
    }

    private PetHandler activeHandler()
    {
        return species == PetSpecies.CAT ? catHandler : dogHandler;
    }

    /** Used by {@link PetOverlay}; {@code null} when there's nothing to paint. */
    PetStatus activeStatus()
    {
        return species == null ? null : activeHandler().status();
    }

    private int getCurrentFollowerId()
    {
        int followerVarPlayerValue = client.getVarpValue(VAR_PLAYER_FOLLOWER);
        // followerID is the first 2 bytes
        return (followerVarPlayerValue >> 16);
    }

    boolean playerHasFollower()
    {
        return (client.getVarpValue(VAR_PLAYER_FOLLOWER)) > 0;
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (species != null)
        {
            activeHandler().onGameTick();
        }

        Widget playerDialog = client.getWidget(InterfaceID.ChatRight.TEXT);
        if (playerDialog != null && species != null)
        {
            activeHandler().onDialogText(Text.removeTags(playerDialog.getText()));
        }

        Widget notificationDialog = client.getWidget(InterfaceID.Messagebox.TEXT);
        if (notificationDialog != null)
        {
            String notificationText = Text.removeTags(notificationDialog.getText());
            if (notificationText.equals(DIALOG_GERTRUDE_GIVES_YOU_ANOTHER_KITTEN))
            {
                // A brand new kitten - route to the cat handler regardless of what we were tracking before.
                species = PetSpecies.CAT;
            }
            if (species != null)
            {
                activeHandler().onDialogText(notificationText);
            }
        }

        Widget objectBoxDialog = client.getWidget(InterfaceID.OBJECTBOX, 2);
        if (objectBoxDialog != null && species != null)
        {
            String notificationText = Text.removeTags(objectBoxDialog.getText());
            if (notificationText.startsWith(DIALOG_HAND_OVER_CAT_CIVILIAN))
            {
                activeHandler().onDialogText(notificationText);
            }
        }
    }

    @Subscribe
    public void onOverheadTextChanged(OverheadTextChanged e)
    {
        if (species == PetSpecies.CAT && e.getActor().equals(client.getFollower()))
        {
            catHandler.onFollowerOverheadText();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() != ChatMessageType.GAMEMESSAGE)
        {
            return;
        }
        if (species == null)
        {
            return;
        }

        String message = Text.removeTags(event.getMessage());
        activeHandler().onChatMessage(message);
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (species != null)
        {
            activeHandler().onMenuOptionClicked(event);
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        catHandler.onGameStateChanged(event.getGameState());
        dogHandler.onGameStateChanged(event.getGameState());
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!event.getGroup().equals(PetTrackerConfig.GROUP))
        {
            return;
        }

        catHandler.onConfigChanged(event.getKey(), event.getNewValue());
        dogHandler.onConfigChanged(event.getKey(), event.getNewValue());
    }
}
