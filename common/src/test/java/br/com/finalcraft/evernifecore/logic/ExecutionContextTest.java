package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.region.RegionId;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The context: its mandatory caller, the owner, text resolution, attributes, multipliers and the owner kinds. */
class ExecutionContextTest {

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld shop;

    @BeforeEach
    void setUp() {
        world = Platforms.strict().ignoringPlatformRegistrations().mainThreadInline().loggingToStdout().playerState().install();
        shop = LogicWorld.open("FakeShop", tempDir);
    }

    @AfterEach
    void tearDown() {
        Multipliers.unregisterAll(shop.getOwner());
        shop.close();
        world.close();
    }

    @Test
    void aContextCannotBeBuiltWithoutACaller() {
        NullPointerException refused = assertThrows(NullPointerException.class, () -> ExecutionContext.builder(null));

        assertTrue(refused.getMessage().contains("Caller.of(plugin"), refused.getMessage());
    }

    @Test
    void callerToStringIsPluginAndOriginInLowercase() {
        assertEquals("fakeshop:shop/vip#slot12/rule1", shop.caller("shop/VIP#slot12/rule1").toString());
    }

    @Test
    void theDefaultOwnerIsThePlayerWithAPlayerAndGlobalWithout() {
        TestFPlayerSender alex = shop.player("Alex");

        assertEquals(ProgressOwner.player(alex.getUniqueId()), shop.context(alex).build().getOwner());
        assertEquals(ProgressOwner.global(), shop.context().build().getOwner());
    }

    @Test
    void progressOwnerParseRoundTrips() {
        for (ProgressOwner owner : Arrays.asList(ProgressOwner.global(), ProgressOwner.player(UUID.randomUUID()),
                ProgressOwner.region(RegionId.of("FinalProtection", "arena")), ProgressOwner.world("world_nether"),
                ProgressOwner.of("group", "finalparty/abc"))) {
            assertEquals(owner, ProgressOwner.parse(owner.toString()), owner.toString());
        }
        assertEquals("region:finalprotection:arena", ProgressOwner.region(RegionId.of("FinalProtection", "arena")).toString());
        assertThrows(IllegalArgumentException.class, () -> ProgressOwner.parse("nobody"));
        assertThrows(IllegalArgumentException.class, () -> ProgressOwner.of("group", " "));
    }

    @Test
    void resolveWritesContextVariablesAndRegisteredVariablesAndLeavesUnknownKeysWritten() {
        Variables.registry().register(shop.getOwner(), TypeDescriptor.<Variable<?>>of("fakeshop:level")
                .describe("A fixed level.")
                .factory(params -> new Variable<BigDecimal>() {
                    @Override
                    public String getId() {
                        return "fakeshop:level";
                    }

                    @Override
                    public Class<BigDecimal> getType() {
                        return BigDecimal.class;
                    }

                    @Override
                    public BigDecimal read(ExecutionContext ctx) {
                        return new BigDecimal("12.50");
                    }
                })
                .build());
        TestFPlayerSender alex = shop.player("Alex");
        ExecutionContext ctx = shop.context(alex).variable("missing", "").variable("cost", "100 coins").build();

        assertEquals("Alex pays 100 coins at level 12.5 [] {nada} fakeshop:test",
                ctx.resolve("{player} pays {cost} at level {level} [{missing}] {nada} {caller}"));
        assertEquals("12.5", ctx.resolve("{fakeshop:level}"));
    }

    @Test
    void getAttributeIsNullForAnotherTypeAndRequireAttributeNamesBoth() {
        ExecutionContext ctx = shop.context().attribute("screen", "main-menu").build();

        assertEquals("main-menu", ctx.getAttribute("screen", String.class));
        assertNull(ctx.getAttribute("screen", Integer.class));
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> ctx.requireAttribute("screen", Integer.class));
        assertTrue(refused.getMessage().contains("Integer") && refused.getMessage().contains("String") && refused.getMessage().contains("'screen'"),
                refused.getMessage());
    }

    @Test
    void deriveKeepsEverythingAndSharesTheAuditTrail() {
        TestFPlayerSender alex = shop.player("Alex");
        TestFPlayerSender bia = shop.player("Bia");
        ExecutionContext ctx = shop.context(alex).locale(Locale.CANADA).attribute("screen", "x").build();

        ExecutionContext derived = ctx.derive().player(bia).build();

        assertSame(bia, derived.getPlayer());
        assertEquals(ctx.getOwner(), derived.getOwner(), "the owner stays the original player's");
        assertEquals(Locale.CANADA, derived.getLocale());
        assertEquals("x", derived.getAttribute("screen", String.class));
        assertSame(ctx.getAudit(), derived.getAudit());
    }

    @Test
    void getMultiplierIsTheProductOfTheRegisteredProvidersAndOneWithoutAny() {
        ExecutionContext ctx = shop.context().build();
        assertEquals(0, BigDecimal.ONE.compareTo(ctx.getMultiplier("money")));

        Multipliers.register(shop.getOwner(), (context, variable) -> variable.equals("money") ? new BigDecimal("1.5") : BigDecimal.ONE);
        Multipliers.register(shop.getOwner(), (context, variable) -> new BigDecimal("2"));

        assertEquals(0, new BigDecimal("3").compareTo(ctx.getMultiplier("money")));
        assertEquals(0, new BigDecimal("2").compareTo(ctx.getMultiplier("exp")));
    }

    @Test
    void theCoreOwnerKindsAnswerTheirMembers() {
        TestFPlayerSender alex = shop.player("Alex");
        shop.player("Bia").at("nether", 0, 64, 0);

        assertEquals(Arrays.asList(alex), ProgressOwners.find("world").get().getMembers(ProgressOwner.world("world")));
        assertEquals(2, ProgressOwners.find("global").get().getMembers(ProgressOwner.global()).size());
        assertEquals(Arrays.asList(alex), ProgressOwners.find("player").get().getMembers(ProgressOwner.player(alex.getUniqueId())));
        assertTrue(ProgressOwners.find("region").isPresent());
        assertTrue(!ProgressOwners.find("group").isPresent(), "group is a provider a plugin registers");
    }
}
