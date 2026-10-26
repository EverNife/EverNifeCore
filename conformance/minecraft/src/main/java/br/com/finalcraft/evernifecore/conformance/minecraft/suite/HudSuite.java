package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.notify.BossBarColor;
import br.com.finalcraft.evernifecore.notify.IHudAccess;
import br.com.finalcraft.evernifecore.notify.IHudAccess.Capability;
import br.com.finalcraft.evernifecore.notify.ToastStyle;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * What the core's screen answers on this server, capability by capability, against the table each version must
 * answer (action bar and title above 1.7.10 or with NecroTempus, boss bar from 1.9, toast from 1.12, the rest
 * everywhere); and that what it says it has, it draws on a real player without throwing.
 */
public final class HudSuite implements ConformanceSuite {

    private static final MCDetailedVersion ACTION_BAR_AND_TITLE_SINCE = MCDetailedVersion.v1_8_R1;
    private static final MCDetailedVersion BOSS_BAR_SINCE = MCDetailedVersion.v1_9_R1;
    private static final MCDetailedVersion TOAST_SINCE = MCDetailedVersion.v1_12_R1;

    @Override
    public String getId() {
        return "hud";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        List<ConformanceCase> cases = new ArrayList<>();
        for (Capability capability : Capability.values()) {
            cases.add(ConformanceCase.of("supports-" + capability.name().toLowerCase().replace('_', '-'), () -> {
                boolean expected = expected(capability);
                boolean answered = hud().supports(capability);
                return Verdict.of(answered == expected, "supports(" + capability + ")=" + answered + " expected=" + expected
                        + " version=" + MCVersion.getCurrent() + " necroTempus=" + FCBukkitUtil.isModLoaded("necrotempus"));
            }));
        }
        cases.add(PlayerCases.of("title-and-bossbar-deliver", ctx, player -> {
            FPlayer fPlayer = FCBukkitUtil.adapt(player);
            List<String> done = new ArrayList<>();
            if (hud().supports(Capability.TITLE)) {
                hud().sendTitle(fPlayer, FancyText.of("§aECC title"), FancyText.of("§7subtitle"), 5, 20, 5);
                hud().clearTitle(fPlayer);
                done.add("title");
            }
            if (hud().supports(Capability.BOSS_BAR)) {
                hud().showBossBar(fPlayer, "ecc", FancyText.of("§bECC bar"), 0.5f, BossBarColor.BLUE);
                hud().showBossBar(fPlayer, "ecc", FancyText.of("§bECC bar"), 1f, BossBarColor.GREEN);
                hud().hideBossBar(fPlayer, "ecc");
                done.add("boss-bar");
            }
            return done.isEmpty() ? Verdict.skipped("neither TITLE nor BOSS_BAR on this server") : Verdict.pass("delivered=" + done);
        }));
        cases.add(PlayerCases.of("sidebar-delivers", ctx, player -> {
            FPlayer fPlayer = FCBukkitUtil.adapt(player);
            hud().setSidebar(fPlayer, FancyText.of("§eECC"), Arrays.asList(FancyText.of("§fline one"), FancyText.of("§fline one"), FancyText.of("§7two")));
            Objective shown = player.getScoreboard().getObjective(DisplaySlot.SIDEBAR);
            hud().clearSidebar(fPlayer);
            Objective after = player.getScoreboard().getObjective(DisplaySlot.SIDEBAR);
            return Verdict.of(shown != null && (after == null || !after.getName().equals("ec_hud")),
                    "shown=" + (shown == null ? null : shown.getName()) + " afterClear=" + (after == null ? null : after.getName()));
        }));
        cases.add(PlayerCases.later("toast-delivers", ctx, player -> {
            if (!hud().supports(Capability.TOAST)) {
                return CompletableFuture.completedFuture(Verdict.skipped("no TOAST before " + TOAST_SINCE.getLowestRelease()));
            }
            int before = Toasts.left();
            String unique = "§7conformance " + UUID.randomUUID();
            hud().showToast(FCBukkitUtil.adapt(player), FancyText.of("§aECC toast"), FancyText.of(unique), ToastStyle.SUCCESS);
            CompletableFuture<Verdict> answer = new CompletableFuture<>();
            //the server never unloads an advancement: the toast's stays loaded, and the same toast again must reuse it
            ECScheduler.onMainThreadLater(() -> {
                int afterFirst = Toasts.left();
                hud().showToast(FCBukkitUtil.adapt(player), FancyText.of("§aECC toast"), FancyText.of(unique), ToastStyle.SUCCESS);
                ECScheduler.onMainThreadLater(() -> ECScheduler.onMainThreadLater(() -> {
                    int afterSecond = Toasts.left();
                    boolean stillSupported = hud().supports(Capability.TOAST);
                    answer.complete(Verdict.of(afterFirst == before + 1 && afterSecond == afterFirst && stillSupported,
                            "toastAdvancements before=" + before + " afterFirst=" + afterFirst + " afterSameAgain=" + afterSecond
                                    + " supportsAfter=" + stillSupported));
                }));
            });
            return PlayerCases.within(answer, 5000);
        }));
        return cases;
    }

    private static IHudAccess hud() {
        return EverNifeCore.getPlatform().getHud();
    }

    private static boolean expected(Capability capability) {
        switch (capability) {
            case ACTION_BAR:
            case TITLE:
                return MCVersion.isHigherEquals(ACTION_BAR_AND_TITLE_SINCE) || FCBukkitUtil.isModLoaded("necrotempus");
            case BOSS_BAR:
                return MCVersion.isHigherEquals(BOSS_BAR_SINCE);
            case TOAST:
                return MCVersion.isHigherEquals(TOAST_SINCE);
            default:
                return true;
        }
    }

    /** The toast advancements still loaded - a 1.12 API, only reached once {@code supports(TOAST)} said yes. */
    private static final class Toasts {
        static int left() {
            int left = 0;
            for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
                Advancement advancement = it.next();
                if (advancement.getKey().getKey().startsWith("toast/") && advancement.getKey().getNamespace().equals("evernifecore")) {
                    left++;
                }
            }
            return left;
        }
    }
}
