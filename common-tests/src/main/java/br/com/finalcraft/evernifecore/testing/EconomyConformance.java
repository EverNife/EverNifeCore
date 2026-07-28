package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What every {@link IEconomyProvider} has to hold, whoever implements it - the double in this library,
 * the Vault bridges, an economy plugin that registers its own.
 *
 * <p>It reports instead of throwing, so the same checks run inside a JUnit test and against a live
 * server, where a thrown exception would be the wrong answer.</p>
 *
 * <p>The checks are split by what they cost. {@link #check(IEconomyProvider)} only reads, and is safe to
 * point at a production economy. {@link #checkMutating(IEconomyProvider, UUID)} moves money, so it
 * demands the account to move it in: against a real economy that account is a real balance, and picking
 * it is the caller's decision, not this class's.</p>
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
     * @return one line per violation, empty when the provider conforms.
     */
    public static List<String> check(IEconomyProvider provider) {
        List<String> failures = new ArrayList<String>();
        UUID unknownAccount = UUID.randomUUID();

        try {
            BigDecimal balance = provider.getBalance(unknownAccount);
            if (balance == null) {
                failures.add("getBalance returned null for an unknown account; zero is the way to say 'no money here'");
            }
        } catch (Throwable e) {
            failures.add("getBalance threw for an unknown account: " + e);
        }

        try {
            if (!provider.hasEnough(unknownAccount, BigDecimal.ZERO)) {
                failures.add("hasEnough(0) was false; asking for nothing is always affordable");
            }
            if (!provider.hasEnough(unknownAccount, NEGATIVE)) {
                failures.add("hasEnough(negative) was false; it is treated as asking for nothing");
            }
        } catch (Throwable e) {
            failures.add("hasEnough threw for a zero/negative amount: " + e);
        }

        try {
            if (provider.format(ONE_HUNDRED) == null) {
                failures.add("format returned null; a message has to be able to print an amount");
            }
        } catch (Throwable e) {
            failures.add("format threw: " + e);
        }

        return failures;
    }

    /**
     * The half that moves money, on the account the caller names. The balance that account had is put
     * back at the end, but a provider that fails these checks may well leave it somewhere else - do not
     * point this at a player who is playing.
     *
     * @return one line per violation, empty when the provider conforms.
     */
    public static List<String> checkMutating(IEconomyProvider provider, UUID scratchAccount) {
        List<String> failures = new ArrayList<String>(check(provider));

        BigDecimal originalBalance;
        try {
            originalBalance = provider.getBalance(scratchAccount);
        } catch (Throwable e) {
            failures.add("getBalance threw for the scratch account, so nothing else can be checked: " + e);
            return failures;
        }

        try {
            expectSuccess(provider.set(scratchAccount, ONE_HUNDRED), "set to 100", failures);
            expectBalance(provider, scratchAccount, ONE_HUNDRED, "after set to 100", failures);

            //The one that used to mean something different on every economy.
            expectSuccess(provider.set(scratchAccount, ONE_HUNDRED), "set to the balance the account already has", failures);
            expectBalance(provider, scratchAccount, ONE_HUNDRED, "after the no-op set", failures);

            expectSuccess(provider.give(scratchAccount, BigDecimal.ZERO), "give of zero", failures);
            expectSuccess(provider.take(scratchAccount, BigDecimal.ZERO), "take of zero", failures);
            expectBalance(provider, scratchAccount, ONE_HUNDRED, "after the zero-amount calls", failures);

            expectSuccess(provider.give(scratchAccount, FIFTY), "give of 50", failures);
            expectBalance(provider, scratchAccount, new BigDecimal("150"), "after give of 50", failures);

            expectReason(provider.take(scratchAccount, new BigDecimal("1000")), EcoResponse.Reason.INSUFFICIENT_FUNDS,
                    "take of more than the balance", failures);
            expectBalance(provider, scratchAccount, new BigDecimal("150"), "after a refused take", failures);

            expectSuccess(provider.take(scratchAccount, FIFTY), "take of 50", failures);
            expectBalance(provider, scratchAccount, ONE_HUNDRED, "after take of 50", failures);

            expectReason(provider.give(scratchAccount, NEGATIVE), EcoResponse.Reason.INVALID_AMOUNT, "give of a negative amount", failures);
            expectReason(provider.take(scratchAccount, NEGATIVE), EcoResponse.Reason.INVALID_AMOUNT, "take of a negative amount", failures);
            expectReason(provider.set(scratchAccount, NEGATIVE), EcoResponse.Reason.INVALID_AMOUNT, "set to a negative amount", failures);
            expectBalance(provider, scratchAccount, ONE_HUNDRED, "after the negative-amount calls", failures);

            if (!provider.hasEnough(scratchAccount, ONE_HUNDRED)) {
                failures.add("hasEnough was false for exactly the balance the account holds");
            }
            if (provider.hasEnough(scratchAccount, new BigDecimal("1000"))) {
                failures.add("hasEnough was true for more than the account holds");
            }
        } catch (Throwable e) {
            failures.add("a mutating check threw: " + e);
        } finally {
            try {
                provider.set(scratchAccount, originalBalance);
            } catch (Throwable e) {
                failures.add("could not put the original balance back on the scratch account: " + e);
            }
        }

        return failures;
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

    private static void expectBalance(IEconomyProvider provider, UUID account, BigDecimal expected, String when, List<String> failures) {
        BigDecimal balance = provider.getBalance(account);
        if (balance == null || balance.compareTo(expected) != 0) {
            failures.add("balance was " + balance + " " + when + ", expected " + expected);
        }
    }

}
