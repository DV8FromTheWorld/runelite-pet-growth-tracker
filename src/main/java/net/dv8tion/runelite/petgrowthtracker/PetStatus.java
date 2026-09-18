package net.dv8tion.runelite.petgrowthtracker;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a {@link PetHandler} wants shown in the overlay right now: a title
 * line plus a small number of label/value rows, each with its own colour.
 *
 * A row is either a countdown - given as raw milliseconds so {@link PetOverlay}
 * can apply the player's shared "display seconds" preference in one place -
 * or a literal piece of text ("Overgrown cat", "stopped growing"). Kept as a
 * plain, RuneLite-free DTO so both species build the same shape of output
 * and the overlay never needs to know which one produced it.
 */
public final class PetStatus
{
    public static final class Row
    {
        private final String label;
        private final Long durationMillis;
        private final String text;
        private final Color valueColor;

        private Row(String label, Long durationMillis, String text, Color valueColor)
        {
            this.label = label;
            this.durationMillis = durationMillis;
            this.text = text;
            this.valueColor = valueColor;
        }

        public String getLabel()
        {
            return label;
        }

        public boolean isDuration()
        {
            return durationMillis != null;
        }

        public long getDurationMillis()
        {
            return durationMillis == null ? 0L : durationMillis;
        }

        public String getText()
        {
            return text;
        }

        public Color getValueColor()
        {
            return valueColor;
        }
    }

    private final String title;
    private final List<Row> rows = new ArrayList<>();

    public PetStatus(String title)
    {
        this.title = title;
    }

    public PetStatus addDurationRow(String label, long durationMillis, Color valueColor)
    {
        rows.add(new Row(label, durationMillis, null, valueColor));
        return this;
    }

    public PetStatus addRow(String label, String text, Color valueColor)
    {
        rows.add(new Row(label, null, text, valueColor));
        return this;
    }

    public String getTitle()
    {
        return title;
    }

    public List<Row> getRows()
    {
        return Collections.unmodifiableList(rows);
    }

    public boolean isEmpty()
    {
        return rows.isEmpty();
    }
}
