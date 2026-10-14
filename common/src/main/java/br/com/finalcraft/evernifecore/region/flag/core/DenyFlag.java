package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.actor.ECEntityType;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.DamageContext;
import br.com.finalcraft.evernifecore.region.flag.context.MoveContext;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.Collections;

/**
 * The shape of a deny flag: default FALSE, TRUE wins a tie, TRUE denies the actions {@link #applies} accepts and done
 * by an actor its {@link DenySettings} cover. A subclass narrows {@code applies} to the actions it is about and
 * {@link #covers} to what its own settings say, and keeps the decision.
 */
public abstract class DenyFlag extends TristateFlag {

    /** The trigger interval the movement denials start with: a denied crossing repeats every tick while the player pushes the border. */
    protected static final Duration MOVE_TRIGGER_INTERVAL = Duration.ofMillis(250);

    public DenyFlag(FlagKey key, EventSource source) {
        this(key, source, null);
    }

    /** @param parent the deny flag this one refines; null for none */
    public DenyFlag(FlagKey key, EventSource source, @Nullable DenyFlag parent) {
        super(key, Collections.singleton(source), Tristate.FALSE, Tristate.TRUE, parent);
    }

    /** TRUE denies, for the actions {@link #applies} accepts. */
    @Override
    public final Decision decide(ActionContext context, Tristate value) {
        return value == Tristate.TRUE && applies(context) ? Decision.DENY : Decision.PASS;
    }

    /**
     * TRUE denies, when the actor is one the {@link DenySettings} cover and {@link #covers} accepts the rest - the actor
     * filter runs here, for every deny flag, before the subclass's own rule.
     */
    @Override
    public final Decision decide(ActionContext context, Tristate value, FlagSettings settings) {
        if (decide(context, value) != Decision.DENY) {
            return Decision.PASS;
        }
        if (settings instanceof DenySettings && !((DenySettings) settings).covers(context.getActor())) {
            return Decision.PASS;
        }
        return covers(context, settings) ? Decision.DENY : Decision.PASS;
    }

    /** Whether {@code settings}, past the actor filter, cover {@code context}; the default covers everything. */
    protected boolean covers(ActionContext context, FlagSettings settings) {
        return true;
    }

    @Override
    public Class<? extends FlagSettings> getSettingsType() {
        return DenySettings.class;
    }

    /** Runs {@link #onApplied(ActionContext, Tristate)} only after a denial - a value the settings let through is no denial. */
    @Override
    public void onApplied(ActionContext context, Tristate value, FlagSettings settings) {
        if (decide(context, value, settings) == Decision.DENY) {
            onApplied(context, value);
        }
    }

    /**
     * Tells the denied player the {@code deny-message} of the region crossed, or of the action's point for any other
     * action, when there is one - nobody is told when no player is behind the actor (a creeper, fire). Override and
     * call super to add an effect, or skip super to stay silent.
     */
    @Override
    public void onApplied(ActionContext context, Tristate value) {
        FPlayer player = context.getPlayer();
        if (player == null) {
            return;
        }
        WorldLocPos at = context.getPosition();
        String message = context instanceof MoveContext
                ? RegionLookup.get().resolve(CoreFlags.DENY_MESSAGE, ((MoveContext) context).getRegion(), player)
                : RegionLookup.get().resolve(CoreFlags.DENY_MESSAGE, context.getWorld(), at.getX(), at.getY(), at.getZ(), player);
        if (message != null) {
            player.sendMessage(message);
        }
    }

    /** A crossing of the region this flag's value comes from, in {@code transition}, optionally only by {@code cause}. */
    protected static boolean crossing(ActionContext context, MoveContext.Transition transition, @Nullable MoveCause cause) {
        if (!(context instanceof MoveContext)) {
            return false;
        }
        MoveContext move = (MoveContext) context;
        return move.getTransition() == transition && (cause == null || move.getCause() == cause);
    }

    /** Damage to a victim of one of {@code types}. */
    protected static boolean hurts(ActionContext context, ECEntityType... types) {
        if (!(context instanceof DamageContext)) {
            return false;
        }
        ECEntityType victim = ((DamageContext) context).getVictim().getEntityType();
        for (ECEntityType type : types) {
            if (victim == type) {
                return true;
            }
        }
        return false;
    }
}
