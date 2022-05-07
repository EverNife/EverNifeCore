package br.com.finalcraft.evernifecore.time;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;

import java.util.Date;
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

    protected Long days;
    protected Long hours;
    protected Long minutes;
    protected Long seconds;
    protected Long millis;

    public FCTimeFrame() {
        this.millis = System.currentTimeMillis();
        days = TimeUnit.MILLISECONDS.toDays(millis);
        hours = TimeUnit.MILLISECONDS.toHours(millis - TimeUnit.DAYS.toMillis(days));
        minutes = TimeUnit.MILLISECONDS.toMinutes((millis - TimeUnit.DAYS.toMillis(days)) - TimeUnit.HOURS.toMillis(hours));
        seconds = TimeUnit.MILLISECONDS.toSeconds(((millis - TimeUnit.DAYS.toMillis(days)) - TimeUnit.HOURS.toMillis(hours)) - TimeUnit.MINUTES.toMillis(minutes));
    }

    public FCTimeFrame(Long millis) {
        this.millis = millis;
        days = TimeUnit.MILLISECONDS.toDays(millis);
        hours = TimeUnit.MILLISECONDS.toHours(millis - TimeUnit.DAYS.toMillis(days));
        minutes = TimeUnit.MILLISECONDS.toMinutes((millis - TimeUnit.DAYS.toMillis(days)) - TimeUnit.HOURS.toMillis(hours));
        seconds = TimeUnit.MILLISECONDS.toSeconds(((millis - TimeUnit.DAYS.toMillis(days)) - TimeUnit.HOURS.toMillis(hours)) - TimeUnit.MINUTES.toMillis(minutes));
    }

    public Long getDays() {
        return days;
    }

    public Long getHours() {
        return hours;
    }

    public Long getMinutes() {
        return minutes;
    }

    public Long getSeconds() {
        return seconds;
    }

    public Long getMillis() {
        return millis;
    }

    public Long getAllInDays(){
        return TimeUnit.MILLISECONDS.toDays(this.millis);
    }

    public Long getAllInHours(){
        return TimeUnit.MILLISECONDS.toHours(this.millis);
    }

    public Long getALlInMinutes(){
        return TimeUnit.MILLISECONDS.toMinutes(this.millis);
    }

    public Long getAllInSeconds(){
        return TimeUnit.MILLISECONDS.toSeconds(this.millis);
    }

    public String getFormatted(){
        Date date = new Date(millis);
        return ECSettings.SIMPLE_DATE_FORMAT.format(date);
    }

    public String getFormattedNoHours(){
        Date date = new Date(millis);
        return ECSettings.DATE_FORMAT_WITH_HOURS.format(date);
    }

    public FCTimeFrame getDiferenceUntilNow(){
        return new FCTimeFrame(System.currentTimeMillis() - this.millis);
    }

    public String getFormattedDiscursive(){
        return getFormattedDiscursive("","");
    }

    public String getShortenedFormattedDiscursive(){
        return getFormattedDiscursive("","", false, true);
    }

    public String getFormattedDiscursive(boolean includeMillis){
        return getFormattedDiscursive("","", includeMillis);
    }

    public String getFormattedDiscursive(final String numberColor, final String textColor){
        return getFormattedDiscursive(numberColor,numberColor,false);
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
        final String E = AND.getDefaultFancyText().getText();

        if (this.getDays() > 0){
            return numberColor + this.getDays() + SPACE + textColor + dia + COMMA + numberColor + this.getHours() + SPACE + textColor + hora + COMMA + numberColor + this.getMinutes() + SPACE + textColor + minuto + " " + E + " " + numberColor + this.getSeconds() + SPACE + textColor + segundo;
        }else if (this.getHours() > 0){
            return numberColor + this.getHours() + SPACE + textColor + hora + COMMA + numberColor + this.getMinutes() + SPACE + textColor + minuto + " " + E + " " + numberColor + this.getSeconds() + SPACE + textColor + segundo;
        }else if (this.getMinutes() > 0){
            return numberColor + this.getMinutes() + SPACE + textColor + minuto + " " + E + " " + numberColor + this.getSeconds() + SPACE + textColor + segundo;
        }else {
            return numberColor + this.getSeconds() + (this.getSeconds() == 0 || includeMillis ? "." + (this.millis % 1000) : "") + " " +textColor + segundo;
        }
    }

    public Date toDate(){
        return new Date(this.millis);
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Static Commands
    // -----------------------------------------------------------------------------------------------------------------------------//

    public static FCTimeFrame fromDate(Date date){
        return new FCTimeFrame(date.getTime());
    }

    public static Long getSeconds(Long millis) {
        return TimeUnit.MILLISECONDS.toSeconds(millis);
    }

    public static Long getMiutes(Long millis) {
        return TimeUnit.MILLISECONDS.toMinutes(millis);
    }

    public static Long getDays(Long millis) {
        return TimeUnit.MILLISECONDS.toDays(millis);
    }

    public static String getFormatted(Long millis){
        Date date = new Date(millis);
        return ECSettings.DATE_FORMAT_WITH_HOURS.format(date);
    }

    public static String getFormattedNoHours(Long millis){
        Date date = new Date(millis);
        return ECSettings.SIMPLE_DATE_FORMAT.format(date);
    }
}
