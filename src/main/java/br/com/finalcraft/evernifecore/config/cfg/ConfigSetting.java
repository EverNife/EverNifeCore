package br.com.finalcraft.evernifecore.config.cfg;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
public @interface ConfigSetting {

    String key(); //YML key for the object

    String comment() default ""; //Simple comment for this key

    String context() default "";

}
