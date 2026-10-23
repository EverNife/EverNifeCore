package br.com.finalcraft.evernifecore.minecraft.consolefilter;

import br.com.finalcraft.evernifecore.consolefilter.ECConsoleFilter;
import br.com.finalcraft.evernifecore.minecraft.consolefilter.base.BaseLog4jFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;

/**
 * The core's hook into Log4j, the stack a Bukkit or Forge server prints through: hides each line the
 * {@link ECConsoleFilter} provider says to. A plugin replaces the decision, never this hook.
 */
public class ECBukkitConsoleFilter extends BaseLog4jFilter {

	private final ECConsoleFilter consoleFilter;

	public ECBukkitConsoleFilter(ECConsoleFilter consoleFilter) {
		this.consoleFilter = consoleFilter;
	}

	@Override
	public Result filter(LogEvent event) {
		return consoleFilter.isHidden(event.getMessage().getFormattedMessage()) ? Result.DENY : Result.NEUTRAL;
	}

	/**
	 * Adds the hook to the Log4j root logger. Log4j accumulates filters, so the returned undo has to run
	 * on the core's shutdown, or a reload leaves one more copy answering.
	 */
	public static Runnable install() {
		Logger rootLogger = (Logger) LogManager.getRootLogger();
		ECBukkitConsoleFilter filter = new ECBukkitConsoleFilter(ECConsoleFilter.global());
		rootLogger.addFilter(filter);
		//Logger#addFilter delegates to its LoggerConfig, which is where the matching removeFilter lives
		return () -> rootLogger.get().removeFilter(filter);
	}
}
