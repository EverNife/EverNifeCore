package br.com.finalcraft.evernifecore.gui.layout;

import br.com.finalcraft.evernifecore.locale.FCLocale;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface LayoutIconData {

    int[] slot();

    String permission() default "";

    FCLocale[] locale() default {};

}
