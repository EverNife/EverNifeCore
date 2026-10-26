package br.com.finalcraft.evernifecore.economy;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The bridge from an economy plugin to {@link Currencies#money()} - internal: plugins read and move money through the
 * {@code money} {@link Currency} (or {@code FCEcoUtil}, its one-liner facade), never through this interface. An economy
 * plugin still registers its implementation here ({@code EverNifeCore.getProviders()}), and the Vault, VaultUnlocked
 * and Hytale VaultUnlocked bridges are the ones the core ships.
 *
 * <p>BigDecimal-only and UUID-keyed. The rules every verb keeps (negative amounts refused, zero a no-op, an owner kind
 * other than a player refused) are {@link AbstractCurrency}'s: a bridge only ever sees a positive amount on
 * {@code give}/{@code take} and a non-negative one on {@code set}. What a bridge still owes:</p>
 * <ul>
 *   <li>{@code set} to the balance the account already has succeeds - reaching the target is not a failure;</li>
 *   <li>{@code take} checks and withdraws in one step and never leaves a negative balance: without funds it is
 *       {@link EcoResponse.Reason#INSUFFICIENT_FUNDS} and nothing moves;</li>
 *   <li>a transaction the economy refuses is {@link EcoResponse.Reason#PROVIDER_ERROR} carrying its text, never a
 *       silent success;</li>
 *   <li>{@link #getBalance(UUID)} never returns null.</li>
 * </ul>
 *
 * <p>Thread: whatever the economy plugin below offers - read and write from the main thread; off it, only read.</p>
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
