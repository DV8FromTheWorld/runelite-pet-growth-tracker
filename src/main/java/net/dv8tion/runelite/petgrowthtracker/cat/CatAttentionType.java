package net.dv8tion.runelite.petgrowthtracker.cat;

/**
 * How the cat was last given attention, which decides how long the
 * resulting attention window lasts. Values match the game's own timings;
 * see {@link CatPetHandler} for how they're applied.
 */
public enum CatAttentionType {
    NEW_KITTEN(0, CatTracker.ATTENTION_TIME_NEW_KITTEN_IN_SECONDS),
    SINGLE_STROKE(1, CatTracker.ATTENTION_TIME_SINGLE_STROKE_IN_SECONDS),
    MULTIPLE_STROKES(2, CatTracker.ATTENTION_TIME_MULTIPLE_STROKES_IN_SECONDS),
    BALL_OF_WOOL(3, CatTracker.ATTENTION_TIME_BALL_OF_WOOL_IN_SECONDS);

    private final int id;
    private final int attentionTime;

    CatAttentionType(int id, int attentionTime) {
        this.id = id;
        this.attentionTime = attentionTime;
    }

    int getId() {
        return id;
    }

    int getAttentionTime() {
        return attentionTime;
    }
}
