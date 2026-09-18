package net.dv8tion.runelite.petgrowthtracker.cat;

import net.dv8tion.runelite.petgrowthtracker.PetLifeStage;
import net.runelite.api.gameval.NpcID;

/**
 * Which feline life-stage a follower NPC id belongs to, or
 * {@link #NON_FELINE} if it isn't a cat/kitten at all - which is also how
 * {@code PetTrackerPlugin} decides a follower belongs to the dog side
 * instead.
 */
public enum FollowerKind implements PetLifeStage {
    NORMAL_CAT, LAZY_CAT, WILY_CAT, KITTEN, OVERGROWN_CAT, NON_FELINE;

    public static FollowerKind getFromFollowerId(int followerId) {
        if (followerId >= NpcID.GROWNCAT && followerId <= NpcID.GROWNCAT_HELL) {
            return NORMAL_CAT;
        } else if (followerId >= NpcID.LAZYCAT_LIGHT && followerId <= NpcID.LAZYCAT_HELL) {
            return LAZY_CAT;
        } else if (followerId >= NpcID.WILEYCAT_LIGHT && followerId <= NpcID.WILEYCAT_HELL) {
            return WILY_CAT;
        } else if (followerId >= NpcID.KITTENPET1 && followerId <= NpcID.KITTENPET_HELL) {
            return KITTEN;
        } else if (followerId >= NpcID.OVERGROWNCAT && followerId <= NpcID.OVERGROWNCAT_HELL) {
            return OVERGROWN_CAT;
        } else {
            return NON_FELINE;
        }
    }

    @Override
    public boolean isTracked() {
        return this != NON_FELINE;
    }

    @Override
    public boolean isFullyGrown() {
        return this == OVERGROWN_CAT;
    }
}
