package net.dv8tion.runelite.petgrowthtracker.dog;

/**
 * A puppy's growth, read straight from the server rather than inferred.
 *
 * The account carries two varbits for the currently-followed puppy -
 * {@code VarbitID.PUPPY_GROWTH_TRACKER} (active-growth minutes so far) and
 * {@code VarbitID.PUPPY_FED_TIMER} (minutes since last fed) - confirmed
 * directly against the real client jar: both increment together while
 * growing, the growth tracker alone halts once the puppy needs feeding, and
 * feeding resets the fed timer to zero.
 *
 * An earlier version of this class instead banked growth in milliseconds and
 * inferred pauses from feed timestamps, the kind of workaround the cat side
 * still needs because no equivalent varbit exists for kittens. With the
 * server doing the accounting, there is nothing left to reconstruct: no
 * banked-growth clock to accrue, no "Guess age" dialog to parse, no
 * menu-click feed detection or refusal-window rollback (the fed timer is a
 * server value that only a real, successful feed resets), and no needing to
 * know a growth-blocking interface list at all - see
 * {@link DogPetHandler#onGameTick()}, which simply polls both varbits every
 * tick.
 *
 * Pause detection reads {@code fedMinutes} directly against a fixed cutoff,
 * rather than watching the growth tracker for a stall (an even earlier
 * version of this class did that instead). That was more "self-calibrating"
 * in theory, but it made the whole thing exactly as laggy as the growth
 * tracker's own once-a-minute cadence in <em>both</em> directions: slow to
 * notice a pause, and - what testing actually surfaced - just as slow to
 * notice a feed had ended one, since resuming still had to wait for the
 * counter to visibly tick over again. {@code fedMinutes} itself updates the
 * instant the server registers a feed, so reading it directly is both
 * simpler and immediately responsive.
 *
 * The three minute thresholds below are the OSRS Wiki's, read verbatim from
 * <a href="https://oldschool.runescape.wiki/w/Puppy#Hunger">the Puppy
 * page's Hunger section</a>: "Your puppy is getting quite hungry" at 15
 * minutes, "Your puppy is very hungry" at 25, and "Your puppy has stopped
 * growing..." at 30.
 *
 * Holds no RuneLite types, so it's exercised with plain numbers rather than
 * a live client.
 */
class DogTracker
{
    /** Active growth needed to become a dog: three hours, i.e. 180 minutes on the growth-tracker varbit. */
    static final int GROWTH_MINUTES = 180;

    /** Minutes unfed before "Your puppy is getting quite hungry." */
    static final int HUNGRY_WARNING_MINUTES = 15;

    /** Minutes unfed before "Your puppy is very hungry." */
    static final int VERY_HUNGRY_WARNING_MINUTES = 25;

    /** Minutes unfed before "Your puppy has stopped growing..." - growth pauses until the next feed. */
    static final int FEED_PAUSE_MINUTES = 30;

    private int growthMinutes;
    private int fedMinutes;
    private boolean fullyGrown;
    private boolean following;
    private String petName;

    /** Called every tick a puppy follower is present, with the two varbits read fresh. */
    void update(int growthMinutes, int fedMinutes)
    {
        this.growthMinutes = growthMinutes;
        this.fedMinutes = fedMinutes;

        if (growthMinutes >= GROWTH_MINUTES)
        {
            fullyGrown = true;
        }
    }

    void setFollowing(boolean following, String petName)
    {
        this.following = following;
        if (petName != null && !petName.isEmpty())
        {
            this.petName = petName;
        }
    }

    /** The NPC id itself says this one already finished growing - no varbit needed to know that. */
    void grownUp()
    {
        fullyGrown = true;
        growthMinutes = GROWTH_MINUTES;
    }

    boolean isFollowing()
    {
        return following;
    }

    boolean isFullyGrown()
    {
        return fullyGrown;
    }

    String petName()
    {
        return petName == null ? "Puppy" : petName;
    }

    int fedMinutes()
    {
        return fedMinutes;
    }

    /** True once unfed for {@link #FEED_PAUSE_MINUTES} - growth has stopped until the next feed. */
    boolean isGrowthPaused()
    {
        return !fullyGrown && fedMinutes >= FEED_PAUSE_MINUTES;
    }

    /** True from {@link #HUNGRY_WARNING_MINUTES} - "Your puppy is getting quite hungry." */
    boolean isHungry()
    {
        return !fullyGrown && fedMinutes >= HUNGRY_WARNING_MINUTES;
    }

    /** True from {@link #VERY_HUNGRY_WARNING_MINUTES} - "Your puppy is very hungry." */
    boolean isVeryHungry()
    {
        return !fullyGrown && fedMinutes >= VERY_HUNGRY_WARNING_MINUTES;
    }

    /** Remaining active growth in milliseconds; 0 once fully grown. */
    long untilGrownMillis()
    {
        return Math.max(0L, (long) (GROWTH_MINUTES - growthMinutes) * 60_000L);
    }

    /** Milliseconds until growth pauses from hunger; 0 once it already has. */
    long untilPausedMillis()
    {
        return Math.max(0L, (long) (FEED_PAUSE_MINUTES - fedMinutes) * 60_000L);
    }
}
