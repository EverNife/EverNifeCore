package br.com.finalcraft.evernifecore.cooldown;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.config.yaml.anntation.Loadable;
import br.com.finalcraft.evernifecore.config.yaml.anntation.Salvable;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class Cooldown implements Salvable {

    protected final String identifier;
    protected long timeStart = 0; //millis
    protected long timeDuration = 0; //millis
    protected boolean persist = false; //should be saved on restart

    public Cooldown(String identifier) {
        this.identifier = identifier;
    }

    public Cooldown(String identifier, long timeStart, long timeDuration, boolean persist) {
        this.identifier = identifier;
        this.timeStart = timeStart;
        this.timeDuration = timeDuration;
        this.persist = persist;
    }

    public String getIdentifier() {
        return identifier;
    }

    public long getStart() {
        return timeStart;
    }

    public long getDuration() {
        return timeDuration;
    }

    public Cooldown setDuration(long timeDuration) {
        this.timeDuration = timeDuration;
        return this;
    }

    public boolean isPersistent() {
        return persist;
    }

    public Cooldown setPersist(boolean persist) {
        this.persist = persist;
        return this;
    }

    public long getTimeLeft(){
        return getTimeLeft(this.timeDuration, TimeUnit.MILLISECONDS);
    }

    public long getTimeLeft(long secondsToWait){
        return getTimeLeft(secondsToWait, TimeUnit.SECONDS);
    }

    public long getTimeLeft(long value, TimeUnit timeUnit){
        long timeDuration = timeUnit.toMillis(value);
        long elapsedTime = (System.currentTimeMillis() - this.timeStart);
        return (elapsedTime - timeDuration) * -1;
    }

    public FCTimeFrame getFCTimeFrame(){
        return new FCTimeFrame(this.getTimeLeft());
    }

    public FCTimeFrame getFCTimeFrame(long customTimeInSeconds){
        return new FCTimeFrame(this.getTimeLeft(customTimeInSeconds));
    }

    public FCTimeFrame getFCTimeFrame(long customTime, TimeUnit timeUnit){
        return new FCTimeFrame(this.getTimeLeft(customTime, timeUnit));
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§cYou need to wait more %discursive_time% to do this!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cVocê precisa esperar mais %discursive_time% para fazer isto!")
    private static LocaleMessage YOU_NEED_TO_WAIT_MORE_TIME;
    public Cooldown warnPlayer(CommandSender commandSender){
        YOU_NEED_TO_WAIT_MORE_TIME
                .addPlaceholder("%discursive_time%", getFCTimeFrame().getFormattedDiscursive("§6","§c"))
                .send(commandSender);
        return this;
    }

    public Cooldown warnPlayer(CommandSender commandSender, long customTimeInSeconds){
        YOU_NEED_TO_WAIT_MORE_TIME
                .addPlaceholder("%discursive_time%", getFCTimeFrame(customTimeInSeconds).getFormattedDiscursive("§6","§c"))
                .send(commandSender);
        return this;
    }

    public Cooldown warnPlayer(CommandSender commandSender, long customTime, TimeUnit timeUnit){
        YOU_NEED_TO_WAIT_MORE_TIME
                .addPlaceholder("%discursive_time%", getFCTimeFrame(customTime, timeUnit).getFormattedDiscursive("§6","§c"))
                .send(commandSender);
        return this;
    }

    public boolean isInCooldown(){
        return isInCooldown(this.timeDuration, TimeUnit.MILLISECONDS);
    }

    public boolean isInCooldown(long customWaitTimeInSeconds){
        return isInCooldown(customWaitTimeInSeconds, TimeUnit.SECONDS);
    }

    public boolean isInCooldown(long customWaitTime, TimeUnit timeUnit){
        return (timeStart == 0 || customWaitTime <= 0) ? false : getTimeLeft(customWaitTime, timeUnit) >= 1;
    }

    //Convenient method, as almost always cooldows are based on seconds not millis
    public Cooldown startWith(long timeInSeconds){
        return startWith(timeInSeconds, TimeUnit.SECONDS);
    }

    public Cooldown startWith(long value, TimeUnit timeUnit){
        this.timeDuration = timeUnit.toMillis(value);
        start();
        return this;
    }

    public Cooldown start(){
        this.timeStart = System.currentTimeMillis();
        if (persist) handleSaveIfPermanent();
        return this;
    }

    public Cooldown stop(){
        this.timeStart = 0;
        this.persist = false;
        if (persist) handleStopIfPermanent();
        return this;
    }

    @Override
    public void onConfigSave(ConfigSection section) {
        section.setValue("identifier", this.identifier);
        section.setValue("timeStart", this.timeStart);
        section.setValue("timeDuration", this.timeDuration);
    }

    @Loadable
    public static Cooldown loadFromConfig(Config config, String path) {
        String identifier = config.getString(path + ".identifier");
        Long timeStart = config.getLong(path + ".timeStart");
        Long timeDuration = config.getLong(path + ".timeDuration");

        return new GenericCooldown(
                identifier,
                timeStart,
                timeDuration,
                true
        );
    }

    public abstract void handleSaveIfPermanent();

    public abstract void handleStopIfPermanent();


    // -----------------------------------------------------------------------------------------------------------------------------//
    // Static Methods
    // -----------------------------------------------------------------------------------------------------------------------------//

    private static Map<String, Cooldown> MAP_OF_COOLDOWNS = new HashMap<String, Cooldown>();

    public static void initialize(){
        MAP_OF_COOLDOWNS.clear();

        Config config = ConfigManager.getCooldowns();

        for (String key : config.getKeys("AllCooldowns")){
            try {
                Cooldown cooldown = loadFromConfig(config, "AllCooldowns." + key);
                MAP_OF_COOLDOWNS.put(cooldown.getIdentifier(),cooldown);
            }catch (Exception e) {
                EverNifeCore.warning("Failed to load coodown [" + key + "] at [" + ConfigManager.getCooldowns().getTheFile().getAbsolutePath() + "]");
                e.printStackTrace();
            }
        }
    }

    public static Cooldown of(String identifier){
        Cooldown cooldown = MAP_OF_COOLDOWNS.get(identifier);
        if (cooldown == null){
            cooldown = new GenericCooldown(identifier);
            MAP_OF_COOLDOWNS.put(identifier, cooldown);
        }
        return cooldown;
    }

    public static class GenericCooldown extends Cooldown{

        public GenericCooldown(String identifier) {
            super(identifier);
        }

        public GenericCooldown(String identifier, long timeStart, long timeDuration, boolean persist) {
            super(identifier, timeStart, timeDuration, persist);
        }

        @Override
        public void handleSaveIfPermanent() {
            ConfigManager.getCooldowns().setValue("AllCooldowns." + this.getIdentifier(),this);
            ConfigManager.getCooldowns().saveAsync();
        }

        @Override
        public void handleStopIfPermanent() {
            ConfigManager.getCooldowns().setValue("AllCooldowns." + this.getIdentifier(),null);
            ConfigManager.getCooldowns().saveAsync();
        }

    }
}
