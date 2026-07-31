package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The command tree every FinalCMD test can point at instead of writing its own fixture - four levels
 * deep, with everything the traversal has to get right in one place:
 *
 * <pre>
 * /reftree                                             ROOT
 * ├── ping                                             LEAF, no arguments
 * └── user &lt;user&gt;                                      NODE (alias u), eats one token, declares --dry
 *     ├── (executable)                                 /reftree user Steve
 *     ├── info                                         LEAF, reads the capture
 *     └── server &lt;server&gt; &lt;world&gt;                      NODE (alias sv), eats TWO tokens
 *         ├── show                                     LEAF, reads both captures and one token of one
 *         └── say &lt;words...&gt;                           LEAF, variadic tail
 * </pre>
 *
 * Every method appends a line to {@link #calls()}, so a test asserts what ran and with which values:
 * {@code /reftree user Steve server survival world say hi there} leaves
 * {@code "user.server.say(Steve, survival/world, hi there)"} behind.
 * <p>
 * Mounted with permissions ({@code reftree.use}, {@code reftree.user}) so a test can also assert what
 * a sender is NOT shown.
 */
@FinalCMD(aliases = {"reftree"}, permission = "reftree.use")
public class ReferenceCommandTree {

    /** Permission of the root, and therefore of everything under it. */
    public static final String PERMISSION = "reftree.use";

    /** Permission of the {@code user} node - the one that hides a whole subtree when missing. */
    public static final String USER_PERMISSION = "reftree.user";

    private final List<String> calls = new ArrayList<String>();

    /** Every method this tree ran, in order, with the values it received. */
    public List<String> calls() {
        return Collections.unmodifiableList(calls);
    }

    /** The last method this tree ran, or null when nothing has run yet. */
    public String lastCall() {
        return calls.isEmpty() ? null : calls.get(calls.size() - 1);
    }

    public void clearCalls() {
        calls.clear();
    }

    private void record(String call) {
        calls.add(call);
    }

    @FinalCMD.SubCMD(subcmd = "ping")
    public void ping(FCommandSender sender) {
        record("ping()");
    }

    @FinalCMD.Node(subcmd = {"user", "u"}, permission = USER_PERMISSION)
    public class UserNode {

        @FinalCMD.Capture
        public String capture(@Arg("<user>") String user,
                              @Arg.Flag(value = "--dry", aliases = "-d", def = "false") Boolean dry) {
            record("user.capture(" + user + ", dry=" + dry + ")");
            return user;
        }

        @FinalCMD.Execute
        public void show(FCommandSender sender, @Arg.NodeCaptured String user) {
            record("user(" + user + ")");
        }

        @FinalCMD.SubCMD(subcmd = "info")
        public void info(FCommandSender sender, @Arg.NodeCaptured String user) {
            record("user.info(" + user + ")");
        }

        @FinalCMD.Node(subcmd = {"server", "sv"})
        public class ServerNode {

            @FinalCMD.Capture
            public String capture(@Arg("<server>") String server,
                                  @Arg("<world>") String world) {
                return server + "/" + world;
            }

            //two String captures on the path, so neither @Captured can stay anonymous here
            @FinalCMD.SubCMD(subcmd = "show")
            public void show(FCommandSender sender,
                             @Arg.NodeCaptured("user") String user,
                             @Arg.NodeCaptured("user.server") String place,
                             @Arg.NodeCaptured("user.server:<world>") String world) {
                record("user.server.show(" + user + ", " + place + ", " + world + ")");
            }

            @FinalCMD.SubCMD(subcmd = "say")
            public void say(FCommandSender sender,
                            @Arg.NodeCaptured("user") String user,
                            @Arg.NodeCaptured("user.server") String place,
                            @Arg("<words...>") String words) {
                record("user.server.say(" + user + ", " + place + ", " + words + ")");
            }
        }
    }
}
