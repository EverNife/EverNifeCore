package br.com.finalcraft.evernifecore.time;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class FCTimeFrame {

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

    public String getFormated(){
        Date date = new Date(millis);
        return sdf1.format(date);
    }

    public String getFormatedNoHours(){
        Date date = new Date(millis);
        return sdf2.format(date);
    }

    public FCTimeFrame getDiferenceUntilNow(){
        return new FCTimeFrame(System.currentTimeMillis() - this.millis);
    }

    public String getFormatedDiscursive(){
        return getFormatedDiscursive("","");
    }

    public String getFormatedDiscursive(boolean includeMillis){
        return getFormatedDiscursive("","", includeMillis);
    }

    public String getFormatedDiscursive(final String numberColor, final String textColor){
        return getFormatedDiscursive(numberColor,numberColor,false);
    }

    public String getFormatedDiscursive(final String numberColor, final String textColor, boolean includeMillis){
        String dia = this.getDays() >= 2 ? "dias" : "dia";
        String hora = this.getHours() >= 2 ? "horas" :"hora";
        String minuto = this.getMinutes() >= 2 ? "minutos" :"minuto";
        String segundo = this.getSeconds() >= 2 || ((includeMillis || this.getSeconds() == 0) && (this.millis % 1000) >= 2)  ? "segundos" :"segundo";

        if (this.getDays() > 0){
            return (numberColor + this.getDays() + " " + textColor + dia + ", " + numberColor + this.getHours() + " " + textColor + hora + ", " + numberColor + this.getMinutes() + " " + textColor + minuto + " e " + numberColor + this.getSeconds() + " " + textColor + segundo);
        }else if (this.getHours() > 0){
            return (numberColor + this.getHours() + " " + textColor + hora + ", " + numberColor + this.getMinutes() + " " + textColor + minuto + " e " + numberColor + this.getSeconds() + " " + textColor + segundo);
        }else if (this.getMinutes() > 0){
            return (numberColor + this.getMinutes() + " " + textColor + minuto + " e " + numberColor + this.getSeconds() + " " + textColor + segundo);
        }else {
            return (numberColor + this.getSeconds() + (this.getSeconds() == 0 || includeMillis ? "." + (this.millis % 1000) : "") + " " +textColor + segundo);
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

    private static SimpleDateFormat sdf1 = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    public static String getFormated(Long millis){
        Date date = new Date(millis);
        return sdf1.format(date);
    }

    private static SimpleDateFormat sdf2 = new SimpleDateFormat("dd/MM/yyyy");
    public static String getFormatedNoHours(Long millis){
        Date date = new Date(millis);
        return sdf2.format(date);
    }
}
