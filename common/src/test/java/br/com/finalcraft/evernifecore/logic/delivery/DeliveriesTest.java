package br.com.finalcraft.evernifecore.logic.delivery;

import br.com.finalcraft.evernifecore.api.events.player.ECPlayerFullyLoggedInEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestCurrency;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestInventory;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What could not be handed over waits in the recipient's deliveries, and comes out on a claim, a login, an expiry or a purge. */
class DeliveriesTest {

    private static final String IRON = "minecraft:iron_ingot";

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld shop;
    private TestFPlayerSender alex;
    private TestInventory inventory;
    private Deliveries deliveries;

    @BeforeEach
    void open() {
        world = Platforms.strict().ignoringPlatformRegistrations().mainThreadInline().loggingToStdout().withInventory(2).install();
        shop = LogicWorld.open("FakeShop", tempDir);
        alex = shop.player("Alex");
        inventory = world.platform().getTestInventory();
        deliveries = shop.deliveries();
    }

    @AfterEach
    void close() {
        shop.close();
        world.close();
    }

    static JsonNode node(String singleQuotedJson) {
        try {
            return new ObjectMapper().readTree(singleQuotedJson.replace('\'', '"'));
        } catch (IOException unreadable) {
            throw new IllegalArgumentException(unreadable);
        }
    }

    private Effect effect(String json) {
        return Effects.parse(shop.getOwner(), node(json), ZoneOffset.UTC);
    }

    private List<Delivery> pending() {
        return await(deliveries.listOf(alex.getUniqueId()));
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private int held(String id) {
        return inventory.count(alex, ItemMatcher.of(id));
    }

    @Test
    void aFullInventoryKeepsExactlyTheRestPendingFromTheCaller() {
        inventory.add(alex, ItemSpec.of("minecraft:dirt", 64));

        Effects.apply(shop.context(alex).build(), effect("{'type': 'give-item', 'item': '" + IRON + "', 'amount': 100}"));

        assertEquals(64, held(IRON), "one free slot takes one stack");
        List<Delivery> pending = pending();
        assertEquals(1, pending.size(), pending.toString());
        Delivery rest = pending.get(0);
        assertEquals("fakeshop", rest.getOrigin().getPlugin());
        assertEquals("test", rest.getOrigin().getEmitter());
        assertEquals(DeliveryPolicy.KEEP_SELF_CONTAINED, rest.getPolicy());
        assertEquals(36, rest.getEffects().get(0).get("amount").asInt(), rest.toString());
        assertTrue(rest.isSelfContained());
    }

    @Test
    void anItemEffectWithoutAnInventoryFailsNamingTheBuilderThatGivesOne() {
        ECPluginData platformCore = DeliveryDestinations.registry().find(InventoryDestination.ID)
                .flatMap(DeliveryDestinations.registry()::getOwner).get();
        assertTrue(DeliveryDestinations.unregister(platformCore, InventoryDestination.ID));
        ExecutionContext ctx = shop.context(alex).build();

        Effects.apply(ctx, effect("{'type': 'give-item', 'item': '" + IRON + "'}"));

        String failure = ctx.getAudit().getEntries(AuditKind.EFFECT_FAILED).toString();
        assertTrue(failure.contains(InventoryDestination.ID) && failure.contains("Platforms.withInventory"), failure);
        assertTrue(pending().isEmpty(), "nothing is kept for an effect that could not run");
    }

    @Test
    void aDestinationNobodyRegisteredKeepsTheWholeItemWithItsTo() {
        Effects.apply(shop.context(alex).build(), effect("{'type': 'give-item', 'item': '" + IRON + "', 'amount': 3,"
                + " 'to': 'finalcolonies:warehouse'}"));

        assertEquals(0, held(IRON));
        JsonNode kept = pending().get(0).getEffects().get(0);
        assertEquals("finalcolonies:warehouse", kept.get("to").asText());
        assertEquals(3, kept.get("amount").asInt());
    }

    @Test
    void moneyToAPendingDestinationIsKeptAndPaidOnTheClaim() {
        CurrencyOwner wallet = CurrencyOwner.player(alex.getUniqueId());
        TestCurrency gold = Economies.currency("fakeshop:gold", CurrencyOwner.Kind.PLAYER).balance(wallet, 0);
        world.withCurrency(shop.getOwner(), gold);

        Effects.apply(shop.context(alex).build(), effect("{'type': 'money', 'amount': 25, 'currency': 'fakeshop:gold', 'to': 'pending'}"));
        assertEquals(0, gold.getBalance(wallet).signum(), "pending pays nothing now");
        assertEquals(1, pending().size());

        ClaimResult claimed = await(deliveries.claimAll(alex));

        assertEquals(1, claimed.getDelivered().size(), claimed.toString());
        assertEquals(0, new BigDecimal("25").compareTo(gold.getBalance(wallet)), gold.getBalance(wallet).toPlainString());
        assertTrue(pending().isEmpty());
    }

    @Test
    void aClaimHandsOutWhatFitsAndTheRestStaysInTheSameDelivery() {
        Delivery owed = await(deliveries.enqueue(alex.getUniqueId(), DeliveryOrigin.of("FakeShop", "fakeshop:sale", "Refund"),
                DeliveryPolicy.KEEP_SELF_CONTAINED, null, Collections.singletonList(node("{'type': 'give-item', 'item': '" + IRON
                        + "', 'amount': 200}")))); //two slots: 128 fit
        List<DeliveryEvent> events = shop.recorded(DeliveryEvent.class);

        ClaimResult claimed = await(deliveries.claim(alex, owed.getId()));

        assertEquals(128, held(IRON));
        assertTrue(claimed.getDelivered().isEmpty());
        Delivery kept = pending().get(0);
        assertEquals(owed.getId(), kept.getId(), "the rest stays in the claimed delivery, not a new one");
        assertEquals(72, kept.getEffects().get(0).get("amount").asInt());
        assertTrue(events.stream().noneMatch(event -> event.getKind() == DeliveryEvent.Kind.ENQUEUED), events.toString());
    }

    @Test
    void requiresOriginWaitsForItsPluginWhileKeepSelfContainedHandsOutTheCoresPart() {
        List<JsonNode> effects = Arrays.asList(node("{'type': 'give-item', 'item': '" + IRON + "', 'amount': 5}"),
                node("{'type': 'fakequests:unlock-title', 'title': 'Miner'}"));
        DeliveryOrigin origin = DeliveryOrigin.of("FakeQuests", "fakequests:mine-iron", "Quest reward");
        Delivery whole = await(deliveries.enqueue(alex.getUniqueId(), origin, DeliveryPolicy.REQUIRES_ORIGIN, null, effects));
        Delivery split = await(deliveries.enqueue(alex.getUniqueId(), origin, DeliveryPolicy.KEEP_SELF_CONTAINED, null, effects));
        assertFalse(whole.isSelfContained());

        await(deliveries.claimAll(alex));

        assertEquals(5, held(IRON), "only the keep-self-contained one handed out the core's item");
        List<Delivery> pending = pending();
        assertEquals(2, pending.get(0).getEffects().size(), "requires-origin is untouched: " + pending);
        assertEquals(whole.getId(), pending.get(0).getId());
        assertEquals(1, pending.get(1).getEffects().size(), "keep-self-contained keeps only the plugin's node: " + pending);
        assertEquals(split.getId(), pending.get(1).getId());

        AtomicInteger titles = new AtomicInteger();
        ECPluginData quests = Plugins.fakePluginData("FakeQuests", tempDir.resolve("quests").toFile());
        try {
            Effects.registry().register(quests, TypeDescriptor.<Effect>of("fakequests:unlock-title")
                    .describe("Unlocks a title.")
                    .param(ParamSpec.required("title", ParamSpec.Kind.STRING, "The title."))
                    .factory(params -> ctx -> titles.incrementAndGet())
                    .build());

            ClaimResult claimed = await(deliveries.claimAll(alex));

            assertEquals(2, claimed.getDelivered().size(), claimed.toString());
            assertEquals(2, titles.get());
            assertEquals(10, held(IRON));
            assertTrue(pending().isEmpty());
        } finally {
            Plugins.forget("FakeQuests");
        }
    }

    @Test
    void theLoginClaimsEveryPendingDelivery() {
        await(deliveries.enqueue(alex.getUniqueId(), DeliveryOrigin.of("FakeShop", "test", ""), DeliveryPolicy.KEEP_SELF_CONTAINED, null,
                Collections.singletonList(node("{'type': 'give-item', 'item': '" + IRON + "', 'amount': 7}"))));
        Deliveries.start();
        PlayerData data = await(PlayerController.getOrCreate(alex.getUniqueId()));

        ECEventBus.global().post(new ECPlayerFullyLoggedInEvent(data, false));

        long until = System.currentTimeMillis() + 5000;
        while (held(IRON) == 0 && System.currentTimeMillis() < until) {
            Thread.yield();
        }
        assertEquals(7, held(IRON));
        assertTrue(pending().isEmpty());
    }

    @Test
    void theSweepDropsWhatExpiredByTheInjectedClock() {
        inventory.fill(alex, "minecraft:dirt");
        Effects.apply(shop.context(alex).build(), effect("{'type': 'give-item', 'item': '" + IRON + "', 'amount': 4, 'expires': '1h'}"));
        Effects.apply(shop.context(alex).build(), effect("{'type': 'give-item', 'item': '" + IRON + "', 'amount': 2}"));
        assertEquals(2, pending().size());
        List<DeliveryEvent> events = shop.recorded(DeliveryEvent.class);
        Deliveries.start();

        shop.advance(Duration.ofMinutes(59));
        shop.runDue();
        assertEquals(2, pending().size(), "not yet");

        shop.advance(Duration.ofMinutes(2));
        shop.runDue();

        List<Delivery> left = pending();
        assertEquals(1, left.size(), left.toString());
        assertEquals(2, left.get(0).getEffects().get(0).get("amount").asInt(), "the one without expires stays");
        assertTrue(events.stream().anyMatch(event -> event.getKind() == DeliveryEvent.Kind.EXPIRED), events.toString());
    }

    @Test
    void aPurgeTakesOutEveryDeliveryOfThatPluginAndPostsPurgedForEach() {
        DeliveryOrigin fromShop = DeliveryOrigin.of("FakeShop", "test", "");
        List<JsonNode> iron = Collections.singletonList(node("{'type': 'give-item', 'item': '" + IRON + "'}"));
        TestFPlayerSender bia = shop.player("Bia");
        await(deliveries.enqueue(alex.getUniqueId(), fromShop, DeliveryPolicy.KEEP_SELF_CONTAINED, null, iron));
        await(deliveries.enqueue(bia.getUniqueId(), fromShop, DeliveryPolicy.KEEP_SELF_CONTAINED, null, iron));
        await(deliveries.enqueue(alex.getUniqueId(), DeliveryOrigin.of("OtherPlugin", "x", ""), DeliveryPolicy.KEEP_SELF_CONTAINED, null, iron));
        List<DeliveryEvent> events = shop.recorded(DeliveryEvent.class);

        int purged = await(deliveries.purge("fakeshop", shop.caller("admin")));

        assertEquals(2, purged);
        assertEquals(2, events.stream().filter(event -> event.getKind() == DeliveryEvent.Kind.PURGED).count(), events.toString());
        assertEquals("otherplugin", pending().get(0).getOrigin().getPlugin());
        assertTrue(await(deliveries.listOf(bia.getUniqueId())).isEmpty());
    }

    @Test
    void aDelayedEffectWhosePlayerLeftKeepsOnlyTheCoresNodesAndAuditsTheRest() {
        ExecutionContext ctx = shop.context(alex).build();
        List<DeliveryEvent> events = shop.recorded(DeliveryEvent.class);

        Delivery kept = await(deliveries.enqueueSelfContained(ctx, alex.getUniqueId(), Arrays.asList(
                node("{'type': 'evernifecore:give-item', 'item': '" + IRON + "', 'amount': 2}"),
                node("{'type': 'fakequests:unlock-title', 'title': 'Miner'}"))));

        assertEquals(1, kept.getEffects().size());
        assertTrue(kept.isSelfContained());
        assertEquals(1, ctx.getAudit().getEntries(AuditKind.DELIVERY_DISCARDED).size(), ctx.getAudit().getEntries().toString());
        assertTrue(events.stream().anyMatch(event -> event.getKind() == DeliveryEvent.Kind.DISCARDED), events.toString());
    }
}
