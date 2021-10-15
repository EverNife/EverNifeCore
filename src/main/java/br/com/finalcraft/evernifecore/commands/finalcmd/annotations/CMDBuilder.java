package br.com.finalcraft.evernifecore.commands.finalcmd.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CMDBuilder {

    boolean flagArgs() default false;

    String[] aliases() default {""};

    String usage() default "";

    String desc() default "";
}
