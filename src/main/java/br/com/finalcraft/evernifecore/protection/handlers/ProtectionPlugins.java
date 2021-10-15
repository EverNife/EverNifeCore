package br.com.finalcraft.evernifecore.protection.handlers;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.protection.ProtectionWorldGuard;
import br.com.finalcraft.evernifecore.protection.handlers.protectionplugins.GriefPreventionPlusHandler;
import br.com.finalcraft.evernifecore.protection.handlers.protectionplugins.WorldGuardHandler;
import br.com.finalcraft.evernifecore.util.ReflectionUtil;
import br.com.finalcraft.evernifecore.util.reflection.MethodInvoker;
import org.bukkit.Bukkit;

import java.util.Arrays;

public enum ProtectionPlugins {

	GriefPreventionPlus(GriefPreventionPlusHandler.class),
	WorldGuard(WorldGuardHandler.class, () -> { //This will prevent Boostrap CLI INIT ERRORs when WorldGuard is not present
		MethodInvoker invoker = ReflectionUtil.getMethod(ProtectionWorldGuard.class, "initialize");
		invoker.invoke(null);
	}),
	;

	public static void initialize(){
		for (ProtectionPlugins protectionPlugin : ProtectionPlugins.values()) {
			if (Bukkit.getServer().getPluginManager().isPluginEnabled(protectionPlugin.name())) {
				try {
					EverNifeCore.info("[ProtectionPlugins] Loading protection plugin: " + protectionPlugin.name());
					protectionPlugin.createHandler();
					if (protectionPlugin.isEnabled()){
						protectionPlugin.onPostHandler.run();
					}
				}catch (Throwable e){ //Some REQUIRED Plugin is not present
					EverNifeCore.warning("[ProtectionPlugins] Protection plugin [" + protectionPlugin.name() + "] was not found! ClassNotFound[" +  e.getMessage() +  "]");
				}
			}
		}
	}

	private final Class<? extends ProtectionHandler> clazz;
	private ProtectionHandler handler;
	private boolean enabled = false;
	private Runnable onPostHandler = null;

	private static ProtectionHandler[] handlersList={};

	ProtectionPlugins(Class<? extends ProtectionHandler> clazz) {
		this.clazz=clazz;
	}

	ProtectionPlugins(Class<? extends ProtectionHandler> clazz, Runnable onPostHandler) {
		this.clazz = clazz;
		this.onPostHandler = onPostHandler;
	}

	public ProtectionHandler getHandler() {
		return handler;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void createHandler() throws InstantiationException, IllegalAccessException {
		this.handler = this.clazz.newInstance();
		this.enabled = true;
		generateHandlersList();
	}

	public void removeHandler() {
		this.handler = null;
		this.enabled = false;
		generateHandlersList();
	}

	protected static void generateHandlersList() {
		handlersList = Arrays.stream(ProtectionPlugins.values())
				.filter(ProtectionPlugins::isEnabled)
				.toArray(ProtectionHandler[]::new);
	}
	
	public static ProtectionHandler[] getHandlers() {
		return handlersList;
	}

}
