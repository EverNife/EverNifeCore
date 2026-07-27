package br.com.finalcraft.evernifecore.cooldown;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.cooldown.server.ServerCooldowns;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.everydatabase.util.JsonAutoDetectFieldsOnly;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A handle over one {@link CooldownEntry}: it names the entry, does all the time math over it, and
 * routes every mutation to whatever storage owns it.
 *
 * <p>The entry is the value, this object is only a view of it - several handles may share one entry,
 * and an entry may well outlive every handle over it. A subclass IS the route: it decides where the
 * entry lives and implements {@link #onMutated()} to keep that storage in agreement.</p>
 */
@JsonAutoDetectFieldsOnly
public abstract class Cooldown {

    protected String identifier;
    protected CooldownEntry entry;

    protected Cooldown() {
        //Jackson no-arg constructor
        this.entry = new CooldownEntry();
    }

    public Cooldown(String identifier) {
        this(identifier, new CooldownEntry());
    }

    /** Wraps an entry that already exists - whoever passed it in keeps owning where it is stored. */
    public Cooldown(String identifier, CooldownEntry entry) {
        this.identifier = identifier;
        this.entry = entry;
    }

    /** Rebuilds a cooldown out of raw state: the last thing known to have happened to it is its start. */
    public Cooldown(String identifier, long timeStart, long timeDuration, boolean persist) {
        this(identifier, new CooldownEntry(timeStart, timeDuration, timeStart, persist));
    }

    public String getIdentifier() {
        return identifier;
    }

    /**
     * The value this handle reads and writes. Mutating it directly bypasses the mutation clock and the
     * route, so a change made that way is neither stored nor visible to a merge - go through this
     * handle's own mutators instead.
     */
    public CooldownEntry getEntry() {
        return entry;
    }

    public long getStart() {
        return entry.getTimeStart();
    }

    public long getDuration() {
        return entry.getTimeDuration();
    }

    /** This cooldown's config form: identifier + start + duration + mutation clock (the persist flag is
     *  implicit - a stored cooldown is always persistent). Used by the {@code GenericCooldown} map
     *  registration in ECBuiltinTypes. */
    public Map<String, Object> toConfigMap(){
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("identifier", getIdentifier());
        map.put("timeStart", getStart());
        map.put("timeDuration", getDuration());
        map.put("updatedAt", entry.getUpdatedAt());
        return map;
    }

    public Cooldown setDuration(long timeDuration) {
        entry.setTimeDuration(timeDuration);
        touch();
        return this;
    }

    public boolean isPersistent() {
        return entry.isPersist();
    }

    public Cooldown setPersist(boolean persist) {
        boolean hadStoredRow = entry.isPersist();
        entry.setPersist(persist);
        touch(hadStoredRow);
        return this;
    }

    /**
     * Marks the entry persistent WITHOUT stamping the mutation clock or notifying the route - the
     * born-persistent seam of the network routes, whose entries only mean anything if they replicate.
     * Deliberately not {@link #setPersist(boolean)}, which would file a still-blank entry: this way the
     * row only grows once the cooldown is actually started, never on a bare read.
     */
    protected final void markBornPersistent() {
        entry.setPersist(true);
    }

    public long getTimeLeft(){
        return getTimeLeft(entry.getTimeDuration(), TimeUnit.MILLISECONDS);
    }

    public long getTimeLeft(long secondsToWait){
        return getTimeLeft(secondsToWait, TimeUnit.SECONDS);
    }

    public long getTimeLeft(long value, TimeUnit timeUnit){
        long timeDuration = timeUnit.toMillis(value);
        long elapsedTime = (System.currentTimeMillis() - entry.getTimeStart());
        return (elapsedTime - timeDuration) * -1;
    }

    public FCTimeFrame getFCTimeFrame(){
        return FCTimeFrame.of(this.getTimeLeft());
    }

    public FCTimeFrame getFCTimeFrame(long customTimeInSeconds){
        return FCTimeFrame.of(this.getTimeLeft(customTimeInSeconds));
    }

    public FCTimeFrame getFCTimeFrame(long customTime, TimeUnit timeUnit){
        return FCTimeFrame.of(this.getTimeLeft(customTime, timeUnit));
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§cYou need to wait more ${discursive_time}§c to do this!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cVocê precisa esperar mais ${discursive_time}§c para fazer isto!")
    private static LocaleMessage YOU_NEED_TO_WAIT_MORE_TIME;
    public Cooldown warnPlayer(FCommandSender commandSender){
        YOU_NEED_TO_WAIT_MORE_TIME
                .addPlaceholder("discursive_time", getFCTimeFrame().getFormattedDiscursive("§6","§c"))
                .send(commandSender);
        return this;
    }

    public Cooldown warnPlayer(FCommandSender commandSender, long customTimeInSeconds){
        YOU_NEED_TO_WAIT_MORE_TIME
                .addPlaceholder("discursive_time", getFCTimeFrame(customTimeInSeconds).getFormattedDiscursive("§6","§c"))
                .send(commandSender);
        return this;
    }

    public Cooldown warnPlayer(FCommandSender commandSender, long customTime, TimeUnit timeUnit){
        YOU_NEED_TO_WAIT_MORE_TIME
                .addPlaceholder("discursive_time", getFCTimeFrame(customTime, timeUnit).getFormattedDiscursive("§6","§c"))
                .send(commandSender);
        return this;
    }

    public boolean isInCooldown(){
        return isInCooldown(entry.getTimeDuration(), TimeUnit.MILLISECONDS);
    }

    public boolean isInCooldown(long customWaitTimeInSeconds){
        return isInCooldown(customWaitTimeInSeconds, TimeUnit.SECONDS);
    }

    public boolean isInCooldown(long customWaitTime, TimeUnit timeUnit){
        return (entry.getTimeStart() == 0 || customWaitTime <= 0) ? false : getTimeLeft(customWaitTime, timeUnit) >= 1;
    }

    //Convenience method, since cooldowns are almost always based on seconds rather than millis
    public Cooldown startWith(long timeInSeconds){
        return startWith(timeInSeconds, TimeUnit.SECONDS);
    }

    public Cooldown startWith(long value, TimeUnit timeUnit){
        //the duration goes straight onto the entry so that start() stays the single mutation, and the
        //route is told once instead of twice
        entry.setTimeDuration(timeUnit.toMillis(value));
        start();
        return this;
    }

    public Cooldown start(){
        entry.setTimeStart(System.currentTimeMillis());
        touch();
        return this;
    }

    public Cooldown stop(){
        boolean hadStoredRow = entry.isPersist();
        entry.setTimeStart(0);
        entry.setPersist(false);
        touch(hadStoredRow);
        return this;
    }

    /** Ends a mutation that cannot have changed the persist flag. */
    protected final void touch(){
        touch(false);
    }

    /**
     * Ends every mutation: stamps the mutation clock, then hands the change to the route while there is
     * a stored row to keep in agreement. A cooldown that neither is nor was persistent has no row
     * anywhere, so the route is not called for it - that is what keeps a throwaway, memory-only cooldown
     * from ever reaching a backend.
     *
     * @param hadStoredRow the persist flag as it stood BEFORE the mutation - a cooldown that has just
     *                     lost it still has a row to drop
     */
    protected final void touch(boolean hadStoredRow){
        entry.setUpdatedAt(System.currentTimeMillis());
        if (entry.isPersist() || hadStoredRow){
            onMutated();
        }
    }

    /**
     * Makes the storage owning this cooldown's entry agree with it: hold the entry while
     * {@link #isPersistent()}, drop it once it is not. Called after every mutation able to reach
     * storage, with the mutation clock already stamped.
     */
    protected abstract void onMutated();


    // -----------------------------------------------------------------------------------------------------------------------------//
    // Static Methods
    // -----------------------------------------------------------------------------------------------------------------------------//

    private static LinkedHashMap<String, Cooldown> MAP_OF_COOLDOWNS = new LinkedHashMap<>();

    public static void initialize(){
        MAP_OF_COOLDOWNS.clear();

        Config config = ConfigManager.getCooldowns();

        for (String key : config.getKeys("AllCooldowns")){
            try {
                Cooldown cooldown = config.getValue("AllCooldowns." + key, Cooldown.class);
                MAP_OF_COOLDOWNS.put(cooldown.getIdentifier(),cooldown);
            }catch (Exception e) {
                EverNifeCore.getLog().warning("Failed to load coodown [" + key + "] from Cooldowns.yml");
                e.printStackTrace();
            }
        }
    }

    public static LinkedHashMap<String, Cooldown> getMapOfCooldowns() {
        return MAP_OF_COOLDOWNS;
    }

    public static Cooldown of(String identifier){
        Cooldown cooldown = MAP_OF_COOLDOWNS.get(identifier);
        if (cooldown == null){
            cooldown = new GenericCooldown(identifier);
            MAP_OF_COOLDOWNS.put(identifier, cooldown);
        }
        return cooldown;
    }

    /**
     * The SERVER + NETWORK cooldown {@code identifier}: a handle over its row on the shared backend, so
     * every server of the network reads and writes the same state. Synchronous - the collection is warm.
     *
     * <p>Born PERSISTENT, unlike the local one: a network cooldown only means anything if it replicates,
     * and the route to storage is gated on the entry being persistent - a non-persistent one would
     * silently never propagate. The flag is set on the entry directly (not through {@code setPersist}),
     * so the row still only grows once the cooldown is actually started, never on a bare read.</p>
     *
     * <p>Collapses to the local {@link #of(String)} when the network storage is not bootstrapped, which
     * is exactly a single-server setup with no shared backend: one server IS the whole network, so a
     * local cooldown already has network reach. (An admin who DECLARED multi-instance intent -
     * cache-sync/redis - on a backend that cannot enforce it fails the boot earlier instead, so this
     * fallback is never the silent cross-server bypass.)</p>
     */
    public static Cooldown network(String identifier){
        ServerCooldowns store = ServerCooldowns.get();
        if (store == null){
            return of(identifier);
        }
        return store.resolve(identifier);
    }

    /** Reads the config-map form written by {@link #toConfigMap()} into a persistent {@link GenericCooldown}. */
    public static Cooldown fromConfigMap(Map<String, Object> map){
        long timeStart = asLong(map.get("timeStart"));
        Object rawUpdatedAt = map.get("updatedAt");
        //a file written before the mutation clock existed: the start is the newest thing it can tell us
        long updatedAt = rawUpdatedAt == null ? timeStart : asLong(rawUpdatedAt);

        return new GenericCooldown(
                String.valueOf(map.get("identifier")),
                new CooldownEntry(timeStart, asLong(map.get("timeDuration")), updatedAt, true)
        );
    }

    /** Coerce a stored numeric (Integer/Long/Double or a stringified number) to a long, 0 when absent. */
    private static long asLong(Object raw){
        if (raw instanceof Number){
            return ((Number) raw).longValue();
        }
        if (raw == null){
            return 0L;
        }
        return Long.parseLong(raw.toString().trim());
    }

}
