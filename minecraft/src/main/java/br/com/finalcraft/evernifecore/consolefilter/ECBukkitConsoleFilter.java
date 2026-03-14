package br.com.finalcraft.evernifecore.consolefilter;

import br.com.finalcraft.evernifecore.consolefilter.base.BaseLog4jFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;


//This will only filter BUKKIT consoles
public class ECBukkitConsoleFilter extends BaseLog4jFilter {

	@Override
	public Result filter(LogEvent event) {
		String[] split = event.getMessage().getFormattedMessage().split(" ");

		if (split.length >= 5 && split[4].equals("/ecdcmd")){
			return Result.DENY;
		}

		return Result.NEUTRAL;
	}

	public static void applyFilter() {
		((Logger) LogManager.getRootLogger()).addFilter(new ECBukkitConsoleFilter());
	}
}
