package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.integration.placeholders.PAPIIntegration;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.region.CreationResult;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import org.bukkit.Bukkit;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The optional hooks, on both of their branches. Without PlaceholderAPI the core says so and hands text back
 * untouched; with it routed beside the core, the core finds it and resolves a placeholder this plugin registered
 * through it, and the core's own {@code evernifecore} prefix answers a region of this plugin by its id. Without an
 * economy plugin the always-registered economy provider says none is available. Each case runs
 * on the branch this server is on and is SKIPPED on the other, so a matrix with PlaceholderAPI in one flavor covers
 * both.
 */
public final class HooksSuite implements ConformanceSuite {

    private static final String PLACEHOLDER = "%ecc_name%";
    private static final String REGION_DISPLAY_NAME = "Conformance Arena";

    private final RegionManager regions;
    private final CompletableFuture<Void> loaded;

    /**
     * @param regions the plugin's own region manager, where the region placeholder case puts its region
     * @param loaded  that manager's {@code load()}
     */
    public HooksSuite(RegionManager regions, CompletableFuture<Void> loaded) {
        this.regions = regions;
        this.loaded = loaded;
    }

    /**
     * Gives this plugin a placeholder through the core's own PAPI bridge, so {@code papi-present} proves the whole
     * round trip (register, then resolve) without depending on an eCloud expansion. A no-op without PlaceholderAPI.
     */
    public static void registerPlaceholders(ECPluginData plugin) {
        PAPIIntegration.createPlaceholderIntegration(plugin, "ecc", IPlayerData.class)
                .addParser("name", data -> data == null ? null : data.getName());
    }

    @Override
    public String getId() {
        return "hooks";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                ConformanceCase.of("papi-absent", () -> {
                    if (papiInstalled()) return Verdict.skipped("PlaceholderAPI is installed here");
                    boolean present = EverNifeCore.getPlatform().isPAPIPresent();
                    String parsed = EverNifeCore.getPlatform().parse(null, PLACEHOLDER);
                    return Verdict.of(!present && PLACEHOLDER.equals(parsed), "isPAPIPresent=" + present + " parse=" + parsed);
                }),
                PlayerCases.of("papi-present", ctx, player -> {
                    if (!papiInstalled()) return Verdict.skipped("PlaceholderAPI is not installed here");
                    boolean present = EverNifeCore.getPlatform().isPAPIPresent();
                    String parsed = EverNifeCore.getPlatform().parse(FCBukkitUtil.adapt(player), PLACEHOLDER);
                    return Verdict.of(present && player.getName().equals(parsed), "isPAPIPresent=" + present + " parse=" + parsed);
                }),
                ConformanceCase.later("papi-region-placeholder-by-name", () -> loaded.thenApply(done -> regionPlaceholderByName())),
                ConformanceCase.of("economy-absent", () -> {
                    if (Bukkit.getPluginManager().getPlugin("Vault") != null) return Verdict.skipped("Vault is installed here");
                    //the lazy provider is always registered; without Vault it must say there is no economy
                    IEconomyProvider economy = EverNifeCore.getProviders().getEconomyOrNull();
                    boolean available = economy != null && economy.isAvailable();
                    return Verdict.of(economy != null && !available, "provider=" + economy + " isAvailable=" + available);
                }));
    }

    /** {@code %evernifecore_region_[<full id>]_name%}, with no player, through the real PlaceholderAPI. */
    private Verdict regionPlaceholderByName() {
        if (!papiInstalled()) return Verdict.skipped("PlaceholderAPI is not installed here");
        CreationResult created = regions.create("ecc-papi", Bukkit.getWorlds().get(0).getName(),
                CuboidShape.of(BlockPos.of(0, 250, 0), BlockPos.of(1, 251, 1)));
        Region region = created.getRegion();
        if (region == null) {
            return Verdict.fail("ecc-papi refused: " + created.getRefusal());
        }
        try {
            region.setDisplayName(REGION_DISPLAY_NAME);
            String placeholder = "%evernifecore_region_[" + region.getId() + "]_name%";
            String parsed = EverNifeCore.getPlatform().parse(null, placeholder);
            return Verdict.of(REGION_DISPLAY_NAME.equals(parsed), "placeholder=" + placeholder + " parse=" + parsed);
        } finally {
            regions.remove(region);
        }
    }

    private static boolean papiInstalled() {
        return Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }
}
