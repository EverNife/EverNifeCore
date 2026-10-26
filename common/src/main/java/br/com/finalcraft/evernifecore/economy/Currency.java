package br.com.finalcraft.evernifecore.economy;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.MutableVariable;
import br.com.finalcraft.everylibs.util.numberwrapper.NumericSpec;

import java.math.BigDecimal;

/**
 * Something a wallet holds and a plugin moves: money, a plugin's points, a clan vault, experience. Found by id through
 * {@link Currencies}; as a variable it reads and sets the balance of the context's owner. Every implementation extends
 * {@link AbstractCurrency}, which owns the rules every verb keeps.
 *
 * <p>Thread: the verbs run on the caller's thread and never block on I/O; an implementation that needs a specific
 * thread (a live player's experience) says so on its class.</p>
 */
public interface Currency extends MutableVariable<BigDecimal> {

    /** {@code owner:name}, e.g. {@code evernifecore:money}. */
    @Override
    String getId();

    FancyText getDisplayName();

    /** The scale and bounds the amounts of this currency follow. */
    NumericSpec getSpec();

    /** False while nothing can move - {@code money} with no economy plugin behind it. */
    boolean isAvailable();

    /** Whether this currency keeps wallets for {@code kind}: {@code money} only for players, a clan vault for groups. */
    boolean supports(CurrencyOwner.Kind kind);

    /** Never null; zero for an owner it has no wallet for, and while it is not {@link #isAvailable() available}. */
    BigDecimal getBalance(CurrencyOwner owner);

    /** True for an amount of zero or less; a hint, since only {@link #take} decides. */
    boolean has(CurrencyOwner owner, BigDecimal amount);

    /** Checks and takes in one step: never leaves the wallet below what the currency allows. */
    EcoResponse take(CurrencyOwner owner, BigDecimal amount, Caller caller);

    EcoResponse give(CurrencyOwner owner, BigDecimal amount, Caller caller);

    EcoResponse set(CurrencyOwner owner, BigDecimal amount, Caller caller);

    /** Takes from {@code from} and gives to {@code to}; when the give fails the take is given back. */
    EcoResponse transfer(CurrencyOwner from, CurrencyOwner to, BigDecimal amount, Caller caller);

    FancyText format(BigDecimal amount);
}
