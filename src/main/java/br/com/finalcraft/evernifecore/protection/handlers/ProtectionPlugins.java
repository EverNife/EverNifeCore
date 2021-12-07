package br.com.finalcraft.evernifecore.protection.handlers;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.protection.handlers.protectionplugins.GriefPreventionPlusHandler;
import br.com.finalcraft.evernifecore.protection.handlers.protectionplugins.WorldGuardHandler;
import org.bukkit.Bukkit;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

public enum ProtectionPlugins {
	GriefPreventionPlus(GriefPreventionPlusHandler.class),
	WorldGuard(WorldGuardHandler.class),
	;

	public static void initialize(){
		for (ProtectionPlugins protectionPlugin : ProtectionPlugins.values()) {
			if (Bukkit.getServer().getPluginManager().isPluginEnabled(protectionPlugin.name())) {
				try {
					EverNifeCore.info("[ProtectionPlugins] Loading protection plugin: " + protectionPlugin.name());
					protectionPlugin.createHandler();
				}catch (Throwable e){
					EverNifeCore.warning("[ProtectionPlugins] Protection plugin [" + protectionPlugin.name() + "] was found but i was not able to integrate with it!");
					e.printStackTrace();
				}
			}
		}
		generateHandlersList();
	}

	private final Class<? extends ProtectionHandler> clazz;
	private ProtectionHandler handler;
	private boolean enabled = false;

	private static ProtectionHandler[] handlersList={};

	ProtectionPlugins(Class<? extends ProtectionHandler> clazz) {
		this.clazz=clazz;
	}

	public ProtectionHandler getHandler() {
		return handler;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void createHandler() throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
		this.handler = this.clazz.getDeclaredConstructor().newInstance();
		this.enabled = true;
	}

	public void removeHandler() {
		this.handler = null;
		this.enabled = false;
	}

	protected static void generateHandlersList() {
		handlersList = Arrays.stream(ProtectionPlugins.values())
				.filter(ProtectionPlugins::isEnabled)
				.map(protectedPlugin -> protectedPlugin.getHandler())
				.toArray(ProtectionHandler[]::new);
	}
	
	public static ProtectionHandler[] getHandlers() {
		return handlersList;
	}

}
