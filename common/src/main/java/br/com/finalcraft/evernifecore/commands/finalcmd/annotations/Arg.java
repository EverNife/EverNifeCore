package br.com.finalcraft.evernifecore.commands.finalcmd.annotations;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ResolutionPhase;
import br.com.finalcraft.evernifecore.locale.FCLocale;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface Arg {

    /**
     * How this argument is spelled on the usage line, brackets included: {@code <required>} or
     * {@code [optional]}. A {@code ...} suffix ({@code <reason...>}) makes it the variadic tail: the
     * method's last {@code @Arg}, taking every token left, handed over unparsed as {@code String},
     * {@code String[]}, {@code Argumento}, {@code MultiArgumentos}, {@code List<String>} or
     * {@code Set<String>}.
     * <p>
     * An optional tail nobody typed is empty rather than null, unless it declares a {@link #def()}.
     */
    String value();

    String context() default "";

    /**
     * Lets the parser answer from the sender's own state when the token is absent, so one method
     * serves both {@code /arena enable true} (the arena the admin is standing on) and
     * {@code /arena enable true myarena} (the named one).
     * <p>
     * Only legal on a leaf: a node's {@code @FinalCMD.Capture} always eats its tokens.
     */
    boolean fromSender() default false;

    Class<? extends ArgParser> parser() default ArgParser.class;

    FCLocale[] locales() default {};

    /**
     * Declarative default for an OPTIONAL argument: when omitted, this text is parsed by the
     * argument's own parser as if typed, as ONE token. Empty means an absent optional argument is
     * null. On a variadic tail it is split on whitespace instead.
     * <p>
     * Setting it on a required argument or next to {@link #fromSender()} fails at registration.
     */
    String def() default "";

    /**
     * Never a token. A contextual parameter is read off the surroundings of the invocation - the
     * sender, the path it was reached by, the help line - and consumes nothing off the line, so it
     * changes neither the usage nor which word lands on the next argument.
     * <p>
     * A parameter that carries no annotation at all is contextual already. Write this one only to name
     * the value, to pin a parser the manager would not pick, or to move the parameter across the tokens
     * with {@link #phase()}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    @interface Contextual {

        /** How the resolved value is addressed afterwards. Unique among every parameter of the method. */
        String value();

        String context() default "";

        Class<? extends ArgParserContextual> parser() default ArgParserContextual.class;

        /**
         * Overrides {@link ArgParserContextual#defaultPhase()} for this parameter alone -
         * {@link ResolutionPhase#AFTER_ARGUMENTS} to let the parser read a token this method declares,
         * {@link ResolutionPhase#BEFORE_ARGUMENTS} to let the tokens read this.
         */
        ResolutionPhase phase() default ResolutionPhase.PARSER_DEFAULT;

    }

    /**
     * A token addressed by name instead of by position: {@code --force}, {@code --page 3}. Unlike
     * {@link Contextual} it does eat a word off the line, and it runs through the very same
     * {@link ArgParser} a positional would.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    @interface Flag {

        /** Canonical flag name, always in long form: "--force", "--page". */
        String value();

        /** Extra accepted spellings, short or long: {"-f"}, {"--f", "-fo"}. */
        String[] aliases() default {};

        /** Same context mini-DSL of {@link Arg}, applied to the VALUE parser: "[1:*]", "a|b|c". */
        String context() default "";

        /** Value parser override; defaults to the ArgParserManager lookup by parameter type. */
        Class<? extends ArgParser> parser() default ArgParser.class;

        /** Shown on the help hover exactly like {@link Arg} locales. */
        FCLocale[] locales() default {};

        /**
         * Declarative default parsed by the value parser when the flag is absent.
         * Empty = absent flag yields null. On a Boolean flag, def = "false" makes absence
         * FALSE instead of null (present is always TRUE).
         */
        String def() default "";

        /**
         * Permission required to USE the flag: using it without the permission sends the standard
         * permission message and aborts the command. An absent flag never checks this. Empty = no
         * per-flag permission.
         */
        String permission() default "";

        /**
         * Which declared spelling shows on the usage line. Empty = the long {@link #value()}. It has to
         * be the name or one of the aliases; the hover always lists every spelling, so the long form
         * stays discoverable even when the line shows the short one.
         */
        String usageName() default "";

        /**
         * Whether the flag shows up on the command help (usage line + hover block). Set false to
         * keep internal/rarely-used flags out of the help; the flag still works and still
         * tab-completes (to hide from tab, use {@link #permission()} instead).
         */
        boolean showOnUsage() default true;

    }

    /**
     * Marks a parameter that receives an ancestor node's captured context instead of a token of the
     * line - the consumer end of a {@link FinalCMD.Capture}. An unannotated parameter is always the
     * CALLER; an annotated one always comes from outside.
     * <p>
     * Leave {@link #value()} empty when a single ancestor capture fits the parameter type; name the
     * ancestor's node path ({@code "user"}, {@code "user.compare"}) when more than one does.
     * <p>
     * Never null: a {@link FinalCMD.Capture} answering {@code null} aborts the dispatch before this
     * method runs, having already told the sender why.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    @interface NodeCaptured {

        /**
         * The ancestor to read from, as a dot path of primary labels - {@code "user.compare"} - which
         * hands over whatever that node's {@link FinalCMD.Capture} returned.
         * <p>
         * Append {@code ":<argName>"} to take a single token of that capture instead, spelled exactly as
         * the capture declared it: {@code "user:<server>"}. The name is part of the leaf's contract, so
         * renaming the capture's {@code @Arg} is refused at registration.
         */
        String value() default "";

    }

}
