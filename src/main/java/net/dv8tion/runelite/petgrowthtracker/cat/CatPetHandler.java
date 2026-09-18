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

import net.dv8tion.runelite.petgrowthtracker.PetHandler;
import net.dv8tion.runelite.petgrowthtracker.PetStatus;
import net.dv8tion.runelite.petgrowthtracker.PetTrackerConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.time.Duration;
import java.time.Instant;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.Notifier;

/**
 * The kitten/cat RuneLite adapter: chat/dialog parsing, the overhead-text and
 * interface-blocking growth detection (via {@link CatGrowthGate}), and
 * config-driven notifications and persistence, around a {@link CatTracker} -
 * the pure growth/hunger/attention clock ported unchanged from the original
 * Kitten Tracker's {@code KittenPlugin}. This class owns none of the timing
 * constants or growth-tick math itself; see {@link CatTracker} for that.
 *
 * Classification is by NPC id via {@link FollowerKind} - contiguous, known
 * ranges the same way {@code net.dv8tion.runelite.petgrowthtracker.dog.FollowerKind} classifies
 * dogs.
 */
@Slf4j
@Singleton
public class CatPetHandler implements PetHandler
{
    private static final String DIALOG_CAT_BALL_OF_WOOL = "That kitten loves to play with that ball of wool. I think itis its favourite."; // The typo is intentional and how the game reads it...
    private static final String DIALOG_CAT_GROWN = "Your kitten has grown into a healthy cat that can hunt for itself.";
    private static final String DIALOG_CAT_OVERGROWN = "Your cat has grown into a mighty feline, but it will no longer be able to chase vermin.";
    private static final String DIALOG_HAND_OVER_CAT_CIVILIAN = "You hand over the cat.You are given";
    private static final String DIALOG_GERTRUDE_GIVES_YOU_ANOTHER_KITTEN = "Gertrude gives you another kitten.";

    private static final String CHAT_STROKE_CAT = "You softly stroke your cat.";
    private static final String CHAT_THE_KITTEN_GOBBLES_UP_THE_FISH = "The kitten gobbles up the fish.";
    private static final String CHAT_THE_KITTEN_LAPS_UP_THE_MILK = "The kitten laps up the milk.";
    private static final String CHAT_YOUR_KITTEN_IS_HUNGRY = "Your kitten is hungry.";
    private static final String CHAT_YOUR_KITTEN_IS_VERY_HUNGRY = "Your kitten is very hungry.";
    private static final String CHAT_YOUR_KITTEN_WANTS_ATTENTION = "Your kitten wants attention.";
    private static final String CHAT_YOUR_KITTEN_REALLY_WANTS_ATTENTION = "Your kitten really wants attention.";
    private static final String CHAT_YOUR_KITTEN_GOT_LONELY_AND_RAN_OFF = "Your kitten got lonely and ran off.";
    private static final String CHAT_THE_CAT_HAS_RUN_AWAY = "The cat has run away.";

    private static final String NOTIFICATION_KITTEN_WILL_RUN_AWAY_IN_1_MINUTE = "Kitten will run away in 1 minute!";

    private final Client client;
    private final Notifier notifier;
    private final PetTrackerConfig config;
    private final CatGrowthGate growthGate;
    private final CatTracker tracker = new CatTracker();

    @Inject
    public CatPetHandler(Client client, Notifier notifier, PetTrackerConfig config, CatGrowthGate growthGate)
    {
        this.client = client;
        this.notifier = notifier;
        this.config = config;
        this.growthGate = growthGate;
    }

    private int followerID = 0;
    private int previousFollowerId = 0;
    private boolean previousFollowerIdPrimed = false;

    private Instant lastLoadingTime = Instant.now();
    private boolean ready;

    private boolean attentionNotificationSend = false;
    private boolean hungryNotificationSend = false;

    private Instant blinkHunger = null;
    private Instant blinkAttention = null;
    private static final int BLINK_PERIOD_MS = 1000;

    public boolean isTrackedId(int npcId)
    {
        return FollowerKind.getFromFollowerId(npcId).isTracked();
    }

    @Override
    public void onFollowerChanged(int npcId)
    {
        if (!previousFollowerIdPrimed)
        {
            // Mirrors the original's startUp(): seed from the last feline id we ever saved,
            // so a relog with the same kitten/cat is recognised as "the same one back" rather
            // than starting its growth over from zero.
            previousFollowerId = config.catFelineId();
            previousFollowerIdPrimed = true;
        }

        if (npcId == 0)
        {
            byeFollower();
            return;
        }

        followerID = npcId;
        attentionNotificationSend = false;
        hungryNotificationSend = false;

        FollowerKind kind = FollowerKind.getFromFollowerId(npcId);
        boolean sameFollowerAsBefore = followerID == previousFollowerId;
        tracker.newFollower(kind, sameFollowerAsBefore,
                config.catGrowthTicksAlive(), config.catNextHungryTick(), config.catNextAttentionTick(),
                config.catLastAttentionType());

        previousFollowerId = followerID;
    }

    private void byeFollower()
    {
        saveGrowthProgress();
        tracker.byeFollower();
        followerID = 0;
    }

    @Override
    public void saveState()
    {
        saveGrowthProgress();
    }

    private void saveGrowthProgress()
    {
        if (!tracker.isKitten() && !tracker.isCat())
        {
            return;
        }

        config.catFelineId(followerID);
        if (tracker.isTickInProgress())
        {
            config.catGrowthTicksAlive(tracker.growthTicksAlive());
            if (tracker.isKitten())
            {
                config.catNextHungryTick(tracker.nextHungryTick());
                config.catNextAttentionTick(tracker.nextAttentionTick());
            }
        }
        else
        {
            log.debug("no growth tick in progress, no follower...");
        }
        if (tracker.isKitten())
        {
            config.catLastAttentionType(tracker.lastAttentionType());
        }
    }

    /** Called by the plugin when the follower has overhead text this tick. */
    public void onFollowerOverheadText()
    {
        int secondsBeforeCheckingForGrowth = tracker.noGrowthSinceLoggedIn() ? 80 : 87;
        if (tracker.secondsInTick() >= secondsBeforeCheckingForGrowth && nearbyPlayerCount() < 200)
        {
            tracker.advanceGrowthTick();
        }
    }

   private long nearbyPlayerCount()
    {
        return client.getTopLevelWorldView().players().stream().count();
    }

    private void checkToProgressGrowthInterfaceMethod()
    {
        if (!growthGate.isBlocked())
        {
            tracker.advanceGrowthTick();
        }
    }

    @Override
    public void onGameTick()
    {
        if (tracker.isTickInProgress())
        {
            tracker.updateSecondsInTick();

            Duration timeSinceLastLoad = Duration.between(lastLoadingTime, Instant.now());
            int secondsInTick = tracker.secondsInTick();
            if (secondsInTick >= 89 && nearbyPlayerCount() >= 200)
            {
                checkToProgressGrowthInterfaceMethod();
            }
            else if (secondsInTick >= 89 && Math.toIntExact(timeSinceLastLoad.getSeconds()) <= 2)
            {
                checkToProgressGrowthInterfaceMethod();
            }
        }

        tracker.checkForDuplicateGrowthTicks();

        long timeBeforeNeedingAttention = tracker.timeBeforeNeedingAttention();
        if (!attentionNotificationSend && timeBeforeNeedingAttention != 0 && timeBeforeNeedingAttention < CatTracker.ATTENTION_TIME_ONE_MINUTE_WARNING_MS)
        {
            notifier.notify(NOTIFICATION_KITTEN_WILL_RUN_AWAY_IN_1_MINUTE);
            attentionNotificationSend = true;
        }

        long timeBeforeHungry = tracker.timeBeforeHungry();
        if (!hungryNotificationSend && timeBeforeHungry != 0 && timeBeforeHungry < CatTracker.HUNGRY_TIME_ONE_MINUTE_WARNING_MS)
        {
            notifier.notify(NOTIFICATION_KITTEN_WILL_RUN_AWAY_IN_1_MINUTE);
            hungryNotificationSend = true;
        }
    }

    @Override
    public boolean onChatMessage(String message)
    {
        switch (message)
        {
            case CHAT_STROKE_CAT:
                // The original recomputes secondsInTick fresh right here rather than trusting
                // it was already refreshed by this tick's onGameTick - matched for fidelity.
                tracker.updateSecondsInTick();
                tracker.onStroke(config.catAttentionOverlay());
                return true;

            case CHAT_THE_KITTEN_GOBBLES_UP_THE_FISH:
            case CHAT_THE_KITTEN_LAPS_UP_THE_MILK:
                tracker.updateSecondsInTick();
                tracker.onFed(config.catHungryOverlay());
                return true;

            case CHAT_YOUR_KITTEN_IS_HUNGRY:
                if (config.catNotifications())
                {
                    notifier.notify(message);
                }
                tracker.onHungerFirstWarning(config.catHungryOverlay());
                return true;

            case CHAT_YOUR_KITTEN_IS_VERY_HUNGRY:
                if (config.catNotifications())
                {
                    notifier.notify(message);
                }
                tracker.onHungerFinalWarning(config.catHungryOverlay());
                return true;

            case CHAT_YOUR_KITTEN_WANTS_ATTENTION:
                if (config.catNotifications())
                {
                    notifier.notify(message);
                }
                tracker.onAttentionFirstWarning(config.catAttentionOverlay());
                return true;

            case CHAT_YOUR_KITTEN_REALLY_WANTS_ATTENTION:
                if (config.catNotifications())
                {
                    notifier.notify(message);
                }
                tracker.onAttentionFinalWarning(config.catAttentionOverlay());
                return true;

            case CHAT_YOUR_KITTEN_GOT_LONELY_AND_RAN_OFF:
            case CHAT_THE_CAT_HAS_RUN_AWAY:
                if (config.catNotifications())
                {
                    notifier.notify(message);
                }
                tracker.reset();
                previousFollowerId = 0;
                config.catFelineId(0); // in case the new kitten has the same NpcID. We need to track growth progress from the beginning.
                followerID = 0;
                return true;

            default:
                return false;
        }
    }

    @Override
    public void onDialogText(String notificationText)
    {
        if (notificationText == null)
        {
            return;
        }

        if (notificationText.equals(DIALOG_CAT_BALL_OF_WOOL))
        {
            tracker.onBallOfWool(config.catAttentionOverlay());
        }
        else if (notificationText.equals(DIALOG_GERTRUDE_GIVES_YOU_ANOTHER_KITTEN))
        {
            tracker.freshKitten();
            config.catLastAttentionType(tracker.lastAttentionType());
            config.catGrowthTicksAlive(tracker.growthTicksAlive());
            config.catNextHungryTick(tracker.nextHungryTick());
            config.catNextAttentionTick(tracker.nextAttentionTick());
        }
        else if (notificationText.equals(DIALOG_CAT_GROWN))
        {
            tracker.grownIntoCat();
            config.catGrowthTicksAlive(tracker.growthTicksAlive());
        }
        else if (notificationText.equals(DIALOG_CAT_OVERGROWN))
        {
            tracker.grownIntoOvergrown();
        }
        else if (CatTracker.isGuessAgeReply(notificationText))
        {
            Integer ticksAliveInDialog = CatTracker.parseTicksAlive(notificationText);
            if (ticksAliveInDialog != null)
            {
                if (tracker.applyGuessedTicksAlive(ticksAliveInDialog))
                {
                    log.debug("Kitten's growth ticks alive corrected to {} via guess age", ticksAliveInDialog);
                }
            }
        }
        else if (notificationText.startsWith(DIALOG_HAND_OVER_CAT_CIVILIAN))
        {
            tracker.byeFollower();
            previousFollowerId = 0;
            config.catFelineId(0);
            followerID = 0;
        }
    }

    @Override
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        // Cat interactions are read from chat/dialog text, not menu clicks.
    }

    @Override
    public void onGameStateChanged(GameState state)
    {
        switch (state)
        {
            case LOGGING_IN:
            case HOPPING:
                tracker.markLoggedIn();
                ready = true;
                break;
            case CONNECTION_LOST:
                ready = true;
                break;
            case LOGGED_IN:
                if (ready)
                {
                    ready = false;
                }
                break;
            case LOGIN_SCREEN:
                byeFollower();
                break;
            case LOADING:
                lastLoadingTime = Instant.now();
                break;
            default:
                break;
        }
    }

    @Override
    public void onConfigChanged(String key, String newValue)
    {
        // No live re-arm behaviour is needed beyond what newFollower()/advanceGrowthTick() already do
        // the next time a growth tick or follower change occurs.
    }

    @Override
    public boolean isActive()
    {
        return tracker.stage().isTracked();
    }

    @Override
    public PetStatus status()
    {
        if (!isActive() || !(tracker.isKitten() || tracker.isCat() || tracker.isOverGrown()))
        {
            return null;
        }

        if (tracker.isOverGrown() && (config.catHideWhenGrown() || !config.catOverlay()))
        {
            return null;
        }

        PetStatus status = new PetStatus(tracker.isKitten() ? "Kitten status" : "Cat status");

        if (tracker.isKitten())
        {
            if (config.catOverlay())
            {
                status.addDurationRow("Grown up in", tracker.timeUntilFullyGrown(), Color.WHITE);
            }
            if (config.catHungryOverlay())
            {
                long timeUntilHungryMs = tracker.timeBeforeHungry();
                status.addDurationRow("Hungry in", timeUntilHungryMs, hungerColor(timeUntilHungryMs));
            }
            if (config.catAttentionOverlay())
            {
                long timeBeforeNeedingAttention = tracker.timeBeforeNeedingAttention();
                status.addDurationRow("Needs attention in", timeBeforeNeedingAttention, attentionColor(timeBeforeNeedingAttention));
            }
        }
        else if (tracker.isOverGrown())
        {
            // catOverlay is guaranteed on here - the early return above already handled it off.
            status.addRow("Status", "Overgrown cat", Color.WHITE);
        }
        else
        {
            if (config.catOverlay())
            {
                status.addDurationRow("Overgrown in", tracker.timeUntilOvergrown(), Color.WHITE);
            }
        }

        return status.isEmpty() ? null : status;
    }

    /**
     * The blinking red/orange in the final minute: ~1s red, ~1s orange,
     * repeating - matching the original exactly, including that the anchor
     * is never reset outside this minute, so re-entering it later (e.g.
     * after being fed and going hungry again) always finds it stale and
     * restarts the cycle fresh via the {@code > 2 * BLINK_PERIOD_MS} branch
     * below, with no explicit reset needed anywhere else.
     */
    private Color hungerBlinkColor()
    {
        if (blinkHunger == null)
        {
            blinkHunger = Instant.now();
            return Color.WHITE;
        }
        long sinceMs = Duration.between(blinkHunger, Instant.now()).toMillis();
        if (sinceMs > 2 * BLINK_PERIOD_MS)
        {
            blinkHunger = Instant.now();
            return Color.ORANGE;
        }
        return sinceMs > BLINK_PERIOD_MS ? Color.ORANGE : Color.RED;
    }

    private Color attentionBlinkColor()
    {
        if (blinkAttention == null)
        {
            blinkAttention = Instant.now();
            return Color.WHITE;
        }
        long sinceMs = Duration.between(blinkAttention, Instant.now()).toMillis();
        if (sinceMs > 2 * BLINK_PERIOD_MS)
        {
            blinkAttention = Instant.now();
            return Color.ORANGE;
        }
        return sinceMs > BLINK_PERIOD_MS ? Color.ORANGE : Color.RED;
    }

    private Color hungerColor(long remainingMs)
    {
        if (remainingMs < CatTracker.HUNGRY_TIME_ONE_MINUTE_WARNING_MS)
        {
            return hungerBlinkColor();
        }
        else if (remainingMs < CatTracker.HUNGRY_FINAL_WARNING_TIME_LEFT_IN_SECONDS * 1000L)
        {
            return Color.RED;
        }
        else if (remainingMs < CatTracker.HUNGRY_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000L)
        {
            return Color.ORANGE;
        }
        return Color.WHITE;
    }

    private Color attentionColor(long remainingMs)
    {
        if (remainingMs < CatTracker.ATTENTION_TIME_ONE_MINUTE_WARNING_MS)
        {
            return attentionBlinkColor();
        }
        else if (remainingMs < CatTracker.ATTENTION_FINAL_WARNING_TIME_LEFT_IN_SECONDS * 1000L)
        {
            return Color.RED;
        }
        else if (remainingMs < CatTracker.ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000L)
        {
            return Color.ORANGE;
        }
        return Color.WHITE;
    }
}
