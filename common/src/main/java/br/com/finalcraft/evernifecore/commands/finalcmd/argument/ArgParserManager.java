package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.everylibs.commons.Tuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ArgParserManager {

    //volatile: restoreRegistry replaces both of these wholesale, and a command dispatch reads them
    //from whatever thread it arrived on
    private static volatile ParserContext GLOBAL_CONTEXT_PARSER = new ParserContext();
    private static volatile Map<String,ParserContext> PLUGIN_CONTEXT_MAP = new HashMap<>();

    public static <T> void addGlobalParser(Class<? extends T> clazz, Class<? extends ArgParser<T>> parser){
        GLOBAL_CONTEXT_PARSER.addParser(clazz, parser);
        ECDebugModule.ARG_PARSER.debug("Added Global Parser: {} -> {}", clazz.getSimpleName(), parser.getSimpleName());
        ECPluginData ecPluginData = ECPluginManager.getProvidingPlugin(parser);
        FCLocaleManager.loadLocale(ecPluginData, true, parser);
    }

    public static <T> void addGlobalContextualParser(Class<? extends T> clazz, Class<? extends ArgParserContextual<T>> contextualParser){
        GLOBAL_CONTEXT_PARSER.addContextualParser(clazz, contextualParser);
        ECDebugModule.CONTEXTUAL_ARG_PARSER.debug("Added Global ContextualParser: {} -> {}", clazz.getSimpleName(), contextualParser.getSimpleName());
        ECPluginData ecPluginData = ECPluginManager.getProvidingPlugin(contextualParser);
        FCLocaleManager.loadLocale(ecPluginData, true, contextualParser);
    }

    public static <T> void addPluginParser(ECPluginData plugin, Class<? extends T> clazz, Class<? extends ArgParser<T>> parser){
        PLUGIN_CONTEXT_MAP.computeIfAbsent(plugin.getMetaInfo().getName(), s -> new ParserContext())
                .addParser(clazz, parser);

        ECDebugModule.ARG_PARSER.debug("Added Plugin [{}] Parser: {} -> {}", plugin.getMetaInfo().getName(), clazz.getSimpleName(), parser.getSimpleName());

        ECPluginData ecPluginData = ECPluginManager.getProvidingPlugin(parser);//Not always the same as the plugin adding it
        FCLocaleManager.loadLocale(ecPluginData, true, parser);
    }

    public static <T> void addPluginContextualParser(ECPluginData plugin, Class<? extends T> clazz, Class<? extends ArgParserContextual<T>> parser){
        PLUGIN_CONTEXT_MAP.computeIfAbsent(plugin.getMetaInfo().getName(), s -> new ParserContext())
                .addContextualParser(clazz, parser);

        ECDebugModule.CONTEXTUAL_ARG_PARSER.debug("Added Plugin [{}] ContextualParser: {} -> {}", plugin.getMetaInfo().getName(), clazz.getSimpleName(), parser.getSimpleName());

        ECPluginData ecPluginData = ECPluginManager.getProvidingPlugin(parser);//Not always the same as the plugin adding it
        FCLocaleManager.loadLocale(ecPluginData, true, parser);
    }

    public static Class<? extends ArgParser> getParser(ECPluginData plugin, Class argument){
        ParserContext pluginContext = PLUGIN_CONTEXT_MAP.get(plugin.getMetaInfo().getName());
        Class<? extends ArgParser> argParser = pluginContext == null ? null : pluginContext.getParser(argument);

        if (argParser == null){
            argParser = GLOBAL_CONTEXT_PARSER.getParser(argument);
        }

        return argParser;
    }

    public static Class<? extends ArgParserContextual> getContextualParser(ECPluginData plugin, Class argument){
        ParserContext pluginContext = PLUGIN_CONTEXT_MAP.get(plugin.getMetaInfo().getName());
        Class<? extends ArgParserContextual> argParser = pluginContext == null ? null : pluginContext.getContextualParser(argument);

        if (argParser == null){
            argParser = GLOBAL_CONTEXT_PARSER.getContextualParser(argument);
        }

        return argParser;
    }

    /**
     * A copy of the registry as it stands right now, to be handed back to
     * {@link #restoreRegistry(RegistrySnapshot)} later.
     * <p>
     * The registry is process-wide and lives as long as the JVM, so a parser registered anywhere
     * answers every lookup that follows it. A caller whose registrations have a bounded lifetime -
     * a harness driving the framework in a reused JVM, a plugin that comes and goes - takes one of
     * these before registering, and what it added stops answering once the snapshot goes back.
     */
    public static RegistrySnapshot snapshotRegistry(){
        return new RegistrySnapshot(GLOBAL_CONTEXT_PARSER, PLUGIN_CONTEXT_MAP);
    }

    /**
     * Puts back exactly what the snapshot captured, dropping every registration made after it.
     *
     * <p>The swap is atomic per field, not per dispatch: a command already resolving its arguments
     * goes on reading the registry it started with, and the next one reads the restored one. Restore
     * where no dispatch is in flight - between tests, or while the plugin that registered them is
     * being unloaded.</p>
     */
    public static void restoreRegistry(RegistrySnapshot snapshot){
        GLOBAL_CONTEXT_PARSER = new ParserContext(snapshot.global);
        PLUGIN_CONTEXT_MAP = copyOf(snapshot.pluginContexts);
    }

    private static Map<String,ParserContext> copyOf(Map<String,ParserContext> source){
        Map<String,ParserContext> copy = new HashMap<>();
        for (Map.Entry<String,ParserContext> entry : source.entrySet()) {
            copy.put(entry.getKey(), new ParserContext(entry.getValue()));
        }
        return copy;
    }

    /**
     * The registry captured at one point in time. Copied on the way in and on the way out, so
     * neither side can reach the other's parsers - see {@link #snapshotRegistry()}.
     */
    public static final class RegistrySnapshot {
        private final ParserContext global;
        private final Map<String,ParserContext> pluginContexts;

        private RegistrySnapshot(ParserContext global, Map<String,ParserContext> pluginContexts){
            this.global = new ParserContext(global);
            this.pluginContexts = copyOf(pluginContexts);
        }
    }

    /**
     * One registry, read in two passes: the type ITSELF first, and only then the registered types it
     * can be assigned to, in registration order.
     * <p>
     * The order of the two passes is the whole point. Registering a parser for a subtype after one for
     * its supertype used to be a silent no-op - the supertype matched first and answered for both -
     * which is exactly the shape every platform registration has, since the builtins are in place
     * before {@code registerArgParsers()} ever runs. Asking for the type itself first means
     * "I registered a parser for MyType" holds no matter what was registered before it.
     */
    private static class ParserContext{
        private final Map<Class, Class<? extends ArgParser>> exactParsers = new LinkedHashMap<>();
        private final Map<Class, Class<? extends ArgParserContextual>> exactContextualParsers = new LinkedHashMap<>();
        private final List<Tuple<Class, Class<? extends ArgParser>>> argParsers = new ArrayList<>();
        private final List<Tuple<Class, Class<? extends ArgParserContextual>>> contextualArgParsers = new ArrayList<>();

        private ParserContext(){
        }

        private ParserContext(ParserContext other){
            exactParsers.putAll(other.exactParsers);
            exactContextualParsers.putAll(other.exactContextualParsers);
            argParsers.addAll(other.argParsers);
            contextualArgParsers.addAll(other.contextualArgParsers);
        }

        public void addParser(Class argument, Class<? extends ArgParser> parser){
            Class<? extends ArgParser> previous = exactParsers.put(argument, parser);
            replace(argParsers, argument, parser, previous);
            logOverride(argument, previous, parser);
        }

        public void addContextualParser(Class argument, Class<? extends ArgParserContextual> parser){
            Class<? extends ArgParserContextual> previous = exactContextualParsers.put(argument, parser);
            replace(contextualArgParsers, argument, parser, previous);
            logOverride(argument, previous, parser);
        }

        /** Keeps the assignable pass consistent with the exact one: one entry per type, its place kept. */
        private static <P> void replace(List<Tuple<Class, P>> registered, Class argument, P parser, P previous){
            if (previous == null){
                registered.add(Tuple.of(argument, parser));
                return;
            }
            for (int i = 0; i < registered.size(); i++) {
                if (registered.get(i).getLeft().equals(argument)){
                    registered.set(i, Tuple.of(argument, parser));
                    return;
                }
            }
        }

        /**
         * Registering the same exact type twice is a deliberate override - a platform replacing a
         * builtin - so the last one wins and says so once, instead of the first one winning in silence.
         */
        private static void logOverride(Class argument, Object previous, Object parser){
            if (previous != null && !previous.equals(parser)){
                EverNifeCore.getLog().info("[FinalCMD] Parser for " + argument.getSimpleName() + " overridden: "
                        + ((Class<?>) previous).getSimpleName() + " -> " + ((Class<?>) parser).getSimpleName());
            }
        }

        public Class<? extends ArgParser> getParser(Class argument){
            Class<? extends ArgParser> exact = exactParsers.get(argument);
            if (exact != null){
                return exact;
            }
            for (Tuple<Class, Class<? extends ArgParser>> parser : argParsers) {
                if (parser.getLeft().isAssignableFrom(argument)){
                    return parser.getRight();
                }
            }
            return null;
        }

        public Class<? extends ArgParserContextual> getContextualParser(Class argument){
            Class<? extends ArgParserContextual> exact = exactContextualParsers.get(argument);
            if (exact != null){
                return exact;
            }
            for (Tuple<Class, Class<? extends ArgParserContextual>> parser : contextualArgParsers) {
                if (parser.getLeft().isAssignableFrom(argument)){
                    return parser.getRight();
                }
            }
            return null;
        }
    }

}
