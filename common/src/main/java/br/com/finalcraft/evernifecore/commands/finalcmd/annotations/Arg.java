package br.com.finalcraft.evernifecore.commands.finalcmd.annotations;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.locale.FCLocale;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface Arg {

    String name();

    String context() default "";

    Class<? extends ArgParser> parser() default ArgParser.class;

    FCLocale[] locales() default {};

    /**
     * Declarative default for an OPTIONAL argument: when the player omits it, this text is
     * parsed by the argument's own parser (honoring context bounds/choices) as if typed.
     * Empty (the default) keeps the current behavior: absent optional -> null.
     * Setting it on a required or provided-by-context argument fails at registration.
     */
    String def() default "";

}
