package br.com.finalcraft.evernifecore.conformance;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.logic.HoldableEffect;
import br.com.finalcraft.evernifecore.logic.MutableVariable;
import br.com.finalcraft.evernifecore.logic.Variable;
import br.com.finalcraft.evernifecore.logic.Variables;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.player.state.ECGameMode;
import br.com.finalcraft.evernifecore.player.state.HeldPlayerState;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.player.state.StateHolder;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.time.Window;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The checks of the operations engine both platforms run: the stock types and what this platform supports of them, a
 * gate and a variable read on the real player, a console command, a held game mode given back, a window in the
 * server's zone. Each runs where it is called - a player case on that player's world thread.
 */
public final class LogicCases {

    public static final List<String> STOCK_VARIABLES = Arrays.asList(
            "exp-level", "exp-points", "health", "food", "gamemode", "permission", "placeholder", "playtime", "stat", "custom");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final StateHolder HOLDER = StateHolder.of("ecoreconformance:logic");

    private LogicCases() {
    }

    /** Every stock variable is registered, and {@code exp-level}/{@code stat} are supported exactly when {@code experience} says. */
    public static Verdict stockVariables(boolean experience) {
        List<String> missing = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        for (String name : STOCK_VARIABLES) {
            Optional<TypeDescriptor<Variable<?>>> found = Variables.registry().find(name);
            if (!found.isPresent()) {
                missing.add(name);
            } else if (Variables.registry().getUnsupported(found.get()).isPresent()) {
                unsupported.add(name);
            }
        }
        boolean expLevel = !unsupported.contains("exp-level");
        boolean stat = !unsupported.contains("stat");
        return Verdict.of(missing.isEmpty() && expLevel == experience && stat == experience,
                "missing=" + missing + " unsupported=" + unsupported + " expected-experience=" + experience
                        + " platform=" + EverNifeCore.getPlatform().getPlatformProviderId());
    }

    /** {@code gate} (a {@code {require: [...]}} definition) checked for {@code player} passes. */
    public static Verdict gatePasses(ECPluginData plugin, FPlayer player, String gate) throws Exception {
        GateResult result = Gate.parse(plugin, json(gate), Window.getServerZone()).check(context(plugin, player).build());
        return Verdict.of(result.isPassed(), "outcome=" + result.getOutcome() + " failures=" + result.explain().toPlainText());
    }

    /** {@code variable} written {@code value} on {@code player} reads it back; the value it had is put back after. */
    public static Verdict variableRoundTrip(ECPluginData plugin, FPlayer player, String variable, BigDecimal value) {
        Optional<Variable<?>> found = Variables.find(plugin, variable);
        if (!found.isPresent() || !(found.get() instanceof MutableVariable)) {
            return Verdict.fail(variable + " is not a writable variable here: " + found);
        }
        @SuppressWarnings("unchecked")
        MutableVariable<BigDecimal> writable = (MutableVariable<BigDecimal>) found.get();
        ExecutionContext ctx = context(plugin, player).build();
        BigDecimal before = writable.read(ctx);
        try {
            writable.set(ctx, value);
            BigDecimal read = writable.read(ctx);
            return Verdict.of(read != null && read.compareTo(value) == 0, variable + " set=" + value + " read=" + read + " before=" + before);
        } finally {
            writable.set(ctx, before);
        }
    }

    /** The {@code command} effect with {@code as: console} runs {@code command} and is audited as applied. */
    public static Verdict consoleCommand(ECPluginData plugin, FPlayer player, String command) throws Exception {
        Effect effect = Effects.parse(plugin, json("{\"type\": \"command\", \"command\": \"" + command + "\", \"as\": \"console\"}"),
                Window.getServerZone());
        ExecutionContext ctx = context(plugin, player).build();
        Effects.apply(ctx, effect);
        int applied = ctx.getAudit().getEntries(AuditKind.EFFECT_APPLIED).size();
        int failed = ctx.getAudit().getEntries(AuditKind.EFFECT_FAILED).size();
        return Verdict.of(applied == 1 && failed == 0, "command=" + command + " applied=" + applied + " failed=" + failed
                + " audit=" + ctx.getAudit().getEntries());
    }

    /**
     * {@code gamemode} with {@code scope: held} switches {@code player} between adventure and creative, and releasing the
     * holder gives the mode they had back.
     */
    public static Verdict heldGameMode(ECPluginData plugin, FPlayer player) throws Exception {
        IPlayerStateAccess access = EverNifeCore.getPlatform().getPlayerState();
        ECGameMode before = access.getGameMode(player);
        ECGameMode held = before == ECGameMode.ADVENTURE ? ECGameMode.CREATIVE : ECGameMode.ADVENTURE;
        Effect effect = Effects.parse(plugin, json("{\"type\": \"gamemode\", \"value\": \"" + held.name().toLowerCase(Locale.ROOT)
                + "\", \"scope\": \"held\"}"), Window.getServerZone());
        Effects.apply(context(plugin, player).attribute(HoldableEffect.HOLDER_ATTRIBUTE, HOLDER).build(), effect);
        ECGameMode during = access.getGameMode(player);
        boolean released = HeldPlayerState.of(player.getUniqueId()).release(HeldPlayerState.GAME_MODE, HOLDER, player);
        ECGameMode after = access.getGameMode(player);
        return Verdict.of(during == held && released && after == before,
                "before=" + before + " held=" + held + " during=" + during + " released=" + released + " after=" + after);
    }

    /** {@code daily at 04:00} in the server's zone started within the last day. */
    public static Verdict dailyWindowInTheServerZone() {
        ZoneId zone = Window.getServerZone();
        Instant now = ECClock.now();
        Instant start = Window.daily(LocalTime.of(4, 0), zone).currentStart(now);
        boolean inThePast = start != null && !start.isAfter(now) && start.isAfter(now.minus(Duration.ofDays(1)).minusSeconds(3600));
        return Verdict.of(inThePast, "zone=" + zone + " now=" + now + " currentStart=" + start);
    }

    private static ExecutionContext.Builder context(ECPluginData plugin, FPlayer player) {
        return ExecutionContext.builder(Caller.of(plugin, "conformance/logic")).player(player);
    }

    private static JsonNode json(String text) throws Exception {
        return JSON.readTree(text);
    }
}
