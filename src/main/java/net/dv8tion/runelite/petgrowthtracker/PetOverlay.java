package net.dv8tion.runelite.petgrowthtracker;

import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import org.apache.commons.lang3.time.DurationFormatUtils;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.font.TextAttribute;
import java.util.Collections;

/**
 * The panel that used to be {@code KittenOverlay}, generalised to paint
 * whichever {@link PetHandler} currently has something to say. Deliberately
 * kept as plain title + LineComponent rows - the look the kitten side
 * already had - rather than a custom-painted card, since the whole point of
 * this fork is to keep that UI for both species.
 */
public class PetOverlay extends OverlayPanel
{
    private final PetTrackerPlugin plugin;
    private final PetTrackerConfig config;

    @Inject
    private PetOverlay(PetTrackerPlugin plugin, PetTrackerConfig config)
    {
        super(plugin);
        setPosition(OverlayPosition.BOTTOM_LEFT);
        this.plugin = plugin;
        this.config = config;
        getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, OverlayManager.OPTION_CONFIGURE, "Pet Tracker Overlay"));
        setPreferredSize(new Dimension(190, 88));
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        PetStatus status = plugin.activeStatus();
        if (status != null && !status.isEmpty())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .leftFont(graphics.getFont().deriveFont(
                            Collections.singletonMap(
                                    TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD)))
                    .left(status.getTitle())
                    .build());

            for (PetStatus.Row row : status.getRows())
            {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left(row.getLabel() + ": ")
                        .rightColor(row.getValueColor())
                        .right(row.isDuration() ? formatDuration(row.getDurationMillis()) : row.getText())
                        .build());
            }
        }

        return super.render(graphics);
    }

    private String formatDuration(long millis)
    {
        return DurationFormatUtils.formatDuration(millis, config.displaySeconds() ? "H:mm:ss" : "H:mm", true);
    }
}
