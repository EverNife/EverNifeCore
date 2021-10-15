package br.com.finalcraft.evernifecore.commands.finalcmd;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class FinalCMDBuilder {

    protected boolean flagByDefault = false;
    protected List<String> aliases = Collections.EMPTY_LIST;
    protected String name;
    protected JavaPlugin javaPlugin = null;

    public FinalCMDBuilder() {
    }

    public FinalCMDBuilder(boolean flagByDefault, String... aliases) {
        this.flagByDefault = flagByDefault;
        this.aliases = Arrays.asList(aliases);
    }

    protected boolean isFlagByDefault() {
        return flagByDefault;
    }

    public FinalCMDBuilder setFlagByDefault(boolean flagByDefault) {
        this.flagByDefault = flagByDefault;
        return this;
    }

    protected List<String> getAliases() {
        return aliases;
    }

    public FinalCMDBuilder setAliases(String... aliases) {
        this.aliases = Arrays.asList(aliases);
        return this;
    }

    protected String getName() {
        return name;
    }

    protected JavaPlugin getJavaPlugin() {
        return javaPlugin;
    }

    public void setJavaPlugin(JavaPlugin javaPlugin) {
        this.javaPlugin = javaPlugin;
    }


    public HashMap<Integer, List<FinalCMDPluginCommand.TabCommand>> tabComplete = new HashMap<>();

    //<editor-fold desc="TabbComplete">
    /**
     * Adds an argument to an index with permission and the words before
     *
     * @param indice     index where the argument is in the command. /myCmd is at the index -1, so
     *                   /myCmd index0 index1 ...
     * @param permission permission to add (may be null)
     * @param arg        word to add
     * @param beforeText text preceding the argument (may be null)
     *
     * @return AMyCommand, instance of the class
     */
    public FinalCMDBuilder addOneTabbComplete(int indice, String permission, String arg, String... beforeText) {
        if (arg != null && indice >= 0) {
            if (tabComplete.containsKey(indice)) {
                tabComplete.get(indice).add(new FinalCMDPluginCommand.TabCommand(indice, arg, permission, beforeText));
            } else {
                ArrayList<FinalCMDPluginCommand.TabCommand> tabCommands = new ArrayList<>();
                tabCommands.add(new FinalCMDPluginCommand.TabCommand(indice, arg, permission, beforeText));
                tabComplete.put(indice, tabCommands);
            }
        }
        return this;
    }


    /**
     * Add multiple arguments to an index with permission and words before
     *
     * @param indice     index where the argument is in the command. /myCmd is at the index -1, so
     *                   /myCmd index0 index1 ...
     * @param permission permission to add (may be null)
     * @param arg        mots à ajouter
     * @param beforeText text preceding the argument (may be null)
     *
     * @return AMyCommand, instance of the class
     */
    public FinalCMDBuilder addListTabbComplete(int indice, String permission, String[] beforeText, String... arg) {
        if (arg != null && arg.length > 0 && indice >= 0) {
            if (tabComplete.containsKey(indice)) {
                tabComplete.get(indice).addAll(Arrays.stream(arg).collect(
                        ArrayList::new,
                        (tabCommands, s) -> tabCommands.add(new FinalCMDPluginCommand.TabCommand(indice, s, permission, beforeText)),
                        ArrayList::addAll));
            } else {
                tabComplete.put(indice, Arrays.stream(arg).collect(
                        ArrayList::new,
                        (tabCommands, s) -> tabCommands.add(new FinalCMDPluginCommand.TabCommand(indice, s, permission, beforeText)),
                        ArrayList::addAll)
                );
            }
        }
        return this;
    }
}
