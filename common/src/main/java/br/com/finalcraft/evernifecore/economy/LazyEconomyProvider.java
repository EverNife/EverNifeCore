package br.com.finalcraft.evernifecore.economy;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An economy provider that only resolves the real one when someone actually moves money.
 *
 * <p>Economy plugins may enable after EverNifeCore, so a miss is not final: every call retries the
 * resolution. Once resolved it is never re-resolved - swapping the economy plugin at runtime needs a
 * restart.</p>
 *
 * <p>The diagnostic is logged once, from {@link #warmUp()}; after that the absence speaks through the
 * exception every call throws, which points at the plugin that asked for money instead of drowning the
 * console in EverNifeCore's own warning.</p>
 */
public abstract class LazyEconomyProvider implements IEconomyProvider {

    private volatile IEconomyProvider delegate;

    /** @return the economy that is up right now, or null while the platform has none. */
    protected abstract IEconomyProvider resolve();

    /** The platform's "there is no economy here, do X" block. Called at most once, from {@link #warmUp()}. */
    protected abstract void logMissingEconomy();

    /**
     * Runs on the first tick, once every plugin has enabled - so the complaint is never a boot-order
     * false alarm about an economy that simply registers later than EverNifeCore.
     */
    @Override
    public final void warmUp() {
        if (current() == null) {
            logMissingEconomy();
        }
    }

    // Two threads racing here resolve twice and one wrapper wins: both are stateless views over the
    // same underlying economy, so the race costs an allocation and nothing else.
    private IEconomyProvider current() {
        IEconomyProvider resolved = delegate;
        if (resolved == null) {
            resolved = resolve();
            delegate = resolved;
        }
        return resolved;
    }

    private IEconomyProvider require() {
        IEconomyProvider resolved = current();
        if (resolved == null) {
            throw new IllegalStateException("No economy available: install Vault and an economy plugin, "
                    + "or guard the call with FCEcoUtil.isEcoAvailable()");
        }
        return resolved;
    }

    @Override
    public boolean isAvailable() {
        return current() != null;
    }

    @Override
    public BigDecimal getBalance(UUID playerUUID) {
        return require().getBalance(playerUUID);
    }

    @Override
    public boolean hasEnough(UUID playerUUID, BigDecimal amount) {
        return require().hasEnough(playerUUID, amount);
    }

    @Override
    public EcoResponse give(UUID playerUUID, BigDecimal amount) {
        return require().give(playerUUID, amount);
    }

    @Override
    public EcoResponse take(UUID playerUUID, BigDecimal amount) {
        return require().take(playerUUID, amount);
    }

    @Override
    public EcoResponse set(UUID playerUUID, BigDecimal amount) {
        return require().set(playerUUID, amount);
    }

    @Override
    public String format(BigDecimal amount) {
        return require().format(amount);
    }

    @Override
    public Object getHandle() {
        return require().getHandle();
    }

}
