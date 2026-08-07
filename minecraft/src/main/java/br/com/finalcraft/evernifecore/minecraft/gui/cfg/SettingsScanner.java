package br.com.finalcraft.evernifecore.minecraft.gui.cfg;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.everyconfig.binding.BindException;
import br.com.finalcraft.everyconfig.binding.BindResult;
import br.com.finalcraft.everyconfig.binding.LoadIssue;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everyconfig.rule.RuleEvaluation;
import br.com.finalcraft.everyconfig.rule.RuleEvaluator;
import br.com.finalcraft.everyconfig.rule.RuleFinding;
import br.com.finalcraft.everyconfig.rule.RuleModel;
import br.com.finalcraft.everyconfig.rule.RulePolicy;
import br.com.finalcraft.everyconfig.rule.RuleSite;
import br.com.finalcraft.everyconfig.rule.ValueSource;
import br.com.finalcraft.everyconfig.ruleset.StandardRules;
import com.fasterxml.jackson.databind.JsonNode;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fills an object's {@link ConfigSetting} fields from a config file: it seeds each key with the field's own
 * value the first time, reads back what the file says from then on, and judges the result against the
 * semantic rules declared on the field.
 *
 * <p>What a refusal costs depends on where the value came from, and that difference is the whole point:
 *
 * <ul>
 *   <li>a value the FILE supplied is user data - it is logged once, replaced by the handler's correction
 *       or by the field's own default, and the server keeps booting;</li>
 *   <li>a value the field's own DEFAULT supplied is a code defect - no file can fix it and every run
 *       reproduces it - so it throws.</li>
 * </ul>
 *
 * <p>It works on any class. The requirements are a field carrying {@link ConfigSetting} and a non-null
 * value in it: the field's value is both the default written to a fresh file and the type the stored value
 * is read back as.
 */
public class SettingsScanner {

    /** The standard vocabulary under this scanner's policy: file data warns instead of failing the boot, a
     *  default that breaks its own rule still throws, and a handler may correct what it refused. */
    private static final RuleEvaluator EVALUATOR = RuleEvaluator.of(StandardRules.engine())
            .withPolicy(RulePolicy.defaults()
                    .withSeverity(RulePolicy.Severity.LOG)
                    .withCorrections(true));

    /** What has already been warned about, so a file nobody fixed does not repeat itself on every reload. */
    private static final Set<String> WARNED =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    /**
     * Load every {@link ConfigSetting} of {@code instance} - its own and the ones it inherits - from
     * {@code config}, seeding what the file lacks.
     *
     * @throws BindException when a field's own default breaks a rule declared on that same field
     */
    public static void load(ECPluginData ecPluginData, Config config, Object instance) {
        Class<?> type = instance.getClass();
        Map<Field, List<RuleSite>> rules = rulesByField(type);

        for (Field field : settingFields(type)) {
            ConfigSetting setting = field.getAnnotation(ConfigSetting.class);
            String key = setting.key();
            Object defaultValue = valueOf(field, instance);

            if (defaultValue == null) {
                ecPluginData.getLog().warning("Skipping the ConfigSetting '" + key + "' on "
                        + type.getSimpleName() + "." + field.getName() + ": the field has no default value, "
                        + "which is what seeds the file and names the type to read it back as. Initialize it.");
                continue;
            }

            List<RuleSite> sites = sitesOf(rules, field);
            if (sites.isEmpty() && Modifier.isStatic(field.getModifiers()) && declaresRule(field)) {
                ecPluginData.getLog().warning("The rules on " + type.getSimpleName() + "." + field.getName()
                        + " will never be checked: a static field is not a config site. Make it an instance "
                        + "field, or drop the rule.");
            }

            boolean fromFile = config.contains(key);
            config.setValueIfAbsent(key, defaultValue,
                    comment(setting, ecPluginData.getPluginLanguage(), sites));

            ValueSource source = fromFile ? ValueSource.FILE : ValueSource.DEFAULT;
            Object value = defaultValue;
            if (fromFile) {
                Object stored = storedValue(ecPluginData, type, config, key, field, defaultValue);
                if (stored == null) {
                    //A value the file supplied and the read then discarded is still file-sourced: the type
                    //is already the complaint, and calling it a default would make @Explicit fire on top of it
                    warnOnce(ecPluginData, type, key, "unreadable",
                            "The value at '" + key + "' cannot be read as "
                                    + defaultValue.getClass().getSimpleName() + ". Fix it in the file; "
                                    + "until then the default " + defaultValue + " is in use.");
                } else {
                    value = stored;
                }
            }

            for (RuleSite site : sites) {
                RuleEvaluation evaluation = EVALUATOR.evaluate(site, config.getConfigSection(key), value,
                        source, instance);
                //A handler that corrected has answered its own refusal; one that merely refused leaves the
                //field's default as the only value left to use
                Object surviving = evaluation.corrected() ? evaluation.value()
                        : evaluation.findings().isEmpty() ? value : defaultValue;
                for (RuleFinding finding : evaluation.findings()) {
                    if (finding.severity() == RulePolicy.Severity.THROW) {
                        throw new BindException(finding.message());
                    }
                    //what the rule refused is only half the news; the other half is what the server is
                    //running on until someone acts on it
                    warnOnce(ecPluginData, type, key, site.rule().annotationType().getName(),
                            finding.message() + " The value in use is '" + surviving + "'.");
                }
                value = surviving;
            }

            inject(ecPluginData, field, instance, value);
        }
    }

    /**
     * The {@link ConfigSetting} fields of the whole hierarchy, base class first and in declaration order: a
     * layout inherits its parent's settings, and the file they seed reads from the base down.
     */
    private static List<Field> settingFields(Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> clazz = type; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            hierarchy.add(0, clazz);
        }
        List<Field> fields = new ArrayList<>();
        for (Class<?> clazz : hierarchy) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(ConfigSetting.class)) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    /** Every rule EveryConfig resolves for the type, indexed by the field carrying it. */
    private static Map<Field, List<RuleSite>> rulesByField(Class<?> type) {
        Map<Field, List<RuleSite>> byField = new HashMap<>();
        for (RuleSite site : RuleModel.of(type, EVALUATOR.engine().selector())) {
            if (site.kind() != RuleSite.Kind.FIELD) {
                continue;
            }
            List<RuleSite> sites = byField.get(site.field());
            if (sites == null) {
                sites = new ArrayList<>();
                byField.put(site.field(), sites);
            }
            sites.add(site);
        }
        return byField;
    }

    private static List<RuleSite> sitesOf(Map<Field, List<RuleSite>> rules, Field field) {
        List<RuleSite> sites = rules.get(field);
        return sites != null ? sites : Collections.<RuleSite>emptyList();
    }

    /** Whether the field declares a rule the engine would claim - asked only where no site exists to
     *  claim it, so a declaration that can never fire is reported instead of ignored. */
    private static boolean declaresRule(Field field) {
        for (Annotation annotation : field.getDeclaredAnnotations()) {
            if (EVALUATOR.engine().selector().claims(annotation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * What the file says above the key: the comment written for the plugin's language, then a line per rule
     * describing itself, so the admin reads "At most 100." without opening any documentation.
     */
    private static String comment(ConfigSetting setting, String language, List<RuleSite> sites) {
        List<String> lines = new ArrayList<>();
        FCLocale[] comments = setting.comment();
        if (comments.length > 0) {
            FCLocale chosen = comments[0];
            for (FCLocale candidate : comments) {
                if (candidate.lang().equalsIgnoreCase(language)) {
                    chosen = candidate;
                    break;
                }
            }
            lines.add(chosen.text());
        }
        for (RuleSite site : sites) {
            lines.addAll(EVALUATOR.engine().describe(site));
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    /**
     * The stored value read as the default's own type, or null when the file holds something that cannot be
     * read as it. A list is read element-typed - from the field's type argument, since the default may be
     * empty - and an empty list IS an answer, because emptying a list is how an admin removes every entry.
     *
     * <p>A list degrades one entry at a time: an entry the file got wrong costs that entry and the rest of
     * the list still loads. The reading itself is silent about it, so the loss is reported here, naming the
     * entries that caused it - a menu that quietly lost a button is a menu nobody knows is broken.</p>
     */
    private static Object storedValue(ECPluginData ecPluginData, Class<?> type, Config config, String key,
                                      Field field, Object defaultValue) {
        if (defaultValue instanceof List) {
            Class<?> elementType = elementType(field, (List<?>) defaultValue);
            BindResult<? extends List<?>> result = config.getListResult(key, elementType);
            List<?> stored = result.value();
            JsonNode node = config.getNode(key);
            boolean isList = node != null && node.isArray();
            if (isList && node.size() > stored.size()) {
                warnOnce(ecPluginData, type, key, "entries", (node.size() - stored.size()) + " of the "
                        + node.size() + " entries at '" + key + "' cannot be read as "
                        + elementType.getSimpleName() + " and were left out"
                        + brokenEntries(result.issues()) + ". The others are in use; fix or "
                        + "delete the broken ones.");
            }
            return stored.isEmpty() && !isList ? null : stored;
        }
        return config.getValue(key, defaultValue.getClass());
    }

    /** The keys of the entries the read refused, as ' (Layout[1], Layout[3])' - empty when the read lost an
     *  entry without naming one, so the count above is all the message can honestly claim. */
    private static String brokenEntries(List<LoadIssue> issues) {
        if (issues.isEmpty()) {
            return "";
        }
        List<String> keys = new ArrayList<>();
        for (LoadIssue issue : issues) {
            keys.add(issue.key());
        }
        return " (" + String.join(", ", keys) + ")";
    }

    /** The declared type argument of a list field, falling back to what the default holds and finally to
     *  {@code Object} - the same fallback EveryConfig's own seeding uses for an untyped default. */
    private static Class<?> elementType(Field field, List<?> defaultValue) {
        Type generic = field.getGenericType();
        if (generic instanceof ParameterizedType) {
            Type[] arguments = ((ParameterizedType) generic).getActualTypeArguments();
            if (arguments.length == 1 && arguments[0] instanceof Class) {
                return (Class<?>) arguments[0];
            }
        }
        return defaultValue.isEmpty() ? Object.class : defaultValue.get(0).getClass();
    }

    private static Object valueOf(Field field, Object instance) {
        try {
            field.setAccessible(true);
            return field.get(instance);
        } catch (Exception unreadable) {
            return null;
        }
    }

    private static void inject(ECPluginData ecPluginData, Field field, Object instance, Object value) {
        try {
            field.setAccessible(true);
            field.set(instance, value);
        } catch (Exception unwritable) {
            ecPluginData.getLog().warning("Could not write " + instance.getClass().getSimpleName() + "."
                    + field.getName() + ": " + unwritable);
        }
    }

    /** Warn once per site, not once per load: the same broken line on every reload teaches nothing new. */
    private static void warnOnce(ECPluginData ecPluginData, Class<?> type, String key, String about,
                                 String message) {
        if (WARNED.add(type.getName() + '#' + key + '#' + about)) {
            ecPluginData.getLog().warning(message);
        }
    }
}
