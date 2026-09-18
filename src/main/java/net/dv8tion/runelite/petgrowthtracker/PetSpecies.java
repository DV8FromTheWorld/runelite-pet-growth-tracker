package net.dv8tion.runelite.petgrowthtracker;

/**
 * Which family of pet is currently following the player.
 *
 * A follower is classified as {@link #CAT} the moment its NPC id falls in one
 * of the known feline ranges (see {@code net.dv8tion.runelite.petgrowthtracker.cat.FollowerKind}).
 * There is no equivalent id table for dogs: Jagex ships new puppy breeds
 * faster than any hand-maintained range could track, so a non-feline
 * follower is provisionally treated as {@link #DOG} and only confirmed once
 * the dog-specific state machine sees real evidence (a growth-paused
 * message, a "grown into a dog" message, or a guess-age reply). Until then
 * it tracks quietly and shows nothing, so an unrelated pet (a boss pet, a
 * skilling pet) never produces a false overlay.
 */
public enum PetSpecies
{
    CAT,
    DOG
}
