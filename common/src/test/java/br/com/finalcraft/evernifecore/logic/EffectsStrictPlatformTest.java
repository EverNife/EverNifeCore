package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An effect that hits a platform call the strict double was never taught fails the test, not just the audit and the log. */
class EffectsStrictPlatformTest {

    @TempDir
    Path tempDir;

    @Test
    void anEffectRefusedByAnUnconfiguredDoubleFailsTheWorldNamingTheBuilder() {
        ECoreTestWorld world = Platforms.strict().ignoringPlatformRegistrations().mainThreadInline().loggingToStdout().install();
        LogicWorld shop = LogicWorld.open("FakeShop", tempDir);
        ExecutionContext ctx = shop.context().build();
        try {
            Effects.apply(ctx, run -> EverNifeCore.getPlatform().isPluginLoaded("Vault"));
        } finally {
            shop.close();
        }

        assertEquals(1, ctx.getAudit().getEntries(AuditKind.EFFECT_FAILED).size()); //still not applied
        AssertionError failure = assertThrows(AssertionError.class, world::close);
        assertTrue(failure.getMessage().contains("Platforms.strict().pluginsLoaded(\"Vault\")"), failure.getMessage());
    }

}
