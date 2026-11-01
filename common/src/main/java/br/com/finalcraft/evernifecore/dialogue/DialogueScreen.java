package br.com.finalcraft.evernifecore.dialogue;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.screen.Choice;
import br.com.finalcraft.evernifecore.screen.DefaultIcons;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.IconData;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.Layouts;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenKey;
import br.com.finalcraft.evernifecore.screen.ScreenLayout;
import br.com.finalcraft.evernifecore.screen.Slots;

import java.util.Arrays;
import java.util.List;

/**
 * The core's default for {@link ScreenKey#DIALOGUE}: the speaker and their line on top, one {@link Choice} per option
 * below. An option whose gate fails is drawn disabled with every failure on its lore (and as the choice's reason, for a
 * renderer that draws by element); a {@link DialogueOption#isHidden() hidden} one is not drawn until its gate passes.
 * Clicking an option is {@link DialogueSession#choose}. A plugin replaces it with
 * {@code Screens.override(plugin, ScreenKey.DIALOGUE, ScreenScope.ownedBy(plugin), ctx -> ...)}, reading the session from
 * {@code ctx.getRequest(DialogueSession.class)}; the admin restyles it in {@code guis/DialogueLayout.yml}.
 */
public final class DialogueScreen extends Screen<DialogueScreen.DialogueLayout> {

    private final DialogueSession session;

    private DialogueScreen(DialogueLayout layout, DialogueSession session) {
        super(layout.getRows(), layout);
        this.session = session;
        Dialogue dialogue = session.getDialogue();
        DialogueNode node = session.getNode();
        ExecutionContext ctx = session.newExecutionContext().build();
        key(ScreenKey.DIALOGUE);
        owner(dialogue.getOwner());
        String speaker = dialogue.getSpeaker(ctx);
        if (!speaker.isEmpty()) {
            title(speaker);
        }
        List<String> line = Arrays.asList(dialogue.getText(node, ctx).split("\n"));
        label(l -> l.SPEAKER).render(icon -> icon.displayName(speaker.isEmpty() ? "§7..." : "§e" + speaker).lore(line));

        Layout.PlacedIcon placed = takeOver(l -> l.OPTION);
        int[] slots = placed.getSlots().resolve(getGrid()).toArray();
        int index = 0;
        for (DialogueOption option : node.getOptions().values()) {
            if (index >= slots.length) {
                EverNifeCore.getLog().warning("The node '{}' of the dialogue {} has {} options and DialogueLayout.OPTION places {}: the rest"
                        + " are not drawn. Give OPTION more slots, or split the node.", node.getId(), dialogue.getId(),
                        node.getOptions().size(), slots.length);
                break;
            }
            String text = dialogue.getText(node, option, ctx);
            Choice choice = choice(Slots.of(slots[index++]), placed.getIcon(), FancyText.of(text), option.getGate(), option.getEffects())
                    .pickedBy(click -> session.choose(option.getId()));
            choice.id("option:" + option.getId());
            choice.render(icon -> icon.displayName(text));
            if (option.isHidden()) {
                choice.hiddenUntilPassed();
            }
        }
    }

    /** The node {@code session} is on, drawn on the admin's {@code DialogueLayout}. */
    public static DialogueScreen of(DialogueSession session) {
        return new DialogueScreen(Layouts.of(DialogueLayout.class), session);
    }

    public DialogueSession getSession() {
        return session;
    }

    /** What a dialogue node looks like, and the file the admin restyles it in. */
    @ScreenLayout(title = "§8...", rows = 4)
    public static class DialogueLayout extends Layout {

        @IconData(slot = {4})
        public Icon SPEAKER = Icon.of(ItemSpec.of("minecraft:paper", 1));

        @IconData(slot = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34})
        public Icon OPTION = Icon.of(ItemSpec.of("minecraft:book", 1));

        @IconData(slot = {0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 26, 27, 35}, background = true)
        public Icon BACKGROUND = Icon.of(DefaultIcons.pane(DefaultIcons.Color.GRAY)).displayName("§7");

    }
}
