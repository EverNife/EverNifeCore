package br.com.finalcraft.evernifecore.hytale.integration;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;
import br.com.finalcraft.evernifecore.economy.LazyEconomyProvider;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import net.cfh.vault.VaultUnlockedServicesManager;
import net.milkbowl.vault2.economy.Economy;

import java.util.Optional;

/**
 * Finds the Hytale economy and puts it behind the platform-agnostic contract.
 *
 * <p>VaultUnlocked is an optional dependency of this plugin ({@code manifest.json}), so its classes may
 * simply not be there - which is why the lookup is guarded by a classpath probe.</p>
 */
public class HyVaultIntegration {

    private static final HyLazyEconomy ECONOMY = new HyLazyEconomy();

    /** Registers the economy provider, from the constructor, next to the other providers. */
    public static void register() {
        EverNifeCore.getProviders().getBaseProvider().register(IEconomyProvider.class, ECONOMY);
    }

    /**
     * @return the economy that is up right now, or null when there is none. Asked again on every call
     * while it stays null, because an economy plugin may register after EverNifeCore.
     */
    static IEconomyProvider detect() {
        if (!isVaultUnlockedPresent()) {
            return null;
        }

        Optional<Economy> economy = VaultUnlockedServicesManager.get().economy();
        return economy.<IEconomyProvider>map(HyVaultEconomy::new).orElse(null);
    }

    private static boolean isVaultUnlockedPresent() {
        return FCReflectionUtil.getClasses().isClassLoaded("net.cfh.vault.VaultUnlocked");
    }

    static class HyLazyEconomy extends LazyEconomyProvider {

        @Override
        protected IEconomyProvider resolve() {
            return detect();
        }

        //Two different problems with two different fixes: either VaultUnlocked itself is missing, or it
        //is there and no economy plugin registered a service behind it.
        @Override
        protected void logMissingEconomy() {
            if (!isVaultUnlockedPresent()) {
                EverNifeCore.getLog().warning("VaultUnlocked was not found! EverNifeCore needs it to manage economy transactions.");
                EverNifeCore.getLog().warning("It is declared as an optional dependency (TheNewEconomy:VaultUnlocked) - install it to enable economy.");
                return;
            }

            EverNifeCore.getLog().warning("VaultUnlocked is present but no Economy plugin registered an economy service!");
        }
    }

}
