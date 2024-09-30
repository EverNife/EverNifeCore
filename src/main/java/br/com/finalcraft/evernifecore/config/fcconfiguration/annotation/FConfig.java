package br.com.finalcraft.evernifecore.config.fcconfiguration.annotation;


import br.com.finalcraft.evernifecore.config.yaml.anntation.Loadable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface FConfig {

    /**
     * Used to pass context to the ArgParser.
     *
     * Usually things like intervals, for example:
     *     [0:*]  = Number between 0 and MAX_NUMBER
     */
    String context() default "";

    /**
     * Should this method/field be used as the ID
     * when saving this Object as a List?.
     *
     * If more than one field/method is marked as ID,
     * an error will be thrown.
     *
     * See {@link br.com.finalcraft.evernifecore.config.fcconfiguration.FCConfigurationManager#VALID_PRIMARY_KEYS}
     */
    boolean useAsID() default false;

    /**
     * The keyName to save/load this element.
     *
     * By default, is the field/method's name.
     *
     * This is ignored when the field has the
     * annotation {@link FConfig.Id}
     */
    String key() default "";

    /**
     * Comment used on this field/method.
     */
    String comment() default "";

    /**
     * Used to enforce a Specific type
     * of Loadable.
     *
     * This has 3 main uses:
     *  - When the field is a List or Set, to enforce the type of the elements
     *     (In these cases, by default we use the Generic type of these collections)
     *  - When the field is an Interface or a non_final class, to enforce the type of the implementation
     */
    Class loadableClass() default Loadable.class;

    /**
     * Should this field be used as the ID
     * when saving this Object?.
     *
     * If more than one field is marked as ID,
     * an error will be thrown.
     *
     * See {@link br.com.finalcraft.evernifecore.config.fcconfiguration.FCConfigurationManager#VALID_PRIMARY_KEYS}
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public @interface Id {

    }

    /**
     * Used to exclude a field from the
     * Load/Save process. Transient fields are
     * already excluded by default.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public @interface Exclude {

    }

}
