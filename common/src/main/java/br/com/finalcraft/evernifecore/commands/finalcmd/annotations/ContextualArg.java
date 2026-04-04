package br.com.finalcraft.evernifecore.commands.finalcmd.annotations;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface ContextualArg {

    String name();

    String context() default "";

    Class<? extends ArgParserContextual> parser() default ArgParserContextual.class;

}
