package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.action.event.BreakActionEvent;
import br.com.finalcraft.evernifecore.action.event.CommandActionEvent;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.RegisteredListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The public action events on a real server: a bus subscriber of {@code CommandActionEvent.Post} is what puts the core's
 * command listener on the server, and a real player's command and block break arrive as their {@code Post}.
 * {@code /ecc actions <player>} reads the handler list, subscribes and starts recording; the client then runs
 * {@code /ecc list} and breaks a block, and this suite reads what was recorded - and unsubscribes.
 */
public final class ActionsSuite implements ConformanceSuite {

    private static final String CORE = "EverNifeCore";

    private static volatile Armed armed;

    @Override
    public String getId() {
        return "actions";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                PlayerCases.of("command-source-on-demand", ctx, player -> {
                    Armed current = armedFor(player);
                    if (current == null) {
                        return Verdict.skipped("not armed - /ecc actions " + player.getName() + " first, then run /ecc list as them");
                    }
                    int after = coreCommandHandlers();
                    boolean heard = current.commands.stream().anyMatch(names -> names.contains("ecc"));
                    //the listener may come on a tick after the subscription: "now" (still subscribed) is the proof
                    return Verdict.of(current.handlersBefore == 0 && after > 0 && heard,
                            "coreHandlersBefore=" + current.handlersBefore + " whileSubscribed=" + current.handlersWhileSubscribed
                                    + " now=" + after + " commandNames=" + current.commands);
                }),
                PlayerCases.of("break-post-after-regions", ctx, player -> {
                    Armed current = armedFor(player);
                    if (current == null) {
                        return Verdict.skipped("not armed - /ecc actions " + player.getName() + " first, then break a block as them");
                    }
                    try {
                        return Verdict.of(!current.breaks.isEmpty(), current.breaks.isEmpty() ? "armed, but no BreakActionEvent.Post arrived"
                                : "breaks=" + current.breaks);
                    } finally {
                        current.close(); //the last case to read it: the sources may go idle again
                        armed = null;
                    }
                }));
    }

    private static Armed armedFor(Player player) {
        Armed current = armed;
        return current != null && current.player.equals(player.getUniqueId()) ? current : null;
    }

    /** Reads the core's command handlers, subscribes to the command and break posts and records {@code player}'s. */
    public static String arm(Player player) {
        Armed previous = armed;
        if (previous != null) {
            previous.close();
        }
        Armed next = new Armed(player.getUniqueId(), coreCommandHandlers());
        next.subscriptions.add(ECEventBus.global().subscribe(CommandActionEvent.Post.class, event -> {
            FPlayer who = event.getContext().getPlayer();
            if (who != null && who.getUniqueId().equals(next.player)) {
                next.commands.add(new ArrayList<>(event.getContext().getNames()));
            }
        }));
        next.subscriptions.add(ECEventBus.global().subscribe(BreakActionEvent.Post.class, event -> {
            FPlayer who = event.getContext().getPlayer();
            if (who != null && who.getUniqueId().equals(next.player)) {
                next.breaks.add(String.valueOf(event.getContext().getBlockKey()));
            }
        }));
        next.handlersWhileSubscribed = coreCommandHandlers();
        armed = next;
        return "coreCommandHandlersBefore=" + next.handlersBefore + " whileSubscribed=" + next.handlersWhileSubscribed;
    }

    private static int coreCommandHandlers() {
        int count = 0;
        for (RegisteredListener listener : PlayerCommandPreprocessEvent.getHandlerList().getRegisteredListeners()) {
            if (CORE.equals(listener.getPlugin().getName())) {
                count++;
            }
        }
        return count;
    }

    private static final class Armed {

        private final UUID player;
        private final int handlersBefore;
        private volatile int handlersWhileSubscribed;
        private final List<List<String>> commands = Collections.synchronizedList(new ArrayList<>());
        private final List<String> breaks = Collections.synchronizedList(new ArrayList<>());
        private final List<ECEventSubscription<?>> subscriptions = new ArrayList<>();

        private Armed(UUID player, int handlersBefore) {
            this.player = player;
            this.handlersBefore = handlersBefore;
        }

        private void close() {
            subscriptions.forEach(ECEventSubscription::unsubscribe);
        }
    }
}
