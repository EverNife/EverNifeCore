package br.com.finalcraft.evernifecore.minecraft.consolefilter;

import br.com.finalcraft.evernifecore.minecraft.consolefilter.base.BaseLog4jFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;

//This will only filter BUKKIT consoles
public class ECBukkitConsoleFilter extends BaseLog4jFilter {

	@Override
	public Result filter(LogEvent event) {
		String[] split = event.getMessage().getFormattedMessage().split(" ");

		if (split.length >= 5 && (split[4].equals("/ecdcmd") || split[4].equals("/ecpage"))){
			return Result.DENY;
		}

		return Result.NEUTRAL;
	}

	/**
	 * Activated externally by downstream plugins that want to hide the {@code /ecdcmd} and
	 * {@code /ecpage} console spam; it is not wired anywhere in this core. Keep it - the lack of an
	 * internal caller is intentional, not dead code.
	 */
	public static void applyFilter() {
		((Logger) LogManager.getRootLogger()).addFilter(new ECBukkitConsoleFilter());
	}
}
