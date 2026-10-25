package br.com.finalcraft.evernifecore.logic.core;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.integration.placeholders.PAPIIntegration;
import br.com.finalcraft.evernifecore.logic.CustomVariables;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.MutableVariable;
import br.com.finalcraft.evernifecore.logic.Variable;
import br.com.finalcraft.evernifecore.logic.Variables;
import br.com.finalcraft.evernifecore.ontime.OntimeManager;
import br.com.finalcraft.evernifecore.player.state.ECGameMode;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess.Capability;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** The stock variables: {@code exp-level}, {@code exp-points}, {@code health}, {@code food}, {@code gamemode}, {@code permission}, {@code placeholder}, {@code playtime}, {@code stat}. */
final class StockVariables {

    private StockVariables() {
    }

    static void register(ECPluginData core) {
        register(core, "exp-level", "The player's experience level.", Capability.EXPERIENCE, new Writable<>("exp-level", BigDecimal.class,
                player -> BigDecimal.valueOf(access().getLevel(player)), (player, value) -> access().setLevel(player, whole(value))));
        register(core, "exp-points", "The player's total experience points.", Capability.EXPERIENCE, new Writable<>("exp-points", BigDecimal.class,
                player -> BigDecimal.valueOf(access().getExp(player)), (player, value) -> access().setExp(player, whole(value))));
        register(core, "health", "The player's health.", Capability.HEALTH, new Writable<>("health", BigDecimal.class,
                player -> BigDecimal.valueOf(access().getHealth(player)), (player, value) -> access().setHealth(player, value.doubleValue())));
        register(core, "food", "The player's food level.", Capability.FOOD, new Writable<>("food", BigDecimal.class,
                player -> BigDecimal.valueOf(access().getFood(player)), (player, value) -> access().setFood(player, whole(value))));
        register(core, "gamemode", "The player's game mode: survival, creative, adventure or spectator.", Capability.GAME_MODE,
                new Writable<>("gamemode", String.class, player -> access().getGameMode(player).name().toLowerCase(Locale.ROOT),
                        (player, value) -> access().setGameMode(player, ECGameMode.valueOf(value.trim().toUpperCase(Locale.ROOT)))));
        register(core, "playtime", "How long the player has played on this server.", null, new Readable<>("playtime", Duration.class,
                player -> Duration.ofMillis(OntimeManager.getProvider().getOntime(loadedData(player)))));

        Variables.registry().register(core, TypeDescriptor.<Variable<?>>of("evernifecore:permission")
                .describe("Whether the player has a permission node.")
                .param(ParamSpec.required("node", ParamSpec.Kind.ID, "The permission node, e.g. shop.vip."))
                .factory(params -> {
                    String node = params.getString("node");
                    return new Readable<>("permission", Boolean.class, player -> player.hasPermission(node));
                })
                .build());
        Variables.registry().register(core, TypeDescriptor.<Variable<?>>of("evernifecore:placeholder")
                .describe("A text with %placeholders% filled for the player.")
                .param(ParamSpec.required("text", ParamSpec.Kind.STRING, "The text, e.g. %vault_eco_balance%."))
                .factory(params -> {
                    String text = params.getString("text");
                    return new Readable<>("placeholder", String.class, player -> PAPIIntegration.parse(player, text));
                })
                .build());
        Variables.registry().register(core, TypeDescriptor.<Variable<?>>of("evernifecore:custom")
                .describe("A plugin's own variable, one value per owner, by the id it was defined with (plugin:name).")
                .param(ParamSpec.required("id", ParamSpec.Kind.ID, "The variable's id, e.g. fakeshop:daily-money-earned."))
                .factory(params -> {
                    String id = params.getString("id");
                    return CustomVariables.find(id).orElseThrow(() -> new IllegalArgumentException("No plugin defined the custom variable '"
                            + id + "': define it with Variables.custom(plugin, storage).define(name, type, initial), and write its id as plugin:name."));
                })
                .build());
        Variables.registry().register(core, TypeDescriptor.<Variable<?>>of("evernifecore:stat")
                .describe("A statistic of the platform by its native name, e.g. PLAY_ONE_MINUTE on Bukkit.")
                .param(ParamSpec.required("name", ParamSpec.Kind.ID, "The statistic's native name."))
                .needsCapability(Capability.STATISTICS)
                .factory(params -> {
                    String name = params.getString("name");
                    return new Readable<>("stat", BigDecimal.class, player -> {
                        BigDecimal value = access().getStatistic(player, name);
                        if (value == null) {
                            throw new IllegalArgumentException("This platform has no statistic named '" + name + "': write its native"
                                    + " name, e.g. PLAY_ONE_MINUTE on Bukkit.");
                        }
                        return value;
                    });
                })
                .build());
    }

    private static void register(ECPluginData core, String name, String description, Capability capability, Variable<?> variable) {
        TypeDescriptor.Builder<Variable<?>> builder = TypeDescriptor.<Variable<?>>of("evernifecore:" + name)
                .describe(description)
                .factory(params -> variable);
        if (capability != null) {
            builder.needsCapability(capability);
        }
        Variables.registry().register(core, builder.build());
    }

    private static IPlayerStateAccess access() {
        return EverNifeCore.getPlatform().getPlayerState();
    }

    private static int whole(BigDecimal value) {
        return value.setScale(0, RoundingMode.DOWN).intValueExact();
    }

    private static PlayerData loadedData(FPlayer player) {
        PlayerData data = PlayerController.getLoaded(player.getUniqueId());
        if (data == null) {
            throw new IllegalStateException("The playtime of " + player.getName() + " is not loaded - it is read from their player data,"
                    + " which loads on login.");
        }
        return data;
    }

    /** A variable read from the context's player. */
    static class Readable<T> implements Variable<T> {
        private final String id;
        private final Class<T> type;
        private final Function<FPlayer, T> reader;

        Readable(String id, Class<T> type, Function<FPlayer, T> reader) {
            this.id = id;
            this.type = type;
            this.reader = reader;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Class<T> getType() {
            return type;
        }

        @Override
        public T read(ExecutionContext ctx) {
            return reader.apply(player(ctx));
        }

        FPlayer player(ExecutionContext ctx) {
            FPlayer player = ctx.getPlayer();
            if (player == null) {
                throw new IllegalStateException("The variable '" + id + "' reads a player, and the context of " + ctx.getCaller()
                        + " has none (a preview, the scheduler or a global owner).");
            }
            return player;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    /** A variable read from and written to the context's player. */
    static final class Writable<T> extends Readable<T> implements MutableVariable<T> {
        private final BiConsumer<FPlayer, T> writer;

        Writable(String id, Class<T> type, Function<FPlayer, T> reader, BiConsumer<FPlayer, T> writer) {
            super(id, type, reader);
            this.writer = writer;
        }

        @Override
        public void set(ExecutionContext ctx, T value) {
            writer.accept(player(ctx), value);
        }
    }
}
