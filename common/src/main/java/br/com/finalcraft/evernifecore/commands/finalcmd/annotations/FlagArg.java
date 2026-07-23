package br.com.finalcraft.evernifecore.commands.finalcmd.annotations;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.locale.FCLocale;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface FlagArg {

    /** Canonical flag name, always in long form: "--force", "--page". */
    String name();

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
     * Whether the flag shows up on the command help (usage line + hover block). Set false to
     * keep internal/rarely-used flags out of the help; the flag still works and still
     * tab-completes (to hide from tab, use {@link #permission()} instead).
     */
    boolean showOnUsage() default true;

}
