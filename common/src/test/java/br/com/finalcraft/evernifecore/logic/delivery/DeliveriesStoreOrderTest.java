package br.com.finalcraft.evernifecore.logic.delivery;

import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The deliveries store a test boots, used out of order: refused out loud, never a rest that vanishes. */
class DeliveriesStoreOrderTest {

    @TempDir
    Path tempDir;

    private static ECoreTestWorld platform() {
        return Platforms.strict().ignoringPlatformRegistrations().mainThreadInline().loggingToStdout().withInventory(1).playerState().install();
    }

    @Test
    void deliveriesBeforeAnyPlayerIsRefusedSayingWhatToCallFirst() {
        try (ECoreTestWorld world = platform(); LogicWorld shop = LogicWorld.open("FakeShop", tempDir)) {
            IllegalStateException refused = assertThrows(IllegalStateException.class, shop::deliveries);

            assertTrue(refused.getMessage().contains("call player("), refused.getMessage());
        }
    }

    @Test
    void aRestWithNoDeliveriesStoreIsAuditedAsLost() {
        try (ECoreTestWorld world = platform(); LogicWorld shop = LogicWorld.open("FakeShop", tempDir)) {
            TestFPlayerSender alex = shop.player("Alex");
            world.platform().getTestInventory().fill(alex, "minecraft:dirt");
            ExecutionContext ctx = shop.context(alex).build();

            Effects.apply(ctx, Effects.parse(shop.getOwner(), DeliveriesTest.node("{'type': 'give-item', 'item': 'minecraft:stone', 'amount': 5}"),
                    ZoneOffset.UTC));

            assertEquals(1, ctx.getAudit().getEntries(AuditKind.DELIVERY_DISCARDED).size(), ctx.getAudit().getEntries(AuditKind.DELIVERY_DISCARDED).toString());
        }
    }
}
