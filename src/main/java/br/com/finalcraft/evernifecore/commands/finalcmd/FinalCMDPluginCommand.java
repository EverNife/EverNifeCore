package br.com.finalcraft.evernifecore.commands.finalcmd;

import br.com.finalcraft.evernifecore.commands.finalcmd.executor.ExecutorInterpreter;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.FCDefaultExecutor;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.SubCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLine;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

//Reference * https://gist.github.com/redsarow/46a9eb30991bf6007508f72aba7da89f
public class FinalCMDPluginCommand extends Command implements PluginIdentifiableCommand{

    public JavaPlugin owningPlugin;
    private boolean registered = false;
    public HashMap<Integer, List<TabCommand>> tabComplete = new HashMap<>();
    public HashMap<Integer, List<SubCommand>> subCommands = new HashMap<>();
    //
    //KEY: 0
    //VALUES: 0
    //   SUB_CMD  ? /sharelag add <Player>
    //   SUB_CMD  ? /sharelag remove <Player>
    //   SUB_CMD  ? /sharelag list
    //
    //
    public HelpContext helpContext;                      // Imuttable Context from all HelpLines from all SubCmds (come from list 'helpLineList' bellow)
    public List<HelpLine> helpLineList = new ArrayList<>();
    public HelpLine mainHelpLine;
    public IFinalCMDExecutor executor;
    public List<Field> localeMessageFields = new ArrayList<>();

    private @Nullable br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD finalCMDAnnotation = null;

    /**
     * @param owningPlugin plugin responsible of the command.
     * @param name   name of the command.
     */
    public FinalCMDPluginCommand(JavaPlugin owningPlugin, String name) {
        super(name);

        assert owningPlugin != null;
        assert name != null;
        assert name.length() > 0;

        setLabel(name);
        this.owningPlugin = owningPlugin;
    }

    private void assureNotRegistered(){
        if (registered) throw new IllegalStateException("This command has already been registered, you cant change it anymore!");
    }

    /**
     * @param aliases aliases of the command.
     *
     * @return AMyCommand, instance of the class
     */
    public FinalCMDPluginCommand setAliases(String... aliases) {
        assureNotRegistered();
        assert aliases != null;
        assert aliases.length > 0;
        super.setAliases(Arrays.stream(aliases).filter(aliase -> !aliase.equalsIgnoreCase(this.getName())).collect(Collectors.toList()));
        return this;
    }

    /**
     * @param finalCMDAnnotation annotation of the command.
     *
     * @return A MyCommand, instance of the class
     */
    public FinalCMDPluginCommand setUsage(br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD finalCMDAnnotation) {
        assureNotRegistered();
        assert finalCMDAnnotation != null;
        String use = "§3§l ▶ §a/§e%label% " + finalCMDAnnotation.usage().replace("%label%","");
        super.setUsage(use);
        this.finalCMDAnnotation = finalCMDAnnotation;
        return this;
    }

    /**
     * @param use use of the command (ex: /myCmd [val1]
     *
     * @return AMyCommand, instance of the class
     */
    @Override
    public FinalCMDPluginCommand setUsage(String use) {
        assureNotRegistered();
        assert use != null;
        super.setUsage(use);
        return this;
    }

    /**
     * @param description description of the command
     *
     * @return AMyCommand, instance of the class
     */
    @Override
    public FinalCMDPluginCommand setDescription(String description) {
        assureNotRegistered();
        if (description != null)
            super.setDescription(description);
        return this;
    }

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
    public FinalCMDPluginCommand addOneTabbComplete(int indice, String permission, String arg, String... beforeText) {
        assureNotRegistered();
        if (arg != null && indice >= 0) {
            if (tabComplete.containsKey(indice)) {
                tabComplete.get(indice).add(new TabCommand(indice, arg, permission, beforeText));
            } else {
                ArrayList<TabCommand> tabCommands = new ArrayList<>();
                tabCommands.add(new TabCommand(indice, arg, permission, beforeText));
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
    public FinalCMDPluginCommand addListTabbComplete(int indice, String permission, String[] beforeText, String... arg) {
        assureNotRegistered();
        if (arg != null && arg.length > 0 && indice >= 0) {
            if (tabComplete.containsKey(indice)) {
                tabComplete.get(indice).addAll(Arrays.stream(arg).collect(
                        ArrayList::new,
                        (tabCommands, s) -> tabCommands.add(new TabCommand(indice, s, permission, beforeText)),
                        ArrayList::addAll));
            } else {
                tabComplete.put(indice, Arrays.stream(arg).collect(
                        ArrayList::new,
                        (tabCommands, s) -> tabCommands.add(new TabCommand(indice, s, permission, beforeText)),
                        ArrayList::addAll)
                );
            }
        }
        return this;
    }

    /**
     * Add multiple arguments to an index
     *
     * @param indice index where the argument is in the command. /myCmd is at the index -1, so
     *               /myCmd index0 index1 ...
     * @param arg    mots à ajouter
     *
     * @return AMyCommand, instance of the class
     */
    public FinalCMDPluginCommand addListTabbComplete(int indice, String... arg) {
        assureNotRegistered();
        if (arg != null && arg.length > 0 && indice >= 0) {
            addListTabbComplete(indice, null, null, arg);
        }
        return this;
    }

    /**
     * add permission to command
     *
     * @param permission permission to add (may be null)
     *
     * @return AMyCommand, instance of the class
     */
    public FinalCMDPluginCommand addPermission(String permission) {
        assureNotRegistered();
        if (permission != null)
            setPermission(permission);
        return this;
    }

    /**
     * @param permissionMessage message if the player does not have permission
     *
     * @return AMyCommand, instance of the class
     */
    public FinalCMDPluginCommand addPermissionMessage(String permissionMessage) {
        assureNotRegistered();
        if (permissionMessage != null)
            setPermissionMessage(permissionMessage);
        return this;
    }

    public void addLocaleMessages(List<Field> localeMessages){
        localeMessages.forEach(field -> field.setAccessible(true));
        localeMessageFields.addAll(localeMessages);
    }

    /**
     * @return true if the command has been successfully registered
     */
    public boolean registerCommand() {
        assureNotRegistered();
        registered = true;

        FinalCMDManager.unregisterCommand(this.getName());
        for (String alias : this.getAliases()) {
            FinalCMDManager.unregisterCommand(alias);
        }

        this.helpContext = new HelpContext(this);

        mainHelpLine = new HelpLine(
                new FancyText(getUsage())
                        .setHoverText(getDescription()),
                getPermission()
        );
        return FinalCMDManager.getCommandMap().register(owningPlugin.getName(), this);
    }

    /**
     * @return plugin responsible for the command
     */
    @Override
    public JavaPlugin getPlugin() {
        return this.owningPlugin;
    }

    /**
     * Sets the {@link CommandExecutor} to run when parsing this command
     *
     * @param executor New executor to run
     */
    public FinalCMDPluginCommand setExecutor(IFinalCMDExecutor executor) {
        this.executor = executor;
        return this;
    }

    public FinalCMDPluginCommand setExecutor(final Method method, final Object executorObj, br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD finalCMD){
        this.executor = new FCDefaultExecutor(this, new ExecutorInterpreter(method, executorObj), finalCMD);
        return this;
    }

    public FinalCMDPluginCommand setExecutor(br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD finalCMD){
        this.executor = new FCDefaultExecutor(this, finalCMD);
        return this;
    }

    /**
     * Gets the {@link IFinalCMDExecutor} associated with this command
     *
     * @return IFinalCMDExecutor object linked to this command
     */
    public IFinalCMDExecutor getExecutor() {
        return executor;
    }

    /**
     * @return tabComplete
     */
    public HashMap<Integer, List<TabCommand>> getTabComplete() {
        return tabComplete;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        boolean success = false;

        if (!owningPlugin.isEnabled()) {
            throw new CommandException("Cannot execute command '" + commandLabel + "' in plugin " + owningPlugin.getDescription().getFullName() + " - plugin is disabled.");
        }

        try {
            success = executor.onCommand(sender, this, commandLabel, args);
        } catch (Throwable ex) {
            throw new CommandException("Unhandled exception executing command '" + commandLabel + "' in plugin " + owningPlugin.getDescription().getFullName(), ex);
        }

        if (!success && usageMessage.length() > 0) {
            for (String line : usageMessage.replace("<command>", commandLabel).split("\n")) {
                sender.sendMessage(line);
            }
        }

        return success;
    }

    public SubCommand getSubCommand(String[] args) {

        for (int indice = 0; indice < args.length; indice++) {
            String theArg = args[indice].toLowerCase();
            List<SubCommand> subCommandList = subCommands.get(indice);
            if (subCommandList == null) continue;
            for (SubCommand subCommand : subCommandList) {
                if (subCommand.getSubCmdName().equals(theArg)){
                    return subCommand;
                }
            }
        }

        return null;
    }

    /**
     * Add multiple subcommands to the commmand
     *
     * @param method the method to be called
     * @param executor the object with the method
     * */
    public void addSubCommand(Method method, Object executor, br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD.SubCMD subCMD) {
        ExecutorInterpreter executorInterpreter = new ExecutorInterpreter(method, executor);

        String[] subCMDNames = subCMD.subcmd();
        String[] beforeText = null;//TODO add this
        if (subCMDNames.length > 0 && subCMD.index() >= 0) {
            List<SubCommand> subCommandList = this.subCommands.get(subCMD.index());
            if (subCommandList == null){
                this.subCommands.put(subCMD.index(), subCommandList = new ArrayList<>());
            }
            String[] usage = subCMD.usage().replace("%name%","").trim().split(" ");

            for (int i = 0; i < subCMDNames.length; i++) {
                String subCmdName = subCMDNames[i];

                String[] newUsage = new String[usage.length + 1];
                newUsage[0] = subCmdName;
                for (int j = 0; j < usage.length; j++) {
                    newUsage[j + 1] = usage[j];
                }

                SubCommand subCommand = new SubCommand(executorInterpreter, subCMD.index(), subCmdName.toLowerCase(), subCMD.permission(), beforeText);
                subCommandList.add(subCommand); //Add SubCommand properly

                String hoverText = null;

                if (subCMD.locales() != null && subCMD.locales().length > 0){
                    String lang = FCLocaleManager.getLangOf(owningPlugin);
                    for (FCLocale locale : subCMD.locales()) {
                        if (locale.lang().equals(lang)){
                            hoverText = locale.text();
                        }
                    }
                    if (hoverText == null) hoverText = subCMD.locales()[0].text();
                }

                if (hoverText == null) hoverText = subCMD.desc();

                FancyText fancyText = new FancyText("§3§l ▶ §a/§e%label% " + String.join(" ", newUsage))
                        .setHoverText(hoverText);

                String action = "/%label% " + subCMD.onSuggest().replace("%name%", subCmdName);

                if (subCMD.executeSuggest()){
                    fancyText.setRunCommandAction(action);
                } else {
                    fancyText.setSuggestCommandAction(action);
                }

                //fancyText.getTellRawString(); //Cache tellraw, Maybe not necessary at all
                subCommand.helpLine = new HelpLine(fancyText, subCommand.permission);
                if (i == 0){//Add HelpLine from first SubCommand to HelpContext to create default help command
                    addTabbComplete(subCMD.index(), subCMD.permission(), null, newUsage);
                    helpLineList.add(subCommand.helpLine);
                }
            }
        }
    }

    public static class TabCommand {
        private final int indice;
        private final String text;
        private final String permission;
        private final ArrayList<String> textAvant;

        public TabCommand(int indice, String text, String permission, String... textAvant) {
            this.indice = indice;
            this.text = text;
            this.permission = permission;
            if (textAvant == null || textAvant.length < 1) {
                this.textAvant = null;
            } else {
                this.textAvant = Arrays.stream(textAvant).collect(ArrayList::new,
                        ArrayList::add,
                        ArrayList::addAll);
            }
        }

        public String getText() {
            return text;
        }

        public int getIndice() {
            return indice;
        }

        public String getPermission() {
            return permission;
        }

        public ArrayList<String> getTextAvant() {
            return textAvant;
        }
    }

    /**
     * Add multiple arguments to an index with permission and words before
     *
     * @param indice     index where the argument is in the command. /myCmd is at the index -1, so
     *                   /myCmd index0 index1 ...
     * @param permission permission to add (may be null)
     * @param beforeText text preceding the argument (may be null)
     * @param arg        word to add
     */
    public void addTabbComplete(int indice, String permission, String[] beforeText, String... arg) {
        if (arg != null && arg.length > 0 && indice >= 0) {
            if (tabComplete.containsKey(indice)) {
                tabComplete.get(indice).addAll(Arrays.stream(arg).collect(
                        ArrayList::new,
                        (tabCommands, s) -> tabCommands.add(new TabCommand(indice, s, permission, beforeText)),
                        ArrayList::addAll));
            }else {
                tabComplete.put(indice, Arrays.stream(arg).collect(
                        ArrayList::new,
                        (tabCommands, s) -> tabCommands.add(new TabCommand(indice, s, permission, beforeText)),
                        ArrayList::addAll)
                );
            }
        }
    }

    /**
     * Add multiple arguments to an index
     *
     * @param indice index where the argument is in the command. /myCmd is at the index -1, so
     *               /myCmd index0 index1 ...
     * @param arg    word to add
     */
    public void addTabbComplete(int indice, String... arg) {
        addTabbComplete(indice, null, null, arg);
    }

    /**
     * @param sender sender
     * @param alias  alias used
     * @param args   argument of the command
     *
     * @return a list of possible values
     */
    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {

        int indice = args.length - 1;

        if ((getPermission() != null && !sender.hasPermission(getPermission())) || tabComplete.size() == 0 || !tabComplete.containsKey(indice))
            return super.tabComplete(sender, alias, args);

        List<String> list = tabComplete.get(indice).stream()
                .filter(tabCommand -> tabCommand.getTextAvant() == null || tabCommand.getTextAvant().contains(args[indice - 1]))
                .filter(tabCommand -> tabCommand.getPermission() == null || sender.hasPermission(tabCommand.getPermission()))
                .filter(tabCommand -> tabCommand.getText().startsWith(args[indice]))
                .map(TabCommand::getText)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

        return list.size() < 1 ? super.tabComplete(sender, alias, args) : list;

    }
}
