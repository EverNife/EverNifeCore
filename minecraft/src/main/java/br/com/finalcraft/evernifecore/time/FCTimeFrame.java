package br.com.finalcraft.evernifecore.time;

import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCTimeUtil;

import java.util.concurrent.TimeUnit;

public class FCTimeFrame {

    @FCLocale(lang = LocaleType.EN_US, text = "day")
    @FCLocale(lang = LocaleType.PT_BR, text = "dia")
    private static LocaleMessage DAY;
    @FCLocale(lang = LocaleType.EN_US, text = "days")
    @FCLocale(lang = LocaleType.PT_BR, text = "dias")
    private static LocaleMessage DAYS;
    @FCLocale(lang = LocaleType.EN_US, text = "hour")
    @FCLocale(lang = LocaleType.PT_BR, text = "hora")
    private static LocaleMessage HOUR;
    @FCLocale(lang = LocaleType.EN_US, text = "hours")
    @FCLocale(lang = LocaleType.PT_BR, text = "horas")
    private static LocaleMessage HOURS;
    @FCLocale(lang = LocaleType.EN_US, text = "minute")
    @FCLocale(lang = LocaleType.PT_BR, text = "minuto")
    private static LocaleMessage MINUTE;
    @FCLocale(lang = LocaleType.EN_US, text = "minutes")
    @FCLocale(lang = LocaleType.PT_BR, text = "minutos")
    private static LocaleMessage MINUTES;
    @FCLocale(lang = LocaleType.EN_US, text = "second")
    @FCLocale(lang = LocaleType.PT_BR, text = "segundo")
    private static LocaleMessage SECOND;
    @FCLocale(lang = LocaleType.EN_US, text = "seconds")
    @FCLocale(lang = LocaleType.PT_BR, text = "segundos")
    private static LocaleMessage SECONDS;
    @FCLocale(lang = LocaleType.EN_US, text = "and")
    @FCLocale(lang = LocaleType.PT_BR, text = "e")
    private static LocaleMessage AND;

    protected long days;
    protected long hours;
    protected long minutes;
    protected long seconds;
    protected long millis;

    protected FCTimeFrame(long millis) {
        this.millis = millis;
        this.days = TimeUnit.MILLISECONDS.toDays(millis);
        this.hours = TimeUnit.MILLISECONDS.toHours(millis - TimeUnit.DAYS.toMillis(days));
        this.minutes = TimeUnit.MILLISECONDS.toMinutes((millis - TimeUnit.DAYS.toMillis(days)) - TimeUnit.HOURS.toMillis(hours));
        this.seconds = TimeUnit.MILLISECONDS.toSeconds(((millis - TimeUnit.DAYS.toMillis(days)) - TimeUnit.HOURS.toMillis(hours)) - TimeUnit.MINUTES.toMillis(minutes));
    }

    public long getDays() {
        return days;
    }

    public long getHours() {
        return hours;
    }

    public long getMinutes() {
        return minutes;
    }

    public long getSeconds() {
        return seconds;
    }

    public long getMillis() {
        return millis;
    }

    public String getFormatted(){
        return FCTimeUtil.getFormatted(this.millis);
    }

    public String getFormattedNoHours(){
        return FCTimeUtil.getFormattedNoHours(this.millis);
    }

    public String getFormattedDiscursive(){
        return getFormattedDiscursive(true);
    }

    public String getFormattedDiscursive(boolean includeMillis){
        return getFormattedDiscursive("","", includeMillis);
    }

    public String getFormattedDiscursive(final String numberColor, final String textColor){
        return getFormattedDiscursive(numberColor, textColor, false);
    }

    public String getFormattedDiscursive(final String numberColor, final String textColor, boolean includeMillis){
        return getFormattedDiscursive(numberColor, textColor, includeMillis, false);
    }

    public String getFormattedDiscursive(final String numberColor, final String textColor, boolean includeMillis, boolean shortVersion){
        final String dia;
        final String hora;
        final String minuto;
        final String segundo;
        if (shortVersion){
            dia = String.valueOf(DAY.getDefaultFancyText().getText().charAt(0));
            hora = String.valueOf(HOURS.getDefaultFancyText().getText().charAt(0));
            minuto = String.valueOf(MINUTES.getDefaultFancyText().getText().charAt(0));
            segundo = String.valueOf(SECONDS.getDefaultFancyText().getText().charAt(0));
        }else {
            dia = this.getDays() >= 2 ? DAYS.getDefaultFancyText().getText() : DAY.getDefaultFancyText().getText();
            hora = this.getHours() >= 2 ? HOURS.getDefaultFancyText().getText() : HOUR.getDefaultFancyText().getText();
            minuto = this.getMinutes() >= 2 ? MINUTES.getDefaultFancyText().getText() : MINUTE.getDefaultFancyText().getText();
            segundo = this.getSeconds() >= 2 || ((includeMillis || this.getSeconds() == 0) && (this.millis % 1000) >= 2)  ? SECONDS.getDefaultFancyText().getText() : SECOND.getDefaultFancyText().getText();
        }

        final String SPACE = shortVersion ? "" : " ";
        final String COMMA = shortVersion ? " " : ", ";
        final String E = shortVersion ? "" : AND.getDefaultFancyText().getText();

        if (this.getDays() > 0){
            return numberColor + this.getDays() + SPACE + textColor + dia + COMMA + numberColor + this.getHours() + SPACE + textColor + hora + COMMA + numberColor + this.getMinutes() + SPACE + textColor + minuto + SPACE + E + " " + numberColor + this.getSeconds() + SPACE + textColor + segundo;
        }else if (this.getHours() > 0){
            return numberColor + this.getHours() + SPACE + textColor + hora + COMMA + numberColor + this.getMinutes() + SPACE + textColor + minuto + SPACE + E + " " + numberColor + this.getSeconds() + SPACE + textColor + segundo;
        }else if (this.getMinutes() > 0){
            return numberColor + this.getMinutes() + SPACE + textColor + minuto + SPACE + E + " " + numberColor + this.getSeconds() + SPACE + textColor + segundo;
        }else {
            String millisString = "";
            if (this.getSeconds() == 0 || includeMillis){
                int millisInt = (int) (this.millis % 1000);
                millisString = "." + (millisInt < 100 ? "0" : "") + (millisInt < 10 ? "0" : "") + millisInt;
            }
            return numberColor + this.getSeconds() + millisString + " " + textColor + segundo;
        }
    }

    @Override
    public String toString() {
        return this.getFormattedDiscursive();
    }

    // ----------------------------------------------------------------------------------------------------------------//
    //  Static Methods
    // ----------------------------------------------------------------------------------------------------------------//

    public static FCTimeFrame of(long millis){
        return new FCTimeFrame(millis);
    }

    public static FCTimeFrame now(){
        return new FCTimeFrame(System.currentTimeMillis());
    }
}
