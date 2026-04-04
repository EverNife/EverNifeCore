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

    String name();

    String context() default "";

    Class<? extends ArgParser> parser() default ArgParser.class;

    FCLocale[] locales() default {};

}
