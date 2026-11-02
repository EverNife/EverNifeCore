package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.RegionId;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.time.Window;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A plugin's own variables, one value per {@link ProgressOwner}, kept in the plugin's storage (collection
 * {@value #COLLECTION}, key {@code <name>|<owner>}) or in memory only. A defined variable is registered as the variable
 * type {@code <plugin>:<name>}, so a text reads it as {@code {plugin:name}} and {@code compare}/{@code set}/{@code add}
 * reach it like any other. Reached through {@link Variables#custom(ECPluginData, ECStorage)}.
 */
public final class CustomVariables {

    public static final String COLLECTION = "ec_variables";

    private static final Map<String, CustomVariables> OWNED = new ConcurrentHashMap<>();
    private static final Map<String, CustomVariable<?>> DEFINED = new ConcurrentHashMap<>();

    private final ECPluginData plugin;
    private final @Nullable ECStorage storage;
    private final @Nullable CachingManager<String, Row> manager;
    private final Map<String, Row> rows = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<?>> pendingWrites = ConcurrentHashMap.newKeySet();

    private CustomVariables(ECPluginData plugin, @Nullable ECStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        if (storage == null) {
            this.manager = null;
            return;
        }
        EntityDescriptor<String, Row> descriptor = EntityDescriptor.builder(String.class, Row.class)
                .collection(COLLECTION)
                .keyExtractor(row -> row.key)
                .codec(storage.defaultCodec(Row.class))
                .build();
        this.manager = storage.manager(descriptor, CachePolicy.always());
        manager.preloadAll().join(); //a variable is read on the caller's thread: the set is warmed once, here
        for (Row row : manager.cachedValues()) {
            rows.put(row.key, row);
        }
    }

    static CustomVariables of(ECPluginData owner, @Nullable ECStorage storage) {
        return OWNED.compute(owner.getMetaInfo().getName(), (name, existing) ->
                existing != null && existing.storage == storage ? existing : new CustomVariables(owner, storage));
    }

    /** Awaits the writes still on their way and forgets {@code owner}'s variables; called on its shutdown. */
    public static void closeOwnedBy(ECPluginData owner) {
        CustomVariables closed = OWNED.remove(owner.getMetaInfo().getName());
        if (closed != null) {
            DEFINED.values().removeIf(variable -> variable.owner() == closed);
            CompletableFuture.allOf(closed.pendingWrites.toArray(new CompletableFuture[0])).exceptionally(error -> null).join();
        }
    }

    /** The variable a plugin defined under {@code id} ({@code plugin:name}), while that plugin keeps it open. */
    public static Optional<CustomVariable<?>> find(String id) {
        return Optional.ofNullable(DEFINED.get(id.toLowerCase(Locale.ROOT)));
    }

    /**
     * Defines the variable {@code name} of type {@code BigDecimal}, {@code String}, {@code Boolean} or {@code Duration},
     * reading {@code initial} for an owner that never set it. Defining the same name again returns the same variable.
     */
    @SuppressWarnings("unchecked")
    public <T> CustomVariable<T> define(String name, Class<T> type, T initial) {
        if (type != BigDecimal.class && type != String.class && type != Boolean.class && type != Duration.class) {
            throw new IllegalArgumentException("The custom variable '" + name + "' is a " + type.getSimpleName() + ", which compare cannot"
                    + " read: define it as BigDecimal, String, Boolean or Duration.");
        }
        String id = RegionId.requireOwner(plugin.getMetaInfo().getName()) + ":" + RegionId.requireName("variable", name);
        Objects.requireNonNull(initial, "initial");
        boolean[] created = {false};
        //one compute: threads racing to the first define of an id all get the one variable, and only its creator registers it
        CustomVariable<?> variable = DEFINED.compute(id, (key, existing) -> {
            if (existing != null && existing.owner() == this) {
                if (existing.getType() != type) {
                    throw new IllegalArgumentException("The custom variable " + id + " is already defined as a " + existing.getType().getSimpleName()
                            + ", not a " + type.getSimpleName() + ": give the new one another name.");
                }
                return existing;
            }
            created[0] = true;
            return new CustomVariable<>(this, id, name, type, initial);
        });
        if (created[0] && !Variables.registry().find(id).isPresent()) {
            Variables.registry().register(plugin, TypeDescriptor.<Variable<?>>of(id)
                    .describe("A variable of " + plugin.getMetaInfo().getName() + ", one value per owner.")
                    .factory(params -> DEFINED.get(id))
                    .build());
        }
        return (CustomVariable<T>) variable;
    }

    public ECPluginData getPlugin() {
        return plugin;
    }

    private void write(Row row) {
        if (manager == null) {
            return;
        }
        CompletableFuture<?> write = manager.saveAndCache(row);
        pendingWrites.add(write);
        write.whenComplete((ok, error) -> {
            pendingWrites.remove(write);
            if (error != null) {
                EverNifeCore.getLog().warning("A variable of " + plugin.getMetaInfo().getName() + " was not written: " + error);
            }
        });
    }

    /** One variable of a plugin: one value per owner, optionally reset at each start of a {@link Window}. */
    public static final class CustomVariable<T> implements MutableVariable<T> {

        private final CustomVariables owner;
        private final String id;
        private final String name;
        private final Class<T> type;
        private final T initial;
        private volatile @Nullable Window window;

        private CustomVariable(CustomVariables owner, String id, String name, Class<T> type, T initial) {
            this.owner = owner;
            this.id = id;
            this.name = name;
            this.type = type;
            this.initial = initial;
        }

        private CustomVariables owner() {
            return owner;
        }

        /** Makes the value an accumulator that goes back to the initial value at each start of {@code window}. */
        public CustomVariable<T> window(Window window) {
            this.window = Objects.requireNonNull(window, "window");
            return this;
        }

        public @Nullable Window getWindow() {
            return window;
        }

        public T getInitial() {
            return initial;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Class<T> getType() {
            return type;
        }

        @Override
        public T read(ExecutionContext ctx) {
            Row row = owner.rows.get(key(ctx));
            if (row == null || row.value == null || row.windowStart != windowStart()) {
                return initial;
            }
            return decode(row.value);
        }

        @Override
        public void set(ExecutionContext ctx, T value) {
            Row row = owner.rows.computeIfAbsent(key(ctx), Row::new);
            synchronized (row) {
                row.value = encode(Objects.requireNonNull(value, "value"));
                row.windowStart = windowStart();
            }
            owner.write(row);
        }

        private String key(ExecutionContext ctx) {
            return name + "|" + ctx.getOwner();
        }

        private long windowStart() {
            Window current = window;
            if (current == null) {
                return 0L;
            }
            Instant start = current.currentStart(ECClock.now());
            return start == null ? 0L : start.toEpochMilli();
        }

        private String encode(T value) {
            return value instanceof BigDecimal ? ((BigDecimal) value).toPlainString() : value.toString();
        }

        private T decode(String text) {
            Object value;
            if (type == BigDecimal.class) {
                value = new BigDecimal(text);
            } else if (type == Boolean.class) {
                value = Boolean.parseBoolean(text);
            } else if (type == Duration.class) {
                value = Duration.parse(text);
            } else {
                value = text;
            }
            return type.cast(value);
        }

        @Override
        public String toString() {
            return "CustomVariable{" + id + ", " + type.getSimpleName() + ", window=" + window + "}";
        }
    }

    /** Internal: one owner's value of one variable as the plugin's storage keeps it. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Row {
        String key;
        @Nullable String value;
        long windowStart;

        Row() {
            //Jackson no-arg constructor
        }

        Row(String key) {
            this.key = key;
        }
    }
}
