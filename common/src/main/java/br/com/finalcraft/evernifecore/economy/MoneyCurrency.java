package br.com.finalcraft.evernifecore.economy;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.everylibs.util.numberwrapper.NumericSpec;

import java.math.BigDecimal;

/**
 * {@code evernifecore:money} (alias {@code vault}): the server's economy plugin, reached through the
 * {@link IEconomyProvider} bridge registered on {@code EverNifeCore.getProviders()}. Players only. The provider is read
 * on every call, never cached, so an economy plugin that registers late or replaces the bridge is picked up at once.
 *
 * <p>Thread: whatever the economy plugin below allows - read and write from the main thread; off it, only read.</p>
 */
public final class MoneyCurrency extends AbstractCurrency {

    public static final String ID = "evernifecore:money";

    static final MoneyCurrency INSTANCE = new MoneyCurrency();

    private MoneyCurrency() {
        super(ID, FancyText.of("money"), NumericSpec.MONEY);
    }

    private static IEconomyProvider provider() {
        return EverNifeCore.getProviders().getEconomyOrNull();
    }

    private static IEconomyProvider require() {
        IEconomyProvider provider = provider();
        if (provider == null || !provider.isAvailable()) {
            //the final verbs ask isAvailable() first; reaching this is a race with an economy plugin going away
            throw new IllegalStateException(INSTANCE.describeUnavailable());
        }
        return provider;
    }

    @Override
    public boolean isAvailable() {
        IEconomyProvider provider = provider();
        return provider != null && provider.isAvailable();
    }

    @Override
    public boolean supports(CurrencyOwner.Kind kind) {
        return kind == CurrencyOwner.Kind.PLAYER;
    }

    @Override
    protected String describeUnavailable() {
        if (provider() == null) {
            //nobody registered a bridge: a wiring bug of the platform module, not a server without an economy plugin
            return "No economy provider registered - this platform never wired one up.";
        }
        return "No economy on this server: install Vault and an economy plugin (FinalEconomy works), or name another currency.";
    }

    @Override
    protected String describeUnsupported(CurrencyOwner.Kind kind) {
        return "money only has wallets for players: a group or global balance is a Currency of the plugin that owns it"
                + " - register one with Currencies.register(owner, currency) and name it in the cost (currency: <id>).";
    }

    @Override
    public BigDecimal getBalance(CurrencyOwner owner) {
        if (owner.getKind() != CurrencyOwner.Kind.PLAYER || !isAvailable()) {
            return BigDecimal.ZERO;
        }
        return require().getBalance(owner.getUuid());
    }

    @Override
    public boolean has(CurrencyOwner owner, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return true;
        }
        return owner.getKind() == CurrencyOwner.Kind.PLAYER && isAvailable() && require().hasEnough(owner.getUuid(), amount);
    }

    @Override
    protected EcoResponse withdraw(CurrencyOwner owner, BigDecimal amount) {
        return require().take(owner.getUuid(), amount);
    }

    @Override
    protected EcoResponse deposit(CurrencyOwner owner, BigDecimal amount) {
        return require().give(owner.getUuid(), amount);
    }

    @Override
    protected EcoResponse overwrite(CurrencyOwner owner, BigDecimal amount) {
        return require().set(owner.getUuid(), amount);
    }

    /** The economy plugin's own rendering ({@code "$1,234.50"}); the plain amount while there is none. */
    @Override
    public FancyText format(BigDecimal amount) {
        return isAvailable() ? FancyText.of(require().format(amount)) : super.format(amount);
    }
}
