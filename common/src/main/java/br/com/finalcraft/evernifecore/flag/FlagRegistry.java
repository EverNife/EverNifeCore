package br.com.finalcraft.evernifecore.flag;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.action.ActionSupport;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.events.reload.ECPluginReloadEvent;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.config.plugin.RegionSettings;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.flag.core.ActorDenyFlag;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionId;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.everyconfig.config.Config;
import jakarta.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Every flag the server knows, by key. A flag registers at any time: on registering, every loaded region's raw value
 * for it becomes a live value, and its settings are loaded - from {@code <owner's data folder>/regions/flags/<name>.yml},
 * under {@value #SETTINGS_ROOT}, while {@link Flag#getSettingsFilePolicy()} has the file in use (written with the
 * defaults when absent), else from the code defaults. They load again when the owner reloads and when the owner's
 * {@link RegionManager} takes new settings, since those decide whether its files are in use.
 */
public final class FlagRegistry {

    /** The root the settings sit under in a flag's file. */
    public static final String SETTINGS_ROOT = "Settings";
    public static final String GENERATE_FILES_KEY = "Regions.generate-global-settings-configuration-files";

    private static final FlagRegistry INSTANCE = new FlagRegistry();

    private final ConcurrentHashMap<FlagKey, Flag<?>> flags = new ConcurrentHashMap<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private volatile Map<FlagKey, List<Flag<?>>> childrenByParent = Collections.emptyMap();
    private volatile Set<IPlayerStateAccess.Capability> missingCapabilities = Collections.emptySet();
    // The line each flag last logged: a platform declares its sources and its capabilities apart, and a re-check must
    // not repeat a line the boot log already holds.
    private final Map<FlagKey, String> warned = new HashMap<>();

    private FlagRegistry() {
        ECEventBus.global().subscribe(ECPluginReloadEvent.Post.class, event -> reloadSettingsOf(event.getECPlugin()));
    }

    public static FlagRegistry get() {
        return INSTANCE;
    }

    /**
     * Registers {@code flag} under {@code owner}, the plugin its key names, and binds it to that plugin
     * ({@link Flag#getOwner()}); registering the same object again under the same owner does nothing.
     *
     * @throws IllegalArgumentException when the key names another plugin, when the flag is registered by another owner,
     * when another flag already holds the key, when the flag refines a parent that is not registered or that has
     * another value type, when it shares a channel with a registered flag and neither declares how they interact
     * ({@link Flag#getInteractions()}), or when its settings name a resolver it does not accept
     */
    public synchronized <T> void register(ECPluginData owner, Flag<T> flag) {
        Flag<?> existing = flags.get(flag.getKey());
        if (existing == flag) {
            if (flag.getOwner() == owner) {
                return;
            }
            throw new IllegalArgumentException("Flag " + flag.getKey() + " is registered by " + flag.getOwner().getMetaInfo().getName()
                    + ": unregister it there before registering it again under " + owner.getMetaInfo().getName() + ".");
        }
        if (existing != null) {
            throw new IllegalArgumentException("Flag " + flag.getKey() + " is already registered (" + existing.getClass().getName()
                    + "): unregister that one first, or give the new flag another name.");
        }
        String expected = RegionId.requireOwner(owner.getMetaInfo().getName());
        if (!flag.getKey().getOwner().equals(expected)) {
            throw new IllegalArgumentException("Flag " + flag.getKey() + " cannot be registered by " + owner.getMetaInfo().getName()
                    + ": a flag is registered by the plugin its key names. Register it from that plugin, or build its key with"
                    + " FlagKey.of(\"" + expected + "\", \"" + flag.getKey().getName() + "\").");
        }
        Flag<T> parent = flag.getParent();
        if (parent != null) {
            if (!parent.isRegistered()) {
                throw new IllegalArgumentException("Flag " + flag.getKey() + " refines " + parent.getKey() + ", which is not registered:"
                        + " register the parent first, then the flags that refine it.");
            }
            if (!parent.getType().getId().equals(flag.getType().getId())) {
                throw new IllegalArgumentException("Flag " + flag.getKey() + " (" + flag.getType().getId() + ") refines " + parent.getKey()
                        + " (" + parent.getType().getId() + "): a flag refines only a flag of the same value type, since it reads the"
                        + " parent's value where it is not defined.");
            }
        }
        checkInteractions(flag);
        flag.bind(owner);
        try {
            FlagSettings settings = loadSettings(flag);
            flag.checkResolve(settings);
            flag.setSettings(settings);
        } catch (RuntimeException unreadable) {
            flag.unbind();
            throw unreadable;
        }
        flags.put(flag.getKey(), flag);
        indexChildren();
        List<String> unreadable = new ArrayList<>();
        for (Region region : RegionLookup.get().all()) {
            if (!region.getFlagStore().relink(flag)) {
                unreadable.add(region.getId().toString());
            }
            for (Group group : region.getGroups().values()) {
                if (!group.getFlagStore().relink(flag)) {
                    unreadable.add(group.toString());
                }
            }
        }
        for (FlagHolder holder : ActorHolders.all()) {
            if (!holder.getFlagStore().relink(flag)) {
                unreadable.add(holder.getHolderId());
            }
        }
        warnIfActingInPart(flag);
        if (!unreadable.isEmpty()) {
            owner.getLog().warning("Flag " + flag.getKey() + ": the stored value in " + unreadable.size() + " region(s) or group(s) " + unreadable
                    + " does not read as a " + flag.getType().getId() + " value. It is kept raw, nothing was deleted - fix it in storage"
                    + " or set the flag again on those regions.");
        }
        changed();
    }

    /**
     * Unregisters {@code flag}. Its values in loaded regions go back to the raw form, so they come back when a flag
     * with the same key registers - nothing is deleted. A refinement of it stays registered: climbing to a parent that
     * left stops there.
     *
     * @return false when this instance was not registered
     */
    public synchronized boolean unregister(Flag<?> flag) {
        if (!flag.isRegistered()) {
            return false;
        }
        flags.remove(flag.getKey(), flag);
        warned.remove(flag.getKey());
        indexChildren();
        for (Region region : RegionLookup.get().all()) {
            region.getFlagStore().unlink(flag);
            for (Group group : region.getGroups().values()) {
                group.getFlagStore().unlink(flag);
            }
        }
        for (FlagHolder holder : ActorHolders.all()) {
            holder.getFlagStore().unlink(flag);
        }
        flag.unbind();
        changed();
        return true;
    }

    /** {@link #unregister} for every flag of {@code owner}. */
    public synchronized void unregisterAll(ECPluginData owner) {
        for (Flag<?> flag : getByOwner(owner)) {
            unregister(flag);
        }
    }

    /**
     * Internal: after the platform declared what it raises ({@link ActionSupport#declare}), each flag that acts in part
     * here says so in its owner's log, one line per flag; a flag registering later says so on registering. A flag whose
     * line has not changed since it last logged it stays quiet. A flag with no effect at all logs nothing
     * ({@link #isSupported}).
     */
    public synchronized void warnFlagsActingInPart() {
        for (Flag<?> flag : flags.values()) {
            warnIfActingInPart(flag);
        }
    }

    /**
     * Platform: the player-state capabilities this platform lacks ({@link IPlayerStateAccess#getUnsupported()}). From now on
     * each flag that needs one of them is not supported here ({@link #isSupported}). Empty takes it back.
     */
    public synchronized void setMissingCapabilities(Collection<IPlayerStateAccess.Capability> missing) {
        missingCapabilities = missing.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(missing));
        warnFlagsActingInPart();
    }

    /** The capabilities {@code flag} needs that this platform lacks, in declaration order; empty when it has them all. */
    public List<IPlayerStateAccess.Capability> getMissingCapabilities(Flag<?> flag) {
        Set<IPlayerStateAccess.Capability> missing = missingCapabilities;
        List<IPlayerStateAccess.Capability> lacking = new ArrayList<>();
        for (IPlayerStateAccess.Capability capability : IPlayerStateAccess.Capability.values()) {
            if (missing.contains(capability) && flag.getNeededCapabilities().contains(capability)) {
                lacking.add(capability);
            }
        }
        return lacking;
    }

    /** The core sources {@code flag} needs that the platform does not raise ({@link ActionSupport#isServed}); empty when it raises them all. */
    public List<EventSource> getUnservedSources(Flag<?> flag) {
        List<EventSource> unserved = new ArrayList<>();
        for (EventSource source : flag.getNeeds()) {
            if (!source.equals(EventSource.NONE) && !ActionSupport.isServed(source)) {
                unserved.add(source);
            }
        }
        return unserved;
    }

    /**
     * Whether {@code flag} can do anything on this platform, all derived from what the platform declared. An unsupported
     * flag is silent and inert: no boot log, out of the completions and the flag listings of the verbs, which refuse its
     * name; a region may hold its value, and nothing asks it. It is unsupported when a source of
     * {@link Flag#getRequiredSources()} is not raised, when no source it needs is, when a capability it needs is missing,
     * when it refines another flag only for actors the platform never classifies, or when it says so itself
     * ({@link Flag#isAvailableOnPlatform()}).
     */
    public boolean isSupported(Flag<?> flag) {
        if (!flag.isAvailableOnPlatform()) {
            return false;
        }
        List<EventSource> unserved = getUnservedSources(flag);
        if (!unserved.isEmpty()) {
            if (flag.getNeeds().stream().allMatch(source -> source.equals(EventSource.NONE) || unserved.contains(source))) {
                return false;
            }
            for (EventSource required : flag.getRequiredSources()) {
                if (unserved.contains(required)) {
                    return false;
                }
            }
        }
        return getMissingCapabilities(flag).isEmpty()
                && !(flag instanceof ActorDenyFlag && ((ActorDenyFlag) flag).getActorEntries().stream().allMatch(ActionSupport::neverClassifies));
    }

    /** The line a flag that is supported but misses some of its sources logs; null for one that has its whole effect, or none. */
    public @Nullable String describePartialEffect(Flag<?> flag) {
        List<EventSource> unserved = getUnservedSources(flag);
        return unserved.isEmpty() || !isSupported(flag) ? null : "Flag " + flag.getKey() + " acts in part on this platform: it needs " + unserved
                + ", which this server never raises; the rest applies.";
    }

    private void warnIfActingInPart(Flag<?> flag) {
        String line = describePartialEffect(flag);
        String before = line == null ? warned.remove(flag.getKey()) : warned.put(flag.getKey(), line);
        if (line != null && !line.equals(before)) {
            flag.getOwner().getLog().warning(line);
        }
    }

    /** Internal: runs {@code listener} after every register and unregister - raw values became live, or live ones raw. */
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    private void changed() {
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    public @Nullable Flag<?> get(FlagKey key) {
        return flags.get(key);
    }

    /** By {@code owner:name}, or by the bare name when exactly one registered flag has it. */
    public Optional<Flag<?>> find(String text) {
        if (text == null) {
            return Optional.empty();
        }
        if (text.indexOf(':') >= 0) {
            try {
                return Optional.ofNullable(flags.get(FlagKey.parse(text)));
            } catch (IllegalArgumentException notAKey) {
                return Optional.empty();
            }
        }
        String name = text.toLowerCase(Locale.ROOT);
        Flag<?> found = null;
        for (Flag<?> flag : flags.values()) {
            if (flag.getKey().getName().equals(name)) {
                if (found != null) {
                    return Optional.empty();
                }
                found = flag;
            }
        }
        return Optional.ofNullable(found);
    }

    public Collection<Flag<?>> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(flags.values()));
    }

    /** The flags {@link #isSupported supported} here: what a verb lists, completes or accepts by name. */
    public Collection<Flag<?>> getSupported() {
        List<Flag<?>> found = new ArrayList<>();
        for (Flag<?> flag : flags.values()) {
            if (isSupported(flag)) {
                found.add(flag);
            }
        }
        return Collections.unmodifiableList(found);
    }

    /** {@link #find}, for a name typed on a verb: a flag not {@link #isSupported supported} here does not exist for it. */
    public Optional<Flag<?>> findSupported(String text) {
        return find(text).filter(this::isSupported);
    }

    /** {@code values} without those of the flags not {@link #isSupported supported} here, for a verb to list. */
    public Map<FlagKey, FlagValue<?>> supportedOf(Map<FlagKey, FlagValue<?>> values) {
        Map<FlagKey, FlagValue<?>> found = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            Flag<?> flag = flags.get(key);
            if (flag == null || isSupported(flag)) {
                found.put(key, value);
            }
        });
        return found;
    }

    public Collection<Flag<?>> getByOwner(ECPluginData owner) {
        List<Flag<?>> found = new ArrayList<>();
        for (Flag<?> flag : flags.values()) {
            if (flag.getOwner() == owner) {
                found.add(flag);
            }
        }
        return Collections.unmodifiableList(found);
    }

    /** The registered flags whose {@link Flag#getParent() parent} is {@code flag}. */
    public List<Flag<?>> getChildren(Flag<?> flag) {
        List<Flag<?>> children = childrenByParent.get(flag.getKey());
        return children == null ? Collections.<Flag<?>>emptyList() : children;
    }

    private void indexChildren() {
        Map<FlagKey, List<Flag<?>>> index = new HashMap<>();
        for (Flag<?> flag : flags.values()) {
            Flag<?> parent = flag.getParent();
            if (parent != null && parent.isRegistered() && flags.get(parent.getKey()) == parent) {
                index.computeIfAbsent(parent.getKey(), key -> new ArrayList<>()).add(flag);
            }
        }
        index.replaceAll((key, children) -> Collections.unmodifiableList(children));
        childrenByParent = index;
    }

    /** The {@link #isSupported supported} flags that need events of {@code source}: the ones to ask about such an event. */
    public Collection<Flag<?>> getBySource(EventSource source) {
        List<Flag<?>> found = new ArrayList<>();
        for (Flag<?> flag : flags.values()) {
            if (flag.getNeeds().contains(source) && isSupported(flag)) {
                found.add(flag);
            }
        }
        return Collections.unmodifiableList(found);
    }

    /** Forgets every flag, unbinding each; for tests that need an empty registry. */
    synchronized void reset() {
        for (Flag<?> flag : flags.values()) {
            flag.unbind();
        }
        flags.clear();
        indexChildren();
    }

    /**
     * Reads the settings of every flag of {@code owner} again - from its file while the file is in use, else the defaults.
     * Runs on the owner's reload and when its {@code RegionManager} takes new settings; a flag whose new settings it
     * refuses keeps the ones it had.
     */
    public void reloadSettingsOf(ECPluginData owner) {
        for (Flag<?> flag : getByOwner(owner)) {
            FlagSettings settings = loadSettings(flag);
            try {
                flag.checkResolve(settings);
            } catch (IllegalArgumentException refused) {
                flag.getOwner().getLog().warning(refused.getMessage() + " The settings it had before the reload stay.");
                continue;
            }
            flag.setSettings(settings);
        }
    }

    /** Every registered flag that shares a channel with {@code flag} must have one of the two declare how they interact. */
    private void checkInteractions(Flag<?> flag) {
        if (flag.getChannels().isEmpty()) {
            return;
        }
        for (Flag<?> other : flags.values()) {
            for (String channel : other.getChannels()) {
                if (flag.getChannels().contains(channel) && !declares(flag, other) && !declares(other, flag)) {
                    throw new IllegalArgumentException("Flags " + other.getKey() + " and " + flag.getKey() + " both act on " + channel
                            + " and neither declares how they interact. Add FlagInteraction.with(<the other key>, \"<the rule>\") to"
                            + " getInteractions() of one of them, or give yours another channel.");
                }
            }
        }
    }

    private static boolean declares(Flag<?> flag, Flag<?> other) {
        for (FlagInteraction interaction : flag.getInteractions()) {
            if (interaction.getOther().equals(other.getKey())) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code flag}'s settings file is written and read now. */
    public static boolean isSettingsFileInUse(Flag<?> flag) {
        switch (flag.getSettingsFilePolicy()) {
            case ALWAYS:
                return true;
            case NEVER:
                return false;
            default:
                if (isCoreFlag(flag)) {
                    return ECSettings.REGIONS_GENERATE_GLOBAL_SETTINGS_FILES;
                }
                RegionManager manager = RegionManager.ofIfPresent(flag.getOwner());
                return manager != null && manager.getSettings().isFlagSettingsFiles();
        }
    }

    private static boolean isCoreFlag(Flag<?> flag) {
        return flag.getOwner() == EverNifeCore.getEcPluginData();
    }

    /** Where the owner of {@code flag} turns its settings files on, for a log line. */
    private static String fileSwitchOf(Flag<?> flag) {
        return isCoreFlag(flag) ? GENERATE_FILES_KEY + " in EverNifeCore's config.yml"
                : RegionSettings.SECTION + ".flag-settings-files in " + flag.getOwner().getMetaInfo().getName() + "'s config.yml";
    }

    private static FlagSettings loadSettings(Flag<?> flag) {
        String fileName = "regions/flags/" + flag.getKey().getName() + ".yml";
        if (!isSettingsFileInUse(flag)) {
            File file = new File(flag.getOwner().getMetaInfo().getDataFolder(), fileName);
            if (file.isFile()) {
                flag.getOwner().getLog().warning("Ignoring {}: the settings files of region flags are not in use, so flag {} runs on its"
                        + " defaults and the per-region overrides. Set {} to true to use the file, or delete it.",
                        file.getPath(), flag.getKey(), fileSwitchOf(flag));
            }
            return flag.newSettings();
        }
        Config config = ConfigFactory.open(flag.getOwner(), fileName);
        config.setHeader(header(flag, ConfigFactory.standardHeader(flag.getOwner())));
        FlagSettings settings = config.getValueInto(SETTINGS_ROOT, flag.newSettings());
        config.mergeValue(SETTINGS_ROOT, settings);
        config.save();
        return settings;
    }

    /** The standard banner, then what the flag is and the time format its fields take. */
    static String[] header(Flag<?> flag, String[] banner) {
        List<String> lines = new ArrayList<>(Arrays.asList(banner));
        lines.add("");
        lines.add(" Flag: " + flag.getKey());
        String description = flag.getDescription();
        if (description != null && !description.isEmpty()) {
            lines.add(" What it does: " + description);
        }
        lines.add(" Value type: " + flag.getType().getId());
        lines.add(" Server-wide settings of this flag; a region may override them. Times: " + FCTimeFrame.ACCEPTED_FORMAT);
        lines.add("");
        lines.add(banner[banner.length - 1]);
        return lines.toArray(new String[0]);
    }
}
