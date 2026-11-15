package br.com.finalcraft.evernifecore.logic.delivery;

import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestCurrency;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestInventory;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What does not fit in a {@code give-item} goes where its {@code overflow:} says: pending, the ground, or a plugin's destination. */
class OverflowTest {

    private static final String IRON = "minecraft:iron_ingot";

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld shop;
    private TestFPlayerSender alex;
    private TestInventory inventory;

    @BeforeEach
    void open() {
        //two slots, one already taken: 64 of a give-item fit, the rest overflows
        world = Platforms.strict().ignoringPlatformRegistrations().mainThreadInline().loggingToStdout().withInventory(2).playerState().install();
        shop = LogicWorld.open("FakeShop", tempDir);
        alex = shop.player("Alex");
        shop.deliveries();
        inventory = world.platform().getTestInventory();
        inventory.add(alex, ItemSpec.of("minecraft:dirt", 64));
    }

    @AfterEach
    void close() {
        shop.close();
        world.close();
    }

    private Effect giveIron(int amount, @Nullable String overflow) {
        return Effects.parse(shop.getOwner(), DeliveriesTest.node("{'type': 'give-item', 'item': '" + IRON + "', 'amount': " + amount
                + (overflow == null ? "" : ", 'overflow': '" + overflow + "'") + "}"), ZoneOffset.UTC);
    }

    private int pendingIron() throws Exception {
        int pending = 0;
        for (Delivery delivery : shop.deliveries().listOf(alex.getUniqueId()).get(5, TimeUnit.SECONDS)) {
            pending += delivery.getEffects().get(0).get("amount").asInt();
        }
        return pending;
    }

    private static String messages(Throwable failure) {
        StringBuilder all = new StringBuilder();
        for (Throwable each = failure; each != null; each = each.getCause()) {
            all.append(each.getMessage()).append(" | ");
        }
        return all.toString();
    }

    private int droppedIron() {
        int dropped = 0;
        for (ItemSpec item : inventory.getDropped(alex)) {
            dropped += item.getAmount();
        }
        return dropped;
    }

    @Test
    void theDefaultKeepsTheRestPending() throws Exception {
        Effects.apply(shop.context(alex).build(), giveIron(100, null));

        assertEquals(64, inventory.count(alex, ItemMatcher.of(IRON)));
        assertEquals(36, pendingIron());
        assertEquals(0, droppedIron());
    }

    @Test
    void groundDropsTheRestAtThePlayersFeetAndKeepsNothingPending() throws Exception {
        Effects.apply(shop.context(alex).build(), giveIron(100, "ground"));

        assertEquals(64, inventory.count(alex, ItemMatcher.of(IRON)));
        assertEquals(36, droppedIron(), inventory.getDropped(alex).toString());
        assertEquals(0, pendingIron());
    }

    @Test
    void aPlayerWhoIsNotOnlineHasNoGroundSoTheRestWaitsPending() throws Exception {
        shop.quit(alex);

        Effects.apply(shop.context(alex).build(), giveIron(100, "ground"));

        assertEquals(0, droppedIron());
        assertEquals(36, pendingIron(), "the ground refuses a player who is not online: the rest waits");
    }

    @Test
    void aPluginsDestinationGetsTheRestWithItsContextAndWhatItHandsBackWaits() throws Exception {
        List<String> seen = new ArrayList<>();
        DeliveryDestinations.register(shop.getOwner(), new DeliveryDestination() {
            @Override
            public String getId() {
                return "fakeshop:warehouse";
            }

            @Override
            public ItemSpec deliverItem(ExecutionContext ctx, ItemSpec item) {
                seen.add(ctx.getPlayer().getName() + " " + item.getMaterialId() + " x" + item.getAmount() + " from " + ctx.getCaller());
                return item.withAmount(item.getAmount() - 30); //the warehouse holds 30
            }
        });

        Effects.apply(shop.context(alex).build(), giveIron(100, "fakeshop:warehouse"));

        assertEquals(1, seen.size(), seen.toString());
        assertEquals("Alex " + IRON + " x36 from " + shop.caller("test"), seen.get(0));
        assertEquals(6, pendingIron(), "what the warehouse handed back");
        assertEquals(0, droppedIron());
    }

    @Test
    void anOverflowNobodyRegisteredIsRefusedWhenReadListingTheIds() {
        Exception refused = assertThrows(Exception.class, () -> giveIron(1, "grond"));

        String why = messages(refused);
        assertTrue(why.contains("'grond'") && why.contains(GroundDestination.ID), why);
    }

    @Test
    void aPendingMoneyIsKeptAsTheNumberTheTextsShow() {
        TestCurrency coins = Economies.currency("fakeshop:coins");

        assertEquals("1250", PendingDestination.moneyNode(coins, new BigDecimal("1250.0")).get("amount").asText());
        assertEquals("1000", PendingDestination.moneyNode(coins, new BigDecimal("1000")).get("amount").asText());
    }
}
