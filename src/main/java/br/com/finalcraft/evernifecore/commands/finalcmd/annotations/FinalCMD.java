package br.com.finalcraft.evernifecore.commands.finalcmd.annotations;

import br.com.finalcraft.evernifecore.locale.FCLocale;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface FinalCMD {

    String[] aliases();

    String usage() default "";

    String desc() default "";

    String permission() default "";

    String context() default "";

    String helpHeader() default "";

    CMDHelpType useDefaultHelp() default CMDHelpType.FULL;

    FCLocale[] locales() default {};

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface SubCMD {
        String[] subcmd();

        String usage() default "";

        String desc() default "";

        String permission() default "";

        String context() default "";

        FCLocale[] locales() default {};
    }



}
