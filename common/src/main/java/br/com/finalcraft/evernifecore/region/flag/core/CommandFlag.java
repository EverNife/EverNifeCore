package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagCategory;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagTypes;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.context.SessionContext;
import br.com.finalcraft.evernifecore.region.playerstate.IPlayerStateAccess;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code command-on-enter} and {@code command-on-exit}: commands run when a player enters or leaves the region, one per
 * line. A line starting {@code player:} runs as the player, {@code console:} or no prefix as the console; placeholders
 * in the line (the region ones included) are resolved for the player first.
 */
public final class CommandFlag extends RegionFlag<List<String>> {

    private static final String AS_PLAYER = "player:";
    private static final String AS_CONSOLE = "console:";

    private final boolean onExit;

    private CommandFlag(String name, boolean onExit) {
        super(FlagKey.of(CoreFlags.OWNER, name), FlagTypes.TEXT_LIST, Collections.singleton(EventSource.MOVE), null);
        this.onExit = onExit;
    }

    public static CommandFlag onEnter() {
        return new CommandFlag("command-on-enter", false);
    }

    public static CommandFlag onExit() {
        return new CommandFlag("command-on-exit", true);
    }

    @Override
    public String getDescription() {
        return "Commands run when a player " + (onExit ? "leaves" : "enters") + " the region, separated by ';'. 'player:' runs one as"
                + " the player, 'console:' or no prefix as the console; placeholders are resolved for the player.";
    }

    @Override
    public FlagCategory getCategory() {
        return FlagCategory.SESSION;
    }

    @Override
    public Set<IPlayerStateAccess.Capability> getNeededCapabilities() {
        return Collections.singleton(IPlayerStateAccess.Capability.COMMAND);
    }

    /** The first. */
    @Override
    public List<String> resolveTie(List<List<String>> values) {
        return values.get(0);
    }

    @Override
    public void onEnter(SessionContext context, List<String> value) {
        if (!onExit) {
            run(context.getPlayer(), value);
        }
    }

    @Override
    public void onLeave(SessionContext context, List<String> lastValue) {
        if (onExit) {
            run(context.getPlayer(), lastValue);
        }
    }

    /** Crossing straight from one region to another runs the entered one's on-enter, or the left one's on-exit. */
    @Override
    public void onRevalidate(SessionContext context, @Nullable List<String> oldValue, @Nullable List<String> newValue) {
        if (oldValue == null || newValue == null || oldValue.equals(newValue)) {
            return; //an unchanged value asked again runs nothing
        }
        run(context.getPlayer(), onExit ? oldValue : newValue);
    }

    private static void run(FPlayer player, List<String> lines) {
        IPlayerStateAccess access = EverNifeCore.getPlatform().getPlayerState();
        if (!access.supports(IPlayerStateAccess.Capability.COMMAND)) {
            return;
        }
        for (String line : lines) {
            String text = line.trim();
            FPlayer as = null;
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.startsWith(AS_PLAYER)) {
                as = player;
                text = text.substring(AS_PLAYER.length());
            } else if (lower.startsWith(AS_CONSOLE)) {
                text = text.substring(AS_CONSOLE.length());
            }
            text = EverNifeCore.getPlatform().parse(player, text.trim());
            if (text.startsWith("/")) {
                text = text.substring(1);
            }
            if (text.isEmpty()) {
                continue;
            }
            try {
                access.dispatchCommand(as, text);
            } catch (RuntimeException failed) {
                ECDebugModule.REGIONS.debug("Region command '{}' for {} failed: {}", text, player.getName(), failed.toString());
            }
        }
    }
}
