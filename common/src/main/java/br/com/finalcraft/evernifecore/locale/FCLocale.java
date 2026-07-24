package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.fancytext.ClickActionType;

import java.lang.annotation.*;

@Repeatable(FCMultiLocales.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface FCLocale {
    String text() default "";
    String hover() default "";
    String click() default "";
    ClickActionType clickType() default ClickActionType.RUN_COMMAND;
    String lang() default LocaleType.EN_US;
    Child[] children() default {};

    public static @interface Child {
        String text() default "";
        String hover() default "";
        String click() default "";
        ClickActionType clickType() default ClickActionType.RUN_COMMAND;
    }
}
