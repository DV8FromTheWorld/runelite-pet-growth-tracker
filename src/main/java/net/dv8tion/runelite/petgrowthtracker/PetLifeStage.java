package net.dv8tion.runelite.petgrowthtracker;

/**
 * The common shape every species' NPC-id classification exposes: is this id
 * one of ours at all, and if so, has it finished growing?
 *
 * {@code net.dv8tion.runelite.petgrowthtracker.cat.FollowerKind} and {@code net.dv8tion.runelite.petgrowthtracker.dog.FollowerKind}
 * both implement this. Each is still its own enum with its own life stages -
 * a cat has three (kitten, cat, overgrown cat), a dog has two (puppy, dog) -
 * because that's genuinely how the two species differ in-game; this
 * interface only unifies the two questions {@link PetTrackerPlugin} actually
 * needs answered to route a follower: "is this mine?" and "is it done?".
 */
public interface PetLifeStage
{
    /** False for the "not this species" sentinel value (e.g. {@code NON_FELINE}, {@code NON_DOG}). */
    boolean isTracked();

    /** True for the final stage - the one where the pet no longer needs feeding/attention to keep growing. */
    boolean isFullyGrown();
}
