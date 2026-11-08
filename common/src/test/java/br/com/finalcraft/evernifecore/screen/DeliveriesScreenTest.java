package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.delivery.Deliveries;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryIcon;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryOrigin;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryPolicy;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
        world = ScreenWorld.open(tempDir);
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
        try {
            return deliveries.enqueue(alex.getUniqueId(), DeliveryOrigin.of("FakeShop", "test", reason), DeliveryPolicy.KEEP_SELF_CONTAINED,
                    null, icon, Collections.singletonList(node("{'type': '" + GIFT + "', 'name': '" + gift + "'}"))).get(5, TimeUnit.SECONDS);
        } catch (Exception failed) {
            throw new AssertionError(failed);
        }
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
