package br.com.finalcraft.evernifecore.hytale.screen;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.screen.ClickGesture;
import br.com.finalcraft.evernifecore.screen.ElementPatch;
import br.com.finalcraft.evernifecore.screen.Grid;
import br.com.finalcraft.evernifecore.screen.RenderedIcon;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What the core's pages send, as commands and event bindings - no server, no state, so a test reads it line by line.
 * The layouts are the core's own, in its asset pack under {@code Common/UI/Custom/EverNifeCore/}; a screen is a frame
 * with a title and one cell per slot of its grid, wrapped at the grid's width.
 *
 * <p>Text arrives resolved for the viewer and leaves without colour codes: a label takes plain text. A cell shows the
 * item and its amount; name, lore and the reason a choice is closed are its tooltip.</p>
 */
public final class HyPageLayout {

    public static final String SCREEN_LAYOUT = "EverNifeCore/Screen.ui";
    public static final String CELL_LAYOUT = "EverNifeCore/Cell.ui";
    public static final String PROMPT_LAYOUT = "EverNifeCore/Prompt.ui";

    /** The click kinds a cell button reports - the page has no shift or middle click. */
    public static final CustomUIEventBindingType[] CELL_CLICKS = {
            CustomUIEventBindingType.Activating, CustomUIEventBindingType.RightClicking, CustomUIEventBindingType.DoubleClicking};

    /** What the prompt's two buttons report. */
    public static final String SUBMIT = "Submit";
    public static final String CANCEL = "Cancel";

    static final int CELL_SIZE = 72;
    private static final int FRAME_PADDING_WIDTH = 80;
    private static final int FRAME_PADDING_HEIGHT = 150;

    private HyPageLayout() {

    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Screen
    // -----------------------------------------------------------------------------------------------------------------

    /** The whole screen: frame sized to the grid, title, one cell per slot drawn from {@code cells}, three bindings each. */
    public static void draw(@Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events, @Nonnull String title,
                            @Nonnull Grid grid, @Nonnull ElementPatch[] cells) {
        ui.append(SCREEN_LAYOUT);
        ui.setObject("#Frame.Anchor", size(grid.getWidth() * CELL_SIZE + FRAME_PADDING_WIDTH,
                grid.getRows() * CELL_SIZE + FRAME_PADDING_HEIGHT));
        ui.setObject("#Grid.Anchor", size(grid.getWidth() * CELL_SIZE, grid.getRows() * CELL_SIZE));
        title(ui, title);
        for (int slot = 0; slot < grid.getSize(); slot++) {
            ui.append("#Grid", CELL_LAYOUT);
            cell(ui, slot, slot < cells.length ? cells[slot] : null);
            for (CustomUIEventBindingType click : CELL_CLICKS) {
                events.addEventBinding(click, cellSelector(slot) + " #Button",
                        new EventData().append(HyPageEvent.SLOT, String.valueOf(slot)).append(HyPageEvent.KIND, click.name()));
            }
        }
    }

    public static void title(@Nonnull UICommandBuilder ui, @Nonnull String title) {
        ui.set("#ScreenTitle.Text", plain(title));
    }

    /** One cell as {@code patch} says; {@code null} or an empty icon is an empty, inert cell. */
    public static void cell(@Nonnull UICommandBuilder ui, int slot, @Nullable ElementPatch patch) {
        String cell = cellSelector(slot);
        RenderedIcon icon = patch == null ? null : patch.getIcon();
        if (icon == null || icon.isEmpty()) {
            ui.set(cell + " #Icon.Visible", false);
            ui.set(cell + " #Amount.Text", "");
            ui.set(cell + " #Button.TooltipText", "");
            ui.set(cell + " #Button.Disabled", true);
            return;
        }
        ItemSpec item = icon.getItem();
        ui.set(cell + " #Icon.ItemId", HyItemIcons.itemIdOf(icon));
        ui.set(cell + " #Icon.Visible", true);
        ui.set(cell + " #Amount.Text", item.getAmount() > 1 ? String.valueOf(item.getAmount()) : "");
        ui.set(cell + " #Button.TooltipText", tooltip(item, patch.isEnabled() ? null : patch.getReason()));
        ui.set(cell + " #Button.Disabled", !patch.isEnabled());
    }

    /** The selector of the cell drawn for {@code slot}: the slot-th child of the grid. */
    @Nonnull
    public static String cellSelector(int slot) {
        return "#Grid[" + slot + "]";
    }

    /** The gesture a cell binding stands for; anything else is {@link ClickGesture#UNKNOWN}, which no policy allows. */
    @Nonnull
    public static ClickGesture gestureOf(@Nullable String kind) {
        if (CustomUIEventBindingType.Activating.name().equals(kind)) {
            return ClickGesture.PRIMARY;
        }
        if (CustomUIEventBindingType.RightClicking.name().equals(kind)) {
            return ClickGesture.SECONDARY;
        }
        if (CustomUIEventBindingType.DoubleClicking.name().equals(kind)) {
            return ClickGesture.DOUBLE;
        }
        return ClickGesture.UNKNOWN;
    }

    /** Name, lore lines and, for a closed choice, why - one per line, without colour codes. */
    @Nonnull
    static String tooltip(@Nonnull ItemSpec item, @Nullable String reason) {
        List<String> lines = new ArrayList<>();
        if (item.getDisplayName() != null && !item.getDisplayName().isEmpty()) {
            lines.add(item.getDisplayName());
        }
        lines.addAll(item.getLore());
        if (reason != null && !reason.isEmpty()) {
            lines.add(reason);
        }
        return plain(String.join("\n", lines));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Prompt
    // -----------------------------------------------------------------------------------------------------------------

    /** The question page: the text, a field, OK sending the field's text as {@code @Value}, and Cancel. */
    public static void drawPrompt(@Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events, @Nonnull String question) {
        ui.append(PROMPT_LAYOUT);
        ui.set("#Question.Text", plain(question));
        ui.set("#Input.Value", "");
        ui.set("#Error.Text", "");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#Ok",
                new EventData().append(HyPageEvent.KIND, SUBMIT).append(HyPageEvent.VALUE, "#Input.Value"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#Cancel",
                new EventData().append(HyPageEvent.KIND, CANCEL));
    }

    /** A refused answer: the field is emptied and the parser's text shown under it. */
    public static void promptError(@Nonnull UICommandBuilder ui, @Nullable String why) {
        ui.set("#Input.Value", "");
        ui.set("#Error.Text", plain(why == null ? "" : why));
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static Anchor size(int width, int height) {
        Anchor anchor = new Anchor();
        anchor.setWidth(Value.of(width));
        anchor.setHeight(Value.of(height));
        return anchor;
    }

    private static String plain(String text) {
        String stripped = FCColorUtil.stripColor(text);
        return stripped == null ? "" : stripped;
    }

}
