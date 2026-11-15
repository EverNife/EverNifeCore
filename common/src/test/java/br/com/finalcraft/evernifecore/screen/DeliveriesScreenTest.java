package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.delivery.Deliveries;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryIcon;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryOrigin;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryPolicy;
import br.com.finalcraft.evernifecore.logic.delivery.command.DeliveriesCommandNode;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.RecordingSurface;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The core's deliveries screen lists what waits for the viewer, and a click claims one or all of it. */
class DeliveriesScreenTest {

    private static final String GIFT = "fakeshop:gift";

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private LogicWorld shop;
    private TestFPlayerSender alex;
    private Deliveries deliveries;
    private final List<String> given = Collections.synchronizedList(new ArrayList<String>());

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir, platform -> platform.withInventory(1));
        alex = world.viewer("Alex");
        shop = LogicWorld.open("FakeShop", tempDir.resolve("shop"));
        deliveries = shop.deliveries();
        Effects.registry().register(shop.getOwner(), TypeDescriptor.<Effect>of(GIFT)
                .describe("Hands the player a named gift.")
                .param(ParamSpec.required("name", ParamSpec.Kind.STRING, "The gift."))
                .factory(params -> {
                    String name = params.getString("name");
                    return ctx -> given.add(name);
                })
                .build());
        Layouts.clear();
    }

    @AfterEach
    void teardown() {
        Layouts.clear();
        Effects.registry().unregister(shop.getOwner(), GIFT);
        shop.close();
        world.close();
    }

    private static JsonNode node(String singleQuotedJson) {
        try {
            return new ObjectMapper().readTree(singleQuotedJson.replace('\'', '"'));
        } catch (IOException unreadable) {
            throw new IllegalArgumentException(unreadable);
        }
    }

    private Delivery enqueue(String gift, String reason) {
        return enqueue(gift, reason, null);
    }

    private Delivery enqueue(String gift, String reason, DeliveryIcon icon) {
        return enqueueNode(node("{'type': '" + GIFT + "', 'name': '" + gift + "'}"), reason, icon);
    }

    private Delivery enqueueNode(JsonNode effect, String reason, DeliveryIcon icon) {
        try {
            return deliveries.enqueue(alex.getUniqueId(), DeliveryOrigin.of("FakeShop", "test", reason), DeliveryPolicy.KEEP_SELF_CONTAINED,
                    null, icon, Collections.singletonList(effect)).get(5, TimeUnit.SECONDS);
        } catch (Exception failed) {
            throw new AssertionError(failed);
        }
    }

    /** Waits until the listed deliveries answer {@code test}, then for the redraw. */
    private void settleUntil(ScreenView view, Predicate<List<Delivery>> test) {
        long until = System.currentTimeMillis() + 5000;
        DeliveriesScreen screen = (DeliveriesScreen) view.getScreen();
        while (!test.test(screen.getPending()) && System.currentTimeMillis() < until) {
            Thread.yield();
        }
        world.advanceTicks(2);
    }

    private static int stillOwed(List<Delivery> pending, int index) {
        return pending.size() <= index ? -1 : pending.get(index).getEffects().get(0).path("amount").asInt();
    }

    private ScreenView open() {
        ScreenView view = Screens.open(alex, ScreenKey.DELIVERIES, ScreenRequest.of(shop.getOwner())).join();
        settle(view, -1);
        return view;
    }

    /** Waits for the storage read the screen started to land, then for its redraw. */
    private void settle(ScreenView view, int expected) {
        long until = System.currentTimeMillis() + 5000;
        DeliveriesScreen screen = (DeliveriesScreen) view.getScreen();
        while (expected >= 0 && screen.getPending().size() != expected && System.currentTimeMillis() < until) {
            Thread.yield();
        }
        world.advanceTicks(2);
    }

    private RecordingSurface surface(ScreenView view) {
        return world.getRenderer().surfaceOf(view);
    }

    private void close(ScreenView view) {
        view.close();
        world.advanceTicks(2);
    }

    @Test
    void everyPendingDeliveryIsListedAndAClickClaimsThatOne() {
        enqueue("sword", "refund");
        enqueue("shield", "prize");

        ScreenView view = open();
        settle(view, 2);
        assertEquals("§efakeshop", surface(view).getSlot(0).getItem().getDisplayName());
        assertTrue(surface(view).getSlot(1).getItem().getLore().contains("§7prize"), surface(view).getSlot(1).getItem().getLore().toString());

        world.click(view, "deliveries#0", ClickGesture.PRIMARY);
        settle(view, 1);

        assertEquals(Collections.singletonList("sword"), given);
        assertEquals(1, ((DeliveriesScreen) view.getScreen()).getPending().size());
        assertNull(surface(view).getSlot(1), "the claimed one left the list");
        close(view);
    }

    /** The ids the Hytale server's own assets answered for: only a live server can tell an item from a name that looks like one. */
    @Test
    void everyThemeDrawsOnHytaleWithAnIdTheServerHasAsAnItem() throws Exception {
        Set<String> known = new HashSet<>(Arrays.asList("hytale:Furniture_Crude_Chest_Small", "hytale:Ingredient_Bar_Iron", "hytale:Rock_Gem_Diamond"));
        DeliveriesScreen.DeliveriesLayout layout = new DeliveriesScreen.DeliveriesLayout();
        for (DeliveryIcon.Theme theme : DeliveryIcon.Theme.values()) {
            Icon icon = (Icon) DeliveriesScreen.DeliveriesLayout.class.getField("THEME_" + theme).get(layout);
            String hytale = icon.getFallbacks().stream().map(ItemSpec::getMaterialId).filter(id -> id.startsWith("hytale:")).findFirst().orElse(null);
            assertTrue(known.contains(hytale), theme + " draws with " + hytale);
        }
    }

    @Test
    void anEntryDrawsItsOwnItemItsThemesLayoutItemOrTheLayoutsChest() throws IOException {
        Path layout = ECPluginManager.getProvidingPlugin(DeliveriesScreen.DeliveriesLayout.class).getMetaInfo().getDataFolder().toPath().resolve("guis/DeliveriesLayout.yml");
        Files.createDirectories(layout.getParent());
        Files.write(layout, Arrays.asList("Layout:", "  THEME_MONEY:", "    Slot: \"[]\"", "    DisplayItem:", "    - material:minecraft:sunflower"),
                StandardCharsets.UTF_8);
        enqueue("sword", "refund", DeliveryIcon.of(ItemSpec.of("minecraft:diamond", 1)));
        enqueue("coins", "sale", DeliveryIcon.of(DeliveryIcon.Theme.MONEY));
        enqueue("shield", "prize");

        ScreenView view = open();
        settle(view, 3);

        assertEquals("minecraft:diamond", surface(view).getSlot(0).getItem().getMaterialId());
        assertEquals("minecraft:sunflower", surface(view).getSlot(1).getItem().getMaterialId(), "the theme's item is the server's, in the layout");
        assertEquals("minecraft:chest", surface(view).getSlot(2).getItem().getMaterialId(), "a delivery without an icon keeps the entry's item");
        for (int slot = 0; slot < 3; slot++) {
            assertEquals("§efakeshop", surface(view).getSlot(slot).getItem().getDisplayName(), "every entry keeps the layout's text");
        }
        close(view);
    }

    @Test
    void claimAllHandsOutEverythingAndTheEmptyNoticeTakesItsPlace() {
        enqueue("sword", "refund");
        enqueue("shield", "prize");
        ScreenView view = open();
        settle(view, 2);
        assertNotNull(surface(view).getSlot(32), "claim-all shows while something waits");

        world.click(view, "CLAIM_ALL", ClickGesture.PRIMARY);
        settle(view, 0);

        assertEquals(2, given.size(), given.toString());
        assertNull(surface(view).getSlot(0));
        assertNotNull(surface(view).getSlot(30), "the empty notice shows");
        assertNull(surface(view).getSlot(32), "and claim-all goes");
        close(view);
    }

    @Test
    void thirtyDeliveriesArePagedEachDrawnAsWhatItOwesWithItsOrigin() {
        enqueueNode(node("{'type': 'evernifecore:give-item', 'item': 'minecraft:diamond', 'amount': 3}"), "refund", null);
        enqueueNode(node("{'type': 'evernifecore:money', 'amount': '25', 'currency': 'fakeshop:gold'}"), "sale", null);
        for (int i = 0; i < 28; i++) {
            enqueue("gift" + i, "prize");
        }

        ScreenView view = open();
        settle(view, 30);

        ItemSpec diamond = surface(view).getSlot(0).getItem();
        assertEquals("minecraft:diamond", diamond.getMaterialId(), "a give-item draws its own item");
        assertEquals(3, diamond.getAmount());
        assertEquals("§efakeshop", diamond.getDisplayName());
        assertTrue(diamond.getLore().contains("§7refund"), diamond.getLore().toString());
        assertTrue(diamond.getLore().contains("§f• 3x minecraft:diamond"), diamond.getLore().toString());
        assertTrue(diamond.getLore().contains("§8Expires -"), diamond.getLore().toString());
        assertTrue(diamond.getLore().stream().anyMatch(line -> line.matches("§8Since \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")), diamond.getLore().toString());
        ItemSpec money = surface(view).getSlot(1).getItem();
        assertEquals("minecraft:gold_ingot", money.getMaterialId(), "a money draws the layout's THEME_MONEY item");
        assertTrue(money.getLore().contains("§f• 25 fakeshop:gold"), money.getLore().toString());
        assertNotNull(surface(view).getSlot(26), "the first page is full");

        world.click(view, "deliveries#next", ClickGesture.PRIMARY);
        world.advanceTicks(2);

        assertNotNull(surface(view).getSlot(2), "the second page holds the last three");
        assertNull(surface(view).getSlot(3));
        close(view);
    }

    @Test
    void aClickClaimsWhatFitsAndTheEntryRedrawsWithTheRest() {
        enqueueNode(node("{'type': 'evernifecore:give-item', 'item': 'minecraft:stone', 'amount': 100}"), "refund", null);
        ScreenView view = open();
        settle(view, 1);

        world.click(view, "deliveries#0", ClickGesture.PRIMARY);
        settleUntil(view, pending -> stillOwed(pending, 0) == 36);

        assertEquals(64, world.getPlatform().getTestInventory().count(alex, ItemMatcher.of("minecraft:stone")), "one slot took a stack");
        ItemSpec rest = surface(view).getSlot(0).getItem();
        assertEquals(36, rest.getAmount(), "the entry redraws with what is still owed");
        assertTrue(rest.getLore().contains("§f• 36x minecraft:stone"), rest.getLore().toString());
        close(view);
    }

    @Test
    void claimAllHandsOutWhatFitsAndKeepsTheRestListed() {
        enqueue("sword", "refund");
        enqueueNode(node("{'type': 'evernifecore:give-item', 'item': 'minecraft:stone', 'amount': 100}"), "refund", null);
        ScreenView view = open();
        settle(view, 2);

        world.click(view, "CLAIM_ALL", ClickGesture.PRIMARY);
        settleUntil(view, pending -> pending.size() == 1 && stillOwed(pending, 0) == 36);

        assertEquals(Collections.singletonList("sword"), given);
        assertEquals(36, surface(view).getSlot(0).getItem().getAmount());
        assertNull(surface(view).getSlot(1));
        close(view);
    }

    @Test
    void theListVerbOpensTheScreen() {
        new DeliveriesCommandNode().list(alex);
        world.advanceTicks(2);

        ScreenView view = Screens.getOpenView(alex);
        assertNotNull(view, "list opened a screen");
        assertTrue(view.getScreen() instanceof DeliveriesScreen, view.getScreen().toString());
        close(view);
    }

    @Test
    void anEmptyInboxShowsOnlyTheNotice() {
        ScreenView view = open();

        assertEquals("§7Nothing to claim", surface(view).getSlot(30).getItem().getDisplayName());
        assertNull(surface(view).getSlot(0));
        close(view);
    }

    @Test
    void theClaimMessageReadsTheCounts() {
        FCLocaleManager.loadLocale(world.getCore(), true, DeliveriesScreen.class);
        enqueue("sword", "refund");
        ScreenView view = open();
        settle(view, 1);

        world.click(view, "deliveries#0", ClickGesture.PRIMARY);
        settle(view, 0);

        assertTrue(alex.getMessages().contains("§aClaimed 1 delivery(ies); 0 still wait for room."), alex.getMessages().toString());
        assertTrue(alex.getMessages().stream().noneMatch(line -> line.contains("%")), alex.getMessages().toString());
        close(view);
    }
}
