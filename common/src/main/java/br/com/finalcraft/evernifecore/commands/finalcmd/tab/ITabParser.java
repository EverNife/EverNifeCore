package br.com.finalcraft.evernifecore.commands.finalcmd.tab;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

public interface ITabParser {

    public abstract @Nonnull List<String> tabComplete(TabContext tabContext);

    public static class TabContext {
        private final FCommandSender sender;
        private final String alias;
        private final String[] args;
        private final int index;

        public TabContext(FCommandSender sender, String alias, String[] args, int index) {
            this.sender = sender;
            this.alias = alias;
            this.args = args;
            this.index = index;
        }

        public @Nullable String getPreviousArg(){
            return index > 0 ? args[index - 1] : null;
        }

        public @Nullable FPlayer getPlayer(){
            return sender instanceof FPlayer ? (FPlayer) sender : null;
        }

        public @Nonnull FCommandSender getSender() {
            return sender;
        }

        public @Nonnull String getAlias() {
            return alias;
        }

        public @Nonnull String[] getArgs() {
            return args;
        }

        public @Nonnull String getLastWord(){
            return args[index];
        }

        public int getIndex() {
            return index;
        }
    }
}
