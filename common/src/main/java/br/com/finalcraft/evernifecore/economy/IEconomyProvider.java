package br.com.finalcraft.evernifecore.economy;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The economy contract every platform implements, and the only one the rest of EverNifeCore knows.
 *
 * <p>BigDecimal-only and UUID-keyed on purpose: the {@code double} overloads live on
 * {@link br.com.finalcraft.evernifecore.util.FCEcoUtil} and convert exactly once, so no implementation
 * repeats the conversion and no comparison is ever made on floating point.</p>
 *
 * <p>Single currency by design. A plugin that needs several has to talk to its economy plugin
 * directly - {@link #getHandle()} is the way out.</p>
 *
 * <p>Synchronous: call it from the main thread. Thread-safety is whatever the underlying economy
 * plugin offers.</p>
 *
 * <p>Semantics every implementation owes, whichever economy sits below it:</p>
 * <ul>
 *   <li>a negative amount is {@link EcoResponse.Reason#INVALID_AMOUNT} and never reaches the provider;</li>
 *   <li>{@code give}/{@code take} of zero succeed as a no-op;</li>
 *   <li>{@code set} to the balance the account already has succeeds - reaching the target is not a failure;</li>
 *   <li>{@code take} never leaves a negative balance: without funds it is
 *       {@link EcoResponse.Reason#INSUFFICIENT_FUNDS} and nothing moves;</li>
 *   <li>{@code hasEnough} of zero or less is {@code true};</li>
 *   <li>a transaction the provider refuses is {@link EcoResponse.Reason#PROVIDER_ERROR} carrying its text,
 *       never a silent success;</li>
 *   <li>{@link #getBalance(UUID)} never returns null.</li>
 * </ul>
 */
public interface IEconomyProvider {

    /** What the vault2 API stamps on every transaction as its author. */
    public static final String TRANSACTION_SOURCE = "EverNifeCore";

    /** A resolved provider is available by definition; only {@link LazyEconomyProvider} answers otherwise. */
    public default boolean isAvailable() {
        return true;
    }

    /** Boot hook: resolve now and complain once when there is nothing to resolve. A no-op once resolved. */
    public default void warmUp() {
    }

    public BigDecimal getBalance(UUID playerUUID);

    public boolean hasEnough(UUID playerUUID, BigDecimal amount);

    public EcoResponse give(UUID playerUUID, BigDecimal amount);

    public EcoResponse take(UUID playerUUID, BigDecimal amount);

    public EcoResponse set(UUID playerUUID, BigDecimal amount);

    /** The provider's own rendering of an amount ({@code "$1,234.50"}); the default is the plain number. */
    public default String format(BigDecimal amount) {
        return amount.toPlainString();
    }

    /**
     * The underlying economy object - Vault's {@code Economy}, vault2's {@code Economy} - for what this
     * contract deliberately does not cover (multi-currency, banks, provider-specific extensions).
     */
    public Object getHandle();

}
