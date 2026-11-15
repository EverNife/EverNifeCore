package br.com.finalcraft.evernifecore.logic.delivery.command;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryOrigin;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryPolicy;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code claim <tab>} offers the ids {@code list} shows of the player's own pending deliveries, and {@code all}. */
class DeliveriesClaimTabTest {

    private static final String GIFT = "fakeshop:gift";

    @FinalCMD(aliases = "shop")
    public static class Shop_Cmd {
        @FinalCMD.Node(subcmd = "deliveries", permission = "shop.deliveries")
        final DeliveriesCommandNode deliveries = new DeliveriesCommandNode();
    }

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;
    private LogicWorld shop;
    private FinalCMDPluginCommand node;
    private TestFPlayerSender alex;
    private TestFPlayerSender bia;

    @BeforeEach
    void open() {
        harness = Commands.harness("DeliveriesTab", tempDir.resolve("cmd"));
        shop = LogicWorld.open("FakeShop", tempDir.resolve("shop"));
        FCLocaleManager.loadLocale(harness.ecPluginData, DeliveriesCommandNode.class);
        node = harness.register(new Shop_Cmd());
        alex = shop.player("Alex");
        alex.grant("shop.deliveries");
        bia = shop.player("Bia");
        Effects.registry().register(shop.getOwner(), TypeDescriptor.<Effect>of(GIFT)
                .describe("Hands the player a named gift.")
                .param(ParamSpec.required("name", ParamSpec.Kind.STRING, "The gift."))
                .factory(params -> ctx -> { })
                .build());
    }

    @AfterEach
    void close() {
        Effects.registry().unregister(shop.getOwner(), GIFT);
        shop.close();
        harness.close();
    }

    private String enqueue(TestFPlayerSender to) throws IOException {
        Delivery kept = shop.deliveries().enqueue(to.getUniqueId(), DeliveryOrigin.of("FakeShop", "test", ""), DeliveryPolicy.KEEP_SELF_CONTAINED,
                null, null, Collections.singletonList(new ObjectMapper().readTree("{\"type\": \"" + GIFT + "\", \"name\": \"sword\"}"))).join();
        return kept.getId().toString().substring(0, 8);
    }

    @Test
    void claimCompletesTheIdsOfThePlayersOwnDeliveriesAndAll() throws IOException {
        String first = enqueue(alex);
        String second = enqueue(alex);
        String ofBia = enqueue(bia);

        List<String> offered = harness.tab(node, alex, "deliveries", "claim", "");

        assertTrue(offered.containsAll(Arrays.asList("all", first, second)), offered.toString());
        assertEquals(3, offered.size(), "only Alex's own deliveries, not " + ofBia + ": " + offered);
    }

    @Test
    void claimCompletesWhatStartsWithTheTypedWord() throws IOException {
        String first = enqueue(alex);

        assertEquals(Collections.singletonList(first), harness.tab(node, alex, "deliveries", "claim", first.substring(0, 3)));
        assertEquals(Collections.singletonList("all"), harness.tab(node, alex, "deliveries", "claim", "al"));
    }
}
