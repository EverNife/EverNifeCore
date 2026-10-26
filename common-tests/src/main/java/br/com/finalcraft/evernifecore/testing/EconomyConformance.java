package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.economy.BalanceChangeEvent;
import br.com.finalcraft.evernifecore.economy.Currency;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.logic.Caller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What every {@link Currency} has to hold, whoever implements it - {@code money} over any economy bridge, a plugin's
 * points, a clan vault, the double in this library.
 *
 * <p>It reports instead of throwing, so the same checks run inside a JUnit test and against a live server, where a
 * thrown exception would be the wrong answer.</p>
 *
 * <p>The checks are split by what they cost. {@link #check(Currency)} only reads, and is safe to point at a production
 * economy. {@link #checkMutating} moves money, so it demands the wallet to move it in: against a real economy that
 * wallet is a real balance, and picking it is the caller's decision, not this class's.</p>
 */
public final class EconomyConformance {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal FIFTY = new BigDecimal("50");
    private static final BigDecimal NEGATIVE = new BigDecimal("-1");

    private EconomyConformance() {
    }

    /**
     * The read-only half: nothing here moves money, so it is safe against a live economy.
     *
     * @return one line per violation, empty when the currency conforms.
     */
    public static List<String> check(Currency currency) {
        List<String> failures = new ArrayList<String>();
        CurrencyOwner unknown = CurrencyOwner.player(UUID.randomUUID());

        try {
            BigDecimal balance = currency.getBalance(unknown);
            if (balance == null) {
                failures.add("getBalance returned null for an unknown wallet; zero is the way to say 'no money here'");
            }
        } catch (Throwable e) {
            failures.add("getBalance threw for an unknown wallet: " + e);
        }

        try {
            if (!currency.has(unknown, BigDecimal.ZERO)) {
                failures.add("has(0) was false; asking for nothing is always affordable");
            }
            if (!currency.has(unknown, NEGATIVE)) {
                failures.add("has(negative) was false; it is treated as asking for nothing");
            }
        } catch (Throwable e) {
            failures.add("has threw for a zero/negative amount: " + e);
        }

        try {
            if (currency.format(ONE_HUNDRED) == null) {
                failures.add("format returned null; a message has to be able to print an amount");
            }
        } catch (Throwable e) {
            failures.add("format threw: " + e);
        }

        if (currency.isAvailable() && !currency.supports(CurrencyOwner.Kind.PLAYER) && !currency.supports(CurrencyOwner.Kind.GROUP)
                && !currency.supports(CurrencyOwner.Kind.GLOBAL)) {
            failures.add("the currency is available but keeps a wallet for no kind of owner");
        }
        return failures;
    }

    /**
     * The half that moves money, in {@code scratch}'s wallet, on {@code caller}. The balance that wallet had is put back
     * at the end, but a currency that fails these checks may well leave it somewhere else - do not point this at a
     * player who is playing. Each verb that moved money must post exactly one {@link BalanceChangeEvent}, and one that
     * did not, none.
     *
     * @return one line per violation, empty when the currency conforms.
     */
    public static List<String> checkMutating(Currency currency, CurrencyOwner scratch, Caller caller) {
        List<String> failures = new ArrayList<String>(check(currency));
        if (!currency.isAvailable()) {
            expectReason(currency.take(scratch, BigDecimal.ONE, caller), EcoResponse.Reason.UNAVAILABLE, "take on an unavailable currency", failures);
            return failures;
        }

        BigDecimal originalBalance;
        try {
            originalBalance = currency.getBalance(scratch);
        } catch (Throwable e) {
            failures.add("getBalance threw for the scratch wallet, so nothing else can be checked: " + e);
            return failures;
        }

        RecordingAudience audience = new RecordingAudience("economy-conformance-" + UUID.randomUUID());
        ECEventBus.global().addNativeAudience(audience);
        Events events = new Events(audience, currency, scratch, failures);
        try {
            for (CurrencyOwner.Kind kind : CurrencyOwner.Kind.values()) {
                if (!currency.supports(kind)) {
                    expectReason(currency.take(ownerOf(kind), BigDecimal.ONE, caller), EcoResponse.Reason.UNSUPPORTED_OWNER,
                            "take from a " + kind + " wallet the currency does not keep", failures);
                }
            }

            boolean moves = originalBalance.compareTo(ONE_HUNDRED) != 0;
            expectSuccess(currency.set(scratch, ONE_HUNDRED, caller), "set to 100", failures);
            events.expect(moves ? 1 : 0, "set to 100");
            expectBalance(currency, scratch, ONE_HUNDRED, "after set to 100", failures);

            //The one that used to mean something different on every economy.
            expectSuccess(currency.set(scratch, ONE_HUNDRED, caller), "set to the balance the wallet already has", failures);
            events.expect(0, "set to the balance the wallet already has");

            expectSuccess(currency.give(scratch, BigDecimal.ZERO, caller), "give of zero", failures);
            expectSuccess(currency.take(scratch, BigDecimal.ZERO, caller), "take of zero", failures);
            events.expect(0, "the zero-amount calls");
            expectBalance(currency, scratch, ONE_HUNDRED, "after the zero-amount calls", failures);

            expectSuccess(currency.give(scratch, FIFTY, caller), "give of 50", failures);
            events.expect(1, "give of 50");
            expectBalance(currency, scratch, new BigDecimal("150"), "after give of 50", failures);

            expectReason(currency.take(scratch, new BigDecimal("1000"), caller), EcoResponse.Reason.INSUFFICIENT_FUNDS,
                    "take of more than the balance", failures);
            events.expect(0, "a refused take");
            expectBalance(currency, scratch, new BigDecimal("150"), "after a refused take", failures);

            expectSuccess(currency.take(scratch, FIFTY, caller), "take of 50", failures);
            events.expect(1, "take of 50");
            expectBalance(currency, scratch, ONE_HUNDRED, "after take of 50", failures);

            expectReason(currency.give(scratch, NEGATIVE, caller), EcoResponse.Reason.INVALID_AMOUNT, "give of a negative amount", failures);
            expectReason(currency.take(scratch, NEGATIVE, caller), EcoResponse.Reason.INVALID_AMOUNT, "take of a negative amount", failures);
            expectReason(currency.set(scratch, NEGATIVE, caller), EcoResponse.Reason.INVALID_AMOUNT, "set to a negative amount", failures);
            events.expect(0, "the negative-amount calls");
            expectBalance(currency, scratch, ONE_HUNDRED, "after the negative-amount calls", failures);

            if (!currency.has(scratch, ONE_HUNDRED)) {
                failures.add("has was false for exactly the balance the wallet holds");
            }
            if (currency.has(scratch, new BigDecimal("1000"))) {
                failures.add("has was true for more than the wallet holds");
            }
        } catch (Throwable e) {
            failures.add("a mutating check threw: " + e);
        } finally {
            ECEventBus.global().removeNativeAudience(audience.getName());
            try {
                currency.set(scratch, originalBalance, caller);
            } catch (Throwable e) {
                failures.add("could not put the original balance back on the scratch wallet: " + e);
            }
        }

        return failures;
    }

    private static CurrencyOwner ownerOf(CurrencyOwner.Kind kind) {
        switch (kind) {
            case PLAYER:
                return CurrencyOwner.player(UUID.randomUUID());
            case GROUP:
                return CurrencyOwner.group("conformance:scratch");
            default:
                return CurrencyOwner.global();
        }
    }

    /** The balance changes of one wallet, counted since the last {@link #expect}. */
    private static final class Events {
        private final RecordingAudience audience;
        private final Currency currency;
        private final CurrencyOwner owner;
        private final List<String> failures;
        private int seen;

        Events(RecordingAudience audience, Currency currency, CurrencyOwner owner, List<String> failures) {
            this.audience = audience;
            this.currency = currency;
            this.owner = owner;
            this.failures = failures;
        }

        void expect(int expected, String what) {
            int now = 0;
            for (BalanceChangeEvent event : audience.getDispatchedOf(BalanceChangeEvent.class)) {
                if (event.getCurrency() == currency && event.getOwner().equals(owner)) {
                    now++;
                }
            }
            if (now - seen != expected) {
                failures.add(what + " posted " + (now - seen) + " BalanceChangeEvent(s), expected " + expected);
            }
            seen = now;
        }
    }

    private static void expectSuccess(EcoResponse response, String what, List<String> failures) {
        expectReason(response, EcoResponse.Reason.SUCCESS, what, failures);
    }

    private static void expectReason(EcoResponse response, EcoResponse.Reason expected, String what, List<String> failures) {
        if (response == null) {
            failures.add(what + " returned null instead of an EcoResponse");
            return;
        }
        if (response.getReason() != expected) {
            failures.add(what + " answered " + response.getReason() + ", expected " + expected + " (" + response + ")");
        }
    }

    private static void expectBalance(Currency currency, CurrencyOwner owner, BigDecimal expected, String when, List<String> failures) {
        BigDecimal balance = currency.getBalance(owner);
        if (balance == null || balance.compareTo(expected) != 0) {
            failures.add("balance was " + balance + " " + when + ", expected " + expected);
        }
    }

}
