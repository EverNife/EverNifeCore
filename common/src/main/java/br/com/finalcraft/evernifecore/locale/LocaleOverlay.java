package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Per-locale translations of the text inside a plugin's YAML definitions (quests, menus, dialogues).
 *
 * <p>A definition stays in its author's language. Each other language is a flat file,
 * {@code <data>/localization/overlay/<LOCALE>.yml}, whose keys are the stable path of a text leaf -
 * {@code <definitionId>.<path inside the definition>}, e.g. {@code mine-ten-ores.stages.mine.objectives.ore.description} -
 * and whose values have the shape of a {@link FancyText} in a config (a string, a list of lines, or a
 * {@code text}/{@code hoverText}/... map). {@link OverlayExporter} writes that file for a translator.</p>
 *
 * <p>A leaf may also carry its languages inline: {@code description: {EN_US: "...", PT_BR: "..."}} - {@code pt-BR} reads as
 * {@code PT_BR}.</p>
 *
 * <p>The text of a leaf for a locale is, first match wins: the overlay file of that locale, the inline entry of
 * that locale, the inline entry of the plugin's language, the author's text (the plain leaf, or the first inline
 * entry). Every {@code text} call hands out a fresh {@link FancyText}, so a caller may replace placeholders in it.</p>
 */
public final class LocaleOverlay {

    /** The leaf keys that are text when a definition is registered with no list of its own. */
    public static final Set<String> DEFAULT_TEXT_KEYS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "name", "display-name", "description", "lore", "title", "subtitle", "message", "messages", "text", "hover")));

    /** The folder, under the plugin's data folder, that holds one {@code <LOCALE>.yml} per language. */
    public static final String FOLDER = "localization/overlay";

    private static final Pattern LOCALE_KEY = Pattern.compile("[A-Za-z]{2,3}[_-][A-Za-z]{2,3}");

    private static final Map<ECPluginData, LocaleOverlay> OVERLAYS = new ConcurrentHashMap<>();

    private final ECPluginData owner;
    private final Map<String, Definition> definitions = new ConcurrentHashMap<>();
    //locale -> stable path -> value; the whole map is swapped by reload(), never edited in place
    private volatile Map<String, Map<String, JsonNode>> files = Collections.emptyMap();

    private LocaleOverlay(ECPluginData owner) {
        this.owner = owner;
    }

    /** The overlay of {@code owner}, created (and its files read) on first use. */
    public static LocaleOverlay of(ECPluginData owner) {
        return OVERLAYS.computeIfAbsent(owner, created -> {
            LocaleOverlay overlay = new LocaleOverlay(created);
            overlay.reload();
            return overlay;
        });
    }

    /** The overlay of {@code owner} if one was ever asked for, or {@code null}. */
    public static @Nullable LocaleOverlay ofIfPresent(ECPluginData owner) {
        return OVERLAYS.get(owner);
    }

    /** Drops the overlay of {@code owner} - its shutdown. */
    public static void closeOwnedBy(ECPluginData owner) {
        OVERLAYS.remove(owner);
    }

    public ECPluginData getOwner() {
        return owner;
    }

    public File getFolder() {
        return new File(owner.getMetaInfo().getDataFolder(), FOLDER);
    }

    /** The overlay file of {@code locale}, which may not exist yet. */
    public File getFile(String locale) {
        return new File(getFolder(), normalize(locale) + ".yml");
    }

    /**
     * Registers the definition {@code definitionId} whose text leaves can be translated. A leaf is text when its
     * key is one of {@code textKeys} ({@link #DEFAULT_TEXT_KEYS} when none is given) and its value has the shape
     * of a text or of an inline language map; anything inside a list has no stable path and is not a leaf.
     * Registering the same id again replaces the definition (a definition reload).
     */
    public void register(String definitionId, ConfigSection root, String... textKeys) {
        register(definitionId, root.getNode(), textKeys);
    }

    /** {@link #register(String, ConfigSection, String...)} over the definition's tree as read, for one built in code too. */
    public void register(String definitionId, @Nullable JsonNode root, String... textKeys) {
        if (definitionId == null || definitionId.isEmpty()) {
            throw new IllegalArgumentException("A definition needs an id: it is the first segment of every path in the overlay files.");
        }
        Set<String> keys = textKeys.length == 0 ? DEFAULT_TEXT_KEYS : new LinkedHashSet<>(Arrays.asList(textKeys));
        Map<String, Leaf> leaves = new LinkedHashMap<>();
        if (root != null) {
            collect(root, "", keys, leaves);
        }
        definitions.put(definitionId, new Definition(Collections.unmodifiableMap(leaves)));
    }

    public void unregister(String definitionId) {
        definitions.remove(definitionId);
    }

    public boolean isRegistered(String definitionId) {
        return definitions.containsKey(definitionId);
    }

    /** The ids registered, sorted. */
    public Set<String> getDefinitionIds() {
        return Collections.unmodifiableSet(new TreeSet<>(definitions.keySet()));
    }

    /** The paths inside {@code definitionId} that are text leaves, in document order - what {@link #text} accepts. */
    public Set<String> textPaths(String definitionId) {
        return definition(definitionId).leaves.keySet();
    }

    /**
     * The text at {@code path} of {@code definitionId} for {@code locale} ({@code null} = the plugin's language),
     * following the order in the class javadoc. Any thread.
     *
     * @throws IllegalArgumentException for an id that is not registered or a path that is not a text leaf
     */
    public FancyText text(String definitionId, String path, @Nullable String locale) {
        Leaf leaf = leaf(definitionId, path);
        String wanted = locale == null ? getPluginLanguage() : normalize(locale);
        JsonNode translated = fileValue(wanted, definitionId, path);
        return toFancyText(translated != null ? translated : leaf.fallback(wanted, getPluginLanguage()));
    }

    /** Only the overlay file of {@code locale}: the translation of that leaf, or {@code null} when it has none. */
    public @Nullable FancyText translated(String definitionId, String path, String locale) {
        leaf(definitionId, path);
        JsonNode translated = fileValue(normalize(locale), definitionId, path);
        return translated == null ? null : toFancyText(translated);
    }

    /** Re-reads every overlay file in {@link #getFolder()}. Blocking file I/O. */
    public void reload() {
        Map<String, Map<String, JsonNode>> read = new HashMap<>();
        File[] found = getFolder().listFiles((dir, name) -> name.endsWith(".yml"));
        if (found != null) {
            for (File file : found) {
                String locale = normalize(file.getName().substring(0, file.getName().length() - ".yml".length()));
                Config config = ConfigFactory.open(file);
                Map<String, JsonNode> values = new HashMap<>();
                //the keys are flat and dotted: read the root's own fields, never through the dotted-path API
                Iterator<Map.Entry<String, JsonNode>> fields = config.getRoot().fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    values.put(field.getKey(), field.getValue().deepCopy());
                }
                read.put(locale, Collections.unmodifiableMap(values));
            }
        }
        files = Collections.unmodifiableMap(read);
    }

    // ------------------------------------------------------------------
    //  Internals shared with OverlayExporter
    // ------------------------------------------------------------------

    /** What the translator starts from: the text of the leaf for {@code locale} without the overlay file. */
    JsonNode seed(String definitionId, String path, String locale) {
        return leaf(definitionId, path).fallback(normalize(locale), getPluginLanguage());
    }

    //PT_BR, pt_BR and the tag form pt-BR (what java.util.Locale writes and a YAML author often types) are one locale
    private static String normalize(String locale) {
        return LocaleType.normalize(locale.replace('-', '_'));
    }

    private String getPluginLanguage() {
        String language = owner.getPluginLanguage();
        return normalize(language == null ? FCLocaleManager.DEFAULT_EVERNIFECORE_LOCALE : language);
    }

    private @Nullable JsonNode fileValue(String locale, String definitionId, String path) {
        Map<String, JsonNode> file = files.get(locale);
        return file == null ? null : file.get(definitionId + "." + path);
    }

    private Definition definition(String definitionId) {
        Definition definition = definitions.get(definitionId);
        if (definition == null) {
            throw new IllegalArgumentException("No definition '" + definitionId + "' is registered in the LocaleOverlay of "
                    + owner.getMetaInfo().getName() + ". Registered: " + getDefinitionIds()
                    + ". Register it with LocaleOverlay.of(plugin).register(id, root) when the definition loads.");
        }
        return definition;
    }

    private Leaf leaf(String definitionId, String path) {
        Definition definition = definition(definitionId);
        Leaf leaf = definition.leaves.get(path);
        if (leaf == null) {
            throw new IllegalArgumentException("'" + path + "' is not a text leaf of the definition '" + definitionId
                    + "'. Its text leaves are: " + definition.leaves.keySet()
                    + ". A key outside DEFAULT_TEXT_KEYS is text only when the register call names it.");
        }
        return leaf;
    }

    private static void collect(JsonNode node, String prefix, Set<String> textKeys, Map<String, Leaf> leaves) {
        if (!node.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String path = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
            JsonNode value = field.getValue();
            if (textKeys.contains(field.getKey())) {
                Leaf leaf = Leaf.of(value);
                if (leaf != null) {
                    leaves.put(path, leaf);
                    continue;
                }
            }
            collect(value, path, textKeys, leaves);
        }
    }

    private static FancyText toFancyText(JsonNode node) {
        try {
            return ConfigFactory.inMemoryCodec().getObjectMapper().treeToValue(node, FancyText.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read a text out of " + node + ": " + e.getOriginalMessage(), e);
        }
    }

    private static final class Definition {
        private final Map<String, Leaf> leaves;

        private Definition(Map<String, Leaf> leaves) {
            this.leaves = leaves;
        }
    }

    /** One text leaf: the author's text and, when the leaf is an inline language map, every language it carries. */
    private static final class Leaf {
        private final JsonNode author;
        private final Map<String, JsonNode> inline;

        private Leaf(JsonNode author, Map<String, JsonNode> inline) {
            this.author = author;
            this.inline = inline;
        }

        /** The leaf the value is, or {@code null} when the value is a structure that only shares a text key's name. */
        static @Nullable Leaf of(JsonNode value) {
            if (value.isValueNode() && !value.isNull()) {
                return new Leaf(value.deepCopy(), Collections.emptyMap());
            }
            if (value.isArray()) {
                for (JsonNode line : value) {
                    if (!line.isValueNode()) {
                        return null;
                    }
                }
                return new Leaf(value.deepCopy(), Collections.emptyMap());
            }
            if (!value.isObject() || value.size() == 0) {
                return null;
            }
            if (value.has("text") || value.has("formatter")) {
                return new Leaf(value.deepCopy(), Collections.emptyMap());
            }
            Map<String, JsonNode> inline = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!LOCALE_KEY.matcher(field.getKey()).matches()) {
                    return null;
                }
                inline.put(normalize(field.getKey()), field.getValue().deepCopy());
            }
            //the first language the author wrote is the author's own
            return new Leaf(inline.values().iterator().next(), Collections.unmodifiableMap(inline));
        }

        JsonNode fallback(String locale, String pluginLanguage) {
            JsonNode value = inline.get(locale);
            if (value == null) {
                value = inline.get(pluginLanguage);
            }
            return value != null ? value : author;
        }
    }
}
