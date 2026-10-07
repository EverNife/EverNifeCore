package br.com.finalcraft.evernifecore.minecraft.region;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.math.game.selection.CuboidSelection;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.region.flag.stock.DenyBlockPlaceFlag;
import br.com.finalcraft.evernifecore.region.flag.stock.DenyPvpFlag;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The regions answering whoever asks {@code ProtectionAll}, with no event behind the question. */
class McRegionProtectionHandlerTest {

    @TempDirNobodyCleans
    Path tempDir;

    private final McRegionProtectionHandler handler = new McRegionProtectionHandler();

    private static CuboidSelection selection(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new CuboidSelection(BlockPos.of(x1, y1, z1), BlockPos.of(x2, y2, z2));
    }

    @Test
    void theHandlerAnswersThePlaceFlagAtEveryCornerAndTheCenter() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("vault", 0, 0, 0, 10, 10, 10, server.flag(new DenyBlockPlaceFlag(server.regions.getOwner())));
            Player builder = server.player("builder", 50, 5, 50);

            assertFalse(handler.canBuild(builder, server.at(5, 5, 5)));
            assertTrue(handler.canBuild(builder, server.at(50, 5, 50)));
            assertFalse(handler.canBuildOnRegion(builder, server.world, selection(10, 10, 10, 20, 20, 20)),
                    "the selection touches the region at one corner only");
            assertFalse(handler.canBuildOnRegion(builder, server.world, selection(-20, 0, -20, 30, 10, 30)),
                    "every corner is outside, the center is not");
            assertTrue(handler.canBuildOnRegion(builder, server.world, selection(11, 11, 11, 20, 20, 20)));
            assertTrue(handler.canBreakOnRegion(builder, server.world, selection(0, 0, 0, 10, 10, 10)), "the region denies placing only");
            assertFalse(handler.canUseAoE(builder, server.at(15, 5, 15), 5), "the corner at 10,0,10 is in the region");
        }
    }

    @Test
    void canAttackFollowsDenyPvp() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("lobby", 0, 0, 0, 10, 10, 10, server.flag(new DenyPvpFlag(server.regions.getOwner())));
            Player attacker = server.player("attacker", 50, 5, 50);

            assertFalse(handler.canAttack(attacker, server.player("inside", 5, 5, 5)));
            assertTrue(handler.canAttack(attacker, server.player("outside", 50, 5, 50)));
        }
    }

    @Test
    void aDisabledWorldAlwaysAllows() {
        List<String> before = ECSettings.REGIONS_DISABLED_WORLDS;
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("vault", 0, 0, 0, 10, 10, 10, server.flag(new DenyBlockPlaceFlag(server.regions.getOwner())),
                    server.flag(new DenyPvpFlag(server.regions.getOwner())));
            Player player = server.player("builder", 5, 5, 5);
            assertFalse(handler.canBuild(player, server.at(5, 5, 5)), "denied while the world is protected");

            ECSettings.REGIONS_DISABLED_WORLDS = Collections.singletonList(RegionServer.WORLD);

            assertTrue(handler.canBuild(player, server.at(5, 5, 5)));
            assertTrue(handler.canAttack(player, server.player("inside", 5, 5, 5)));
            assertTrue(handler.canBuildOnRegion(player, server.world, selection(0, 0, 0, 10, 10, 10)));
            assertTrue(handler.canUseAoE(player, server.at(5, 5, 5), 3));
        } finally {
            ECSettings.REGIONS_DISABLED_WORLDS = before;
        }
    }
}
