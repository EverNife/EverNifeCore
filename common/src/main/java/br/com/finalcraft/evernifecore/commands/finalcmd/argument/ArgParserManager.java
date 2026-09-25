package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;

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

    /** Who owns the parsers the core registers for itself - see {@link #addBuiltinParser}. */
    public static final String CORE_OWNER = "EverNifeCore";

    /**
     * Registers {@code parser} for {@code clazz} for every plugin's commands, owned by {@code owner}: the
     * registration leaves with {@code owner} ({@link #unregisterAll(ECPluginData)} on its shutdown), and a
     * parser another plugin had registered for the same type answers again from then on. Registering over a
     * parser another plugin owns logs a warning naming both. {@link #addPluginParser} keeps a parser to one
     * plugin's own commands instead.
     */
    public static <T> void addGlobalParser(ECPluginData owner, Class<? extends T> clazz, Class<? extends ArgParser<T>> parser){
        addGlobal(ownerName(owner), clazz, parser);
    }

    /** The contextual counterpart of {@link #addGlobalParser}, with the same ownership. */
    public static <T> void addGlobalContextualParser(ECPluginData owner, Class<? extends T> clazz, Class<? extends ArgParserContextual<T>> contextualParser){
        addGlobalContextual(ownerName(owner), clazz, contextualParser);
    }

    /**
     * A global parser the core itself provides - its builtins and each platform's own types. They are
     * registered while classes load, possibly before the core has plugin data of its own, so they are owned
     * by the core's name ({@value #CORE_OWNER}) and a plugin's {@link #unregisterAll} never reaches them.
     * Plugins register with {@link #addGlobalParser} instead.
     */
    public static <T> void addBuiltinParser(Class<? extends T> clazz, Class<? extends ArgParser<T>> parser){
        addGlobal(CORE_OWNER, clazz, parser);
    }

    /** The contextual counterpart of {@link #addBuiltinParser}. */
    public static <T> void addBuiltinContextualParser(Class<? extends T> clazz, Class<? extends ArgParserContextual<T>> contextualParser){
        addGlobalContextual(CORE_OWNER, clazz, contextualParser);
    }

    private static synchronized void addGlobal(String owner, Class<?> clazz, Class<? extends ArgParser> parser){
        GLOBAL_CONTEXT_PARSER.addParser(owner, clazz, parser);
        ECDebugModule.ARG_PARSER.debug("Added Global Parser [{}]: {} -> {}", owner, clazz.getSimpleName(), parser.getSimpleName());
        ECPluginData ecPluginData = ECPluginManager.getProvidingPlugin(parser);
        FCLocaleManager.loadLocale(ecPluginData, true, parser);
    }

    private static synchronized void addGlobalContextual(String owner, Class<?> clazz, Class<? extends ArgParserContextual> contextualParser){
        GLOBAL_CONTEXT_PARSER.addContextualParser(owner, clazz, contextualParser);
        ECDebugModule.CONTEXTUAL_ARG_PARSER.debug("Added Global ContextualParser [{}]: {} -> {}", owner, clazz.getSimpleName(), contextualParser.getSimpleName());
        ECPluginData ecPluginData = ECPluginManager.getProvidingPlugin(contextualParser);
        FCLocaleManager.loadLocale(ecPluginData, true, contextualParser);
    }

    /**
     * Takes back the global parser {@code owner} registered for {@code clazz}; the one registered before it,
     * if any, answers again. A parser another plugin owns is never touched.
     *
     * @return whether {@code owner} had one registered for {@code clazz}
     */
    public static synchronized boolean unregisterGlobalParser(ECPluginData owner, Class<?> clazz){
        return GLOBAL_CONTEXT_PARSER.removeParser(ownerName(owner), clazz);
    }

    /** The contextual counterpart of {@link #unregisterGlobalParser}. */
    public static synchronized boolean unregisterGlobalContextualParser(ECPluginData owner, Class<?> clazz){
        return GLOBAL_CONTEXT_PARSER.removeContextualParser(ownerName(owner), clazz);
    }

    /**
     * Takes back everything {@code plugin} registered: its own plugin parsers and every global one it owns.
     * The default {@code onECPluginShutdownPre} calls it together with the command teardown, so a plugin that
     * is disabled or reloaded leaves no parser behind.
     */
    public static synchronized void unregisterAll(ECPluginData plugin){
        String owner = ownerName(plugin);
        PLUGIN_CONTEXT_MAP.remove(owner);
        GLOBAL_CONTEXT_PARSER.removeOwner(owner);
    }

    private static String ownerName(ECPluginData plugin){
        return plugin.getMetaInfo().getName();
    }

    public static synchronized <T> void addPluginParser(ECPluginData plugin, Class<? extends T> clazz, Class<? extends ArgParser<T>> parser){
        PLUGIN_CONTEXT_MAP.computeIfAbsent(ownerName(plugin), s -> new ParserContext())
                .addParser(ownerName(plugin), clazz, parser);

        ECDebugModule.ARG_PARSER.debug("Added Plugin [{}] Parser: {} -> {}", ownerName(plugin), clazz.getSimpleName(), parser.getSimpleName());

        ECPluginData ecPluginData = ECPluginManager.getProvidingPlugin(parser);//Not always the same as the plugin adding it
        FCLocaleManager.loadLocale(ecPluginData, true, parser);
    }

    public static synchronized <T> void addPluginContextualParser(ECPluginData plugin, Class<? extends T> clazz, Class<? extends ArgParserContextual<T>> parser){
        PLUGIN_CONTEXT_MAP.computeIfAbsent(ownerName(plugin), s -> new ParserContext())
                .addContextualParser(ownerName(plugin), clazz, parser);

        ECDebugModule.CONTEXTUAL_ARG_PARSER.debug("Added Plugin [{}] ContextualParser: {} -> {}", ownerName(plugin), clazz.getSimpleName(), parser.getSimpleName());

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
     * <p>
     * Each type keeps every owner's registration, newest last: the newest answers, and taking it back
     * lets the one under it answer again.
     */
    private static class ParserContext{
        private final Map<Class, List<Owned<ArgParser>>> parsers = new LinkedHashMap<>();
        private final Map<Class, List<Owned<ArgParserContextual>>> contextualParsers = new LinkedHashMap<>();

        private ParserContext(){
        }

        private ParserContext(ParserContext other){
            copyInto(other.parsers, parsers);
            copyInto(other.contextualParsers, contextualParsers);
        }

        private static <P> void copyInto(Map<Class, List<Owned<P>>> from, Map<Class, List<Owned<P>>> to){
            for (Map.Entry<Class, List<Owned<P>>> entry : from.entrySet()) {
                to.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }

        public void addParser(String owner, Class argument, Class<? extends ArgParser> parser){
            push(parsers, owner, argument, parser);
        }

        public void addContextualParser(String owner, Class argument, Class<? extends ArgParserContextual> parser){
            push(contextualParsers, owner, argument, parser);
        }

        public boolean removeParser(String owner, Class argument){
            return remove(parsers, owner, argument);
        }

        public boolean removeContextualParser(String owner, Class argument){
            return remove(contextualParsers, owner, argument);
        }

        public void removeOwner(String owner){
            for (Class argument : new ArrayList<>(parsers.keySet())) {
                remove(parsers, owner, argument);
            }
            for (Class argument : new ArrayList<>(contextualParsers.keySet())) {
                remove(contextualParsers, owner, argument);
            }
        }

        /** One registration per owner and type: registering again replaces the owner's own and moves it on top. */
        private static <P> void push(Map<Class, List<Owned<P>>> registry, String owner, Class argument, Class<? extends P> parser){
            List<Owned<P>> stack = registry.computeIfAbsent(argument, k -> new ArrayList<>());
            Owned<P> previous = stack.isEmpty() ? null : stack.get(stack.size() - 1);
            stack.removeIf(registration -> registration.owner.equals(owner));
            stack.add(new Owned<P>(owner, parser));
            logOverride(argument, previous, owner, parser);
        }

        private static <P> boolean remove(Map<Class, List<Owned<P>>> registry, String owner, Class argument){
            List<Owned<P>> stack = registry.get(argument);
            if (stack == null || !stack.removeIf(registration -> registration.owner.equals(owner))){
                return false;
            }
            if (stack.isEmpty()){
                registry.remove(argument);
            }
            return true;
        }

        /**
         * Registering the same exact type twice is a deliberate override - a platform replacing a
         * builtin - so the last one wins and says so once, instead of the first one winning in silence.
         * Over ANOTHER owner's parser it is a warning: two plugins disagree on how a type is read, and
         * every command on the server now reads it the newer one's way.
         */
        private static <P> void logOverride(Class argument, Owned<P> previous, String owner, Class<? extends P> parser){
            if (previous == null || previous.parser.equals(parser)){
                return;
            }
            if (previous.owner.equals(owner)){
                EverNifeCore.getLog().info("[FinalCMD] Parser for " + argument.getSimpleName() + " overridden: "
                        + previous.parser.getSimpleName() + " -> " + parser.getSimpleName());
                return;
            }
            EverNifeCore.getLog().warning("[FinalCMD] " + owner + " registered " + parser.getSimpleName()
                    + " as the global parser for " + argument.getSimpleName() + ", over " + previous.owner + "'s "
                    + previous.parser.getSimpleName() + ": every command on this server now reads that type the "
                    + owner + " way, and " + previous.owner + "'s comes back when " + owner + " unregisters it. If only "
                    + owner + "'s own commands need it, register it with ArgParserManager.addPluginParser instead.");
        }

        public Class<? extends ArgParser> getParser(Class argument){
            return lookup(parsers, argument);
        }

        public Class<? extends ArgParserContextual> getContextualParser(Class argument){
            return lookup(contextualParsers, argument);
        }

        private static <P> Class<? extends P> lookup(Map<Class, List<Owned<P>>> registry, Class argument){
            List<Owned<P>> exact = registry.get(argument);
            if (exact != null){
                return exact.get(exact.size() - 1).parser;
            }
            for (Map.Entry<Class, List<Owned<P>>> entry : registry.entrySet()) {
                if (entry.getKey().isAssignableFrom(argument)){
                    List<Owned<P>> stack = entry.getValue();
                    return stack.get(stack.size() - 1).parser;
                }
            }
            return null;
        }
    }

    /** A parser and the plugin that registered it. */
    private static final class Owned<P>{
        private final String owner;
        private final Class<? extends P> parser;

        private Owned(String owner, Class<? extends P> parser){
            this.owner = owner;
            this.parser = parser;
        }
    }

}
