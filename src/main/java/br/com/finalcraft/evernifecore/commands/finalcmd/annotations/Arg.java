package br.com.finalcraft.evernifecore.commands.finalcmd.annotations;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.FlagParser;

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

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    public static @interface Flag {

        String name();

        String context() default "";

        Class<? extends FlagParser> parser() default FlagParser.class;

    }

}
