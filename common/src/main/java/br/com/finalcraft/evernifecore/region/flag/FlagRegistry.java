package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.events.reload.ECPluginReloadEvent;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import jakarta.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Every flag the server knows, by key. A flag registers at any time: on registering, every loaded region's raw value
 * for it becomes a live value, and its settings are loaded - from {@code <owner's data folder>/regions/flags/<name>.yml},
 * under {@value #SETTINGS_ROOT}, while {@link RegionFlag#getSettingsFilePolicy()} has the file in use (written with the
 * defaults when absent), else from the code defaults. They load again when the owner reloads, and every flag's when
 * EverNifeCore reloads, since its config decides which files are in use.
 */
public final class FlagRegistry {

    /** The root the settings sit under in a flag's file. */
    public static final String SETTINGS_ROOT = "Settings";
    public static final String GENERATE_FILES_KEY = "Regions.generate-global-settings-configuration-files";

    private static final FlagRegistry INSTANCE = new FlagRegistry();

    private final ConcurrentHashMap<FlagKey, RegionFlag<?>> flags = new ConcurrentHashMap<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private volatile Map<FlagKey, List<RegionFlag<?>>> childrenByParent = Collections.emptyMap();

    private FlagRegistry() {
        ECEventBus.global().subscribe(ECPluginReloadEvent.Post.class, event -> reloadSettings(event.getECPlugin()));
    }

    public static FlagRegistry get() {
        return INSTANCE;
    }

    /**
     * Registers {@code flag}; registering the same object again does nothing.
     *
     * @throws IllegalArgumentException when another flag already holds the key, or when the flag refines a parent that
     * is not registered or that has another value type
     */
    public synchronized <T> void register(RegionFlag<T> flag) {
        RegionFlag<?> existing = flags.get(flag.getKey());
        if (existing == flag) {
            return;
        }
        if (existing != null) {
            throw new IllegalArgumentException("Flag " + flag.getKey() + " is already registered (" + existing.getClass().getName()
                    + "): its owner must unregisterAll first, or give the new flag another name.");
        }
        RegionFlag<T> parent = flag.getParent();
        if (parent != null) {
            if (get(parent.getKey()) != parent) {
                throw new IllegalArgumentException("Flag " + flag.getKey() + " refines " + parent.getKey() + ", which is not registered:"
                        + " register the parent first, then the flags that refine it.");
            }
            if (!parent.getType().getId().equals(flag.getType().getId())) {
                throw new IllegalArgumentException("Flag " + flag.getKey() + " (" + flag.getType().getId() + ") refines " + parent.getKey()
                        + " (" + parent.getType().getId() + "): a flag refines only a flag of the same value type, since it reads the"
                        + " parent's value where it is not defined.");
            }
        }
        flag.setSettings(loadSettings(flag));
        flags.put(flag.getKey(), flag);
        indexChildren();
        List<String> unreadable = new ArrayList<>();
        for (Region region : RegionLookup.get().all()) {
            if (!region.getFlagStore().relink(flag)) {
                unreadable.add(region.getId().toString());
            }
            for (AccessGroup group : region.getGroups().values()) {
                if (!group.getFlagStore().relink(flag)) {
                    unreadable.add(group.toString());
                }
            }
        }
        if (!unreadable.isEmpty()) {
            flag.getOwner().getLog().warning("Flag " + flag.getKey() + ": the stored value in " + unreadable.size() + " region(s) or group(s) " + unreadable
                    + " does not read as a " + flag.getType().getId() + " value. It is kept raw, nothing was deleted - fix it in storage"
                    + " or set the flag again on those regions.");
        }
        changed();
    }

    /**
     * Unregisters every flag of {@code owner}. Their values in loaded regions go back to the raw form, so a region
     * of another plugin keeps them until the flag returns - nothing is deleted.
     */
    public synchronized void unregisterAll(ECPluginData owner) {
        List<RegionFlag<?>> leaving = new ArrayList<>(getByOwner(owner));
        if (leaving.isEmpty()) {
            return;
        }
        for (RegionFlag<?> flag : leaving) {
            flags.remove(flag.getKey(), flag);
        }
        indexChildren(); //a refinement of another owner stays: climbing to a parent that left stops there
        for (Region region : RegionLookup.get().all()) {
            for (RegionFlag<?> flag : leaving) {
                region.getFlagStore().unlink(flag);
                for (AccessGroup group : region.getGroups().values()) {
                    group.getFlagStore().unlink(flag);
                }
            }
        }
        changed();
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

    public @Nullable RegionFlag<?> get(FlagKey key) {
        return flags.get(key);
    }

    /** By {@code owner:name}, or by the bare name when exactly one registered flag has it. */
    public Optional<RegionFlag<?>> find(String text) {
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
        RegionFlag<?> found = null;
        for (RegionFlag<?> flag : flags.values()) {
            if (flag.getKey().getName().equals(name)) {
                if (found != null) {
                    return Optional.empty();
                }
                found = flag;
            }
        }
        return Optional.ofNullable(found);
    }

    public Collection<RegionFlag<?>> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(flags.values()));
    }

    public Collection<RegionFlag<?>> getByOwner(ECPluginData owner) {
        List<RegionFlag<?>> found = new ArrayList<>();
        for (RegionFlag<?> flag : flags.values()) {
            if (flag.getOwner() == owner) {
                found.add(flag);
            }
        }
        return Collections.unmodifiableList(found);
    }

    /** The registered flags whose {@link RegionFlag#getParent() parent} is {@code flag}. */
    public List<RegionFlag<?>> getChildren(RegionFlag<?> flag) {
        List<RegionFlag<?>> children = childrenByParent.get(flag.getKey());
        return children == null ? Collections.<RegionFlag<?>>emptyList() : children;
    }

    private void indexChildren() {
        Map<FlagKey, List<RegionFlag<?>>> index = new HashMap<>();
        for (RegionFlag<?> flag : flags.values()) {
            RegionFlag<?> parent = flag.getParent();
            if (parent != null && flags.get(parent.getKey()) == parent) {
                index.computeIfAbsent(parent.getKey(), key -> new ArrayList<>()).add(flag);
            }
        }
        index.replaceAll((key, children) -> Collections.unmodifiableList(children));
        childrenByParent = index;
    }

    /** The flags that need events of {@code source}. */
    public Collection<RegionFlag<?>> getBySource(EventSource source) {
        List<RegionFlag<?>> found = new ArrayList<>();
        for (RegionFlag<?> flag : flags.values()) {
            if (flag.getNeeds().contains(source)) {
                found.add(flag);
            }
        }
        return Collections.unmodifiableList(found);
    }

    /** Forgets every flag; for tests that need an empty registry. */
    synchronized void reset() {
        flags.clear();
        indexChildren();
    }

    private void reloadSettings(ECPluginData reloaded) {
        //EverNifeCore's reload may have flipped the key that decides which files every owner's flags read
        Collection<RegionFlag<?>> affected = reloaded == EverNifeCore.getEcPluginData() ? getAll() : getByOwner(reloaded);
        for (RegionFlag<?> flag : affected) {
            flag.setSettings(loadSettings(flag));
        }
    }

    /** Whether {@code flag}'s settings file is written and read now. */
    public static boolean isSettingsFileInUse(RegionFlag<?> flag) {
        switch (flag.getSettingsFilePolicy()) {
            case ALWAYS:
                return true;
            case NEVER:
                return false;
            default:
                return ECSettings.REGIONS_GENERATE_GLOBAL_SETTINGS_FILES;
        }
    }

    private static FlagSettings loadSettings(RegionFlag<?> flag) {
        String fileName = "regions/flags/" + flag.getKey().getName() + ".yml";
        if (!isSettingsFileInUse(flag)) {
            File file = new File(flag.getOwner().getMetaInfo().getDataFolder(), fileName);
            if (file.isFile()) {
                flag.getOwner().getLog().warning("Ignoring {}: the settings files of region flags are not in use, so flag {} runs on its"
                        + " defaults and the per-region overrides. Set {} to true in EverNifeCore's config.yml to use the file, or delete it.",
                        file.getPath(), flag.getKey(), GENERATE_FILES_KEY);
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
    static String[] header(RegionFlag<?> flag, String[] banner) {
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
