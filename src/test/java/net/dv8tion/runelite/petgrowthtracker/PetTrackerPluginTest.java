package net.dv8tion.runelite.petgrowthtracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PetTrackerPluginTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(PetTrackerPlugin.class);
        RuneLite.main(args);
    }
}
