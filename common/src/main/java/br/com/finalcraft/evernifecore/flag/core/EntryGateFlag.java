package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.MoveContext;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.cooldown.Cooldowns;
import br.com.finalcraft.evernifecore.flag.Decision;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagDefinition;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagSettings;
import br.com.finalcraft.evernifecore.flag.FlagTypes;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.RepeatPolicy;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.time.Window;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code entry-gate}: a {@link Gate} a player must pass to get into the region. Its conditions are checked on every
 * entry; its costs are charged as the settings' {@link EntryGateSettings.Charge charge} says, through the gate's own
 * {@code pay} - and its audited events. A failed condition or a cost that cannot be taken sends the player back, and
 * they are told why, line by line, after the region's {@code deny-message}.
 */
public final class EntryGateFlag extends Flag<FlagDefinition<Gate>> {

    private static final String NAME = "entry-gate";

    //one crossing may be asked more than once (a region with a handler is asked again): it pays once
    private final Map<ActionContext, Decision> decided = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<UUID, GateResult> lastDenied = new ConcurrentHashMap<>();

    public EntryGateFlag() {
        super(FlagKey.of(CoreFlags.OWNER, NAME), FlagTypes.gate(FlagKey.of(CoreFlags.OWNER, NAME)), Collections.singleton(EventSource.MOVE), null);
    }

    @Override
    public String getDescription() {
        return "A gate to get in: {require: [<condition>...], cost: [<cost>...]}. The conditions are checked on every entry, the costs"
                + " charged as the settings' charge says; a player who fails is sent back and told why.";
    }

    @Override
    public boolean applies(ActionContext context) {
        return DenyFlag.crossing(context, MoveContext.Transition.ENTER, null);
    }

    @Override
    public Class<? extends FlagSettings> getSettingsType() {
        return EntryGateSettings.class;
    }

    /** A denied crossing repeats every tick while the player pushes the border. */
    @Override
    public Duration getDefaultTriggerInterval() {
        return DenyFlag.MOVE_TRIGGER_INTERVAL;
    }

    @Override
    public Decision decide(ActionContext context, FlagDefinition<Gate> value, FlagSettings settings) {
        FPlayer player = context.getPlayer();
        if (player == null || !applies(context)
                || settings instanceof DenySettings && !((DenySettings) settings).covers(context.getActor())) {
            return Decision.PASS;
        }
        return decided.computeIfAbsent(context, asked -> pass((MoveContext) asked, player, value.get(),
                settings instanceof EntryGateSettings ? (EntryGateSettings) settings : getSettings()));
    }

    private Decision pass(MoveContext context, FPlayer player, Gate gate, EntryGateSettings settings) {
        Region region = context.getRegion();
        ExecutionContext ctx = ExecutionContext.builder(Caller.of(getOwner(), "region/" + region.getId() + "#" + NAME))
                .player(player)
                .action(context)
                .attribute(EffectsFlag.REGION_ATTRIBUTE, region)
                .build();
        Cooldown window = settings.getCharge() == EntryGateSettings.Charge.PER_WINDOW ? windowCounter(ctx, region, settings) : null;
        boolean paid = settings.getCharge() == EntryGateSettings.Charge.ONCE_PER_SESSION
                ? RegionLookup.get().getSession(player).getPaidGates().contains(region.getId())
                : window != null && !window.canUse();
        GateResult result = paid ? Gate.of(gate.getConditions(), Collections.<Cost>emptyList()).check(ctx) : gate.pay(ctx);
        if (!result.isPassed()) {
            lastDenied.put(player.getUniqueId(), result);
            return Decision.DENY;
        }
        if (!paid && settings.getCharge() == EntryGateSettings.Charge.ONCE_PER_SESSION) {
            RegionLookup.get().getSession(player).getPaidGates().add(region.getId());
        } else if (!paid && window != null) {
            window.tryUse();
        }
        return Decision.PASS;
    }

    private static Cooldown windowCounter(ExecutionContext ctx, Region region, EntryGateSettings settings) {
        if (settings.getWindow() == null) {
            throw new IllegalStateException("The entry-gate of " + region.getId() + " charges PER_WINDOW but names no window: set window,"
                    + " e.g. 'daily at 00:00', in its settings, or charge ONCE_PER_SESSION.");
        }
        RepeatPolicy policy = RepeatPolicy.once().window(Window.parse(settings.getWindow(), Window.getServerZone()));
        return Cooldowns.resolve(ctx.getCaller(), ProgressOwner.player(ctx.getPlayer().getUniqueId()),
                CoreFlags.OWNER + ":" + NAME + ":" + region.getId(), policy);
    }

    /** Tells the player sent back the region's {@code deny-message}, if any, then each reason the gate gave, a line each. */
    @Override
    public void onApplied(ActionContext context, FlagDefinition<Gate> value, FlagSettings settings) {
        FPlayer player = context.getPlayer();
        GateResult result = player == null ? null : lastDenied.remove(player.getUniqueId());
        if (result == null) {
            return;
        }
        String message = RegionLookup.get().resolve(CoreFlags.DENY_MESSAGE, ((MoveContext) context).getRegion(), player);
        if (message != null) {
            player.sendMessage(message);
        }
        for (ConditionResult failure : result.getFailures()) {
            if (failure.getWhy() != null) {
                player.sendMessage(failure.getWhy());
            }
        }
    }
}
