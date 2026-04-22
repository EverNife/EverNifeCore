package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.util.commons.Tuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArgParserManager {

    private static ParserContext GLOBAL_CONTEXT_PARSER = new ParserContext();
    private static Map<String,ParserContext> PLUGIN_CONTEXT_MAP = new HashMap<>();

    public static <T> void addGlobalParser(Class<? extends T> clazz, Class<? extends ArgParser<T>> parser){
        GLOBAL_CONTEXT_PARSER.getParsers().add(Tuple.of(clazz, parser));
        ECDebugModule.ARG_PARSER.debugModule("Added Global Parser: %s -> %s", clazz.getSimpleName(), parser.getSimpleName());
        ECPluginData ecPluginData = ECPluginManager.getProvidingPlugin(parser);
        FCLocaleManager.loadLocale(ecPluginData, true, parser);
    }

    public static <T> void addGlobalContextualParser(Class<? extends T> clazz, Class<? extends ArgParserContextual<T>> contextualParser){
        GLOBAL_CONTEXT_PARSER.getContextualArgParsers().add(Tuple.of(clazz, contextualParser));
        ECDebugModule.CONTEXTUAL_ARG_PARSER.debugModule("Added Global ContextualParser: %s -> %s", clazz.getSimpleName(), contextualParser.getSimpleName());
        ECPluginData ecPluginData = ECPluginManager.getProvidingPlugin(contextualParser);
        FCLocaleManager.loadLocale(ecPluginData, true, contextualParser);
    }

    public static <T> void addPluginParser(ECPluginData plugin, Class<? extends T> clazz, Class<? extends ArgParser<T>> parser){
        PLUGIN_CONTEXT_MAP.computeIfAbsent(plugin.getMetaInfo().getName(), s -> new ParserContext())
                .getParsers()
                .add(Tuple.of(clazz, parser));

        EverNifeCore.getLog().debugModule(ECDebugModule.ARG_PARSER, "Added Plugin [%s] Parser: %s -> %s", plugin.getMetaInfo().getName(), clazz.getSimpleName(), parser.getSimpleName());

        ECPluginData ecPluginData = ECPluginManager.getProvidingPlugin(parser);//Not always the same as the plugin adding it
        FCLocaleManager.loadLocale(ecPluginData, true, parser);
    }

    public static <T> void addPluginContextualParser(ECPluginData plugin, Class<? extends T> clazz, Class<? extends ArgParserContextual<T>> parser){
        PLUGIN_CONTEXT_MAP.computeIfAbsent(plugin.getMetaInfo().getName(), s -> new ParserContext())
                .getContextualArgParsers()
                .add(Tuple.of(clazz, parser));

        EverNifeCore.getLog().debugModule(ECDebugModule.CONTEXTUAL_ARG_PARSER, "Added Plugin [%s] ContextualParser: %s -> %s", plugin.getMetaInfo().getName(), clazz.getSimpleName(), parser.getSimpleName());

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

    private static class ParserContext{
        private List<Tuple<Class, Class<? extends ArgParser>>> argParsers = new ArrayList<>();
        private List<Tuple<Class, Class<? extends ArgParserContextual>>> contextualArgParsers = new ArrayList<>();

        public List<Tuple<Class, Class<? extends ArgParser>>> getParsers() {
            return argParsers;
        }

        public List<Tuple<Class, Class<? extends ArgParserContextual>>> getContextualArgParsers() {
            return contextualArgParsers;
        }

        public Class<? extends ArgParser> getParser(Class argument){
            for (Tuple<Class, Class<? extends ArgParser>> parser : argParsers) {
                if (parser.getLeft().equals(argument) || parser.getLeft().isAssignableFrom(argument)){
                    return parser.getRight();
                }
            }
            return null;
        }

        public Class<? extends ArgParserContextual> getContextualParser(Class argument){
            for (Tuple<Class, Class<? extends ArgParserContextual>> parser : contextualArgParsers) {
                if (parser.getLeft().equals(argument) || parser.getLeft().isAssignableFrom(argument)){
                    return parser.getRight();
                }
            }
            return null;
        }
    }

}
