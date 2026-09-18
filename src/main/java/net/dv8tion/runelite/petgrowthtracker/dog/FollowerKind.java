package net.dv8tion.runelite.petgrowthtracker.dog;

import net.dv8tion.runelite.petgrowthtracker.PetLifeStage;
import net.runelite.api.gameval.NpcID;

/**
 * Which canine life-stage a follower NPC id belongs to, or {@link #NON_DOG}
 * if it isn't one of ours - the same shape as
 * {@code net.dv8tion.runelite.petgrowthtracker.cat.FollowerKind}, using the two contiguous id
 * blocks Jagex actually ships: {@code LABRADOR_YELLOW}..{@code YORKIE_YELLOW}
 * for the twelve breeds' adult form, and {@code LABRADOR_YELLOW_PUPPY}..
 * {@code YORKIE_YELLOW_PUPPY} for the same twelve breeds' puppy form (three
 * colours each). The "wander"/"shelter" ids in between are wild, pre-adoption
 * dogs, never a player's follower, so they don't need excluding - they simply
 * aren't a follower id the plugin will ever see here.
 *
 * These constants only exist in {@code net.runelite.api.gameval.NpcID}
 * (puppies are 2026-09-08 content); the legacy {@code net.runelite.api.NpcID}
 * has the same numeric ranges under uglier per-id names
 * ({@code LABRADOR_PUPPY}, {@code LABRADOR_PUPPY_16434}, ...), which is why
 * this class uses gameval while the cat side still uses the legacy class.
 */
public enum FollowerKind implements PetLifeStage {
    PUPPY, DOG, NON_DOG;

    public static FollowerKind getFromFollowerId(int followerId) {
        if (followerId >= NpcID.LABRADOR_YELLOW && followerId <= NpcID.YORKIE_YELLOW) {
            return DOG;
        } else if (followerId >= NpcID.LABRADOR_YELLOW_PUPPY && followerId <= NpcID.YORKIE_YELLOW_PUPPY) {
            return PUPPY;
        } else {
            return NON_DOG;
        }
    }

    @Override
    public boolean isTracked() {
        return this != NON_DOG;
    }

    @Override
    public boolean isFullyGrown() {
        return this == DOG;
    }
}
