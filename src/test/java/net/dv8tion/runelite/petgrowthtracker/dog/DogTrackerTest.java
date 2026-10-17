package net.dv8tion.runelite.petgrowthtracker.dog;

import org.junit.Test;

import static org.junit.Assert.*;

public class DogTrackerTest
{
    @Test
    public void lowerServerGrowthClearsCompletedState()
    {
        DogTracker tracker = new DogTracker();
        tracker.update(180, 30);
        assertTrue(tracker.isFullyGrown());
        tracker.update(12, 15);
        assertFalse(tracker.isFullyGrown());
        assertEquals(168 * 60_000L, tracker.untilGrownMillis());
        assertTrue(tracker.isHungry());
    }

    @Test
    public void resetDiscardsAllPreviousFollowerState()
    {
        DogTracker tracker = new DogTracker();
        tracker.setFollowing(true, "Old dog");
        tracker.update(180, 30);
        tracker.reset();
        assertFalse(tracker.isFollowing());
        assertFalse(tracker.isFullyGrown());
        assertEquals("Puppy", tracker.petName());
        assertEquals(0, tracker.fedMinutes());
        assertEquals(180 * 60_000L, tracker.untilGrownMillis());
        tracker.update(42, 30);
        assertEquals(138 * 60_000L, tracker.untilGrownMillis());
        assertTrue(tracker.isGrowthPaused());
        tracker.update(42, 0);
        assertFalse(tracker.isGrowthPaused());
        tracker.update(43, 1);
        assertEquals(137 * 60_000L, tracker.untilGrownMillis());
    }
}
