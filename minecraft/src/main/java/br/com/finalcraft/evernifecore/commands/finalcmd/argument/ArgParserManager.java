package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.util.commons.Tuple;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArgParserManager {

    private static ParserContext GLOBAL_CONTEXT_PARSER = new ParserContext();
    private static Map<String,ParserContext> PLUGIN_CONTEXT_MAP = new HashMap<>();

    public static void addGlobalParser(Class clazz, Class<? extends ArgParser> parser){
        GLOBAL_CONTEXT_PARSER.getParsers().add(Tuple.of(clazz, parser));
        EverNifeCore.getLog().debugModule(ECDebugModule.ARG_PARSER, "Added Global Parser: %s -> %s", clazz.getSimpleName(), parser.getSimpleName());
        Plugin plugin = JavaPlugin.getProvidingPlugin(parser);
        FCLocaleManager.loadLocale(plugin, true, parser);
    }

    public static void addPluginParser(Plugin plugin, Class clazz, Class<? extends ArgParser> parser){
        PLUGIN_CONTEXT_MAP.computeIfAbsent(plugin.getName(), s -> new ParserContext())
                .getParsers()
                .add(Tuple.of(clazz, parser));

        EverNifeCore.getLog().debugModule(ECDebugModule.ARG_PARSER, "Added Plugin [%s] Parser: %s -> %s", plugin.getName(), clazz.getSimpleName(), parser.getSimpleName());

        Plugin parserPlugin = JavaPlugin.getProvidingPlugin(parser); //Not always the same as the plugin adding it
        FCLocaleManager.loadLocale(parserPlugin, true, parser);
    }

    public static Class<? extends ArgParser> getParser(Plugin plugin, Class argument){
        ParserContext pluginContext = PLUGIN_CONTEXT_MAP.get(plugin.getName());
        Class<? extends ArgParser> argParser = pluginContext == null ? null : pluginContext.getParser(argument);

        if (argParser == null){
            argParser = GLOBAL_CONTEXT_PARSER.getParser(argument);
        }

        return argParser;
    }

    private static class ParserContext{
        private List<Tuple<Class, Class<? extends ArgParser>>> parsers = new ArrayList<>();

        public List<Tuple<Class, Class<? extends ArgParser>>> getParsers() {
            return parsers;
        }

        public Class<? extends ArgParser> getParser(Class argument){
            for (Tuple<Class, Class<? extends ArgParser>> parser : parsers) {
                if (parser.getLeft().equals(argument) || parser.getLeft().isAssignableFrom(argument)){
                    return parser.getRight();
                }
            }
            return null;
        }
    }

}
