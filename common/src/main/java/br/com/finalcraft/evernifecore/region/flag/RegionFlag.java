package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.SessionContext;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A flag a region (or one of its groups) can carry. A new flag is a new subclass that overrides what it does -
 * {@link #decide} for protection, {@link #onEnter}/{@link #onLeave}/{@link #onRevalidate} for a session effect -
 * never a listener of its own. Each flag declares its default and how ties resolve; there is no global rule.
 *
 * <p>The flag's name says what TRUE does: {@code deny-pvp = TRUE} denies PvP.</p>
 *
 * <p>A flag may refine another of the same {@link FlagType} ({@link #getParent()}): for an action both
 * {@link #applies apply} to, the refinement is the one asked, and where no region defines it, it reads the value of
 * the nearest ancestor that is defined - the decision stays the refinement's own.</p>
 */
public abstract class RegionFlag<T> {

    private final FlagKey key;
    private volatile @Nullable ECPluginData owner;
    private volatile boolean registered;
    private final FlagType<T> type;
    private final Set<EventSource> needs;
    private final @Nullable T defaultValue;
    private final @Nullable RegionFlag<T> parent;
    private volatile @Nullable FlagSettings settings;

    /**
     * @param key   names the plugin that will register the flag
     * @param needs the events the flag must hear; {@link EventSource#NONE} for a value nobody enforces
     */
    protected RegionFlag(FlagKey key, FlagType<T> type, Set<EventSource> needs, @Nullable T defaultValue) {
        this(key, type, needs, defaultValue, null);
    }

    /**
     * @param parent the flag this one refines - registered before it, with the same {@link FlagType}; null for none
     */
    protected RegionFlag(FlagKey key, FlagType<T> type, Set<EventSource> needs, @Nullable T defaultValue, @Nullable RegionFlag<T> parent) {
        this.parent = parent;
        this.key = Objects.requireNonNull(key, "key");
        this.type = Objects.requireNonNull(type, "type");
        this.needs = Collections.unmodifiableSet(needs.isEmpty() ? EnumSet.noneOf(EventSource.class) : EnumSet.copyOf(needs));
        this.defaultValue = defaultValue;
    }

    public FlagKey getKey() {
        return key;
    }

    /**
     * The plugin that registered this flag.
     *
     * @throws IllegalStateException while the flag is not registered
     */
    public ECPluginData getOwner() {
        ECPluginData current = owner;
        if (current == null) {
            throw new IllegalStateException("Flag " + key + " is not registered, so it has no owner yet:"
                    + " FlagRegistry.get().register(owner, flag) binds it to the plugin that registers it.");
        }
        return current;
    }

    /** Whether {@link FlagRegistry} holds this very instance now. */
    public boolean isRegistered() {
        return registered;
    }

    void bind(ECPluginData owner) {
        this.owner = owner;
        this.registered = true;
    }

    void unbind() {
        this.registered = false;
        this.owner = null;
    }

    public FlagType<T> getType() {
        return type;
    }

    /** The value when no region defines the flag. */
    public @Nullable T getDefault() {
        return defaultValue;
    }

    public Set<EventSource> getNeeds() {
        return needs;
    }

    /**
     * The flag this one refines, or null. It is fixed at construction and must be registered first, so a chain of
     * parents can never loop back to the flag that starts it.
     */
    public @Nullable RegionFlag<T> getParent() {
        return parent;
    }

    /**
     * Whether this flag speaks about {@code context} at all. A flag that does not apply is never asked, and a parent is
     * not asked about an action one of its refinements applies to.
     */
    public boolean applies(ActionContext context) {
        return true;
    }

    public FlagCategory getCategory() {
        return FlagCategory.PROTECTION;
    }

    /** Whether the bypass permission lifts this flag for a player; false for a flag that must hold for everyone. */
    public boolean isBypassable() {
        return true;
    }

    /** Picks one of several distinct values defined at the same priority, in a deterministic order. */
    public abstract T resolveTie(List<T> values);

    /** {@link Decision#DENY} cancels the action. */
    public Decision decide(ActionContext context, T value) {
        return Decision.PASS;
    }

    /**
     * As {@link #decide(ActionContext, Object)}, with the settings in effect where the value came from - the region's own
     * for this flag, else the server's. Override this one when the settings narrow what the value covers.
     */
    public Decision decide(ActionContext context, T value, FlagSettings settings) {
        return decide(context, value);
    }

    /** Runs after {@link #decide} denied (or after the value applied), at most once per {@link FlagSettings#getTriggerInterval()}. */
    public void onApplied(ActionContext context, T value) {
    }

    /**
     * As {@link #onApplied(ActionContext, Object)}, with the settings {@link #decide(ActionContext, Object, FlagSettings)} saw.
     * Override this one when the settings tell whether the value applied was a denial.
     */
    public void onApplied(ActionContext context, T value, FlagSettings settings) {
        onApplied(context, value);
    }

    public Revalidation getRevalidation() {
        return Revalidation.onBoundary();
    }

    /** The effective value went from none to {@code value}. */
    public void onEnter(SessionContext context, T value) {
    }

    /** The effective value went from {@code lastValue} to none: the player left or quit, or the flag was removed or expired. */
    public void onLeave(SessionContext context, T lastValue) {
    }

    /** {@link #getRevalidation()} asked for a new look and the value may have changed. */
    public void onRevalidate(SessionContext context, @Nullable T oldValue, @Nullable T newValue) {
    }

    /** What the flag does, in a sentence for a server admin - the header of its settings file shows it. Null omits the line. */
    public @Nullable String getDescription() {
        return null;
    }

    public Class<? extends FlagSettings> getSettingsType() {
        return FlagSettings.class;
    }

    /** The trigger interval a fresh settings object of this flag starts with; zero runs the effect on every denial. */
    public Duration getDefaultTriggerInterval() {
        return Duration.ZERO;
    }

    /**
     * Whether the server-wide settings of this flag live in a file. While none is written, the flag runs on the
     * defaults of {@link #getSettingsType()} and on the overrides a region sets ({@code Region.setFlagSettings}).
     */
    public SettingsFilePolicy getSettingsFilePolicy() {
        return SettingsFilePolicy.FOLLOW_SERVER;
    }

    /**
     * Read from the settings file when the flag registers, again when its owner or EverNifeCore reloads; the defaults
     * when the file is not in use ({@link #getSettingsFilePolicy()}).
     */
    @SuppressWarnings("unchecked")
    public <S extends FlagSettings> S getSettings() {
        FlagSettings current = settings;
        if (current == null) {
            current = newSettings();
            settings = current;
        }
        return (S) current;
    }

    void setSettings(FlagSettings settings) {
        this.settings = settings;
    }

    FlagSettings newSettings() {
        Class<? extends FlagSettings> settingsType = getSettingsType();
        try {
            FlagSettings settings = settingsType.getDeclaredConstructor().newInstance();
            settings.setTriggerInterval(getDefaultTriggerInterval()); //the flag's floor seeds the defaults and the generated file
            return settings;
        } catch (ReflectiveOperationException noDefault) {
            throw new IllegalStateException("The settings of flag " + key + " (" + settingsType.getName() + ") need a no-arg constructor:"
                    + " they are built with their defaults before the file is read.", noDefault);
        }
    }

    @Override
    public String toString() {
        return key.toString();
    }
}
