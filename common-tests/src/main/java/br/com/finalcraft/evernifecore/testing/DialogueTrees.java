package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.dialogue.Dialogue;
import br.com.finalcraft.evernifecore.dialogue.DialogueNode;
import br.com.finalcraft.evernifecore.dialogue.DialogueOption;
import br.com.finalcraft.evernifecore.dialogue.DialogueSession;
import br.com.finalcraft.evernifecore.dialogue.Dialogues;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.screen.ElementPatch;
import br.com.finalcraft.evernifecore.screen.RenderedIcon;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dialogue trees for a test, and what the default dialogue screen drew of them - over a {@link ScreenWorld} (the screen)
 * and a {@link LogicWorld} (the owner, its variables and the clock), no world of its own.
 *
 * <pre>{@code
 * Dialogue talk = DialogueTrees.linear(shop.getOwner(), "greeting", "Hello!", "Bye!");
 * Dialogues.of(shop.getOwner()).register(talk);
 * List<DialogueTrees.DrawnChoice> drawn = DialogueTrees.openDialogueScreen(screens, alex, talk);
 * }</pre>
 */
public final class DialogueTrees {

    /** The element-id prefix the default dialogue screen gives each option. */
    public static final String OPTION_PREFIX = "option:";

    private DialogueTrees() {
    }

    /**
     * One node per line, {@code n0..n<k>}, each with the single option {@code next} leading to the following one; the
     * last line is a terminal node.
     */
    public static Dialogue linear(ECPluginData owner, String name, String... lines) {
        if (lines.length == 0) {
            throw new IllegalArgumentException("A linear dialogue needs at least one line.");
        }
        Dialogue.Builder builder = Dialogue.builder(owner, name).entry("n0");
        for (int i = 0; i < lines.length; i++) {
            DialogueNode.Builder node = DialogueNode.builder("n" + i).text(lines[i]);
            if (i + 1 < lines.length) {
                node.option(DialogueOption.builder("next").text("Next").goTo("n" + (i + 1)));
            }
            builder.node(node);
        }
        return builder.build();
    }

    /** {@code start -> haggle <-> insist}, nothing ending: {@link Dialogue.Builder#build()} refuses it. */
    public static Dialogue.Builder withLoopWithoutExit(ECPluginData owner, String name) {
        return Dialogue.builder(owner, name)
                .node(DialogueNode.builder("start").text("How much?").option(DialogueOption.builder("haggle").text("Less!").goTo("haggle")))
                .node(DialogueNode.builder("haggle").text("No.").option(DialogueOption.builder("insist").text("Please!").goTo("insist")))
                .node(DialogueNode.builder("insist").text("Still no.").option(DialogueOption.builder("again").text("Come on!").goTo("haggle")));
    }

    /** The dialogue {@code name} of {@code owner} read from YAML text, as {@link Dialogue#parse} reads a plugin's file. */
    public static Dialogue fromYaml(ECPluginData owner, String name, String yaml) {
        return Dialogue.parse(owner, name, readYaml(yaml), ZoneOffset.UTC);
    }

    /** YAML text as the tree a config hands over. */
    public static JsonNode readYaml(String yaml) {
        try {
            return ConfigFactory.inMemoryCodec().getObjectMapper().readTree(yaml);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /**
     * Registers {@code dialogue} when it is not yet, starts it for {@code player} as its owner (origin {@code test}) and
     * answers the options the default screen drew.
     *
     * @throws AssertionError when the start was cancelled or no screen opened
     */
    public static List<DrawnChoice> openDialogueScreen(ScreenWorld world, FPlayer player, Dialogue dialogue) {
        if (Dialogues.find(dialogue.getId()).orElse(null) != dialogue) {
            Dialogues.of(dialogue.getOwner()).register(dialogue);
        }
        DialogueSession session = Dialogues.start(player, dialogue, Caller.of(dialogue.getOwner(), "test"), null);
        if (session == null) {
            throw new AssertionError("The start of " + dialogue.getId() + " was cancelled by a DialogueStartedEvent listener.");
        }
        return drawn(world, player);
    }

    /** The options drawn on {@code player}'s open screen right now, in slot order. */
    public static List<DrawnChoice> drawn(ScreenWorld world, FPlayer player) {
        ScreenView view = Screens.getOpenView(player);
        if (view == null) {
            throw new AssertionError(player.getName() + " has no screen open: the dialogue did not show.");
        }
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        List<DrawnChoice> drawn = new ArrayList<>();
        for (int slot = 0; slot < view.getGrid().getSize(); slot++) {
            RenderedIcon icon = surface.getSlot(slot);
            ElementPatch patch = surface.getLastPatch(slot);
            if (icon != null && !icon.isEmpty() && patch != null && patch.getElementId().startsWith(OPTION_PREFIX)) {
                drawn.add(new DrawnChoice(patch.getElementId().substring(OPTION_PREFIX.length()), patch.isEnabled(), patch.getReason(),
                        icon.getItem().getDisplayName(), icon.getItem().getLore()));
            }
        }
        return Collections.unmodifiableList(drawn);
    }

    /** One option as the screen drew it. */
    public static final class DrawnChoice {

        private final String optionId;
        private final boolean enabled;
        private final @Nullable String reason;
        private final @Nullable String name;
        private final List<String> lore;

        DrawnChoice(String optionId, boolean enabled, @Nullable String reason, @Nullable String name, @Nullable List<String> lore) {
            this.optionId = optionId;
            this.enabled = enabled;
            this.reason = reason;
            this.name = name;
            this.lore = lore == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<>(lore));
        }

        public String getOptionId() {
            return optionId;
        }

        /** False = drawn greyed out: its gate fails. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Every failure joined, as a renderer that draws by element shows it; null when enabled. */
        public @Nullable String getReason() {
            return reason;
        }

        public @Nullable String getName() {
            return name;
        }

        /** One line per failure on a greyed option, the costs on an enabled one. */
        public List<String> getLore() {
            return lore;
        }

        @Override
        public String toString() {
            return optionId + (enabled ? "" : " (greyed: " + lore + ")");
        }
    }
}
