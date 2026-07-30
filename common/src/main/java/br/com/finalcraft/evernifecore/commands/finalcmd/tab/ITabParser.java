package br.com.finalcraft.evernifecore.commands.finalcmd.tab;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandPath;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface ITabParser {

    public abstract @Nonnull List<String> tabComplete(TabContext tabContext);

    /**
     * What a parser is told about the word being completed. Everything here is already known by the
     * time the traversal stops - no value was resolved to build it, because tab runs once per
     * keystroke and the traversal only ever counts tokens.
     */
    public static class TabContext {
        private final FCommandSender sender;
        private final String alias;
        private final String[] args;
        private final int index;
        private final int localIndex;
        private final CommandPath path;
        private final Map<String, String> captureTokens;

        /**
         * @param index      where the word being completed sits in {@code args}
         * @param localIndex where it sits once the command path is sliced off
         * @param path       the segments the traversal consumed to get here
         * @param captureTokens each ancestor node's captured token(s), keyed by node path, exactly as typed
         */
        public TabContext(FCommandSender sender, String alias, String[] args, int index, int localIndex,
                          CommandPath path, Map<String, String> captureTokens) {
            this.sender = sender;
            this.alias = alias;
            this.args = args;
            this.index = index;
            this.localIndex = localIndex;
            this.path = path;
            this.captureTokens = captureTokens;
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

        /** Where the word being completed sits in the raw {@code args}. */
        public int getIndex() {
            return index;
        }

        /**
         * Where the word being completed sits inside the window the command path left behind: 0 is the
         * first token after the path, whatever that token turns out to be. Unlike the {@code @Arg}'s own
         * index, this counts every token typed, flags included.
         */
        public int getLocalIndex() {
            return localIndex;
        }

        /** The segments already consumed - labels and captured tokens, as typed. */
        public @Nonnull CommandPath getPath() {
            return path;
        }

        /**
         * The token an ancestor node captured, RAW: never parsed, never validated, so an unknown player
         * name comes back exactly as typed. A capture that eats several tokens hands them back joined by
         * a single space, in the order they were typed.
         * <p>
         * Resolving it is the caller's business, so tab does not pay for a value it may not need:
         * <pre>
         * String userToken = ctx.getCaptureToken("user");
         * return homeCache.namesOf(userToken);
         * </pre>
         *
         * @param nodePath the ancestor's node path, primary labels dot joined ({@code "user"})
         * @return null when no ancestor of that path captured anything on this line
         */
        public @Nullable String getCaptureToken(@Nonnull String nodePath) {
            return captureTokens.get(nodePath);
        }

        /** Every captured token of the path, keyed by node path - what {@link #getCaptureToken} reads. */
        public @Nonnull Map<String, String> getCaptureTokens() {
            return Collections.unmodifiableMap(captureTokens);
        }
    }
}
