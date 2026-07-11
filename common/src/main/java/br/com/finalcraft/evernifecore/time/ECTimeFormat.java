package br.com.finalcraft.evernifecore.time;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.everylibs.util.FCTimeUtil;

/**
 * Formats epoch-millis instants using EverNifeCore's configured date formats
 * ({@link ECSettings#DATE_FORMAT_WITH_HOURS} / {@link ECSettings#SIMPLE_DATE_FORMAT}), resolved
 * against the server's {@link DayOfToday} time zone.
 * <p>
 * The generic, config-free time formatting lives in {@link FCTimeUtil}; this class holds only the
 * ECore-configured convenience shortcuts that bake in those settings.
 */
public class ECTimeFormat {

    public static String getFormatted(long millis){
        return FCTimeUtil.getFormatted(millis, ECSettings.DATE_FORMAT_WITH_HOURS, DayOfToday.getInstance().getZoneId());
    }

    public static String getFormattedNoHours(long millis){
        return FCTimeUtil.getFormatted(millis, ECSettings.SIMPLE_DATE_FORMAT, DayOfToday.getInstance().getZoneId());
    }
}
