package br.com.finalcraft.evernifecore.logic.core;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.EffectModifiers;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.HoldableEffect;
import br.com.finalcraft.evernifecore.logic.MutableVariable;
import br.com.finalcraft.evernifecore.logic.Variable;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.player.state.ECGameMode;
import br.com.finalcraft.evernifecore.player.state.EffectLevel;
import br.com.finalcraft.evernifecore.player.state.HeldPlayerState;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess.Capability;
import br.com.finalcraft.evernifecore.player.state.PlayerTime;
import br.com.finalcraft.evernifecore.player.state.StateHolder;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;

/**
 * The stock effects: {@code message}, {@code command}, {@code set}, {@code add}, {@code teleport}, and the holdable
 * {@code gamemode}, {@code fly}, {@code potion-effect}, {@code player-time} and {@code player-weather}.
 */
final class StockEffects {

    private StockEffects() {
    }

    static void register(ECPluginData core) {
        Effects.registry().register(core, TypeDescriptor.<Effect>of("evernifecore:message")
                .describe("Sends the player a text, its {variables} and %placeholders% filled.")
                .param(ParamSpec.required("text", ParamSpec.Kind.TEXT, "The text sent."))
                .factory(params -> {
                    String text = params.getString("text");
                    return named("message", ctx -> player(ctx, "message").sendMessage(FancyText.of(ctx.resolve(text))));
                })
                .build());
        Effects.registry().register(core, TypeDescriptor.<Effect>of("evernifecore:command")
                .describe("Runs a command, its {variables} filled, as the console or, with as: player, as the player.")
                .param(ParamSpec.required("command", ParamSpec.Kind.STRING, "The command, without the leading /."))
                .needsCapability(Capability.COMMAND)
                .factory(params -> new CommandEffect(params.getString("command")))
                .build());
        Effects.registry().register(core, TypeDescriptor.<Effect>of("evernifecore:set")
                .describe("Sets a variable that can be written to an amount.")
                .param(ParamSpec.required("variable", ParamSpec.Kind.ID, "The variable written, e.g. food."))
                .param(ParamSpec.required("amount", ParamSpec.Kind.STRING, "The value written: a number, a text, true or false."))
                .factory(params -> new Write(params.getCaller(), params.getString("variable"), params.getString("amount"), false, BigDecimal.ONE))
                .build());
        Effects.registry().register(core, TypeDescriptor.<Effect>of("evernifecore:add")
                .describe("Adds an amount to a numeric variable that can be written; a negative amount takes.")
                .param(ParamSpec.required("variable", ParamSpec.Kind.ID, "The variable written, e.g. exp-points."))
                .param(ParamSpec.required("amount", ParamSpec.Kind.STRING, "The number added."))
                .factory(params -> new Write(params.getCaller(), params.getString("variable"), params.getString("amount"), true, BigDecimal.ONE))
                .build());
        Effects.registry().register(core, TypeDescriptor.<Effect>of("evernifecore:teleport")
                .describe("Teleports the player to a point of a world.")
                .param(ParamSpec.required("world", ParamSpec.Kind.STRING, "The world's name."))
                .param(ParamSpec.required("x", ParamSpec.Kind.NUMBER, "X."))
                .param(ParamSpec.required("y", ParamSpec.Kind.NUMBER, "Y."))
                .param(ParamSpec.required("z", ParamSpec.Kind.NUMBER, "Z."))
                .factory(params -> {
                    WorldLocPos to = WorldLocPos.of(params.getNumber("x").doubleValue(), params.getNumber("y").doubleValue(),
                            params.getNumber("z").doubleValue(), params.getString("world"));
                    return named("teleport(" + to + ")", ctx -> player(ctx, "teleport").teleportTo(new FLocation(to, null)));
                })
                .build());

        Effects.registry().register(core, TypeDescriptor.<Effect>of("evernifecore:gamemode")
                .describe("Changes the player's game mode; holdable.")
                .param(ParamSpec.required("value", ParamSpec.Kind.STRING, "The game mode.").oneOf("survival", "creative", "adventure", "spectator"))
                .needsCapability(Capability.GAME_MODE)
                .factory(params -> {
                    ECGameMode mode = ECGameMode.valueOf(params.getString("value").toUpperCase(Locale.ROOT));
                    return new SlotEffect("gamemode " + mode.name().toLowerCase(Locale.ROOT), HeldPlayerState.GAME_MODE, mode) {
                        @Override
                        void write(IPlayerStateAccess access, FPlayer player) {
                            if (!access.supportsGameMode(mode)) {
                                throw new IllegalStateException("This platform has no " + mode + " game mode.");
                            }
                            access.setGameMode(player, mode);
                        }
                    };
                })
                .build());
        Effects.registry().register(core, TypeDescriptor.<Effect>of("evernifecore:fly")
                .describe("Lets the player fly, or takes it away and lands them; holdable.")
                .param(ParamSpec.optional("value", ParamSpec.Kind.BOOLEAN, true, "Whether the player may fly."))
                .needsCapability(Capability.FLIGHT)
                .factory(params -> {
                    boolean allow = params.getBoolean("value");
                    return new SlotEffect("fly " + allow, HeldPlayerState.FLIGHT, allow) {
                        @Override
                        void write(IPlayerStateAccess access, FPlayer player) {
                            access.setAllowFlight(player, allow);
                        }
                    };
                })
                .build());
        Effects.registry().register(core, TypeDescriptor.<Effect>of("evernifecore:potion-effect")
                .describe("Gives the player an effect for a time; holdable.")
                .param(ParamSpec.required("effect", ParamSpec.Kind.ID, "The platform's effect id, e.g. speed."))
                .param(ParamSpec.optional("level", ParamSpec.Kind.NUMBER, 1, "The level, from 1."))
                .param(ParamSpec.optional("duration", ParamSpec.Kind.DURATION, "30s", "How long it lasts."))
                .needsCapability(Capability.EFFECTS)
                .factory(params -> {
                    EffectLevel level = EffectLevel.of(params.getString("effect"), params.getNumber("level").intValueExact());
                    Duration duration = params.getDuration("duration");
                    return new SlotEffect("potion-effect " + level, HeldPlayerState.effect(level.getEffect()), level) {
                        @Override
                        void write(IPlayerStateAccess access, FPlayer player) {
                            access.addEffect(player, level, duration);
                        }
                    };
                })
                .build());
        Effects.registry().register(core, TypeDescriptor.<Effect>of("evernifecore:player-time")
                .describe("Shows the player a time of day of their own (ticks, 0 = 06:00), fixed or running from it; holdable.")
                .param(ParamSpec.required("value", ParamSpec.Kind.NUMBER, "The time in ticks, 0 to 24000."))
                .param(ParamSpec.optional("relative", ParamSpec.Kind.BOOLEAN, false, "Whether the clock keeps running from it."))
                .needsCapability(Capability.TIME)
                .factory(params -> {
                    PlayerTime time = new PlayerTime(params.getNumber("value").longValue(), params.getBoolean("relative"));
                    return new SlotEffect("player-time " + time, HeldPlayerState.TIME, time) {
                        @Override
                        void write(IPlayerStateAccess access, FPlayer player) {
                            access.setTime(player, time, Duration.ZERO);
                        }
                    };
                })
                .build());
        Effects.registry().register(core, TypeDescriptor.<Effect>of("evernifecore:player-weather")
                .describe("Shows the player a weather of their own; holdable.")
                .param(ParamSpec.required("value", ParamSpec.Kind.STRING, "The weather, e.g. clear or rain."))
                .needsCapability(Capability.WEATHER)
                .factory(params -> {
                    String written = params.getString("value");
                    return new SlotEffect("player-weather " + written, HeldPlayerState.WEATHER, written) {
                        @Override
                        void write(IPlayerStateAccess access, FPlayer player) {
                            String weather = access.toWeatherId(written);
                            if (weather == null) {
                                throw new IllegalArgumentException("This platform knows no weather '" + written + "': write clear or rain.");
                            }
                            access.setWeather(player, weather);
                        }
                    };
                })
                .build());
    }

    static FPlayer player(ExecutionContext ctx, String effect) {
        FPlayer player = ctx.getPlayer();
        if (player == null) {
            throw new IllegalStateException("The effect '" + effect + "' acts on a player, and the context of " + ctx.getCaller()
                    + " has none (a preview, the scheduler or a global owner).");
        }
        return player;
    }

    private static IPlayerStateAccess access() {
        return EverNifeCore.getPlatform().getPlayerState();
    }

    private interface Action {
        void run(ExecutionContext ctx);
    }

    private static Effect named(String name, Action action) {
        return new Effect() {
            @Override
            public void apply(ExecutionContext ctx) {
                action.run(ctx);
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    private static final class CommandEffect implements Effect {
        private final String command;

        CommandEffect(String command) {
            this.command = command.startsWith("/") ? command.substring(1) : command;
        }

        /** As the console unless {@code as: player}. */
        @Override
        public void apply(ExecutionContext ctx) {
            boolean asPlayer = "player".equals(ctx.getAttribute(EffectModifiers.SENDER_ATTRIBUTE, String.class));
            access().dispatchCommand(asPlayer ? player(ctx, "command") : null, ctx.resolve(command));
        }

        @Override
        public boolean acceptsSender() {
            return true;
        }

        @Override
        public String toString() {
            return "command(" + command + ")";
        }
    }

    private static final class Write implements Effect {
        private final ECPluginData caller;
        private final String variableId;
        private final String amount;
        private final boolean add;
        private final BigDecimal share;

        Write(ECPluginData caller, String variableId, String amount, boolean add, BigDecimal share) {
            this.caller = caller;
            this.variableId = variableId;
            this.amount = amount;
            this.add = add;
            this.share = share;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void apply(ExecutionContext ctx) {
            Variable<?> variable = StockConditions.variable(caller, variableId);
            if (!(variable instanceof MutableVariable)) {
                throw new IllegalStateException("The variable '" + variableId + "' can only be read, so " + (add ? "add" : "set")
                        + " cannot write it.");
            }
            MutableVariable<Object> writable = (MutableVariable<Object>) variable;
            Object value = StockConditions.read(variable.getType(), amount);
            BigDecimal number = StockConditions.number(value);
            if (add) {
                BigDecimal current = StockConditions.number(writable.read(ctx));
                if (current == null || number == null) {
                    throw new IllegalStateException("add needs a numeric variable and a number; '" + variableId + "' is "
                            + variable.getType().getSimpleName() + ".");
                }
                BigDecimal sum = current.add(number.multiply(share));
                value = variable.getType() == Duration.class ? Duration.ofMillis(sum.movePointRight(3).longValue()) : sum;
            }
            writable.set(ctx, value);
        }

        @Override
        public Effect withShare(BigDecimal newShare) {
            return add ? new Write(caller, variableId, amount, true, share.multiply(newShare)) : this;
        }

        @Override
        public String toString() {
            return (add ? "add(" : "set(") + variableId + ", " + amount + (share.compareTo(BigDecimal.ONE) == 0 ? ")" : " x " + share + ")");
        }
    }

    /** One slot of the player's state: changed for good by {@link #apply}, or held for a holder. */
    private abstract static class SlotEffect implements HoldableEffect {
        private final String name;
        private final String slot;
        private final Object value;

        SlotEffect(String name, String slot, Object value) {
            this.name = name;
            this.slot = slot;
            this.value = value;
        }

        abstract void write(IPlayerStateAccess access, FPlayer player);

        @Override
        public void apply(ExecutionContext ctx) {
            StateHolder heldBy = ctx.getAttribute(HELD_BY_ATTRIBUTE, StateHolder.class);
            if (heldBy != null) {
                hold(ctx, heldBy);
            } else {
                write(access(), player(ctx, name));
            }
        }

        @Override
        public HeldEffect hold(ExecutionContext ctx, StateHolder holder) {
            FPlayer player = player(ctx, name);
            HeldPlayerState state = HeldPlayerState.of(player.getUniqueId());
            state.hold(slot, holder, value, held -> write(access(), held), false, player);
            write(access(), player);
            ctx.getAudit().add(ctx, AuditKind.HELD, name + " held by " + holder.getHolderId());
            return () -> {
                boolean given = state.release(slot, holder, player);
                ctx.getAudit().add(ctx, AuditKind.RELEASED, name + " released by " + holder.getHolderId() + (given ? ", given back" : ""));
                return given;
            };
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
