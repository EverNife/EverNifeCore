package br.com.finalcraft.evernifecore.locale;

import java.lang.annotation.*;

@Repeatable(FCMultiLocales.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface FCLocale {
    String text() default "";
    String hover() default "";
    String runCommand() default "";
    String lang() default LocaleType.EN_US;
}
