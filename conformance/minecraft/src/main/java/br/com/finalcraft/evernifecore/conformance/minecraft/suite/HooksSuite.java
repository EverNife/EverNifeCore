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
import org.bukkit.Bukkit;

import java.util.Arrays;
import java.util.List;

/**
 * The optional hooks, on both of their branches. Without PlaceholderAPI the core says so and hands text back
 * untouched; with it routed beside the core, the core finds it and resolves a placeholder this plugin registered
 * through it. Without an economy plugin the always-registered economy provider says none is available. Each case runs
 * on the branch this server is on and is SKIPPED on the other, so a matrix with PlaceholderAPI in one flavor covers
 * both.
 */
public final class HooksSuite implements ConformanceSuite {

    private static final String PLACEHOLDER = "%ecc_name%";

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
                ConformanceCase.of("economy-absent", () -> {
                    if (Bukkit.getPluginManager().getPlugin("Vault") != null) return Verdict.skipped("Vault is installed here");
                    //the lazy provider is always registered; without Vault it must say there is no economy
                    IEconomyProvider economy = EverNifeCore.getProviders().getEconomyOrNull();
                    boolean available = economy != null && economy.isAvailable();
                    return Verdict.of(economy != null && !available, "provider=" + economy + " isAvailable=" + available);
                }));
    }

    private static boolean papiInstalled() {
        return Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }
}
