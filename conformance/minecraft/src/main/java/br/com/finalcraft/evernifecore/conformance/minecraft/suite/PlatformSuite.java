package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.economy.Currencies;
import br.com.finalcraft.evernifecore.economy.Currency;
import br.com.finalcraft.evernifecore.region.event.RegionChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionCreatedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionEnterEvent;
import br.com.finalcraft.evernifecore.region.event.RegionFlagChangedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupCreatedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupDissolvedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupFlagChangedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupInviteEvent;
import br.com.finalcraft.evernifecore.group.event.GroupLeaderChangedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupMemberJoinedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupMemberLeftEvent;
import br.com.finalcraft.evernifecore.group.event.GroupMemberPreJoinEvent;
import br.com.finalcraft.evernifecore.group.event.GroupRoleChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionLeaveEvent;
import br.com.finalcraft.evernifecore.region.event.RegionLoadFailedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionRedefinedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionRemovedEvent;
import br.com.finalcraft.evernifecore.testing.ECEventConformance;
import br.com.finalcraft.evernifecore.testing.EconomyConformance;
import br.com.finalcraft.evernifecore.testing.PlatformConformance;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The conformance checks the headless suite runs against a double, run against the platform this server
 * registered - the only place a classpath probe that lies about the platform can be caught. One case per
 * violation line, so a report names each one.
 */
public final class PlatformSuite implements ConformanceSuite {

    private static final long NEXT_TICK_TIMEOUT_MILLIS = 10_000;

    @Override
    public String getId() {
        return "platform";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        List<ConformanceCase> cases = new ArrayList<>();
        cases.add(ConformanceCase.of("platform", () -> fromViolations(PlatformConformance.check(EverNifeCore.getPlatform()),
                PlatformConformance.summarize(EverNifeCore.getPlatform()))));
        cases.add(ConformanceCase.later("world-thread-next-tick", PlatformSuite::worldThreadNextTick));
        cases.add(ConformanceCase.of("region-events", () -> fromViolations(ECEventConformance.checkAll(
                RegionEnterEvent.class, RegionLeaveEvent.class, RegionCreatedEvent.class, RegionRemovedEvent.class,
                RegionRedefinedEvent.class, RegionFlagChangedEvent.class, RegionChangedEvent.class, RegionLoadFailedEvent.class,
                GroupCreatedEvent.class, GroupMemberPreJoinEvent.class, GroupMemberJoinedEvent.class, GroupMemberLeftEvent.class,
                GroupRoleChangedEvent.class, GroupLeaderChangedEvent.class, GroupInviteEvent.class, GroupFlagChangedEvent.class,
                GroupDissolvedEvent.class), "8 region events and 9 group events")));
        cases.add(ConformanceCase.of("economy", () -> {
            Currency money = Currencies.money();
            //the core always registers a lazy bridge; only money with an economy behind it can be checked
            if (!money.isAvailable()) {
                return Verdict.skipped("no economy behind the money currency on this server");
            }
            return fromViolations(EconomyConformance.check(money), money.getId() + " over "
                    + EverNifeCore.getProviders().getEconomyOrNull().getClass().getName());
        }));
        return cases;
    }

    /**
     * From the main thread, {@code runOnWorldThreadNextTick} must run its task on the main thread in a later tick. The
     * tick is a counter of this case's own: Bukkit has no tick number before Paper 1.13. The counter
     * is scheduled first, so in every tick it runs before the tasks scheduled after it.
     */
    private static CompletableFuture<Verdict> worldThreadNextTick() {
        String world = Bukkit.getWorlds().get(0).getName();
        int[] ticks = new int[1];
        BukkitTask counter = Bukkit.getScheduler().runTaskTimer(plugin(), () -> ticks[0]++, 0L, 1L);
        CompletableFuture<Verdict> answer = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin(), () -> {
            int before = ticks[0];
            EverNifeCore.getPlatform().runOnWorldThreadNextTick(world, () -> {
                int after = ticks[0];
                boolean onMainThread = Bukkit.isPrimaryThread();
                answer.complete(Verdict.of(after > before && onMainThread,
                        "world=" + world + " before=" + before + " after=" + after + " onMainThread=" + onMainThread));
            }).whenComplete((done, failed) -> {
                if (failed != null) {
                    answer.complete(Verdict.fail(Verdict.describe(failed)));
                }
            });
        });
        return PlayerCases.within(answer, NEXT_TICK_TIMEOUT_MILLIS).whenComplete((verdict, failed) -> counter.cancel());
    }

    private static Plugin plugin() {
        return Bukkit.getPluginManager().getPlugin("ECoreConformance");
    }

    private static Verdict fromViolations(List<String> violations, String subject) {
        return violations.isEmpty() ? Verdict.pass(subject) : Verdict.fail(violations.size() + " violation(s) on " + subject + ": "
                + String.join(" | ", violations));
    }
}
