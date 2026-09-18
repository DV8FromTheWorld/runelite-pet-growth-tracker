package net.dv8tion.runelite.petgrowthtracker.dog;

import net.dv8tion.runelite.petgrowthtracker.PetHandler;
import net.dv8tion.runelite.petgrowthtracker.PetStatus;
import net.dv8tion.runelite.petgrowthtracker.PetTrackerConfig;

import java.awt.Color;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;

/**
 * The puppy/dog RuneLite adapter: polls the two server-side growth varbits
 * (see {@link DogTracker}) and turns their edges into notifications and an
 * overlay status.
 *
 * There is deliberately no persistence here, unlike the cat side. A cat's
 * growth has to be reconstructed client-side across a relog because nothing
 * server-side exposes it; a puppy's growth is a live account value the
 * server already remembers, so re-reading the varbit after logging back in
 * gives the exact answer with no client-side bookkeeping at all. Likewise
 * there's no menu-click feed detection or chat parsing: the fed-timer varbit
 * only resets on a feed the server actually accepted, so there's nothing to
 * infer and nothing a refused feed could get wrong.
 */
@Slf4j
@Singleton
public class DogPetHandler implements PetHandler
{
    private final Client client;
    private final Notifier notifier;
    private final PetTrackerConfig config;

    private final DogTracker tracker = new DogTracker();

    private FollowerKind followerKind = FollowerKind.NON_DOG;

    private boolean warnedHungry;
    private boolean warnedVeryHungry;
    private boolean warnedPaused;
    private boolean warnedGrown;

    @Inject
    public DogPetHandler(Client client, Notifier notifier, PetTrackerConfig config)
    {
        this.client = client;
        this.notifier = notifier;
        this.config = config;
    }

    public boolean isTrackedId(int npcId)
    {
        return FollowerKind.getFromFollowerId(npcId).isTracked();
    }

    @Override
    public void onFollowerChanged(int npcId)
    {
        followerKind = FollowerKind.getFromFollowerId(npcId);
        warnedHungry = false;
        warnedVeryHungry = false;
        warnedPaused = false;
        warnedGrown = false;

        if (followerKind == FollowerKind.DOG)
        {
            // The id alone tells us this one has already finished growing - no need to read a varbit for it.
            tracker.grownUp();
        }
    }

    @Override
    public void onGameTick()
    {
        NPC follower = client.getFollower();
        tracker.setFollowing(follower != null, follower == null ? null : follower.getName());

        if (followerKind == FollowerKind.PUPPY)
        {
            int growthMinutes = client.getVarbitValue(VarbitID.PUPPY_GROWTH_TRACKER);
            int fedMinutes = client.getVarbitValue(VarbitID.PUPPY_FED_TIMER);
            tracker.update(growthMinutes, fedMinutes);
        }

        checkNotifications();
    }

    @Override
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        // Feeding is read back from the fed-timer varbit, not the menu click that caused it.
    }

    @Override
    public boolean onChatMessage(String message)
    {
        // Growth and feed state come straight from varbits; no chat line carries anything the varbits don't already say.
        return false;
    }

    @Override
    public void onDialogText(String text)
    {
        // Nothing to parse - see onChatMessage.
    }

    @Override
    public void onGameStateChanged(GameState state)
    {
        if (state != GameState.LOGGED_IN)
        {
            tracker.setFollowing(false, null);
        }
    }

    @Override
    public void onConfigChanged(String key, String newValue)
    {
        // Nothing to re-arm live; the next tick/status() call reads the config directly.
    }

    @Override
    public boolean isActive()
    {
        return followerKind.isTracked();
    }

    @Override
    public void saveState()
    {
        // Nothing to save - see the class-level note on why this class has no persistence.
    }

    private void checkNotifications()
    {
        if (!tracker.isFollowing())
        {
            return;
        }

        if (tracker.isFullyGrown())
        {
            if (!warnedGrown)
            {
                warnedGrown = true;
                if (config.dogNotifications())
                {
                    notifier.notify("Your puppy has grown into a dog");
                }
            }
            return;
        }

        if (!tracker.isHungry())
        {
            // Freshly fed (or never let it get hungry) - re-arm every warning for the next cycle.
            warnedHungry = false;
            warnedVeryHungry = false;
            warnedPaused = false;
            return;
        }

        if (tracker.isGrowthPaused())
        {
            if (!warnedPaused)
            {
                warnedPaused = true;
                if (config.dogNotifications())
                {
                    notifier.notify("Your puppy has stopped growing. You'll need to feed it for it to continue doing so.");
                }
            }
        }
        else if (tracker.isVeryHungry())
        {
            if (!warnedVeryHungry)
            {
                warnedVeryHungry = true;
                if (config.dogNotifications())
                {
                    notifier.notify("Your puppy is very hungry.");
                }
            }
        }
        else if (!warnedHungry)
        {
            warnedHungry = true;
            if (config.dogNotifications())
            {
                notifier.notify("Your puppy is getting quite hungry.");
            }
        }
    }

    @Override
    public PetStatus status()
    {
        if (!followerKind.isTracked() || !tracker.isFollowing())
        {
            return null;
        }

        if (tracker.isFullyGrown())
        {
            if (config.dogHideWhenGrown() || !config.dogOverlay())
            {
                return null;
            }
            return new PetStatus("Dog status").addRow("Status", tracker.petName() + " (grown)", Color.WHITE);
        }

        PetStatus status = new PetStatus("Puppy status");
        boolean paused = tracker.isGrowthPaused();

        if (config.dogOverlay())
        {
            status.addDurationRow("Grown up in", tracker.untilGrownMillis(), paused ? Color.RED : Color.WHITE);
        }

        if (config.dogFeedOverlay())
        {
            if (paused)
            {
                status.addRow("Hungry in", "stopped growing", Color.RED);
            }
            else
            {
                Color color = tracker.isVeryHungry() ? Color.RED : tracker.isHungry() ? Color.ORANGE : Color.WHITE;
                status.addDurationRow("Hungry in", tracker.untilPausedMillis(), color);
            }
        }

        return status.isEmpty() ? null : status;
    }
}
