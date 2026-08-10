package br.com.finalcraft.evernifecore.minecraft.gui.layout;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.LocalePDSection;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.icons.DefaultIcons;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.PlayerDouble;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.SurfaceDouble;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiView;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One screen, read by people who do not share a language.
 *
 * <p>Text is the only thing that varies per viewer inside the base file: the material, the slot and the
 * nbt are the screen, and a language block cannot turn a stone into a sword. A whole screen per language
 * is a separate, opt-in file the framework never writes, and only there does everything become
 * negotiable.</p>
 *
 * <p>Every assertion reads what the BUFFER wrote for a given viewer, because that is the only place the
 * viewer exists: an icon holds one canonical stack and resolves its text at paint time.</p>
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

    private GuiTestWorld world;
    private ECPluginData plugin;
    private boolean perPlayerLocaleBefore;

    @GuiLayout(title = "Market", rows = 3, locale = {
            @FCLocale(lang = LocaleType.EN_US, text = "Market"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Mercado")
    })
    public static class MarketLayout extends LayoutBase {

        @IconData(slot = {0}, locale = {
                @FCLocale(lang = LocaleType.EN_US, text = "Buy", hover = "Price: %price%"),
                @FCLocale(lang = LocaleType.PT_BR, text = "Comprar", hover = "Preco: %price%")
        })
        public Icon BUY = Icon.of(new ItemStack(Material.EMERALD));

        @IconData(slot = {4}, permission = "market.admin")
        public Icon EDIT = Icon.of(new ItemStack(Material.ANVIL));

        @IconData(slot = {8}, background = true)
        public Icon CORNER = Icon.of(new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
    }

    /** One icon named twice: through the annotation, and through the stack it was built with. */
    @GuiLayout(title = "Confused", rows = 3)
    public static class ConfusedLayout extends LayoutBase {

        @IconData(slot = {0}, locale = {
                @FCLocale(lang = LocaleType.EN_US, text = "Sell"),
                @FCLocale(lang = LocaleType.PT_BR, text = "Vender")
        })
        public Icon SELL = Icon.of(new ItemStack(Material.EMERALD)).displayName("&aSell");

        @IconData(slot = {1})
        public Icon INNOCENT = Icon.of(new ItemStack(Material.PAPER));
    }

    /** An icon with a single text and no language at all - the case the late path must not charge for. */
    @GuiLayout(title = "Plain", rows = 3)
    public static class PlainLayout extends LayoutBase {

        @IconData(slot = {0})
        public Icon LABEL = Icon.of(new ItemStack(Material.PAPER)).displayName("&7Just this");
    }

    /** A placeholder baked into the stack itself, with no language block anywhere near it - the other
     *  way an icon's text differs from what it was declared with. */
    @GuiLayout(title = "Ticker", rows = 3)
    public static class TickerLayout extends LayoutBase {

        @IconData(slot = {0})
        public Icon PRICE = Icon.of(new ItemStack(Material.PAPER)).displayName("Now: %price%");
    }

    /** More than one of everything an overlay can name a single key of: two background keys, two states,
     *  two language blocks, and a title language only the file knows about. */
    @GuiLayout(title = "Locker", rows = 3, locale = {
            @FCLocale(lang = LocaleType.EN_US, text = "Locker"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Armario")
    })
    public static class LockerLayout extends LayoutBase {

        @IconData(slot = {0}, locale = {
                @FCLocale(lang = LocaleType.EN_US, text = "Open"),
                @FCLocale(lang = LocaleType.PT_BR, text = "Abrir")
        })
        public Icon OPEN = Icon.of(new ItemStack(Material.EMERALD))
                .addState("locked", new ItemStack(Material.BARRIER))
                .addState("maxed", new ItemStack(Material.NETHER_STAR));

        @IconData(slot = {1}, background = true)
        public Icon LEFT_PANE = Icon.of(new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
    }

    /** An icon whose alternative appearance has words of its own, in more than one language. */
    @GuiLayout(title = "Forge", rows = 3)
    public static class ForgeLayout extends LayoutBase {

        @IconData(slot = {0}, locale = {
                @FCLocale(lang = LocaleType.EN_US, text = "Smelt"),
                @FCLocale(lang = LocaleType.PT_BR, text = "Fundir")
        })
        public Icon SMELT = Icon.of(new ItemStack(Material.FURNACE))
                .addState("busy", new ItemStack(Material.BARRIER))
                .addStateLocale("busy", LocaleType.EN_US, "Working")
                .addStateLocale("busy", LocaleType.PT_BR, "Trabalhando");
    }

    /** A language the plugin does not run in, declared FIRST: the only shape that tells "the server's
     *  language" apart from "whatever the developer typed first". */
    @GuiLayout(title = "Fallback", rows = 3)
    public static class FallbackLayout extends LayoutBase {

        @IconData(slot = {0}, locale = {
                @FCLocale(lang = LocaleType.PT_BR, text = "Comprar"),
                @FCLocale(lang = LocaleType.EN_US, text = "Buy")
        })
        public Icon BUY = Icon.of(new ItemStack(Material.EMERALD));
    }

    @BeforeEach
    void setup() {
        world = GuiTestWorld.installWithItemMetadata(tempDir);
        plugin = world.getPluginData();
        Plugins.setLanguage(plugin, LocaleType.EN_US);

        perPlayerLocaleBefore = ECSettings.PER_PLAYER_LOCALE;
        ECSettings.PER_PLAYER_LOCALE = true;
        PlayerController.initialize(Storages.memory().writeTo(tempDir));

        Layouts.clear();
    }

    @AfterEach
    void teardown() {
        Layouts.clear();
        PlayerDataWorld.tearDown();
        ECSettings.PER_PLAYER_LOCALE = perPlayerLocaleBefore;
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
        Gui<?> gui = Gui.of(3).icon(0, buy);

        world.openDetached(gui, viewerSpeaking("Steve", LocaleType.EN_US));
        SurfaceDouble english = world.getSurface();
        world.openDetached(gui, viewerSpeaking("Alberto", LocaleType.PT_BR));
        SurfaceDouble brazilian = world.getSurface();

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
        List<String> localized = ItemEngine.get().read(buy.getItemStack()).getLines();
        List<String> baked = ItemEngine.get().read(price.getItemStack()).getLines();
        assertTrue(baked.contains("name:Now: %price%"), baked.toString());

        Gui<?> gui = Gui.of(3).icon(0, buy).icon(1, price);
        world.openDetached(gui, viewerSpeaking("Steve", LocaleType.EN_US));
        SurfaceDouble drawn = world.getSurface();
        world.openDetached(gui, viewerSpeaking("Alberto", LocaleType.PT_BR));

        assertEquals("Now: 9.50", nameAt(drawn, 1), "the viewer's copy did get the resolved text");
        assertEquals(localized, ItemEngine.get().read(buy.getItemStack()).getLines(),
                "an icon two people are looking at cannot be mutated through either of them");
        assertEquals(baked, ItemEngine.get().read(price.getItemStack()).getLines(),
                "and an icon whose text is a placeholder still holds the placeholder afterwards");
        assertEquals(localized, ItemEngine.get().read(
                        Layouts.of(MarketLayout.class).getIcon("BUY").getItemStack()).getLines(),
                "so the layout hands out the same icon it always did");
    }

    @Test
    void aViewerWithoutThePermissionSeesTheLayerBelowRatherThanAHole() {
        MarketLayout layout = Layouts.of(MarketLayout.class);
        Gui<?> gui = Gui.of(3)
                .icon(4, Icon.of(new ItemStack(Material.GRAY_STAINED_GLASS_PANE)).background())
                .icon(4, layout.getIcon("EDIT"));

        PlayerDouble admin = viewerSpeaking("Admin", LocaleType.EN_US);
        admin.withPermission("market.admin");
        world.openDetached(gui, admin);
        SurfaceDouble seen = world.getSurface();

        world.openDetached(gui, viewerSpeaking("Guest", LocaleType.EN_US));
        SurfaceDouble hidden = world.getSurface();

        assertEquals(Material.ANVIL, seen.getItem(4).getType(),
                "who may see an icon is asked of the viewer, not of the plugin that declared it");
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, hidden.getItem(4).getType(),
                "and the slot the icon did not take shows what is under it, not a hole");
    }

    @Test
    void theTitleFollowsTheViewer() {
        MarketLayout layout = Layouts.of(MarketLayout.class);

        assertEquals("Market", layout.getTitleFor(viewerSpeaking("Steve", LocaleType.EN_US).asPlayer()));
        assertEquals("Mercado", layout.getTitleFor(viewerSpeaking("Alberto", LocaleType.PT_BR).asPlayer()));
        assertEquals("Market", layout.getTitle(), "with nobody looking, the plugin's own language answers");
    }

    @Test
    void oneScreenOpenedTwiceTitlesEachWindowInItsOwnViewersLanguage() {
        Gui<MarketLayout> gui = Gui.of(MarketLayout.class);

        GuiView english = world.open(gui, viewerSpeaking("Steve", LocaleType.EN_US));
        GuiView brazilian = world.open(gui, viewerSpeaking("Alberto", LocaleType.PT_BR));

        assertEquals("Market", english.getCurrentTitle());
        assertEquals("Mercado", brazilian.getCurrentTitle(),
                "the same description, and the window each of them got says so");
    }

    @Test
    void aTitleSetByHandIsWhatEveryViewerReads() {
        Gui<MarketLayout> gui = Gui.of(MarketLayout.class).title("Flash sale");

        assertEquals("Flash sale", world.open(gui, viewerSpeaking("Steve", LocaleType.EN_US)).getCurrentTitle());
        assertEquals("Flash sale", world.open(gui, viewerSpeaking("Alberto", LocaleType.PT_BR)).getCurrentTitle(),
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

        Gui<?> base = Gui.of(3).icon(0, Layouts.of(MarketLayout.class).getIcon("BUY"));
        world.openDetached(base, viewerSpeaking("Steve", LocaleType.EN_US));
        assertEquals("Purchase", nameAt(world.getSurface(), 0),
                "what the admin wrote in the file outranks what the plugin declared in Java");

        writeOverlay(LocaleType.EN_US, Arrays.asList(
                "Layout:",
                "  BUY:",
                "    Locale:",
                "      EN_US:",
                "      - name:Acquire"
        ));
        Layouts.clear();

        Gui<?> localized = Gui.of(3)
                .icon(0, Layouts.of(MarketLayout.class, LocaleType.EN_US).getIcon("BUY"));
        world.openDetached(localized, viewerSpeaking("Alex", LocaleType.EN_US));
        assertEquals("Acquire", nameAt(world.getSurface(), 0),
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
                "    - type:DIAMOND"
        ));
        Layouts.clear();

        MarketLayout localized = Layouts.of(MarketLayout.class, LocaleType.PT_BR);
        MarketLayout shared = Layouts.of(MarketLayout.class);

        assertEquals("[2]", localized.getIcons().get("BUY").getSlots().serialize(),
                "here a stone does become a sword: the overlay is an override of any key at all");
        assertEquals(Material.DIAMOND, localized.getIcon("BUY").getItemStack().getType());
        assertEquals("[0]", shared.getIcons().get("BUY").getSlots().serialize(),
                "and the base file everyone else reads is untouched");
        assertEquals(Material.EMERALD, shared.getIcon("BUY").getItemStack().getType());
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

        assertEquals(shared, Layouts.of(MarketLayout.class, LocaleType.PT_BR),
                "without an overlay there is nothing to differ, so no second read and no second copy");
        assertFalse(Files.exists(tempDir.resolve("guis/locale")),
                "the framework never creates the overlay folder, however many languages are declared");
    }

    @Test
    void anIconShowingAStateReadsThatStatesOwnWordsInTheViewersLanguage() {
        Icon smelt = Layouts.of(ForgeLayout.class).getIcon("SMELT").state(() -> "busy");
        Gui<?> gui = Gui.of(3).icon(0, smelt);

        world.openDetached(gui, viewerSpeaking("Steve", LocaleType.EN_US));
        SurfaceDouble english = world.getSurface();
        world.openDetached(gui, viewerSpeaking("Alberto", LocaleType.PT_BR));
        SurfaceDouble brazilian = world.getSurface();

        assertEquals(Material.BARRIER, english.getItem(0).getType(), "the state decides the item");
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
                "    - type:DIAMOND"
        ));
        Layouts.clear();

        Gui<MarketLayout> gui = Gui.of(MarketLayout.class);
        world.openDetached(gui, viewerSpeaking("Alberto", LocaleType.PT_BR));
        SurfaceDouble brazilian = world.getSurface();
        world.openDetached(gui, viewerSpeaking("Steve", LocaleType.EN_US));
        SurfaceDouble english = world.getSurface();

        assertEquals(Material.DIAMOND, brazilian.getItem(0).getType(),
                "the screen a whole file was written for is the screen that viewer opens");
        assertEquals(Material.EMERALD, english.getItem(0).getType(),
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
                "    - type:GRAY_STAINED_GLASS_PANE",
                "  SIDE_PANE:",
                "    Slot: \"[2]\"",
                "    DisplayItem:",
                "    - type:PAPER"
        ));
        writeOverlay("LockerLayout", LocaleType.PT_BR, Arrays.asList(
                "Background:",
                "  LEFT_PANE:",
                "    DisplayItem:",
                "    - type:DIAMOND_BLOCK"
        ));

        LockerLayout localized = Layouts.of(LockerLayout.class, LocaleType.PT_BR);

        assertEquals(Material.DIAMOND_BLOCK, localized.getIcon("LEFT_PANE").getItemStack().getType(),
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
                "        - type:IRON_INGOT",
                "      maxed:",
                "        DisplayItem:",
                "        - type:GOLD_BLOCK"
        ));
        writeOverlay("LockerLayout", LocaleType.PT_BR, Arrays.asList(
                "Layout:",
                "  OPEN:",
                "    States:",
                "      locked:",
                "        DisplayItem:",
                "        - type:DIAMOND"
        ));

        Icon open = Layouts.of(LockerLayout.class, LocaleType.PT_BR).getIcon("OPEN");

        assertEquals(Material.DIAMOND, open.getState("locked").getType(), "the state the overlay restyled");
        assertEquals(Material.GOLD_BLOCK, open.getState("maxed").getType(),
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

        Gui<?> gui = Gui.of(3).icon(0, Layouts.of(LockerLayout.class, LocaleType.PT_BR).getIcon("OPEN"));
        world.openDetached(gui, viewerSpeaking("Alberto", LocaleType.PT_BR));
        SurfaceDouble brazilian = world.getSurface();
        world.openDetached(gui, viewerSpeaking("Steve", LocaleType.EN_US));
        SurfaceDouble english = world.getSurface();

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

        assertEquals("Armario da Loja",
                localized.getTitleFor(viewerSpeaking("Alberto", LocaleType.PT_BR).asPlayer()),
                "the title the overlay wrote");
        assertEquals("Guizi", localized.getTitleFor(viewerSpeaking("Wei", LocaleType.ZH_CN).asPlayer()),
                "and a language only the base file declares is still one of the languages this screen has");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  When a language has nothing to say
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aLanguageNobodyWroteFallsBackToTheOneTheServerRunsIn() {
        assertEquals(LocaleType.EN_US, plugin.getPluginLanguage(),
                "the whole point of this case is that the server's language is not the first declared");
        Gui<?> gui = Gui.of(3).icon(0, Layouts.of(FallbackLayout.class).getIcon("BUY"));

        world.openDetached(gui, viewerSpeaking("Wei", LocaleType.ZH_CN));

        String name = nameAt(world.getSurface(), 0);
        assertEquals("Buy", name, "a screen never renders a missing translation, and what it renders "
                + "instead is the language everyone on this server already reads");
    }

    @Test
    void anIconWithASingleTextReadsTheSameToEveryone() {
        Gui<?> gui = Gui.of(3).icon(0, Layouts.of(PlainLayout.class).getIcon("LABEL"));

        world.openDetached(gui, viewerSpeaking("Steve", LocaleType.EN_US));
        SurfaceDouble english = world.getSurface();
        world.openDetached(gui, viewerSpeaking("Alberto", LocaleType.PT_BR));
        SurfaceDouble brazilian = world.getSurface();

        assertEquals(nameAt(english, 0), nameAt(brazilian, 0));
        assertNotNull(nameAt(english, 0), "and the text it does have still arrives");
    }

    @Test
    void theBackButtonSpeaksTheViewersLanguage() {
        FCLocaleManager.loadLocale(plugin, DefaultIcons.class);
        Gui<?> gui = Gui.of(3).icon(0, DefaultIcons.back());

        world.openDetached(gui, viewerSpeaking("Steve", LocaleType.EN_US));
        SurfaceDouble english = world.getSurface();
        world.openDetached(gui, viewerSpeaking("Alberto", LocaleType.PT_BR));
        SurfaceDouble brazilian = world.getSurface();

        assertTrue(loreAt(english, 0).toString().contains("Back"), loreAt(english, 0).toString());
        assertTrue(loreAt(brazilian, 0).toString().contains("Voltar"), loreAt(brazilian, 0).toString());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------------------------------------------------

    /** A player the server already knows, and who has chosen a language of their own. */
    private PlayerDouble viewerSpeaking(String name, String language) {
        PlayerDouble player = world.newPlayer(name);
        PlayerController.handleLogin(player.getUniqueId(), name).join()
                .getPDSection(LocalePDSection.class).join().setLang(language);
        return player;
    }

    private static String nameAt(SurfaceDouble surface, int slot) {
        ItemStack drawn = surface.getItem(slot);
        return drawn == null || drawn.getItemMeta() == null ? null : drawn.getItemMeta().getDisplayName();
    }

    private static List<String> loreAt(SurfaceDouble surface, int slot) {
        ItemStack drawn = surface.getItem(slot);
        return drawn == null || drawn.getItemMeta() == null ? null : drawn.getItemMeta().getLore();
    }

    private static List<String> orphansOf(LayoutDiff diff) {
        List<String> names = new java.util.ArrayList<>();
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
