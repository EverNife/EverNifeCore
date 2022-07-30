package br.com.finalcraft.evernifecore.gui.layout;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface LayoutBaseData {

    int rows() default 6;

    String title() default "➲  §0§l%layout_name%";

    String permission() default "";

    boolean integrateToPAPI() default false;

}
