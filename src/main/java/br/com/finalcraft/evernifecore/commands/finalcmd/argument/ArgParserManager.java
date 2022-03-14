package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.util.commons.Tuple;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArgParserManager {

    private static ParserContext GLOBAL_CONTEXT_PARSER = new ParserContext();
    private static Map<String,ParserContext> PARSER_MAP = new HashMap<>();

    public static void addGlobalParser(Class clazz, Class<? extends ArgParser> parser){
        GLOBAL_CONTEXT_PARSER.getParsers().add(Tuple.of(clazz, parser));

        Plugin plugin = JavaPlugin.getProvidingPlugin(parser);
        FCLocaleManager.loadLocale(plugin, parser);
    }

    public static void addPluginParser(Plugin plugin, Class clazz, Class<? extends ArgParser> parser){
        PARSER_MAP.computeIfAbsent(plugin.getName(), s -> new ParserContext())
                .getParsers()
                .add(Tuple.of(clazz, parser));

        Plugin parserPlugin = JavaPlugin.getProvidingPlugin(parser); //Not always the same as the plugin adding it
        FCLocaleManager.loadLocale(parserPlugin, parser);
    }

    public static Class<? extends ArgParser> getParser(Plugin plugin, Class argument){
        return PARSER_MAP.getOrDefault(
                plugin.getName(), GLOBAL_CONTEXT_PARSER
        ).getParser(argument);
    }

    private static class ParserContext{
        private List<Tuple<Class, Class<? extends ArgParser>>> parsers = new ArrayList<>();

        public List<Tuple<Class, Class<? extends ArgParser>>> getParsers() {
            return parsers;
        }

        public Class<? extends ArgParser> getParser(Class argument){
            for (Tuple<Class, Class<? extends ArgParser>> parser : parsers) {
                if (parser.getAlfa().equals(argument) || parser.getAlfa().isAssignableFrom(argument)){
                    return parser.getBeta();
                }
            }
            return null;
        }
    }

}
