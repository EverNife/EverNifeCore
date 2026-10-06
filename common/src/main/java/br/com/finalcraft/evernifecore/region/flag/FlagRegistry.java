package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.evernifecore.api.events.reload.ECPluginReloadEvent;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Every flag the server knows, by key. A flag registers at any time: on registering, its settings are read from
 * {@code <owner's data folder>/regions/flags/<name>.yml} (written with the defaults when absent and read again
 * whenever the owner reloads), and every loaded region's raw value for it becomes a live value.
 */
public final class FlagRegistry {

    private static final FlagRegistry INSTANCE = new FlagRegistry();

    private final ConcurrentHashMap<FlagKey, RegionFlag<?>> flags = new ConcurrentHashMap<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private FlagRegistry() {
        ECEventBus.global().subscribe(ECPluginReloadEvent.Post.class, event -> reloadSettings(event.getECPlugin()));
    }

    public static FlagRegistry get() {
        return INSTANCE;
    }

    /**
     * Registers {@code flag}; registering the same object again does nothing.
     *
     * @throws IllegalArgumentException when another flag already holds the key
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
        flag.setSettings(loadSettings(flag));
        flags.put(flag.getKey(), flag);
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
    }

    private void reloadSettings(ECPluginData owner) {
        for (RegionFlag<?> flag : getByOwner(owner)) {
            flag.setSettings(loadSettings(flag));
        }
    }

    private static FlagSettings loadSettings(RegionFlag<?> flag) {
        Config config = ConfigFactory.open(flag.getOwner(), "regions/flags/" + flag.getKey().getName() + ".yml");
        FlagSettings settings = config.getValueInto("", flag.newSettings());
        config.mergeValue("", settings);
        config.save();
        return settings;
    }
}
