package br.com.finalcraft.evernifecore.minecraft.integration;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;
import br.com.finalcraft.evernifecore.economy.LazyEconomyProvider;
import br.com.finalcraft.evernifecore.minecraft.loader.EverNifeCoreBukkitPlugin;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Finds the Bukkit economy and puts it behind the platform-agnostic contract.
 *
 * <p>VaultUnlocked (vault2) is preferred and the classic Vault is the fallback; both APIs ship in the
 * same jar, so which one answers depends on what the server actually registered.</p>
 */
public class VaultIntegration {

    private static final McLazyEconomy ECONOMY = new McLazyEconomy();

    /**
     * Registers the economy provider. This runs from the instance initializer, next to the other
     * providers: registering costs nothing (the lookup only happens on first use), and a plugin that
     * loads BEFORE EverNifeCore - FinalEconomy being the known one - can already charge.
     */
    public static void register() {
        EverNifeCore.getProviders().getBaseProvider().register(IEconomyProvider.class, ECONOMY);
    }

    /**
     * @return the economy that is up right now, or null when there is none. Asked again on every call
     * while it stays null, because an economy plugin may enable after EverNifeCore.
     */
    static IEconomyProvider detect() {
        if (!isVaultPresent()) {
            return null;
        }

        if (FCReflectionUtil.getClasses().isClassLoaded("net.milkbowl.vault2.economy.Economy")) {
            Class economyV2Class = FCReflectionUtil.getClasses().getClass("net.milkbowl.vault2.economy.Economy");
            RegisteredServiceProvider<?> registration = getRegistration(economyV2Class);
            if (registration != null && registration.getProvider() != null) {
                return new VaultEconomyV2(registration.getProvider());
            }
        }

        if (FCReflectionUtil.getClasses().isClassLoaded("net.milkbowl.vault.economy.Economy")) {
            Class economyV1Class = FCReflectionUtil.getClasses().getClass("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> registration = getRegistration(economyV1Class);
            if (registration != null && registration.getProvider() != null) {
                return new VaultEconomyV1(registration.getProvider());
            }
        }

        return null;
    }

    private static RegisteredServiceProvider<?> getRegistration(Class serviceClass) {
        return EverNifeCoreBukkitPlugin.instance.getServer().getServicesManager().getRegistration(serviceClass);
    }

    private static boolean isVaultPresent() {
        return Bukkit.getPluginManager().isPluginEnabled("Vault")
                || Bukkit.getPluginManager().isPluginEnabled("VaultUnlocked");
    }

    static class McLazyEconomy extends LazyEconomyProvider {

        @Override
        protected IEconomyProvider resolve() {
            return detect();
        }

        //Two different problems with two different fixes, so they get two different messages: the admin
        //either has no Vault at all, or has Vault and nothing that registered an economy behind it.
        @Override
        protected void logMissingEconomy() {
            if (!isVaultPresent()) {
                EverNifeCore.getLog().warning("Vault plugin was not found! EverNifeCore needs Vault to manage economy transactions!");
                return;
            }

            EverNifeCore.getLog().warning("Vault is present but no Economy plugin registered an economy service!");

            if (Bukkit.getPluginManager().isPluginEnabled("CMI")) {
                EverNifeCore.getLog().warning("CMI was found, but it's economy module is not enabled i think, you might want to take a look at: https://www.spigotmc.org/resources/cmi.3742/");
                EverNifeCore.getLog().warning("Read their description to learn how to enable CMI Economy module.");
            }

            EverNifeCore.getLog().warning("If you need a simple economy, you might want to take a look at: https://www.spigotmc.org/resources/finaleconomy.97740/");
        }
    }

}
