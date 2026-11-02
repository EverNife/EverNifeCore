package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.testing.Locales;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.RecordingSurface;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One screen, read by people who do not share a language.
 *
 * <p>Text is the only thing that varies per viewer inside the base file: the material, the slot and the
 * raw data lines are the screen, and a language block cannot turn a stone into a sword. A whole screen per
 * language is a separate, opt-in file the framework never writes, and only there does everything become
 * negotiable.</p>
 *
 * <p>Every text assertion reads what the SURFACE was handed for a given viewer, because that is the only
 * place the viewer exists: an icon holds one canonical item and resolves its text at paint time.</p>
 */
class LayoutLocaleTest {

    /** What a menu registers around an icon: a placeholder that does not depend on who is reading. */
    private static final RegexReplacer<Offer> OFFER = new RegexReplacer<Offer>()
            .addParser("price", Offer::getPrice);

    static final class Offer {

        private final String price;

        Offer(String price) {
            this.price = price;
        }

        public String getPrice() {
            return price;
        }
    }

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private Locales locales;
    private ECPluginData plugin;
    private final List<ScreenView> opened = new ArrayList<>();

    private static Icon icon(String materialId) {
        return Icon.of(ItemSpec.of(materialId, 1));
    }

    private static ItemSpec item(String materialId) {
        return ItemSpec.of(materialId, 1);
    }

    @ScreenLayout(title = "Market", rows = 3, locale = {
            @FCLocale(lang = LocaleType.EN_US, text = "Market"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Mercado")
    })
    public static class MarketLayout extends Layout {

        @IconData(slot = {0}, locale = {
                @FCLocale(lang = LocaleType.EN_US, text = "Buy", hover = "Price: %price%"),
                @FCLocale(lang = LocaleType.PT_BR, text = "Comprar", hover = "Preco: %price%")
        })
        public Icon BUY = icon("minecraft:emerald");

        @IconData(slot = {4}, permission = "market.admin")
        public Icon EDIT = icon("minecraft:anvil");

        @IconData(slot = {8}, background = true)
        public Icon CORNER = icon("minecraft:gray_stained_glass_pane");
    }

    /** One icon named twice: through the annotation, and through the builder it was made with. */
    @ScreenLayout(title = "Confused", rows = 3)
    public static class ConfusedLayout extends Layout {

        @IconData(slot = {0}, locale = {
                @FCLocale(lang = LocaleType.EN_US, text = "Sell"),
                @FCLocale(lang = LocaleType.PT_BR, text = "Vender")
        })
        public Icon SELL = icon("minecraft:emerald").displayName("&aSell");

        @IconData(slot = {1})
        public Icon INNOCENT = icon("minecraft:paper");
    }

    /** An icon with a single text and no language at all - the case the late path must not charge for. */
    @ScreenLayout(title = "Plain", rows = 3)
    public static class PlainLayout extends Layout {

        @IconData(slot = {0})
        public Icon LABEL = icon("minecraft:paper").displayName("&7Just this");
    }

    /** A placeholder baked into the item itself, with no language block anywhere near it - the other
     *  way an icon's text differs from what it was declared with. */
    @ScreenLayout(title = "Ticker", rows = 3)
    public static class TickerLayout extends Layout {

        @IconData(slot = {0})
        public Icon PRICE = icon("minecraft:paper").displayName("Now: %price%");
    }

    /** More than one of everything an overlay can name a single key of: two background keys, two states,
     *  two language blocks, and a title language only the file knows about. */
    @ScreenLayout(title = "Locker", rows = 3, locale = {
            @FCLocale(lang = LocaleType.EN_US, text = "Locker"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Armario")
    })
    public static class LockerLayout extends Layout {

        @IconData(slot = {0}, locale = {
                @FCLocale(lang = LocaleType.EN_US, text = "Open"),
                @FCLocale(lang = LocaleType.PT_BR, text = "Abrir")
        })
        public Icon OPEN = icon("minecraft:emerald")
                .addState("locked", item("minecraft:barrier"))
                .addState("maxed", item("minecraft:nether_star"));

        @IconData(slot = {1}, background = true)
        public Icon LEFT_PANE = icon("minecraft:gray_stained_glass_pane");
    }

    /** An icon whose alternative appearance has words of its own, in more than one language. */
    @ScreenLayout(title = "Forge", rows = 3)
    public static class ForgeLayout extends Layout {

        @IconData(slot = {0}, locale = {
                @FCLocale(lang = LocaleType.EN_US, text = "Smelt"),
                @FCLocale(lang = LocaleType.PT_BR, text = "Fundir")
        })
        public Icon SMELT = icon("minecraft:furnace")
                .addState("busy", item("minecraft:barrier"))
                .addStateLocale("busy", LocaleType.EN_US, "Working")
                .addStateLocale("busy", LocaleType.PT_BR, "Trabalhando");
    }

    /** A language the plugin does not run in, declared FIRST: the only shape that tells "the server's
     *  language" apart from "whatever the developer typed first". */
    @ScreenLayout(title = "Fallback", rows = 3)
    public static class FallbackLayout extends Layout {

        @IconData(slot = {0}, locale = {
                @FCLocale(lang = LocaleType.PT_BR, text = "Comprar"),
                @FCLocale(lang = LocaleType.EN_US, text = "Buy")
        })
        public Icon BUY = icon("minecraft:emerald");
    }

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir);
        plugin = world.getOwner();
        Plugins.setLanguage(plugin, LocaleType.EN_US);
        //a viewer's own language only exists once the PlayerData layer that stores the choice is up
        locales = Locales.perPlayerLocale(tempDir);
        Layouts.clear();
    }

    @AfterEach
    void teardown() {
        Layouts.clear();
        for (ScreenView view : opened) {
            view.close();
        }
        if (world != null) {
            world.advanceTicks(2);
        }
        if (locales != null) {
            locales.close();
        }
        if (world != null) {
            world.close();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  One icon, two readers
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void twoViewersReadOneIconInTheirOwnLanguageAndItsPlaceholderTheSameWay() {
        Icon buy = Layouts.of(MarketLayout.class).getIcon("BUY").addScope(OFFER, new Offer("9.50"));
        Screen<?> screen = Screen.of(3);
        screen.icon(0, buy);

        RecordingSurface english = open(screen, viewerSpeaking("Steve", LocaleType.EN_US));
        RecordingSurface brazilian = open(screen, viewerSpeaking("Alberto", LocaleType.PT_BR));

        assertEquals("Buy", nameAt(english, 0));
        assertEquals("Comprar", nameAt(brazilian, 0),
                "the same Icon object answers two viewers with two texts");
        assertEquals(Arrays.asList("Price: 9.50"), loreAt(english, 0));
        assertEquals(Arrays.asList("Preco: 9.50"), loreAt(brazilian, 0),
                "the language decides the words; the placeholder decides the value, and it is the same value");
    }

    @Test
    void renderingForTwoViewersLeavesTheCanonicalIconUntouched() {
        Icon buy = Layouts.of(MarketLayout.class).getIcon("BUY").addScope(OFFER, new Offer("9.50"));
        Icon price = Layouts.of(TickerLayout.class).getIcon("PRICE").addScope(OFFER, new Offer("9.50"));
        ItemSpec localized = buy.getItem();
        List<String> localizedLines = buy.toDisplayItem(false);
        List<String> baked = price.toDisplayItem(false);
        assertTrue(baked.contains("name:Now: %price%"), baked.toString());

        Screen<?> screen = Screen.of(3);
        screen.icon(0, buy);
        screen.icon(1, price);
        RecordingSurface drawn = open(screen, viewerSpeaking("Steve", LocaleType.EN_US));
        open(screen, viewerSpeaking("Alberto", LocaleType.PT_BR));

        assertEquals("Now: 9.50", nameAt(drawn, 1), "the viewer's copy did get the resolved text");
        assertEquals(localized, buy.getItem(),
                "an icon two people are looking at cannot be mutated through either of them");
        assertEquals(localizedLines, buy.toDisplayItem(false));
        assertEquals(baked, price.toDisplayItem(false),
                "and an icon whose text is a placeholder still holds the placeholder afterwards");
        assertEquals(localizedLines, Layouts.of(MarketLayout.class).getIcon("BUY").toDisplayItem(false),
                "so the layout hands out the same icon it always did");
    }

    @Test
    void aViewerWithoutThePermissionSeesTheLayerBelowRatherThanAHole() {
        MarketLayout layout = Layouts.of(MarketLayout.class);
        Screen<?> screen = Screen.of(3);
        screen.icon(4, icon("minecraft:gray_stained_glass_pane").background());
        screen.icon(4, layout.getIcon("EDIT"));

        TestFPlayerSender admin = viewerSpeaking("Admin", LocaleType.EN_US).grant("market.admin");
        RecordingSurface seen = open(screen, admin);
        RecordingSurface hidden = open(screen, viewerSpeaking("Guest", LocaleType.EN_US));

        assertEquals("minecraft:anvil", materialAt(seen, 4),
                "who may see an icon is asked of the viewer, not of the plugin that declared it");
        assertEquals("minecraft:gray_stained_glass_pane", materialAt(hidden, 4),
                "and the slot the icon did not take shows what is under it, not a hole");
    }

    @Test
    void theTitleFollowsTheViewer() {
        MarketLayout layout = Layouts.of(MarketLayout.class);

        assertEquals("Market", layout.getTitleFor(viewerSpeaking("Steve", LocaleType.EN_US)));
        assertEquals("Mercado", layout.getTitleFor(viewerSpeaking("Alberto", LocaleType.PT_BR)));
        assertEquals("Market", layout.getTitle(), "with nobody looking, the plugin's own language answers");
    }

    @Test
    void oneScreenOpenedTwiceTitlesEachWindowInItsOwnViewersLanguage() {
        Screen<MarketLayout> screen = Screen.of(MarketLayout.class);

        ScreenView english = track(world.open(screen, viewerSpeaking("Steve", LocaleType.EN_US)));
        ScreenView brazilian = track(world.open(screen, viewerSpeaking("Alberto", LocaleType.PT_BR)));

        assertEquals("Market", english.getCurrentTitle());
        assertEquals("Mercado", brazilian.getCurrentTitle(),
                "the same description, and the window each of them got says so");
    }

    @Test
    void aTitleSetByHandIsWhatEveryViewerReads() {
        Screen<MarketLayout> screen = Screen.of(MarketLayout.class).title("Flash sale");

        assertEquals("Flash sale",
                track(world.open(screen, viewerSpeaking("Steve", LocaleType.EN_US))).getCurrentTitle());
        assertEquals("Flash sale",
                track(world.open(screen, viewerSpeaking("Alberto", LocaleType.PT_BR))).getCurrentTitle(),
                "a title the plugin insisted on outranks the file's, whoever is reading");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  One channel per icon
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void anIconNamedThroughTwoChannelsIsRefusedByNameAndTheScreenStillLoads() {
        ConfusedLayout layout = Layouts.of(ConfusedLayout.class);

        assertNull(layout.getIcon("SELL"), "the icon nobody can render an unambiguous text for is dropped");
        assertNotNull(layout.getIcon("INNOCENT"), "and the rest of the screen is not punished for it");
        assertTrue(logged("ConfusedLayout.SELL"), logs());
        assertTrue(logged("it names the same icon in two places"), logs());
        assertTrue(logged("Pick one"), "the message has to name the way out: " + logs());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Who wins
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void theFileBeatsTheAnnotationAndAnOverlayBeatsThemBoth() throws IOException {
        Layouts.of(MarketLayout.class);
        rewrite("- name:Buy", "- name:Purchase");
        Layouts.clear();

        Screen<?> base = Screen.of(3);
        base.icon(0, Layouts.of(MarketLayout.class).getIcon("BUY"));
        assertEquals("Purchase", nameAt(open(base, viewerSpeaking("Steve", LocaleType.EN_US)), 0),
                "what the admin wrote in the file outranks what the plugin declared in Java");

        writeOverlay(LocaleType.EN_US, Arrays.asList(
                "Layout:",
                "  BUY:",
                "    Locale:",
                "      EN_US:",
                "      - name:Acquire"
        ));
        Layouts.clear();

        Screen<?> localized = Screen.of(3);
        localized.icon(0, Layouts.of(MarketLayout.class, LocaleType.EN_US).getIcon("BUY"));
        assertEquals("Acquire", nameAt(open(localized, viewerSpeaking("Alex", LocaleType.EN_US)), 0),
                "and a whole screen written for one language outranks the file everyone shares");
    }

    @Test
    void anOverlayAnswersForTheKeysItNamesDownToTheSlot() throws IOException {
        Layouts.of(MarketLayout.class);
        writeOverlay(LocaleType.PT_BR, Arrays.asList(
                "Layout:",
                "  BUY:",
                "    Slot: \"[2]\"",
                "    DisplayItem:",
                "    - material:minecraft:diamond"
        ));
        Layouts.clear();

        MarketLayout localized = Layouts.of(MarketLayout.class, LocaleType.PT_BR);
        MarketLayout shared = Layouts.of(MarketLayout.class);

        assertEquals("[2]", localized.getIcons().get("BUY").getSlots().serialize(),
                "here a stone does become a sword: the overlay is an override of any key at all");
        assertEquals("minecraft:diamond", localized.getIcon("BUY").getItem().getMaterialId());
        assertEquals("[0]", shared.getIcons().get("BUY").getSlots().serialize(),
                "and the base file everyone else reads is untouched");
        assertEquals("minecraft:emerald", shared.getIcon("BUY").getItem().getMaterialId());
    }

    @Test
    void aKeyOnlyAnOverlayKnowsAboutIsNeverMovedIntoQuarantine() throws IOException {
        Layouts.of(MarketLayout.class);
        writeOverlay(LocaleType.PT_BR, Arrays.asList(
                "Layout:",
                "  BUY:",
                "    Slot: \"[2]\"",
                "  A_KEY_THE_PLUGIN_NEVER_HAD:",
                "    Slot: \"[3]\""
        ));
        Layouts.clear();

        Layouts.of(MarketLayout.class, LocaleType.PT_BR);

        assertFalse(seededFile().contains("A_KEY_THE_PLUGIN_NEVER_HAD"),
                "the overlay's keys never reach the base file:\n" + seededFile());
        assertTrue(overlayFile(LocaleType.PT_BR).contains("A_KEY_THE_PLUGIN_NEVER_HAD"),
                "the overlay is the admin's, and the framework does not rewrite their work");
        assertEquals(Arrays.asList("A_KEY_THE_PLUGIN_NEVER_HAD"),
                orphansOf(LayoutDiff.of(plugin, MarketLayout.class, LocaleType.PT_BR)),
                "it is reported instead - which is all a file the framework may not edit allows");
    }

    @Test
    void withNoOverlayEveryLanguageSharesTheOneCopyThatWasRead() {
        MarketLayout shared = Layouts.of(MarketLayout.class);

        assertSame(shared, Layouts.of(MarketLayout.class, LocaleType.PT_BR),
                "without an overlay there is nothing to differ, so no second read and no second copy");
        assertFalse(Files.exists(tempDir.resolve("guis/locale")),
                "the framework never creates the overlay folder, however many languages are declared");
    }

    @Test
    void anIconShowingAStateReadsThatStatesOwnWordsInTheViewersLanguage() {
        Icon smelt = Layouts.of(ForgeLayout.class).getIcon("SMELT").state(() -> "busy");
        Screen<?> screen = Screen.of(3);
        screen.icon(0, smelt);

        RecordingSurface english = open(screen, viewerSpeaking("Steve", LocaleType.EN_US));
        RecordingSurface brazilian = open(screen, viewerSpeaking("Alberto", LocaleType.PT_BR));

        assertEquals("minecraft:barrier", materialAt(english, 0), "the state decides the item");
        assertEquals("Working", nameAt(english, 0),
                "and its own words, not the ones the default appearance reads");
        assertEquals("Trabalhando", nameAt(brazilian, 0), "in the language of whoever is looking");
    }

    @Test
    void aViewerWithAnOverlayGetsAWindowPaintedFromTheirOwnCopyOfTheLayout() throws IOException {
        Layouts.of(MarketLayout.class);
        writeOverlay(LocaleType.PT_BR, Arrays.asList(
                "Layout:",
                "  BUY:",
                "    DisplayItem:",
                "    - material:minecraft:diamond"
        ));
        Layouts.clear();

        Screen<MarketLayout> screen = Screen.of(MarketLayout.class);
        RecordingSurface brazilian = open(screen, viewerSpeaking("Alberto", LocaleType.PT_BR));
        RecordingSurface english = open(screen, viewerSpeaking("Steve", LocaleType.EN_US));

        assertEquals("minecraft:diamond", materialAt(brazilian, 0),
                "the screen a whole file was written for is the screen that viewer opens");
        assertEquals("minecraft:emerald", materialAt(english, 0),
                "and a language nobody wrote a file for still reads the one everybody shares");
    }

    @Test
    void anOverlayIsFoundWhateverCaseTheLanguageWasTypedIn() throws IOException {
        Layouts.of(MarketLayout.class);
        writeOverlay(LocaleType.PT_BR, Arrays.asList("Layout:", "  BUY:", "    Slot: \"[2]\""));

        assertTrue(LayoutDiff.of(plugin, MarketLayout.class, "pt_br").hasOverlay(),
                "an operator types the language, and a case-sensitive filesystem is not their problem");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  An overlay answers for KEYS, never for whole sections
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void anOverlayThatRestylesOneBackgroundKeyLeavesTheDecorationBesideItAlone() throws IOException {
        writeBase("LockerLayout", Arrays.asList(
                "Background:",
                "  LEFT_PANE:",
                "    Slot: \"[1]\"",
                "    DisplayItem:",
                "    - material:minecraft:gray_stained_glass_pane",
                "  SIDE_PANE:",
                "    Slot: \"[2]\"",
                "    DisplayItem:",
                "    - material:minecraft:paper"
        ));
        writeOverlay("LockerLayout", LocaleType.PT_BR, Arrays.asList(
                "Background:",
                "  LEFT_PANE:",
                "    DisplayItem:",
                "    - material:minecraft:diamond_block"
        ));

        LockerLayout localized = Layouts.of(LockerLayout.class, LocaleType.PT_BR);

        assertEquals("minecraft:diamond_block", localized.getIcon("LEFT_PANE").getItem().getMaterialId(),
                "the overlay answers for the key it names");
        assertNotNull(localized.getIcon("SIDE_PANE"),
                "and the decoration next to it, which only the base file knows about, is still drawn");
    }

    @Test
    void anOverlayThatRestylesOneStateLeavesTheOtherStatesAsTheFileLeftThem() throws IOException {
        writeBase("LockerLayout", Arrays.asList(
                "Layout:",
                "  OPEN:",
                "    Slot: \"[0]\"",
                "    States:",
                "      locked:",
                "        DisplayItem:",
                "        - material:minecraft:iron_ingot",
                "      maxed:",
                "        DisplayItem:",
                "        - material:minecraft:gold_block"
        ));
        writeOverlay("LockerLayout", LocaleType.PT_BR, Arrays.asList(
                "Layout:",
                "  OPEN:",
                "    States:",
                "      locked:",
                "        DisplayItem:",
                "        - material:minecraft:diamond"
        ));

        Icon open = Layouts.of(LockerLayout.class, LocaleType.PT_BR).getIcon("OPEN");

        assertEquals("minecraft:diamond", open.getState("locked").getMaterialId(), "the state the overlay restyled");
        assertEquals("minecraft:gold_block", open.getState("maxed").getMaterialId(),
                "and the one it did not, which the admin restyled in the file everyone shares");
    }

    @Test
    void anOverlayThatWritesOneLanguageBlockLeavesTheOtherLanguagesOfTheBaseAlone() throws IOException {
        writeBase("LockerLayout", Arrays.asList(
                "Layout:",
                "  OPEN:",
                "    Slot: \"[0]\"",
                "    Locale:",
                "      EN_US:",
                "      - name:Unlock",
                "      PT_BR:",
                "      - name:Abrir"
        ));
        writeOverlay("LockerLayout", LocaleType.PT_BR, Arrays.asList(
                "Layout:",
                "  OPEN:",
                "    Locale:",
                "      PT_BR:",
                "      - name:Destrancar"
        ));

        Screen<?> screen = Screen.of(3);
        screen.icon(0, Layouts.of(LockerLayout.class, LocaleType.PT_BR).getIcon("OPEN"));
        RecordingSurface brazilian = open(screen, viewerSpeaking("Alberto", LocaleType.PT_BR));
        RecordingSurface english = open(screen, viewerSpeaking("Steve", LocaleType.EN_US));

        assertEquals("Destrancar", nameAt(brazilian, 0), "the block the overlay wrote");
        assertEquals("Unlock", nameAt(english, 0),
                "and the block it did not write still reads what the base file says, not the Java default");
    }

    @Test
    void aTitleLanguageOnlyTheBaseFileKnowsAboutIsStillReadUnderAnOverlay() throws IOException {
        writeBase("LockerLayout", Arrays.asList(
                "Settings:",
                "  title: Locker",
                "  Locale:",
                "    ZH_CN:",
                "      title: Guizi"
        ));
        writeOverlay("LockerLayout", LocaleType.PT_BR, Arrays.asList(
                "Settings:",
                "  Locale:",
                "    PT_BR:",
                "      title: Armario da Loja"
        ));

        LockerLayout localized = Layouts.of(LockerLayout.class, LocaleType.PT_BR);

        assertEquals("Armario da Loja", localized.getTitleFor(viewerSpeaking("Alberto", LocaleType.PT_BR)),
                "the title the overlay wrote");
        assertEquals("Guizi", localized.getTitleFor(viewerSpeaking("Wei", LocaleType.ZH_CN)),
                "and a language only the base file declares is still one of the languages this screen has");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  When a language has nothing to say
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aLanguageNobodyWroteFallsBackToTheOneTheServerRunsIn() {
        assertEquals(LocaleType.EN_US, plugin.getPluginLanguage(),
                "the whole point of this case is that the server's language is not the first declared");
        Screen<?> screen = Screen.of(3);
        screen.icon(0, Layouts.of(FallbackLayout.class).getIcon("BUY"));

        String name = nameAt(open(screen, viewerSpeaking("Wei", LocaleType.ZH_CN)), 0);

        assertEquals("Buy", name, "a screen never renders a missing translation, and what it renders "
                + "instead is the language everyone on this server already reads");
    }

    @Test
    void anIconWithASingleTextReadsTheSameToEveryone() {
        Screen<?> screen = Screen.of(3);
        screen.icon(0, Layouts.of(PlainLayout.class).getIcon("LABEL"));

        RecordingSurface english = open(screen, viewerSpeaking("Steve", LocaleType.EN_US));
        RecordingSurface brazilian = open(screen, viewerSpeaking("Alberto", LocaleType.PT_BR));

        assertEquals(nameAt(english, 0), nameAt(brazilian, 0));
        assertEquals("§7Just this", nameAt(english, 0), "and the text it does have still arrives, read from the file");
    }

    @Test
    void theBackButtonSpeaksTheViewersLanguage() {
        FCLocaleManager.loadLocale(plugin, DefaultIcons.class);
        Screen<?> screen = Screen.of(3);
        screen.icon(0, DefaultIcons.back());

        RecordingSurface english = open(screen, viewerSpeaking("Steve", LocaleType.EN_US));
        RecordingSurface brazilian = open(screen, viewerSpeaking("Alberto", LocaleType.PT_BR));

        assertTrue(loreAt(english, 0).toString().contains("Back"), loreAt(english, 0).toString());
        assertTrue(loreAt(brazilian, 0).toString().contains("Voltar"), loreAt(brazilian, 0).toString());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------------------------------------------------

    /** A player who is logged in and has chosen a language of their own. */
    private TestFPlayerSender viewerSpeaking(String name, String language) {
        return locales.reader(name, language);
    }

    private ScreenView track(ScreenView view) {
        opened.add(view);
        return view;
    }

    /** Opens {@code screen} for {@code viewer} and hands back what the surface of that one window holds. */
    private RecordingSurface open(Screen<?> screen, TestFPlayerSender viewer) {
        ScreenView view = track(world.open(screen, viewer));
        return world.getRenderer().surfaceOf(view);
    }

    private static String materialAt(RecordingSurface surface, int slot) {
        RenderedIcon drawn = surface.getSlot(slot);
        return drawn == null ? null : drawn.getItem().getMaterialId();
    }

    private static String nameAt(RecordingSurface surface, int slot) {
        RenderedIcon drawn = surface.getSlot(slot);
        return drawn == null ? null : drawn.getItem().getDisplayName();
    }

    private static List<String> loreAt(RecordingSurface surface, int slot) {
        RenderedIcon drawn = surface.getSlot(slot);
        return drawn == null ? null : drawn.getItem().getLore();
    }

    private static List<String> orphansOf(LayoutDiff diff) {
        List<String> names = new ArrayList<>();
        for (LayoutDiff.Entry entry : diff.getEntries(LayoutDiff.Verdict.ORPHAN)) {
            names.add(entry.getKey());
        }
        return names;
    }

    private boolean logged(String fragment) {
        for (String line : world.getPlatform().getLoggedMessages()) {
            if (line.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private String logs() {
        return world.getPlatform().getLoggedMessages().toString();
    }

    private Path seededPath() {
        return tempDir.resolve("guis/MarketLayout.yml");
    }

    private String seededFile() throws IOException {
        return new String(Files.readAllBytes(seededPath()), StandardCharsets.UTF_8);
    }

    private void rewrite(String from, String to) throws IOException {
        String content = seededFile();
        assertTrue(content.contains(from), "the file has to hold what the test edits:\n" + content);
        Files.write(seededPath(), content.replace(from, to).getBytes(StandardCharsets.UTF_8));
    }

    private void writeOverlay(String language, List<String> lines) throws IOException {
        writeOverlay("MarketLayout", language, lines);
    }

    private void writeOverlay(String layoutName, String language, List<String> lines) throws IOException {
        Path overlay = tempDir.resolve("guis/locale/" + language + "/" + layoutName + ".yml");
        Files.createDirectories(overlay.getParent());
        Files.write(overlay, lines, StandardCharsets.UTF_8);
    }

    /** The base file as an admin who edits by hand leaves it: only the keys the test is about. */
    private void writeBase(String layoutName, List<String> lines) throws IOException {
        Path file = tempDir.resolve("guis/" + layoutName + ".yml");
        Files.createDirectories(file.getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private String overlayFile(String language) throws IOException {
        return new String(Files.readAllBytes(
                tempDir.resolve("guis/locale/" + language + "/MarketLayout.yml")), StandardCharsets.UTF_8);
    }

}
