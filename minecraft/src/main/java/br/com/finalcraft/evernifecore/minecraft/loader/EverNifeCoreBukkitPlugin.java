package br.com.finalcraft.evernifecore.minecraft.loader;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.api.eventhandler.ECEventDispatcher;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.minecraft.commands.McCommandRegisterer;
import br.com.finalcraft.evernifecore.minecraft.config.McConfigManager;
import br.com.finalcraft.evernifecore.minecraft.dependencies.ECoreDependencies;
import br.com.finalcraft.evernifecore.minecraft.ecplugin.ECBukkitPlugin;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiListener;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiViews;
import br.com.finalcraft.evernifecore.minecraft.integration.VaultIntegration;
import br.com.finalcraft.evernifecore.minecraft.integration.WorldEditIntegration;
import br.com.finalcraft.evernifecore.minecraft.listeners.PlayerInteractListener;
import br.com.finalcraft.evernifecore.minecraft.listeners.PlayerLoginListener;
import br.com.finalcraft.evernifecore.minecraft.listeners.PluginListener;
import br.com.finalcraft.evernifecore.minecraft.loader.imp.McECEventDispatcher;
import br.com.finalcraft.evernifecore.minecraft.loader.imp.McECPluginExtractor;
import br.com.finalcraft.evernifecore.minecraft.loader.imp.McPlatform;
import br.com.finalcraft.evernifecore.minecraft.nbt.NBTSelfTest;
import br.com.finalcraft.evernifecore.minecraft.ontime.McDefaultOntimeManager;
import br.com.finalcraft.evernifecore.minecraft.util.FCMinecraftAdventureUtil;
import br.com.finalcraft.evernifecore.minecraft.util.FCTickUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import de.tr7zw.changeme.nbtapi.utils.MinecraftVersion;
import org.bukkit.Bukkit;

/**
 * Bukkit entry point. It dogfoods the shared {@link ECBukkitPlugin} bridge: the platform-agnostic
 * wiring runs in {@link #onECPluginEnable()} (delegating to {@link EverNifeCore}) and the Bukkit
 * extras in {@link #onECPluginEnablePost()}. The provider registration stays in the instance
 * initializer because other plugins may load EverNifeCore's classes before it and need the platform
 * providers in place before any bootstrap hook runs.
 */
@ECPlugin(
        spigotID = "97739",
        bstatsID = "13351"
)
public class EverNifeCoreBukkitPlugin extends ECBukkitPlugin {

    public static EverNifeCoreBukkitPlugin instance;
    static {
        //First thing to do when this class is loaded is to add REQUIRED dependencies, because there ara plugins that depends
        //on EverNifeCore and are loaded before it, for example, FinalEconomy
        ECoreDependencies.initialize();
        MinecraftVersion.disableBStats();
        MinecraftVersion.disableUpdateCheck();
    }

    {
        instance = this; //Attribute Instance at the exact moment that this class is instantiated

        //Register Providers as Early as Possible
        EverNifeCore.getProviders().getBaseProvider().register(
                IECPluginExtractor.class,
                new McECPluginExtractor()
        );

        EverNifeCore.getProviders().getBaseProvider().register(
                IPlatform.class,
                new McPlatform()
        );

        EverNifeCore.getProviders().getBaseProvider().register(
                ECEventDispatcher.class,
                new McECEventDispatcher()
        );

        //Economy resolves lazily, so registering it this early costs nothing and covers the plugins
        //that load before EverNifeCore and charge during their own enable.
        VaultIntegration.register();

        if (MCVersion.isHigherEquals(MCVersion.v1_19)){
            RegionGridOptions.setCurrent(RegionGridOptions.MINECRAFT);
        }

        McDefaultOntimeManager.initialize();

        EverNifeCore.instance.onLoaderInstantiate(ECPluginManager.getOrCreateECorePluginData(this));
    }

    @Override
    public void onECPluginEnable() {
        //Shared wiring both platforms run: config, cooldown and the platform-agnostic commands.
        EverNifeCore.instance.onLoadPre();
    }

    @Override
    public void onECPluginEnablePost() {
        ECPluginData ecPluginData = getPluginData();

        MinecraftVersion.replaceLogger(this.getLogger());//Replace [NBT-API] logger
        EverNifeCore.getLog().info("§aServer Minecraft Version " + MCVersion.getCurrent().name() + " !");

        logNBTSelfTest(NBTSelfTest.run());

        EverNifeCore.getLog().info("§aLoading up Configurations...");
        McConfigManager.initialize(ecPluginData);

        EverNifeCore.getLog().info("§aRegistering Commands!");
        McCommandRegisterer.registerCommands(ecPluginData);

        EverNifeCore.getLog().info("§aRegistering Listeners");
        ECListener.register(ecPluginData, PlayerLoginListener.class);
        ECListener.register(ecPluginData, PlayerInteractListener.class);
        ECListener.register(ecPluginData, PluginListener.class);
        ECListener.register(ecPluginData, GuiListener.class);

        if (Bukkit.getPluginManager().isPluginEnabled("AuthMe")){
            ECListener.register(ecPluginData, PlayerLoginListener.AuthmeLogin.class);
        }else {
            ECListener.register(ecPluginData, PlayerLoginListener.VanillaLogin.class);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("WorldEdit")) try{WorldEditIntegration.initialize();}catch (Throwable e){e.printStackTrace();}

        FCTickUtil.getTickCount();//This will start tickCounting
    }

    // Prints the NBT-API self-test outcome to the console. A failure here is not
    // fatal (item/GUI NBT would be broken, but the plugin still starts), so this
    // only logs; it never rethrows.
    private static void logNBTSelfTest(NBTSelfTest.Result result) {
        EverNifeCore.getLog().info("§8===== §bNBT-API Self-Test §8=====");
        EverNifeCore.getLog().info("§7  detected NMS version: §f" + result.getDetectedNmsVersion());
        for (String step : result.getSteps()) {
            EverNifeCore.getLog().info("§7  " + step);
        }
        if (result.isEnvironmentUnavailable()) {
            EverNifeCore.getLog().warning("§e[NBT Self-Test] " + result.getSummary());
        } else if (result.isSuccess()) {
            EverNifeCore.getLog().info("§a[NBT Self-Test] " + result.getSummary());
        } else {
            EverNifeCore.getLog().severe("§c[NBT Self-Test] " + result.getSummary());
            if (result.getError() != null) {
                EverNifeCore.getLog().severe("§c[NBT Self-Test] cause: " + result.getError());
            }
            EverNifeCore.getLog().warning("§e[NBT Self-Test] Item/GUI NBT features will not work correctly on this server.");
        }
    }

    @Override
    public void onECPluginShutdownPre() {
        //closeAll runs each screen's onClose itself, and it has to happen while the rest of the
        //framework is still up: that handler is what hands back whatever an editable screen was
        //holding, and it needs storage and player data to still answer
        GuiViews.closeAll();
        //the inherited default is what unregisters listeners and commands; ECBukkitPlugin sits between
        //this class and IECPluginBootstrap, so the qualified `IECPluginBootstrap.super` form is illegal here
        super.onECPluginShutdownPre();
    }

    @Override
    public void onECPluginShutdown() {
        //Listeners and commands were already unregistered by the default onECPluginShutdownPre(),
        //so nothing fires into the resources torn down here.
        EverNifeCore.instance.onUnload();
        FCMinecraftAdventureUtil.close();
    }

    @Override
    public void onECPluginReload() {
        EverNifeCore.instance.onReload();
    }

}
