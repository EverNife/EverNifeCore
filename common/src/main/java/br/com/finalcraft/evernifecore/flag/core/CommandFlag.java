package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.SessionContext;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagCategory;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagResolver;
import br.com.finalcraft.evernifecore.flag.FlagResolvers;
import br.com.finalcraft.evernifecore.flag.FlagTypes;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.time.Window;
import br.com.finalcraft.evernifecore.types.TypeRegistry;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code command-on-enter} and {@code command-on-exit}: commands run when a player enters or leaves the region, one per
 * line, each through the {@code command} effect. A line starting {@code player:} runs as the player, {@code console:} or no
 * prefix as the console; {@code {variables}} and placeholders in the line (the region ones included) are resolved for the
 * player. Overlapping regions each run their own lines.
 */
public final class CommandFlag extends Flag<List<String>> {

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


    @Override
    public void onEnter(SessionContext context, List<String> value) {
        if (!onExit) {
            run(context, value);
        }
    }

    @Override
    public void onLeave(SessionContext context, List<String> lastValue) {
        if (onExit) {
            run(context, lastValue);
        }
    }

    /** Crossing straight from one region to another runs the entered one's on-enter, or the left one's on-exit. */
    @Override
    public void onRevalidate(SessionContext context, @Nullable List<String> oldValue, @Nullable List<String> newValue) {
        if (oldValue == null || newValue == null || oldValue.equals(newValue)) {
            return; //an unchanged value asked again runs nothing
        }
        run(context, onExit ? oldValue : newValue);
    }

    /** Each line runs through the {@code command} effect, in the context {@link EffectsFlag#contextOf} gives. */
    private void run(SessionContext context, List<String> lines) {
        if (!EverNifeCore.getPlatform().getPlayerState().supports(IPlayerStateAccess.Capability.COMMAND)) {
            return;
        }
        ExecutionContext ctx = EffectsFlag.contextOf(this, context, false);
        for (String line : lines) {
            String text = line.trim();
            String lower = text.toLowerCase(Locale.ROOT);
            boolean asPlayer = lower.startsWith(AS_PLAYER);
            if (asPlayer) {
                text = text.substring(AS_PLAYER.length()).trim();
            } else if (lower.startsWith(AS_CONSOLE)) {
                text = text.substring(AS_CONSOLE.length()).trim();
            }
            if (text.startsWith("/")) {
                text = text.substring(1);
            }
            if (text.isEmpty()) {
                continue;
            }
            ObjectNode node = JsonNodeFactory.instance.objectNode().put(TypeRegistry.TYPE_FIELD, "command").put("command", text)
                    .put("as", asPlayer ? "player" : "console");
            try {
                Effect effect = Effects.parse(getOwner(), node, Window.getServerZone());
                if (Effects.getPendingType(effect) != null) {
                    ECDebugModule.REGIONS.debug("Region command '{}' for {} does not run: the command effect is not registered.", text,
                            context.getPlayer().getName());
                    continue;
                }
                Effects.apply(ctx, effect);
            } catch (TypeRegistryException unreadable) {
                ECDebugModule.REGIONS.debug("Region command '{}' for {} does not read: {}", text, context.getPlayer().getName(),
                        unreadable.getMessage());
            }
        }
    }

    /** Overlapping regions each run their own lines, in layer order. */
    @Override
    public FlagResolver<List<String>> getResolver() {
        return FlagResolvers.concat();
    }

    @Override
    public List<FlagResolver<List<String>>> getAcceptedResolvers() {
        return Arrays.asList(getResolver(), FlagResolvers.firstDefined());
    }
}
