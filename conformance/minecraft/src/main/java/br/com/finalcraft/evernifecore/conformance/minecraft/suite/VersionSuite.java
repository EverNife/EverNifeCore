package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.ecnmsutils.NMSUtils;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.minecraft.version.ServerSoftware;
import br.com.finalcraft.evernifecore.region.shape.WorldHeight;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Arrays;
import java.util.List;

/**
 * What the core decided about the server it woke up in: the version (by package up to 1.20.4, by
 * {@code getBukkitVersion()} after), the software (by marker class), the NMS implementation of that era, the world
 * height (-64 from 1.18, per world from 1.17) and the action bar. Every answer is compared with what the server
 * itself reports.
 */
public final class VersionSuite implements ConformanceSuite {

    // What this server must answer, per version.
    /** A loaded world reports its own floor. */
    private static final MCDetailedVersion WORLD_FLOOR_SINCE = MCDetailedVersion.v1_17_R1;
    /** 1.7.10 has no action bar; NecroTempus brings one there. */
    private static final MCVersion ACTION_BAR_ABOVE = MCVersion.v1_7_10;

    @Override
    public String getId() {
        return "version";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                ConformanceCase.of("detected-version", () -> {
                    MCDetailedVersion current = MCDetailedVersion.getCurrent();
                    String reported = Bukkit.getBukkitVersion();
                    return Verdict.of(current != null && reported.startsWith(current.getReleaseFamily()),
                            "detected=" + current + " family=" + (current == null ? null : current.getReleaseFamily())
                                    + " getBukkitVersion=" + reported);
                }),
                ConformanceCase.of("server-software", () -> {
                    ServerSoftware software = ServerSoftware.getCurrent();
                    String reported = Bukkit.getName() + " " + Bukkit.getVersion();
                    boolean named = reported.toLowerCase().contains(announcedAs(software).toLowerCase());
                    return Verdict.of(software != ServerSoftware.UNKNOWN && named,
                            "software=" + software + " hybrid=" + software.isHybrid() + " server=" + reported);
                }),
                ConformanceCase.of("nms", () -> Verdict.of(NMSUtils.isSupported(),
                        "revision=" + NMSUtils.revision().map(Enum::name).orElse("none")
                                + " craftbukkit=" + Bukkit.getServer().getClass().getPackage().getName()
                                + " getBukkitVersion=" + Bukkit.getBukkitVersion())),
                ConformanceCase.of("world-height", VersionSuite::worldHeight),
                ConformanceCase.of("action-bar", () -> {
                    boolean expected = MCVersion.isHigher(ACTION_BAR_ABOVE) || Bukkit.getPluginManager().getPlugin("NecroTempus") != null;
                    boolean answered = EverNifeCore.getPlatform().serverSupportsActionBar();
                    return Verdict.of(answered == expected, "serverSupportsActionBar=" + answered + " expected=" + expected
                            + " above=" + ACTION_BAR_ABOVE);
                }),
                ConformanceCase.of("papi-detection", () -> {
                    boolean present = EverNifeCore.getPlatform().isPAPIPresent();
                    boolean installed = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
                    return Verdict.of(present == installed, "isPAPIPresent=" + present + " installed=" + installed);
                }));
    }

    /** The name a server software puts in Bukkit.getName()/getVersion(), when it is not its own. */
    private static String announcedAs(ServerSoftware software) {
        //Crucible is a Cauldron fork and still says "Cauldron 1.7.10-..." (read on crucible-1.7.10)
        return software == ServerSoftware.CRUCIBLE ? "Cauldron" : software.name();
    }

    private static Verdict worldHeight() {
        World world = Bukkit.getWorlds().get(0);
        WorldHeight height = EverNifeCore.getPlatform().getWorldHeight(world.getName());
        int top = world.getMaxHeight() - 1;
        String detail = "world=" + world.getName() + " platform=" + height + " getMaxHeight-1=" + top;
        if (MCVersion.isHigherEquals(WORLD_FLOOR_SINCE)) {
            int floor = world.getMinHeight();
            return Verdict.of(height.getMaxY() == top && height.getMinY() == floor,
                    detail + " getMinHeight=" + floor + " since=" + WORLD_FLOOR_SINCE.getLowestRelease());
        }
        return Verdict.of(height.getMaxY() == top && height.getMinY() == 0, detail + " floor expected 0 before "
                + WORLD_FLOOR_SINCE.getLowestRelease());
    }
}
