package br.com.finalcraft.evernifecore.commands.finalcmd.tree;

import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpWords;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves which node a line reaches, and how many tokens that costs.
 * <p>
 * The traversal never resolves a value: it matches literals, counts the tokens each capture eats and
 * stops. No {@code @Capture} is invoked, no parser runs, no message is sent - which is what makes it
 * cheap enough for tab-complete to run once per keystroke.
 * <p>
 * At every step a DECLARATION beats a convention: a child claiming the word, or a capture standing
 * where it was typed, is consulted before the help interceptor, and a flag the node already
 * recognizes ends the walk instead of being refused as written too early. Both used to go the other
 * way, which made a subcommand named {@code help} and a flag on a node with children unreachable.
 */
public final class CommandWalker {

    private CommandWalker() {
    }

    public static WalkResult walk(@Nonnull CommandNode root, @Nonnull String[] args, @Nonnull String label) {
        CommandNode node = root;
        int cursor = 0;

        List<String> segments = new ArrayList<>();
        List<String> literals = new ArrayList<>();
        List<String> captureTokens = new ArrayList<>();
        List<CommandNode> pathNodes = new ArrayList<>();
        int lastLiteralIndex = -1;

        while (true) {
            if (!node.hasChildren()){
                //A node with no children ends the path: every token left belongs to its arguments
                return result(node, cursor, captureTokens, pathNodes, label, segments, literals, lastLiteralIndex, terminalOutcome(node), null, null);
            }

            CaptureBinding capture = node.getCapture();

            if (capture != null){
                int width = capture.tokenWidth();
                if (args.length - cursor < width){
                    return result(node, cursor, captureTokens, pathNodes, label, segments, literals, lastLiteralIndex, WalkResult.Outcome.NODE_HELP, capture, null);
                }
                for (int i = 0; i < width; i++) {
                    String token = args[cursor + i];
                    if (MultiArgumentos.isFlagMarker(token)){
                        return result(node, cursor, captureTokens, pathNodes, label, segments, literals, lastLiteralIndex, WalkResult.Outcome.FLAG_TOO_EARLY, null, token);
                    }
                    captureTokens.add(token);
                    segments.add(token);
                }
                cursor += width;
            }

            if (cursor >= args.length){
                return result(node, cursor, captureTokens, pathNodes, label, segments, literals, lastLiteralIndex, terminalOutcome(node), null, null);
            }

            String token = args[cursor];

            CommandNode child = node.getChild(token);
            if (child == null){
                if (MultiArgumentos.isFlagMarker(token)){
                    //A flag written here is only early if nothing that could read it has been reached.
                    //Once the node standing here declares it, the path IS over: the token belongs to
                    //the window, and refusing it would leave the flag impossible to type at all.
                    if (declaresFlag(node, token)){
                        return result(node, cursor, captureTokens, pathNodes, label, segments, literals, lastLiteralIndex, WalkResult.Outcome.EXECUTABLE, null, null);
                    }
                    return result(node, cursor, captureTokens, pathNodes, label, segments, literals, lastLiteralIndex, WalkResult.Outcome.FLAG_TOO_EARLY, null, token);
                }

                //Nothing below claims this word, so the help interceptor may have it
                if (node != root && HelpWords.isHelpWord(token)){
                    return result(node, cursor + 1, captureTokens, pathNodes, label, segments, literals, lastLiteralIndex, WalkResult.Outcome.RESERVED_HELP, null, null);
                }

                //Only the root's executable may take positional arguments (@FinalCMD.Execute forbids
                //@Arg), so an unmatched token there is an argument of it - anywhere else it is a word
                //that had to name a child and did not
                if (node == root && root.getExecutable() != null){
                    return result(root, 0, captureTokens, pathNodes, label, segments, literals, lastLiteralIndex, WalkResult.Outcome.EXECUTABLE, null, null);
                }
                return result(node, cursor, captureTokens, pathNodes, label, segments, literals, lastLiteralIndex, WalkResult.Outcome.NO_MATCH, null, token);
            }

            lastLiteralIndex = segments.size();
            segments.add(token);
            literals.add(child.getPrimaryLabel());
            pathNodes.add(child);
            cursor++;
            node = child;
        }
    }

    private static WalkResult.Outcome terminalOutcome(CommandNode node) {
        return node.getExecutable() != null ? WalkResult.Outcome.EXECUTABLE : WalkResult.Outcome.NODE_HELP;
    }

    /** Whether anything reachable from {@code node} - its executable or an ancestor's capture - declares this marker. */
    private static boolean declaresFlag(CommandNode node, String markerToken) {
        return node.getAccumulatedFlagExtractionBindings().containsKey(MultiArgumentos.flagLookupName(markerToken));
    }

    private static WalkResult result(CommandNode node, int consumed, List<String> captureTokens, List<CommandNode> pathNodes,
                                     String label, List<String> segments, List<String> literals, int lastLiteralIndex,
                                     WalkResult.Outcome outcome, CaptureBinding pendingCapture, String offendingToken) {
        return new WalkResult(node, consumed, captureTokens, pathNodes, new CommandPath(label, segments, literals, lastLiteralIndex), outcome, pendingCapture, offendingToken);
    }
}
