package net.dv8tion.runelite.petgrowthtracker.dog;

import net.dv8tion.runelite.petgrowthtracker.PetStatus;
import net.dv8tion.runelite.petgrowthtracker.PetTrackerConfig;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DogPetHandlerTest
{
    private final Client client = mock(Client.class);
    private final NPC follower = mock(NPC.class);
    private final Notifier notifier = mock(Notifier.class);
    private final PetTrackerConfig config = mock(PetTrackerConfig.class);
    private final DogPetHandler handler = new DogPetHandler(client, notifier, config);

    @Before
    public void setUp()
    {
        when(client.getFollower()).thenReturn(follower);
        when(follower.getName()).thenReturn("Puppy");
        when(config.dogOverlay()).thenReturn(true);
        when(config.dogFeedOverlay()).thenReturn(true);
        when(config.dogNotifications()).thenReturn(true);
    }

    private void tick(int growth, int fed)
    {
        when(client.getVarbitValue(VarbitID.PUPPY_GROWTH_TRACKER)).thenReturn(growth);
        when(client.getVarbitValue(VarbitID.PUPPY_FED_TIMER)).thenReturn(fed);
        handler.onGameTick();
    }

    @Test
    public void grownDogToNewPuppyRestoresOverlayAndNotifications()
    {
        handler.onFollowerChanged(NpcID.LABRADOR_YELLOW);
        tick(180, 30);
        assertEquals("Dog status", handler.status().getTitle());
        verify(notifier).notify("Your puppy has grown into a dog");
        clearInvocations(notifier);

        handler.onFollowerChanged(0);
        assertNull(handler.status());
        handler.onFollowerChanged(NpcID.LABRADOR_YELLOW_PUPPY);
        assertNull(handler.status()); // Wait for fresh server values before showing the new follower.
        tick(7, 0);
        PetStatus status = handler.status();
        assertEquals("Puppy status", status.getTitle());
        assertEquals(173 * 60_000L, status.getRows().get(0).getDurationMillis());
        verifyNoInteractions(notifier);

        tick(8, 15);
        tick(8, 15);
        verify(notifier, times(1)).notify("Your puppy is getting quite hungry.");
        tick(9, 25);
        tick(9, 25);
        verify(notifier, times(1)).notify("Your puppy is very hungry.");
        tick(10, 30);
        tick(10, 30);
        assertEquals("stopped growing", handler.status().getRows().get(1).getText());
        verify(notifier, times(1)).notify("Your puppy has stopped growing. You'll need to feed it for it to continue doing so.");
        tick(10, 0);
        tick(11, 15);
        verify(notifier, times(2)).notify("Your puppy is getting quite hungry.");
        tick(180, 0);
        tick(180, 0);
        verify(notifier, times(1)).notify("Your puppy has grown into a dog");
    }

    @Test
    public void directSwitchRearmsWarningsAndHonorsConfiguration()
    {
        handler.onFollowerChanged(NpcID.LABRADOR_YELLOW_PUPPY);
        tick(180, 0);
        handler.onFollowerChanged(NpcID.LABRADOR_YELLOW_PUPPY);
        tick(20, 15);
        assertEquals("Puppy status", handler.status().getTitle());
        verify(notifier).notify("Your puppy is getting quite hungry.");
        when(config.dogNotifications()).thenReturn(false);
        tick(21, 25);
        tick(22, 30);
        tick(180, 0);
        verify(notifier, times(1)).notify("Your puppy has grown into a dog");
        verifyNoMoreInteractions(notifier);
        when(config.dogHideWhenGrown()).thenReturn(true);
        assertNull(handler.status());
        handler.onFollowerChanged(NpcID.LABRADOR_YELLOW_PUPPY);
        tick(60, 0);
        assertEquals(120 * 60_000L, handler.status().getRows().get(0).getDurationMillis());
    }
}
