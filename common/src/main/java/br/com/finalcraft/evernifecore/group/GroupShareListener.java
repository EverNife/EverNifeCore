package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.action.ActionSupport;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.event.XpGainActionEvent;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.economy.BalanceChangeEvent;
import br.com.finalcraft.evernifecore.economy.Currencies;
import br.com.finalcraft.evernifecore.economy.Currency;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.flag.core.GroupFlag;
import br.com.finalcraft.evernifecore.flag.core.ShareSettings;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one listener of {@code share-xp} and {@code share-money}: each half is on the bus only while some group shares
 * that gain, so a server without such a group listens to nothing (and holds no XP source). A member's gain gives every
 * OTHER online member in range of a sharing group a copy of {@code gain * fraction} - the gainer keeps it all - under
 * the {@link Caller} of the flag, which this listener never shares again.
 */
final class GroupShareListener {

    /** The experience currency the core registers on platforms that have experience. */
    static final String XP_CURRENCY = "evernifecore:exp-points";

    private static final Set<Group> XP = ConcurrentHashMap.newKeySet();
    private static final Set<Group> MONEY = ConcurrentHashMap.newKeySet();
    private static @Nullable ECEventSubscription<XpGainActionEvent.Post> xpSubscription;
    private static @Nullable ECEventSubscription<BalanceChangeEvent> moneySubscription;
    private static volatile boolean warnedNoXp;

    private GroupShareListener() {
    }

    /** Re-reads whether {@code group} shares either gain now. */
    static void track(Group group) {
        update(group, !group.isDissolved() && group.getRegion() == null);
    }

    /** {@code group} shares nothing any more: its manager closed. */
    static void forget(Group group) {
        update(group, false);
    }

    private static synchronized void update(Group group, boolean alive) {
        boolean xp = alive && sharing(group, CoreFlags.SHARE_XP);
        if (xp ? XP.add(group) : XP.remove(group)) {
            if (xp && !warnedNoXp && !ActionSupport.isServed(EventSource.XP_GAIN)) {
                warnedNoXp = true;
                EverNifeCore.getLog().warning("share-xp is set on group " + group.getId() + ", but there is no XP source on this platform ("
                        + ActionSupport.getPlatform() + "): the flag has no effect here.");
            }
        }
        if (alive && sharing(group, CoreFlags.SHARE_MONEY)) {
            MONEY.add(group);
        } else {
            MONEY.remove(group);
        }
        if (!XP.isEmpty() && xpSubscription == null) {
            xpSubscription = ECEventBus.global().subscribe(XpGainActionEvent.Post.class, GroupShareListener::onXp);
        } else if (XP.isEmpty() && xpSubscription != null) {
            xpSubscription.unsubscribe();
            xpSubscription = null;
        }
        if (!MONEY.isEmpty() && moneySubscription == null) {
            moneySubscription = ECEventBus.global().subscribe(BalanceChangeEvent.class, GroupShareListener::onMoney);
        } else if (MONEY.isEmpty() && moneySubscription != null) {
            moneySubscription.unsubscribe();
            moneySubscription = null;
        }
    }

    private static boolean sharing(Group group, GroupFlag<Double> flag) {
        Double fraction = group.getEffectiveFlag(flag);
        return fraction != null && fraction > 0;
    }

    /** {@code evernifecore:group/share-xp} or {@code evernifecore:group/share-money}. */
    static Caller callerOf(GroupFlag<Double> flag) {
        return Caller.of(flag.getOwner(), "group/" + flag.getKey().getName());
    }

    private static void onXp(XpGainActionEvent.Post event) {
        FPlayer earner = event.getContext().getPlayer();
        Currency xp = Currencies.find(XP_CURRENCY).orElse(null);
        if (earner != null && xp != null) {
            share(earner, XP, CoreFlags.SHARE_XP, BigDecimal.valueOf(event.getContext().getAmount()), xp, 0);
        }
    }

    private static void onMoney(BalanceChangeEvent event) {
        UUID uuid = event.getOwner().getUuid();
        if (callerOf(CoreFlags.SHARE_MONEY).equals(event.getCaller()) || uuid == null || event.getDelta().signum() <= 0
                || !event.getCurrency().getId().equals(Currencies.money().getId())) {
            return;
        }
        FPlayer earner = EverNifeCore.getPlatform().getPlayer(uuid);
        if (earner != null && earner.isOnline()) {
            share(earner, MONEY, CoreFlags.SHARE_MONEY, event.getDelta(), event.getCurrency(), 2);
        }
    }

    /** Gives every other online member in range the best fraction among the sharing groups they have with the earner. */
    private static void share(FPlayer earner, Set<Group> sharing, GroupFlag<Double> flag, BigDecimal gain, Currency currency, int scale) {
        ShareSettings settings = flag.getSettings();
        Map<UUID, Double> fractions = new LinkedHashMap<>();
        for (Group group : new ArrayList<>(sharing)) {
            Double fraction = group.getEffectiveFlag(flag);
            if (fraction == null || fraction <= 0 || !group.isMember(earner.getUniqueId())) {
                continue;
            }
            for (FPlayer member : group.getOnlineMembers()) {
                if (!member.getUniqueId().equals(earner.getUniqueId()) && inRange(earner, member, settings)) {
                    fractions.merge(member.getUniqueId(), fraction, Math::max);
                }
            }
        }
        Caller caller = callerOf(flag);
        for (Map.Entry<UUID, Double> entry : fractions.entrySet()) {
            BigDecimal copy = gain.multiply(BigDecimal.valueOf(entry.getValue())).setScale(scale, RoundingMode.DOWN);
            if (copy.signum() > 0) {
                currency.give(CurrencyOwner.player(entry.getKey()), copy, caller);
            }
        }
    }

    private static boolean inRange(FPlayer earner, FPlayer member, ShareSettings settings) {
        if (settings.getRadius() <= 0 && !settings.isSameWorld()) {
            return true;
        }
        FLocation a = earner.getLocation();
        FLocation b = member.getLocation();
        if (a == null || b == null || !a.getWorldName().equals(b.getWorldName())) {
            return false;
        }
        if (settings.getRadius() <= 0) {
            return true;
        }
        WorldLocPos p = a.getWorldLocPos();
        WorldLocPos q = b.getWorldLocPos();
        double dx = p.getX() - q.getX();
        double dy = p.getY() - q.getY();
        double dz = p.getZ() - q.getZ();
        return dx * dx + dy * dy + dz * dz <= settings.getRadius() * settings.getRadius();
    }
}
