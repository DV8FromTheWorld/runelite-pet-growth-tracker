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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The kitten/cat growth, hunger and attention clocks - every number and rule
 * here is ported unchanged from the original Kitten Tracker's
 * {@code KittenPlugin}, just pulled out of the RuneLite adapter so this
 * class holds no {@code Client}/{@code Notifier}/config dependency, the same
 * shape as {@code net.dv8tion.runelite.petgrowthtracker.dog.DogTracker}. {@link CatPetHandler} is
 * the thin RuneLite adapter on top: it feeds this class the two signals that
 * genuinely need {@code Client} - overhead text arriving, and whether a
 * growth-blocking interface is open (see {@link CatGrowthGate}) - and reads
 * back countdowns for the overlay.
 *
 * Unlike a puppy's continuous banked-growth clock, a cat's growth advances in
 * discrete 90-second ticks that an external signal (overhead text, or the
 * high-population interface fallback) confirms one at a time; nothing here
 * advances on its own; {@link CatGrowthGate} and {@link CatPetHandler} decide
 * when to call {@link #advanceGrowthTick()}.
 */
class CatTracker
{
    public static final int HUNGRY_FIRST_WARNING_TIME_LEFT_IN_SECONDS = 6 * 60; // 6 MINUTES
    public static final int HUNGRY_FINAL_WARNING_TIME_LEFT_IN_SECONDS = 3 * 60; // 3 MINUTES
    public static final long HUNGRY_TIME_ONE_MINUTE_WARNING_MS = 60 * 1000; // 1 MINUTE
    private static final int HUNGRY_TIME_BEFORE_KITTEN_RUNS_AWAY_IN_SECONDS = 30 * 60; // 30 MINUTES

    public static final int ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS = 6 * 90; // 9 MINUTES
    public static final int ATTENTION_FINAL_WARNING_TIME_LEFT_IN_SECONDS = 3 * 90; // 4.5 MINUTES
    public static final long ATTENTION_TIME_ONE_MINUTE_WARNING_MS = 60 * 1000; // 1 MINUTE
    public static final int ATTENTION_TIME_NEW_KITTEN_IN_SECONDS = 17 * 90; // 25.5 MINUTES
    public static final int ATTENTION_TIME_SINGLE_STROKE_IN_SECONDS = 18 * 60; // 18 MINUTES
    public static final int ATTENTION_TIME_MULTIPLE_STROKES_IN_SECONDS = 15 * 90; // 22.5 MINUTES
    public static final int ATTENTION_TIME_BALL_OF_WOOL_IN_SECONDS = 51 * 60; // 51 MINUTES
    private static final int ATTENTION_TIME_BEFORE_KITTEN_RUNS_AWAY_BALL_OF_WOOL_IN_SECONDS = ATTENTION_TIME_BALL_OF_WOOL_IN_SECONDS + ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS; // 61.5 MINUTES

    private static final int TIME_TO_ADULTHOOD_IN_SECONDS = 3 * 3600; // 3 HOURS
    private static final int TIME_TILL_OVERGROWN_IN_SECONDS = (int) (2.5 * 3600); // 2-3 HOURS -> 2.5 HOURS

    static final int GROWTH_TICK_IN_SECONDS = 90;  // kitten can only progress growth once every 90s.
    private static final int TICKS_TO_ADULTHOOD = 120; // 3 hours - each growth tick is 90 seconds.
    private static final int TICKS_TO_OVERGROWN = 100;  // 2.5 hours - assuming above timing is correct.
    private static final int TICKS_TO_HUNGER_RUN_AWAY = 20; // 30 min - each growth tick is 90 seconds.
    private static final int TICKS_HUNGER_FIRST_WARNING = 4; // 6 min
    private static final int TICKS_HUNGER_FINAL_WARNING = 2; // 3 min

    /*  See notes on attention timers/notifications in the README.  tl;dr: it's variable
    based on a few factors, but testing seems consistent, so it could be "solved."
    For now, I'm adding in 3 ticks as an "expected" delay - that is what happens next after being fed & stroked 2x
    at the same time, then waiting for notifications for your cat to tell you it needs food/attention,
    which seems like it would be the most common case.  Most people won't probably proactively feed
    their kitten before getting the notification.  I'll only add this in for the multiple stroke constant,
    as the others will likely not be synced up with the hunger timer as closely.
     */
    private static final int EXPECTED_ATTENTION_DELAY_TICKS = 3;

    /* 36 min - each growth tick is 90s.  Normally it *should* be 21 ticks/31.5 mins, but hunger notifications often delay this.
    should the hunger notification delays be "solved", this could get adjusted in the future, it's just a work-around
    for now. */
    static final int TICKS_TO_ATTENTION_RUN_AWAY_MULTIPLE_STROKES = 21 + EXPECTED_ATTENTION_DELAY_TICKS;
    private static final int TICKS_TO_ATTENTION_RUN_AWAY_SINGLE_STROKE = 16; // 24m - it's variable so this isn't exactly accurate.
    static final int TICKS_TO_ATTENTION_RUN_AWAY_BALL_OF_WOOL = 40; // 60
    private static final int TICKS_ATTENTION_FIRST_WARNING = 6; // 9m
    private static final int TICKS_ATTENTION_FINAL_WARNING = 3; // 4.5m

    /** A boxed end time - all the original's {@code Timer} objects were ever used for, see {@link CatPetHandler}. */
    static final class Countdown
    {
        private Instant endTime;

        /** Matches the original's guard: seconds &lt;= 0 leaves whatever end time was already set. */
        void set(int seconds)
        {
            if (seconds <= 0)
            {
                return;
            }
            endTime = Instant.now().plusSeconds(seconds);
        }

        boolean isSet()
        {
            return endTime != null;
        }

        long remainingMillis()
        {
            if (endTime == null)
            {
                return 0L;
            }
            return Math.max(0L, Duration.between(Instant.now(), endTime).toMillis());
        }
    }

    private FollowerKind stage = FollowerKind.NON_FELINE;

    private Instant kittenLastAttentionTime;
    private int timeNeglected = 0;
    private CatAttentionType lastAttentionType = CatAttentionType.NEW_KITTEN;

    private int growthTicksAlive = 0;
    private int nextHungryTick = 0;
    private int nextAttentionTick = 0;
    private int secondsInTick = 0;
    private Instant growthTickStartTime = null;
    private final List<Instant> growthTimes = new ArrayList<>();
    // the first growth tick can be very early upon login - probably something to do with loading the game or plugin.
    private boolean noGrowthSinceLoggedIn = true;

    private final Countdown growthCountdown = new Countdown();
    private final Countdown hungryCountdown = new Countdown();
    private final Countdown attentionCountdown = new Countdown();

    // ---------------------------------------------------------------- state

    FollowerKind stage()
    {
        return stage;
    }

    boolean isKitten()
    {
        return stage == FollowerKind.KITTEN;
    }

    boolean isCat()
    {
        return stage == FollowerKind.NORMAL_CAT;
    }

    boolean isOverGrown()
    {
        return stage == FollowerKind.OVERGROWN_CAT;
    }

    int growthTicksAlive()
    {
        return growthTicksAlive;
    }

    int nextHungryTick()
    {
        return nextHungryTick;
    }

    int nextAttentionTick()
    {
        return nextAttentionTick;
    }

    CatAttentionType lastAttentionType()
    {
        return lastAttentionType;
    }

    /** Recomputed each time the handler observes elapsed time; needed for the "paused at 90s" special case below. */
    void updateSecondsInTick()
    {
        if (growthTickStartTime == null)
        {
            secondsInTick = 0;
            return;
        }
        secondsInTick = Math.toIntExact(Duration.between(growthTickStartTime, Instant.now()).getSeconds());
    }

    int secondsInTick()
    {
        return secondsInTick;
    }

    boolean isTickInProgress()
    {
        return growthTickStartTime != null;
    }

    boolean noGrowthSinceLoggedIn()
    {
        return noGrowthSinceLoggedIn;
    }

    void markLoggedIn()
    {
        noGrowthSinceLoggedIn = true;
    }

    // ------------------------------------------------------------- follower

    /** A kitten or cat follower just appeared. */
    void newFollower(FollowerKind kind, boolean sameFollowerAsBefore,
                      int savedGrowthTicksAlive, int savedNextHungryTick, int savedNextAttentionTick,
                      CatAttentionType savedLastAttentionType)
    {
        this.stage = kind;

        switch (kind)
        {
            case KITTEN:
                if (sameFollowerAsBefore)
                {
                    // no need to subtract secondsInTick below - it's zero by definition, just logged into a new tick
                    secondsInTick = 0;
                    growthTicksAlive = savedGrowthTicksAlive;
                    nextHungryTick = savedNextHungryTick;
                    nextAttentionTick = savedNextAttentionTick;
                    growthTickStartTime = Instant.now();
                    lastAttentionType = savedLastAttentionType;

                    addGrowthCountdown((TICKS_TO_ADULTHOOD - growthTicksAlive) * GROWTH_TICK_IN_SECONDS);
                    addAttentionCountdown((nextAttentionTick - growthTicksAlive) * 90);
                    addHungryCountdown((nextHungryTick - growthTicksAlive) * 90);
                }
                else
                {
                    lastAttentionType = CatAttentionType.NEW_KITTEN;
                    kittenLastAttentionTime = Instant.now();

                    addGrowthCountdown(TIME_TO_ADULTHOOD_IN_SECONDS);
                    addHungryCountdown(HUNGRY_TIME_BEFORE_KITTEN_RUNS_AWAY_IN_SECONDS);
                    addAttentionCountdown(ATTENTION_TIME_MULTIPLE_STROKES_IN_SECONDS + ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS);

                    growthTicksAlive = 0;
                    growthTickStartTime = Instant.now();
                    nextHungryTick = TICKS_TO_HUNGER_RUN_AWAY;
                    nextAttentionTick = TICKS_TO_ATTENTION_RUN_AWAY_MULTIPLE_STROKES;
                }
                break;
            case NORMAL_CAT:
                if (sameFollowerAsBefore)
                {
                    growthTicksAlive = savedGrowthTicksAlive;
                    growthTickStartTime = Instant.now();
                    addGrowthCountdown(((TICKS_TO_ADULTHOOD + TICKS_TO_OVERGROWN) - growthTicksAlive) * GROWTH_TICK_IN_SECONDS);
                }
                else
                {
                    growthTickStartTime = Instant.now();
                    addGrowthCountdown(TIME_TILL_OVERGROWN_IN_SECONDS);
                }
                break;
            case LAZY_CAT:
            case WILY_CAT:
            case OVERGROWN_CAT:
            case NON_FELINE:
                break;
        }
    }

    void byeFollower()
    {
        growthTickStartTime = null;
        kittenLastAttentionTime = null;
        stage = FollowerKind.NON_FELINE;
    }

    // --------------------------------------------------------------- growth

    private void addGrowthCountdown(int seconds)
    {
        growthCountdown.set(seconds);
    }

    private void addHungryCountdown(int seconds)
    {
        hungryCountdown.set(seconds);
    }

    private void addAttentionCountdown(int seconds)
    {
        attentionCountdown.set(seconds);
    }

    /** Called once an external signal (overhead text, or the interface fallback) confirms growth may proceed. */
    void advanceGrowthTick()
    {
        growthTickStartTime = Instant.now();
        growthTicksAlive += 1;
        if (isKitten())
        {
            addGrowthCountdown((TICKS_TO_ADULTHOOD - growthTicksAlive) * GROWTH_TICK_IN_SECONDS);
            addAttentionCountdown((nextAttentionTick - growthTicksAlive) * 90);
            addHungryCountdown((nextHungryTick - growthTicksAlive) * 90);
        }
        else if (isCat())
        {
            addGrowthCountdown((TICKS_TO_OVERGROWN + TICKS_TO_ADULTHOOD - growthTicksAlive) * GROWTH_TICK_IN_SECONDS);
        }
        growthTimes.add(growthTickStartTime);
        noGrowthSinceLoggedIn = false;
    }

    /**
     * Corrects for two growth-detection signals firing on the same tick (see
     * {@link CatPetHandler} - this can happen when a high-population fallback
     * check and a teleport both land on the tick the kitten was already due
     * to grow).
     */
    void checkForDuplicateGrowthTicks()
    {
        if (growthTimes.size() <= 1)
        {
            return;
        }
        while (growthTimes.size() > 3)
        {
            growthTimes.remove(0);
        }
        int growthTicksToSubtract = 0;
        for (int i = 0; i < growthTimes.size() - 1; i++)
        {
            if (Math.toIntExact(Duration.between(growthTimes.get(i), growthTimes.get(i + 1)).getSeconds()) < 2)
            {
                growthTicksToSubtract += 1;
            }
        }
        while (growthTimes.size() > 1)
        {
            growthTimes.remove(0);
        }
        if (growthTicksToSubtract > 0)
        {
            subtractGrowthTicks(growthTicksToSubtract);
        }
    }

    private void subtractGrowthTicks(int numTicksToRemove)
    {
        growthTicksAlive -= numTicksToRemove;
        if (isKitten())
        {
            addGrowthCountdown((TICKS_TO_ADULTHOOD - growthTicksAlive) * GROWTH_TICK_IN_SECONDS - secondsInTick);
            addAttentionCountdown((nextAttentionTick - growthTicksAlive) * 90 - secondsInTick);
            addHungryCountdown((nextHungryTick - growthTicksAlive) * 90 - secondsInTick);
        }
        else if (isCat())
        {
            addGrowthCountdown((TICKS_TO_OVERGROWN + TICKS_TO_ADULTHOOD - growthTicksAlive) * GROWTH_TICK_IN_SECONDS - secondsInTick);
        }
    }

    // ------------------------------------------------------------ attention

    /**
     * The player stroked the cat ("You softly stroke your cat.").
     *
     * {@code attentionOverlayEnabled} mirrors the original exactly: when the
     * attention overlay is turned off, {@code lastAttentionType} still
     * updates (so a later re-enable resumes from the right state), but the
     * attention run-away target and its countdown are left untouched.
     */
    void onStroke(boolean attentionOverlayEnabled)
    {
        if (kittenLastAttentionTime != null)
        {
            long timeSinceLastAttentionSeconds = Duration.between(kittenLastAttentionTime, Instant.now()).toMillis() / 1000 + timeNeglected;

            int maxTimePastForMultiStrokeSeconds = -1;
            if (lastAttentionType == CatAttentionType.SINGLE_STROKE || lastAttentionType == CatAttentionType.MULTIPLE_STROKES)
            {
                maxTimePastForMultiStrokeSeconds = lastAttentionType.getAttentionTime();
            }

            if (timeSinceLastAttentionSeconds < maxTimePastForMultiStrokeSeconds)
            {
                if (attentionOverlayEnabled)
                {
                    nextAttentionTick = growthTicksAlive + TICKS_TO_ATTENTION_RUN_AWAY_MULTIPLE_STROKES;
                    addAttentionCountdown((nextAttentionTick - growthTicksAlive) * 90 - secondsInTick);
                }
                lastAttentionType = CatAttentionType.MULTIPLE_STROKES;
            }
            else
            {
                if (attentionOverlayEnabled)
                {
                    nextAttentionTick = growthTicksAlive + TICKS_TO_ATTENTION_RUN_AWAY_SINGLE_STROKE;
                    addAttentionCountdown((nextAttentionTick - growthTicksAlive) * 90 - secondsInTick);
                }
                lastAttentionType = CatAttentionType.SINGLE_STROKE;
            }
        }
        else
        {
            if (attentionOverlayEnabled)
            {
                nextAttentionTick = growthTicksAlive + TICKS_TO_ATTENTION_RUN_AWAY_SINGLE_STROKE;
                addAttentionCountdown((nextAttentionTick - growthTicksAlive) * 90 - secondsInTick);
            }
            lastAttentionType = CatAttentionType.SINGLE_STROKE;
        }
        kittenLastAttentionTime = Instant.now();
        timeNeglected = 0;
    }

    /** Ball of wool play - both the run-away target and its countdown are gated together, matching the original. */
    void onBallOfWool(boolean attentionOverlayEnabled)
    {
        kittenLastAttentionTime = Instant.now();
        if (attentionOverlayEnabled)
        {
            nextAttentionTick = growthTicksAlive + TICKS_TO_ATTENTION_RUN_AWAY_BALL_OF_WOOL;
            addAttentionCountdown(ATTENTION_TIME_BEFORE_KITTEN_RUNS_AWAY_BALL_OF_WOOL_IN_SECONDS - secondsInTick);
        }
        timeNeglected = 0;
        lastAttentionType = CatAttentionType.BALL_OF_WOOL;
    }

    /** The game's own 9-minute attention warning; the run-away target always advances, only the countdown display is gated. */
    void onAttentionFirstWarning(boolean attentionOverlayEnabled)
    {
        nextAttentionTick = growthTicksAlive + TICKS_ATTENTION_FIRST_WARNING;
        if (attentionOverlayEnabled)
        {
            addAttentionCountdown((nextAttentionTick - growthTicksAlive) * 90 - secondsInTick);
        }
    }

    /** The game's own 4.5-minute attention warning; same gating shape as {@link #onAttentionFirstWarning}. */
    void onAttentionFinalWarning(boolean attentionOverlayEnabled)
    {
        nextAttentionTick = growthTicksAlive + TICKS_ATTENTION_FINAL_WARNING;
        if (attentionOverlayEnabled)
        {
            addAttentionCountdown((nextAttentionTick - growthTicksAlive) * 90 - secondsInTick);
        }
    }

    // --------------------------------------------------------------- hunger

    /**
     * The kitten ate ("gobbles up the fish"/"laps up the milk"). Gated
     * entirely on the hunger overlay, matching the original: with the
     * overlay off, a feed doesn't move the run-away target at all.
     */
    void onFed(boolean hungryOverlayEnabled)
    {
        if (!hungryOverlayEnabled)
        {
            return;
        }
        nextHungryTick = growthTicksAlive + TICKS_TO_HUNGER_RUN_AWAY;
        addHungryCountdown(TICKS_TO_HUNGER_RUN_AWAY * 90 - secondsInTick);
    }

    /** The game's own 6-minute hunger warning; the run-away target always advances, only the countdown display is gated. */
    void onHungerFirstWarning(boolean hungryOverlayEnabled)
    {
        if (hungryOverlayEnabled)
        {
            addHungryCountdown(HUNGRY_FIRST_WARNING_TIME_LEFT_IN_SECONDS - secondsInTick);
        }
        nextHungryTick = growthTicksAlive + TICKS_HUNGER_FIRST_WARNING;
    }

    /** The game's own 3-minute hunger warning; same gating shape as {@link #onHungerFirstWarning}. */
    void onHungerFinalWarning(boolean hungryOverlayEnabled)
    {
        if (hungryOverlayEnabled)
        {
            addHungryCountdown(HUNGRY_FINAL_WARNING_TIME_LEFT_IN_SECONDS - secondsInTick);
        }
        nextHungryTick = growthTicksAlive + TICKS_HUNGER_FINAL_WARNING;
    }

    // ------------------------------------------------------------- lifecycle

    /** The kitten ran away, or was turned in for a reward - start over. */
    void reset()
    {
        growthTickStartTime = null;
        nextHungryTick = TICKS_TO_HUNGER_RUN_AWAY;
        nextAttentionTick = TICKS_TO_ATTENTION_RUN_AWAY_MULTIPLE_STROKES;
        growthTicksAlive = 0;
        kittenLastAttentionTime = null;
        stage = FollowerKind.NON_FELINE;
    }

    /** Gertrude handed over a brand new kitten. */
    void freshKitten()
    {
        stage = FollowerKind.KITTEN;
        lastAttentionType = CatAttentionType.NEW_KITTEN;
        kittenLastAttentionTime = Instant.now();

        growthTicksAlive = 0;
        growthTickStartTime = Instant.now();
        nextHungryTick = TICKS_TO_HUNGER_RUN_AWAY;
        nextAttentionTick = TICKS_TO_ATTENTION_RUN_AWAY_MULTIPLE_STROKES;
        addGrowthCountdown(TIME_TO_ADULTHOOD_IN_SECONDS);
        addHungryCountdown(HUNGRY_TIME_BEFORE_KITTEN_RUNS_AWAY_IN_SECONDS);
        addAttentionCountdown(ATTENTION_TIME_MULTIPLE_STROKES_IN_SECONDS + ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS);
    }

    void grownIntoCat()
    {
        stage = FollowerKind.NORMAL_CAT;
        growthTicksAlive = TICKS_TO_ADULTHOOD;
    }

    void grownIntoOvergrown()
    {
        stage = FollowerKind.OVERGROWN_CAT;
    }

    // ------------------------------------------------------------ guess age

    private static final String AGE_MARKER = "After taking a good look at your kitten you guess that its age is: ";

    /** True when this dialog line looks like the reply to "Guess age". */
    static boolean isGuessAgeReply(String dialogText)
    {
        return dialogText != null && dialogText.startsWith(AGE_MARKER);
    }

    /**
     * Parses the ticks-alive the game just told us, from the exact wording
     * of the "After taking a good look..." dialog. Returns {@code null} if
     * the text couldn't be parsed - the caller should leave the clocks alone
     * in that case, same as the original.
     */
    static Integer parseTicksAlive(String dialogText)
    {
        String ageStr = dialogText.substring(AGE_MARKER.length());
        int end = ageStr.indexOf("And approximate time until");
        if (end < 0)
        {
            return null;
        }
        ageStr = ageStr.substring(0, end);

        int hoursIndex = ageStr.indexOf("hours");
        if (hoursIndex < 0)
        {
            hoursIndex = ageStr.indexOf("hour");
        }

        String hoursStr = "";
        if (hoursIndex > 0)
        {
            hoursStr = ageStr.substring(0, hoursIndex).trim();
        }

        int minutesIndex = ageStr.indexOf("minutes");
        if (minutesIndex < 0)
        {
            minutesIndex = ageStr.indexOf("minute");
        }

        String minutesStr = "";
        if (minutesIndex > 0)
        {
            if (hoursIndex > 0)
            {
                minutesStr = ageStr.substring(hoursIndex + "hours".length(), minutesIndex).trim();
            }
            else
            {
                minutesStr = ageStr.substring(0, minutesIndex).trim();
            }
        }

        int hours = parseIntOrZero(hoursStr);
        int minutes = parseIntOrZero(minutesStr);

        int ageMinutes = (hours * 60) + minutes;
        int ageSeconds;
        if (ageMinutes / 1.5 != 0)
        {
            ageSeconds = ageMinutes * 60 + 30;
        }
        else
        {
            ageSeconds = ageMinutes * 60;
        }

        return ageSeconds / 90;
    }

    private static int parseIntOrZero(String value)
    {
        if (value == null || value.isEmpty())
        {
            return 0;
        }
        try
        {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    /** Applies a guess-age result if it disagrees with what we've been tracking. Returns whether it changed anything. */
    boolean applyGuessedTicksAlive(int ticksAliveInDialog)
    {
        if (ticksAliveInDialog == growthTicksAlive)
        {
            return false;
        }

        growthTicksAlive = ticksAliveInDialog;

        if (nextAttentionTick - growthTicksAlive > TICKS_TO_ATTENTION_RUN_AWAY_BALL_OF_WOOL ||
                nextHungryTick - growthTicksAlive > TICKS_TO_HUNGER_RUN_AWAY)
        {
            nextAttentionTick = TICKS_TO_ATTENTION_RUN_AWAY_MULTIPLE_STROKES;
            nextHungryTick = TICKS_TO_HUNGER_RUN_AWAY;
        }

        if (secondsInTick >= 90)
        {
            addGrowthCountdown((TICKS_TO_ADULTHOOD - growthTicksAlive - 1) * 90);
            addAttentionCountdown((nextAttentionTick - growthTicksAlive - 1) * 90);
            addHungryCountdown((nextHungryTick - growthTicksAlive - 1) * 90);
        }
        else
        {
            addGrowthCountdown((TICKS_TO_ADULTHOOD - growthTicksAlive) * 90 - secondsInTick);
            addAttentionCountdown((nextAttentionTick - growthTicksAlive) * 90 - secondsInTick);
            addHungryCountdown((nextHungryTick - growthTicksAlive) * 90 - secondsInTick);
        }
        return true;
    }

    // ------------------------------------------------------------ countdowns

    long timeUntilFullyGrown()
    {
        if (!growthCountdown.isSet() || !isKitten())
        {
            return 0L;
        }
        if (secondsInTick > 90)
        {
            return Math.max(0L, (long) (TICKS_TO_ADULTHOOD - growthTicksAlive - 1) * 90 * 1000);
        }
        return growthCountdown.remainingMillis();
    }

    long timeUntilOvergrown()
    {
        if (!growthCountdown.isSet())
        {
            return 0L;
        }
        if (secondsInTick >= 90)
        {
            return Math.max(0L, (long) (TICKS_TO_ADULTHOOD + TICKS_TO_OVERGROWN - growthTicksAlive - 1) * 90 * 1000);
        }
        return growthCountdown.remainingMillis();
    }

    long timeBeforeHungry()
    {
        if (!hungryCountdown.isSet() || !isKitten())
        {
            return 0L;
        }
        if (secondsInTick >= 90)
        {
            return Math.max(0L, (long) (nextHungryTick - growthTicksAlive - 1) * 90 * 1000);
        }
        return hungryCountdown.remainingMillis();
    }

    long timeBeforeNeedingAttention()
    {
        if (!attentionCountdown.isSet() || !isKitten())
        {
            return 0L;
        }
        if (secondsInTick >= 90)
        {
            return Math.max(0L, (long) (nextAttentionTick - growthTicksAlive - 1) * 90 * 1000);
        }
        return attentionCountdown.remainingMillis();
    }
}
