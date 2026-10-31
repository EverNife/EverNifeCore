package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.locale.LocalePDSection;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.RecordingSurface;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * How a screen's text is decided, and who each decision speaks about.
 *
 * <p>Two people are involved and they are not the same person: the VIEWER has the window open, and the SUBJECT is whose
 * data is on screen. Permission and language follow the viewer; {@code %playerdata_*%} follows the subject.</p>
 *
 * <p>Six steps run over that text, in this order: the viewer's language block, the opener's positional arguments
 * ({@code {0}}..{@code {n}}), the icon's own scopes, the screen's replacers, the subject's placeholders, and finally
 * PlaceholderAPI. Every assertion here reads what the surface shows
 * for one viewer, because that is the only place a viewer exists.</p>
 */
class TextResolutionOrderTest {

    /** What the icon answers on its own. */
    private static final RegexReplacer<String> ICON_SCOPE = new RegexReplacer<String>()
            .addParser("owner", answer -> answer);

    /** What the screen around it answers - on purpose for keys the steps after it also answer. */
    private static final RegexReplacer<String> MENU = new RegexReplacer<String>()
            .addParser("owner", answer -> answer)
            .addParser("playerdata_name", answer -> answer);

    /** One text with a token for every step - see {@link #everyStepResolvesWhatTheStepsAfterItWouldHaveAnsweredDifferently}. */
    private static final String ORDER_TEXT = "%owner% %playerdata_name% %papi_rank% %playerdata_uuid%";

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private boolean perPlayerLocaleBefore;

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir);
        Plugins.setLanguage(world.getOwner(), LocaleType.EN_US);

        perPlayerLocaleBefore = ECSettings.PER_PLAYER_LOCALE;
        ECSettings.PER_PLAYER_LOCALE = true;
        PlayerController.initialize(Storages.memory().writeTo(tempDir));
    }

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
        ECSettings.PER_PLAYER_LOCALE = perPlayerLocaleBefore;
        world.close();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Two people, one screen
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * A staff member reading somebody else's profile: the button's permission is asked of the staff, and the text on it
     * speaks of the player being read.
     *
     * <p>Both halves are checked because either one alone passes on a screen where viewer and subject are the same
     * person, which is precisely the case this class does not cover.</p>
     */
    @Test
    void aPermissionAsksTheViewerWhileThePlaceholdersAnswerForTheSubject() {
        PlayerData inspected = registered("Inspected");
        Screen<?> screen = Screen.of(3).subject(inspected);
        //what a viewer who may not see the button gets instead, so an unpainted slot is not a blank one
        screen.icon(0, Icon.of(ItemSpec.of("minecraft:gray_stained_glass_pane", 1)).background()).id("filler");
        screen.icon(0, Icon.of(ItemSpec.of("minecraft:anvil", 1)).displayName("Inspect %playerdata_name%")
                .permission("profile.inspect")).id("inspect");
        screen.icon(1, Icon.of(ItemSpec.of("minecraft:paper", 1))
                .displayName("%playerdata_name%")
                .lore("%playerdata_uuid%"));

        TestFPlayerSender staff = world.viewer("Staff").grant("profile.inspect");
        ScreenView staffView = world.open(screen, staff);
        RecordingSurface byStaff = world.getRenderer().surfaceOf(staffView);

        ScreenView bystanderView = world.open(screen, world.viewer("Bystander"));
        RecordingSurface byBystander = world.getRenderer().surfaceOf(bystanderView);

        assertEquals("minecraft:anvil", byStaff.getSlot(0).getItem().getMaterialId(),
                "the permission is checked against whoever has the window open");
        assertEquals("minecraft:gray_stained_glass_pane", byBystander.getSlot(0).getItem().getMaterialId(),
                "and the same screen about the same player hides the button from a viewer without it");
        assertEquals("Inspect Inspected", nameAt(byStaff, 0),
                "the button the viewer earned still talks about the subject");

        assertEquals("Inspected", nameAt(byStaff, 1));
        assertEquals("Inspected", nameAt(byBystander, 1),
                "the subject's placeholders do not follow the viewer, so both read the same name");
        assertNotEquals("Staff", nameAt(byStaff, 1),
                "a screen that answered with the viewer's own name would be a screen about the wrong person");
        assertEquals(Collections.singletonList(inspected.getUniqueId().toString()), loreAt(byStaff, 1));
        assertNotEquals(staff.getUniqueId().toString(), loreAt(byStaff, 1).get(0));

        close(staffView, bystanderView);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The order the five steps run in
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * One text, five steps, and a swap of any two neighbours changes what it reads.
     *
     * <p>The text lives in a language block, so it does not exist before the first step runs: were the substitutions to
     * happen first they would find an icon with no text at all, and the name would arrive still carrying its raw tokens.
     * From there each remaining pair contests one token - two steps answer it and only the earlier one may win, because
     * whoever resolves a token first leaves nothing behind for the next step to see:</p>
     *
     * <ul>
     *   <li>{@code %owner%} - the icon's scope against the screen's replacer;</li>
     *   <li>{@code %playerdata_name%} - the screen's replacer against the subject;</li>
     *   <li>{@code %playerdata_uuid%} - the subject against PlaceholderAPI;</li>
     *   <li>{@code %papi_rank%} - nobody but PlaceholderAPI, which is what makes its absence visible.</li>
     * </ul>
     */
    @Test
    void everyStepResolvesWhatTheStepsAfterItWouldHaveAnsweredDifferently() {
        PlayerData subject = registered("Subject");
        Screen<?> screen = Screen.of(3);
        screen.icon(0, ticket(true).addLocale(LocaleType.PT_BR, "PT " + ORDER_TEXT));
        screen.subject(subject).addReplacer(MENU, "menu-answer");

        TestFPlayerSender english = viewerSpeaking("Reader", LocaleType.EN_US);
        TestFPlayerSender brazilian = viewerSpeaking("Leitor", LocaleType.PT_BR);
        try (ECoreTestWorld papi = Platforms.lenient().parsingWith(lastResort()).install()) {
            ScreenView englishView = world.open(screen, english);
            ScreenView brazilianView = world.open(screen, brazilian);
            String inEnglish = nameAt(world.getRenderer().surfaceOf(englishView), 0);
            String inPortuguese = nameAt(world.getRenderer().surfaceOf(brazilianView), 0);
            close(englishView, brazilianView);

            assertFalse(inEnglish.contains("%"),
                    "a token still standing is a substitution that ran before the text it was written in existed: "
                            + inEnglish);

            String expected = "scope-owner menu-answer diamond " + subject.getUniqueId();
            assertEquals("EN " + expected, inEnglish);
            assertEquals("PT " + expected, inPortuguese,
                    "the language picks the sentence; the four steps after it fill the same blanks");
        }
    }

    /**
     * The last step, switched off: without {@code integrateToPAPI} the token nobody else answers is simply left standing,
     * which is what makes its resolution above a statement about PlaceholderAPI and not about the steps before it.
     */
    @Test
    void aScreenThatNeverAskedForPlaceholderApiLeavesItsTokensAlone() {
        PlayerData subject = registered("Subject");
        Screen<?> screen = Screen.of(3);
        screen.icon(0, ticket(false));
        screen.subject(subject).addReplacer(MENU, "menu-answer");

        TestFPlayerSender reader = viewerSpeaking("Reader", LocaleType.EN_US);
        try (ECoreTestWorld papi = Platforms.lenient().parsingWith(lastResort()).install()) {
            ScreenView view = world.open(screen, reader);
            String shown = nameAt(world.getRenderer().surfaceOf(view), 0);
            close(view);

            assertEquals("EN scope-owner menu-answer %papi_rank% " + subject.getUniqueId(), shown,
                    "the four steps that are not PlaceholderAPI answered, and nothing else did");
        }
    }

    /**
     * The opener's arguments go in right after the language block: an argument that itself carries a placeholder is then
     * answered by the steps after it, here the icon's own scope. Put in last, the token would reach the viewer raw.
     */
    @Test
    void positionalArgumentsResolveBeforeTheIconScopes() {
        Icon icon = Icon.of(ItemSpec.of("minecraft:paper", 1)).addLocale(LocaleType.EN_US, "EN {0}")
                .addScope(ICON_SCOPE, "scope-owner");
        icon.setLocaleOwner(world.getOwner());
        Screen<?> screen = Screen.of(3);
        screen.icon(0, icon);

        ScreenView view = world.open(screen, viewerSpeaking("Reader", LocaleType.EN_US), "%owner%");
        String shown = nameAt(world.getRenderer().surfaceOf(view), 0);
        close(view);

        assertEquals("EN scope-owner", shown);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------------------------------------------------

    /** The icon whose only text is a language block - nothing is baked into the item. */
    private Icon ticket(boolean integrateToPAPI) {
        Icon icon = Icon.of(ItemSpec.of("minecraft:paper", 1))
                .addLocale(LocaleType.EN_US, "EN " + ORDER_TEXT)
                .addScope(ICON_SCOPE, "scope-owner")
                .integrateToPAPI(integrateToPAPI);
        icon.setLocaleOwner(world.getOwner());
        return icon;
    }

    /**
     * A PlaceholderAPI that answers every token still standing when it is reached. It is deliberately able to answer the
     * earlier steps' keys too, so a step that failed to run leaves its own name behind instead of a hole that could pass
     * for anything.
     */
    private static BiFunction<FPlayer, String, String> lastResort() {
        return (player, text) -> text
                .replace("%papi_rank%", "diamond")
                .replace("%owner%", "papi-owner")
                .replace("%playerdata_name%", "papi-name")
                .replace("%playerdata_uuid%", "papi-uuid");
    }

    /** A player the storage knows, to be the subject of a screen somebody else is looking at. */
    private PlayerData registered(String name) {
        return PlayerController.handleLogin(UUID.randomUUID(), name).join();
    }

    /** A viewer who has chosen a language of their own. */
    private TestFPlayerSender viewerSpeaking(String name, String language) {
        TestFPlayerSender player = world.viewer(name);
        PlayerController.handleLogin(player.getUniqueId(), name).join()
                .getPDSection(LocalePDSection.class).join().setLang(language);
        return player;
    }

    private void close(ScreenView... views) {
        for (ScreenView view : views) {
            view.close();
        }
        world.advanceTicks(2);
    }

    private static String nameAt(RecordingSurface surface, int slot) {
        RenderedIcon drawn = surface.getSlot(slot);
        return drawn == null ? null : drawn.getItem().getDisplayName();
    }

    private static List<String> loreAt(RecordingSurface surface, int slot) {
        RenderedIcon drawn = surface.getSlot(slot);
        return drawn == null ? null : drawn.getItem().getLore();
    }

}
