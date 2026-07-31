package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Every shape a command tree may NOT have, one minimal class per refusal, with the fragments the
 * error message has to carry.
 * <p>
 * These are registration errors: they fire while the server is still opening, never in the middle of
 * a game. That is only worth anything if the message names the class, names the member and says the
 * call that fixes it, so each case pins those fragments rather than the fact that something threw.
 * <p>
 * The battery is published because a plugin that builds trees of its own inherits the same rules -
 * running {@link #check(FinalCmdTestHarness)} answers "does the framework I am compiled against still
 * refuse what I think it refuses" without copying a single fixture.
 */
public final class CommandShapeErrors {

    private CommandShapeErrors() {
    }

    /** One refused shape: the class that triggers it and what its error message has to teach. */
    public static final class Case {

        private final String name;
        private final Object executor;
        private final List<String> teaches;

        Case(String name, Object executor, String... teaches) {
            this.name = name;
            this.executor = executor;
            this.teaches = Collections.unmodifiableList(Arrays.asList(teaches));
        }

        /** What the refusal is about, in prose - also the display name of a parameterized run. */
        public String name() {
            return name;
        }

        /** The minimal executor whose registration must be refused. */
        public Object executor() {
            return executor;
        }

        /** Fragments the error message must contain, so it teaches instead of just failing. */
        public List<String> teaches() {
            return teaches;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** A fresh battery: new fixture instances, so a case can never carry state into the next one. */
    public static List<Case> cases() {
        List<Case> cases = new ArrayList<Case>();

        cases.add(new Case("a node with no child at all is dead weight",
                new NodeWithoutChildren(), "EmptyNode", "empty", "@FinalCMD.SubCMD"));

        cases.add(new Case("a node has ONE entry point",
                new TwoCaptures(), "@FinalCMD.Capture", "twice", "first", "second"));

        cases.add(new Case("a capture that consumes nothing is not a capture",
                new CaptureWithoutTokens(), "NothingNode", "capture", "nothing", "@Arg"));

        cases.add(new Case("a capture that hands nothing to its subtree is not a capture either",
                new VoidCaptureWithoutFlags(), "VoidNode", "capture", "voidnode", "void"));

        cases.add(new Case("a capture's @Arg has to be the required form",
                new OptionalCaptureArg(), "[user]", "OptNode", "capture", "opt", "<user>"));

        cases.add(new Case("an anonymous @Captured with more than one compatible capture on the path",
                new AmbiguousCaptured(), "@Arg.NodeCaptured", "leaf",
                "@Arg.NodeCaptured(\"outer\")", "@Arg.NodeCaptured(\"outer.inner\")"));

        //"<a>" too: an @Arg of a capture is addressable, so what the path offers has to name it
        cases.add(new Case("a @Captured naming no ancestor at all",
                new CapturedNamingNothing(), "@Arg.NodeCaptured(\"nowhere\")", "leaf", "\"node\"", "String", "<a>"));

        cases.add(new Case("a @Captured naming an @Arg the capture does not declare",
                new CapturedNamingAnUnknownArg(), "@Arg.NodeCaptured(\"node:<host>\")", "leaf", "\"node\"", "<server>", "<world>"));

        cases.add(new Case("a @Captured naming an @Arg without its node",
                new CapturedArgWithoutItsNode(), "@Arg.NodeCaptured(\":<server>\")", "leaf", "\"node\"", "<server>"));

        cases.add(new Case("a @Captured reading a capture's @Arg as another type",
                new CapturedArgOfAnotherType(), "@Arg.NodeCaptured(\"node:<server>\")", "leaf", "<server>", "String"));

        cases.add(new Case("two children of the same node answering the same word",
                new LabelClash(), "same", "labelclashcmd"));

        cases.add(new Case("a label is a literal, not an argument",
                new BracketedLabel(), "<user>", "bracketed", "@Arg"));

        cases.add(new Case("only the root of a tree is a @FinalCMD",
                new NodeAnnotatedAsRoot(), "WrongNode", "@FinalCMD.Node", "@FinalCMD"));

        cases.add(new Case("a null node field the framework cannot build",
                new UnbuildableNodeField(), "built", "NeedsAConstructorArg", "no-arg constructor"));

        cases.add(new Case("the segment cannot be declared twice",
                new NodeDeclaredTwice(), "mounted", "SelfDeclaredNode", "@FinalCMD.Node"));

        cases.add(new Case("a mount cycle would never end",
                new MountCycle(), "cycle", "CycleA", "CycleB"));

        cases.add(new Case("usage() next to an @Arg is dead text",
                new UsageNextToAnArg(), "DEAD_USAGE", "leaf", "Delete the usage()"));

        cases.add(new Case("a capture cannot resolve from the sender: it would stop eating its token",
                new CaptureResolvingFromTheSender(), "<user>", "capture", "inferred", "@Arg.Flag"));

        cases.add(new Case("a capture eats a FIXED number of tokens",
                new VariadicCaptureArg(), "<rest...>", "capture", "greedy", "<rest>"));

        cases.add(new Case("nothing can follow the variadic tail",
                new ArgAfterTheTail(), "<after>", "leaf", "end of the parameter list"));

        cases.add(new Case("there is only one variadic tail",
                new TwoTails(), "<second...>", "leaf", "at most one"));

        cases.add(new Case("a tail that takes every token left has nothing to infer",
                new VariadicFromSenderArg(), "<rest...>", "leaf", "fromSender"));

        cases.add(new Case("a tail handed over as a collection carries the raw tokens, so it is of String",
                new VariadicListOfAnotherType(), "<rest...>", "leaf", "List<String>", "Integer"));

        cases.add(new Case("an argument answered by the sender has no use for a written default",
                new FromSenderWithADefault(), "[arena]", "leaf", "fromSender", "def()"));

        cases.add(new Case("fromSender against a parser that cannot answer without a token",
                new FromSenderOnAPlainParser(), "<value>", "leaf", "ArgParserString", "fromSender(ParseCall)"));

        cases.add(new Case("a node executable takes no positional argument",
                new NodeExecutableWithAnArg(), "@FinalCMD.Execute", "run", "node", "@FinalCMD.SubCMD"));

        cases.add(new Case("a flag spelling an ancestor already claims",
                new FlagClash(), "--force", "leaf", "node"));

        cases.add(new Case("a flag alias colliding with an ancestor's flag",
                new FlagAliasClash(), "--f", "leaf", "node"));

        cases.add(new Case("one method cannot declare the same argument name twice",
                new RepeatedDeclaredName(), "<user>", "index=1", "index=2", "rename"));

        //One parameter, one source: every pair of the four families, none of which the invocation
        //could size its argument array for
        cases.add(new Case("a parameter is not both a positional and a flag",
                new ArgAndFlag(), "index=1", "@Arg.Flag", "exactly one"));

        cases.add(new Case("a parameter is not both a positional and a contextual",
                new ArgAndContextual(), "index=1", "@Arg.Contextual", "exactly one"));

        cases.add(new Case("a parameter is not both a flag and a contextual",
                new FlagAndContextual(), "index=1", "@Arg.Flag", "@Arg.Contextual", "exactly one"));

        cases.add(new Case("a parameter is not both a capture reader and a positional",
                new NodeCapturedAndArg(), "index=1", "@Arg.NodeCaptured", "exactly one"));

        cases.add(new Case("a parameter is not both a capture reader and a flag",
                new NodeCapturedAndFlag(), "index=1", "@Arg.NodeCaptured", "@Arg.Flag", "exactly one"));

        cases.add(new Case("a parameter is not both a capture reader and a contextual",
                new NodeCapturedAndContextual(), "index=1", "@Arg.NodeCaptured", "@Arg.Contextual", "exactly one"));

        cases.add(new Case("an argument that may resolve to nothing cannot be primitive",
                new PrimitiveOptionalArg(), "[amount]", "leaf", "primitive", "wrapper type"));

        cases.add(new Case("an argument inferred from the sender cannot be primitive",
                new PrimitiveFromSenderArg(), "<amount>", "leaf", "primitive", "wrapper type"));

        cases.add(new Case("a flag alias of dashes alone names nothing",
                new FlagAliasOfDashesOnly(), "--force", "leaf", "typed as a flag"));

        cases.add(new Case("a flag alias that reads as a negative number never matches",
                new FlagAliasThatIsANumber(), "-5", "leaf", "typed as a flag"));

        cases.add(new Case("a flag alias with whitespace is not one token",
                new FlagAliasWithASpace(), "-a b", "leaf", "typed as a flag"));

        cases.add(new Case("a @Captured naming a capture that hands out another type",
                new CapturedContextOfAnotherType(), "@Arg.NodeCaptured(\"node\")", "leaf", "String", "not assignable"));

        cases.add(new Case("a method that reads nothing off the invocation knows nobody",
                new NothingContextual(), "leaf", "FCommandSender", "@Arg.NodeCaptured"));

        cases.add(new Case("a type nothing parses cannot be a positional",
                new UnparseableType(), "leaf", "Thread", "ArgParserManager"));

        cases.add(new Case("a parser refusing its own declaration keeps its own words",
                new ParserRefusingItsDeclaration(), "leaf", "names no colour this parser knows"));

        return cases;
    }

    /**
     * Runs the whole battery through {@code harness}.
     * <p>
     * It reports instead of throwing, so a consumer can print every disagreement at once instead of
     * fixing them one exception at a time.
     *
     * @return one line per case that was accepted when it should have been refused, or whose message
     * failed to teach; empty when the framework refuses everything it promises to refuse
     */
    public static List<String> check(FinalCmdTestHarness harness) {
        List<String> failures = new ArrayList<String>();
        for (Case shapeError : cases()) {
            String message;
            try {
                message = harness.registerExpectingError(shapeError.executor()).getMessage();
            } catch (AssertionError accepted) {
                failures.add("[" + shapeError.name() + "] was accepted, it should have been refused");
                continue;
            }
            for (String fragment : shapeError.teaches()) {
                if (message == null || !message.contains(fragment)) {
                    failures.add("[" + shapeError.name() + "] was refused, but the message never mentions ["
                            + fragment + "]: " + message);
                }
            }
        }
        return failures;
    }

    // ------------------------------------------------------------------
    // The fixtures. Each one is the SMALLEST tree that reaches its refusal - anything else in it
    // would be a second reason the registration could fail.
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "repeatednamecmd")
    public static class RepeatedDeclaredName {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender,
                         @Arg("<user>") String first,
                         @Arg("<user>") String second) {
        }
    }

    @FinalCMD(aliases = "nochildcmd")
    public static class NodeWithoutChildren {
        @FinalCMD.Node(subcmd = "empty")
        public static class EmptyNode {
        }
    }

    @FinalCMD(aliases = "twocapturescmd")
    public static class TwoCaptures {
        @FinalCMD.Node(subcmd = "twice")
        public static class TwiceNode {
            @FinalCMD.Capture
            public String first(@Arg("<a>") String a) { return a; }

            @FinalCMD.Capture
            public String second(@Arg("<b>") String b) { return b; }

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender) {}
        }
    }

    @FinalCMD(aliases = "emptycapturecmd")
    public static class CaptureWithoutTokens {
        @FinalCMD.Node(subcmd = "nothing")
        public static class NothingNode {
            @FinalCMD.Capture
            public String capture(FCommandSender sender) { return "x"; }

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender) {}
        }
    }

    @FinalCMD(aliases = "voidcapturecmd")
    public static class VoidCaptureWithoutFlags {
        @FinalCMD.Node(subcmd = "voidnode")
        public static class VoidNode {
            @FinalCMD.Capture
            public void capture(@Arg("<a>") String a) {}

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender) {}
        }
    }

    @FinalCMD(aliases = "optcapturecmd")
    public static class OptionalCaptureArg {
        @FinalCMD.Node(subcmd = "opt")
        public static class OptNode {
            @FinalCMD.Capture
            public String capture(@Arg("[user]") String user) { return user; }

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender) {}
        }
    }

    @FinalCMD(aliases = "ambiguouscapturedcmd")
    public static class AmbiguousCaptured {
        @FinalCMD.Node(subcmd = "outer")
        public static class OuterNode {
            @FinalCMD.Capture
            public String capture(@Arg("<a>") String a) { return a; }

            @FinalCMD.Node(subcmd = "inner")
            public static class InnerNode {
                @FinalCMD.Capture
                public String capture(@Arg("<b>") String b) { return b; }

                @FinalCMD.SubCMD(subcmd = "leaf")
                public void leaf(FCommandSender sender, @Arg.NodeCaptured String ambiguous) {}
            }
        }
    }

    @FinalCMD(aliases = "nowherecapturedcmd")
    public static class CapturedNamingNothing {
        @FinalCMD.Node(subcmd = "node")
        public static class ANode {
            @FinalCMD.Capture
            public String capture(@Arg("<a>") String a) { return a; }

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender, @Arg.NodeCaptured("nowhere") String missing) {}
        }
    }

    @FinalCMD(aliases = "unknownargcmd")
    public static class CapturedNamingAnUnknownArg {
        @FinalCMD.Node(subcmd = "node")
        public static class ANode {
            @FinalCMD.Capture
            public String capture(@Arg("<server>") String server, @Arg("<world>") String world) {
                return server + world;
            }

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender, @Arg.NodeCaptured("node:<host>") String missing) {}
        }
    }

    @FinalCMD(aliases = "namelessargcmd")
    public static class CapturedArgWithoutItsNode {
        @FinalCMD.Node(subcmd = "node")
        public static class ANode {
            @FinalCMD.Capture
            public String capture(@Arg("<server>") String server) { return server; }

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender, @Arg.NodeCaptured(":<server>") String nameless) {}
        }
    }

    @FinalCMD(aliases = "argtypecmd")
    public static class CapturedArgOfAnotherType {
        @FinalCMD.Node(subcmd = "node")
        public static class ANode {
            @FinalCMD.Capture
            public String capture(@Arg("<server>") String server) { return server; }

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender, @Arg.NodeCaptured("node:<server>") Integer wrongType) {}
        }
    }

    @FinalCMD(aliases = "labelclashcmd")
    public static class LabelClash {
        @FinalCMD.SubCMD(subcmd = "same")
        public void first(FCommandSender sender) {}

        @FinalCMD.SubCMD(subcmd = {"other", "same"})
        public void second(FCommandSender sender) {}
    }

    @FinalCMD(aliases = "bracketedlabelcmd")
    public static class BracketedLabel {
        @FinalCMD.SubCMD(subcmd = "<user>")
        public void bracketed(FCommandSender sender) {}
    }

    @FinalCMD(aliases = "noderootcmd")
    public static class NodeAnnotatedAsRoot {
        @FinalCMD(aliases = "notaroot")
        @FinalCMD.Node(subcmd = "wrong")
        public static class WrongNode {
            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender) {}
        }
    }

    public static class NeedsAConstructorArg {
        public NeedsAConstructorArg(String required) {}

        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender) {}
    }

    @FinalCMD(aliases = "unbuildablecmd")
    public static class UnbuildableNodeField {
        @FinalCMD.Node(subcmd = "built")
        private NeedsAConstructorArg built;
    }

    @FinalCMD.Node(subcmd = "selfdeclared")
    public static class SelfDeclaredNode {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender) {}
    }

    @FinalCMD(aliases = "declaredtwicecmd")
    public static class NodeDeclaredTwice {
        @FinalCMD.Node(subcmd = "mounted")
        private final SelfDeclaredNode mounted = new SelfDeclaredNode();
    }

    public static class CycleA {
        @FinalCMD.Node(subcmd = "b")
        private final CycleB b = new CycleB();
    }

    public static class CycleB {
        @FinalCMD.Node(subcmd = "a")
        private CycleA a;
    }

    @FinalCMD(aliases = "cyclecmd")
    public static class MountCycle {
        @FinalCMD.Node(subcmd = "start")
        private final CycleA start = new CycleA();
    }

    @FinalCMD(aliases = "deadusagecmd")
    public static class UsageNextToAnArg {
        @FinalCMD.SubCMD(subcmd = "leaf", usage = "DEAD_USAGE")
        public void leaf(FCommandSender sender, @Arg("<value>") String value) {}
    }

    @FinalCMD(aliases = "sendercapturecmd")
    public static class CaptureResolvingFromTheSender {
        @FinalCMD.Node(subcmd = "inferred")
        public static class InferredNode {
            @FinalCMD.Capture
            public String capture(@Arg(value = "<user>", fromSender = true, parser = AlwaysFromSenderParser.class) String user) { return user; }

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender) {}
        }
    }

    @FinalCMD(aliases = "greedycapturecmd")
    public static class VariadicCaptureArg {
        @FinalCMD.Node(subcmd = "greedy")
        public static class GreedyNode {
            @FinalCMD.Capture
            public String capture(@Arg("<rest...>") String rest) { return rest; }

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender) {}
        }
    }

    @FinalCMD(aliases = "aftertailcmd")
    public static class ArgAfterTheTail {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender,
                         @Arg("<rest...>") String rest,
                         @Arg("<after>") String after) {}
    }

    @FinalCMD(aliases = "twotailscmd")
    public static class TwoTails {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender,
                         @Arg("<first...>") String first,
                         @Arg("<second...>") String second) {}
    }

    @FinalCMD(aliases = "tailfromsendercmd")
    public static class VariadicFromSenderArg {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender,
                         @Arg(value = "<rest...>", fromSender = true, parser = AlwaysFromSenderParser.class) String rest) {}
    }

    @FinalCMD(aliases = "taillistcmd")
    public static class VariadicListOfAnotherType {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender, @Arg("<rest...>") List<Integer> rest) {}
    }

    @FinalCMD(aliases = "fromsenderdefcmd")
    public static class FromSenderWithADefault {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender,
                         @Arg(value = "[arena]", fromSender = true, def = "spawn", parser = AlwaysFromSenderParser.class) String arena) {}
    }

    @FinalCMD(aliases = "fromsendercmd")
    public static class FromSenderOnAPlainParser {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender, @Arg(value = "<value>", fromSender = true) String value) {}
    }

    @FinalCMD(aliases = "execargcmd")
    public static class NodeExecutableWithAnArg {
        @FinalCMD.Node(subcmd = "node")
        public static class ArgTakingNode {
            @FinalCMD.Execute
            public void run(FCommandSender sender, @Arg("<value>") String value) {}

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender) {}
        }
    }

    @FinalCMD(aliases = "flagclashcmd")
    public static class FlagClash {
        @FinalCMD.Node(subcmd = "node")
        public static class FlaggedNode {
            @FinalCMD.Capture
            public String capture(@Arg("<a>") String a,
                                  @Arg.Flag(value = "--force", aliases = "-f") Boolean force) { return a; }

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender, @Arg.Flag("--force") Boolean forceAgain) {}
        }
    }

    @FinalCMD(aliases = "flagaliasclashcmd")
    public static class FlagAliasClash {
        @FinalCMD.Node(subcmd = "node")
        public static class FlaggedNode {
            @FinalCMD.Capture
            public String capture(@Arg("<a>") String a,
                                  @Arg.Flag(value = "--force", aliases = "-f") Boolean force) { return a; }

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender, @Arg.Flag(value = "--fast", aliases = "-f") Boolean fast) {}
        }
    }

    @FinalCMD(aliases = "argandflagcmd")
    public static class ArgAndFlag {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender, @Arg("<value>") @Arg.Flag("--value") String both) {}
    }

    @FinalCMD(aliases = "argandcontextualcmd")
    public static class ArgAndContextual {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender, @Arg("<value>") @Arg.Contextual("label") String both) {}
    }

    @FinalCMD(aliases = "flagandcontextualcmd")
    public static class FlagAndContextual {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender, @Arg.Flag("--value") @Arg.Contextual("label") String both) {}
    }

    @FinalCMD(aliases = "capturedandargcmd")
    public static class NodeCapturedAndArg {
        @FinalCMD.Node(subcmd = "node")
        public static class ANode {
            @FinalCMD.Capture
            public String capture(@Arg("<server>") String server) { return server; }

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender, @Arg.NodeCaptured("node") @Arg("<value>") String both) {}
        }
    }

    @FinalCMD(aliases = "capturedandflagcmd")
    public static class NodeCapturedAndFlag {
        @FinalCMD.Node(subcmd = "node")
        public static class ANode {
            @FinalCMD.Capture
            public String capture(@Arg("<server>") String server) { return server; }

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender, @Arg.NodeCaptured("node") @Arg.Flag("--value") String both) {}
        }
    }

    @FinalCMD(aliases = "capturedandcontextualcmd")
    public static class NodeCapturedAndContextual {
        @FinalCMD.Node(subcmd = "node")
        public static class ANode {
            @FinalCMD.Capture
            public String capture(@Arg("<server>") String server) { return server; }

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender, @Arg.NodeCaptured("node") @Arg.Contextual("label") String both) {}
        }
    }

    /**
     * The parser is pinned on purpose: without it the manager finds none for {@code int} and the
     * registration would fail for a second reason, hiding the one this case is about.
     */
    @FinalCMD(aliases = "primitiveoptionalcmd")
    public static class PrimitiveOptionalArg {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender, @Arg(value = "[amount]", parser = AlwaysFromSenderParser.class) int amount) {}
    }

    @FinalCMD(aliases = "primitivefromsendercmd")
    public static class PrimitiveFromSenderArg {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender, @Arg(value = "<amount>", fromSender = true, parser = AlwaysFromSenderParser.class) int amount) {}
    }

    @FinalCMD(aliases = "dashaliascmd")
    public static class FlagAliasOfDashesOnly {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender, @Arg.Flag(value = "--force", aliases = "-") Boolean force) {}
    }

    @FinalCMD(aliases = "numberaliascmd")
    public static class FlagAliasThatIsANumber {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender, @Arg.Flag(value = "--force", aliases = "-5") Boolean force) {}
    }

    @FinalCMD(aliases = "spacealiascmd")
    public static class FlagAliasWithASpace {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender, @Arg.Flag(value = "--force", aliases = "-a b") Boolean force) {}
    }

    @FinalCMD(aliases = "capturedcontexttypecmd")
    public static class CapturedContextOfAnotherType {
        @FinalCMD.Node(subcmd = "node")
        public static class ANode {
            @FinalCMD.Capture
            public String capture(@Arg("<server>") String server) { return server; }

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender, @Arg.NodeCaptured("node") Integer wrongType) {}
        }
    }

    @FinalCMD(aliases = "nothingcontextualcmd")
    public static class NothingContextual {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(@Arg("<value>") String value) {}
    }

    @FinalCMD(aliases = "unparseabletypecmd")
    public static class UnparseableType {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender, @Arg("<value>") Thread value) {}
    }

    @FinalCMD(aliases = "parserrefusalcmd")
    public static class ParserRefusingItsDeclaration {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender, @Arg(value = "<colour>", context = "zzz", parser = PickyParser.class) String colour) {}
    }

    /**
     * A parser that refuses the {@code context()} it was handed the way most code says no - a plain
     * {@code IllegalArgumentException} - rather than through the framework's own exception. What it
     * wrote is the only sentence that names the actual problem, so it has to reach the developer.
     */
    public static class PickyParser extends ArgParser<String> {
        public PickyParser(ArgInfo argInfo) {
            super(argInfo);
            throw new IllegalArgumentException("The context [" + argInfo.getArgData().getContext() + "] names no colour this parser knows");
        }

        @Override
        public ParseResult<String> parse(ParseCall call) {
            return ParseResult.of(call.getArgumento().toString());
        }
    }

    /** A parser that DOES answer from the sender, so the capture case is about the node, not the parser. */
    public static class AlwaysFromSenderParser extends ArgParser<String> {
        public AlwaysFromSenderParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public ParseResult<String> parse(ParseCall call) {
            return ParseResult.of(call.getArgumento().toString());
        }

        @Override
        public ParseResult<String> fromSender(ParseCall call) {
            return ParseResult.of(call.getSender().getName());
        }
    }
}
