package br.com.finalcraft.evernifecore.commands.finalcmd.tab;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ITabParser {

    public abstract @NotNull List<String> tabComplete(TabContext tabContext);

    public static class TabContext {
        private final CommandSender sender;
        private final String alias;
        private final String[] args;
        private final int index;

        public TabContext(CommandSender sender, String alias, String[] args, int index) {
            this.sender = sender;
            this.alias = alias;
            this.args = args;
            this.index = index;
        }

        public @Nullable String getPreviousArg(){
            return index > 0 ? args[index - 1] : null;
        }

        public @Nullable Player getPlayer(){
            return sender instanceof Player ? (Player) sender : null;
        }

        public @NotNull CommandSender getSender() {
            return sender;
        }

        public @NotNull String getAlias() {
            return alias;
        }

        public @NotNull String[] getArgs() {
            return args;
        }

        public @NotNull String getLastWord(){
            return args[index];
        }

        public int getIndex() {
            return index;
        }
    }
}
