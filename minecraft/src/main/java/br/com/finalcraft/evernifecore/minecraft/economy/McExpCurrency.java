package br.com.finalcraft.evernifecore.minecraft.economy;

import br.com.finalcraft.evernifecore.economy.AbstractCurrency;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.everylibs.util.numberwrapper.NumericSpec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An online player's experience as a currency: whole levels ({@code evernifecore:exp-levels}, alias {@code levels})
 * or points ({@code evernifecore:exp-points}, alias {@code xp}). Players only; an offline player reads zero and every
 * move answers {@link EcoResponse.Reason#PROVIDER_ERROR}. Amounts are whole: a fraction is cut.
 *
 * <p>Thread: the main thread - it reads and writes the live player.</p>
 */
public final class McExpCurrency extends AbstractCurrency {

    private static final NumericSpec WHOLE = NumericSpec.of(0, RoundingMode.DOWN).withFloor(BigDecimal.ZERO);

    public static final McExpCurrency LEVELS = new McExpCurrency("evernifecore:exp-levels", "levels", true);
    public static final McExpCurrency POINTS = new McExpCurrency("evernifecore:exp-points", "exp", false);

    private final boolean levels;

    private McExpCurrency(String id, String displayName, boolean levels) {
        super(id, FancyText.of(displayName), WHOLE);
        this.levels = levels;
    }

    @Override
    public boolean supports(CurrencyOwner.Kind kind) {
        return kind == CurrencyOwner.Kind.PLAYER;
    }

    @Override
    public BigDecimal getBalance(CurrencyOwner owner) {
        Player player = online(owner);
        return player == null ? BigDecimal.ZERO : BigDecimal.valueOf(balanceOf(player));
    }

    @Override
    protected EcoResponse withdraw(CurrencyOwner owner, BigDecimal amount) {
        Player player = online(owner);
        if (player == null) {
            return offline(owner, amount);
        }
        int current = balanceOf(player);
        int take = whole(amount);
        if (current < take) {
            return EcoResponse.insufficientFunds(amount, BigDecimal.valueOf(current));
        }
        return store(player, amount, current - take);
    }

    @Override
    protected EcoResponse deposit(CurrencyOwner owner, BigDecimal amount) {
        Player player = online(owner);
        if (player == null) {
            return offline(owner, amount);
        }
        return store(player, amount, balanceOf(player) + whole(amount));
    }

    @Override
    protected EcoResponse overwrite(CurrencyOwner owner, BigDecimal amount) {
        Player player = online(owner);
        if (player == null) {
            return offline(owner, amount);
        }
        return store(player, amount, whole(amount));
    }

    @Override
    public FancyText format(BigDecimal amount) {
        String number = WHOLE.round(amount).toPlainString();
        return FancyText.of(number + (levels ? (amount.compareTo(BigDecimal.ONE) == 0 ? " level" : " levels") : " exp"));
    }

    private static Player online(CurrencyOwner owner) {
        return owner.getKind() == CurrencyOwner.Kind.PLAYER ? Bukkit.getPlayer(owner.getUuid()) : null;
    }

    private static EcoResponse offline(CurrencyOwner owner, BigDecimal amount) {
        return EcoResponse.providerError(amount, BigDecimal.ZERO, owner + " is offline: experience is read and changed on an online"
                + " player only.");
    }

    private static int whole(BigDecimal amount) {
        return WHOLE.round(amount).intValue();
    }

    private int balanceOf(Player player) {
        return levels ? player.getLevel() : totalPoints(player);
    }

    private EcoResponse store(Player player, BigDecimal amount, int balance) {
        if (levels) {
            player.setLevel(balance);
        } else {
            //setTotalExperience alone moves neither the level nor the bar: start from nothing and give the points
            player.setExp(0F);
            player.setLevel(0);
            player.setTotalExperience(0);
            player.giveExp(balance);
        }
        return EcoResponse.success(amount, BigDecimal.valueOf(balanceOf(player)));
    }

    //getTotalExperience drifts from the level once anything sets the level directly: read the points off the curve
    private static int totalPoints(Player player) {
        int level = player.getLevel();
        long points = 0;
        for (int each = 0; each < level; each++) {
            points += pointsToNext(each);
        }
        points += Math.round(player.getExp() * pointsToNext(level));
        return (int) Math.min(Integer.MAX_VALUE, points);
    }

    /** The server's own curve: 1.8 changed it, so a total read here matches what {@code giveExp} counts. */
    private static int pointsToNext(int level) {
        if (MCVersion.isLowerEquals(MCVersion.v1_7_10)) {
            return level >= 30 ? 62 + (level - 30) * 7 : level >= 15 ? 17 + (level - 15) * 3 : 17;
        }
        return level >= 30 ? 9 * level - 158 : level >= 15 ? 5 * level - 38 : 2 * level + 7;
    }
}
