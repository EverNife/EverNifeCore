package br.com.finalcraft.evernifecore.config.fcconfiguration.annotation;


import br.com.finalcraft.evernifecore.config.fcconfiguration.FCConfigurationManager;
import br.com.finalcraft.evernifecore.config.yaml.anntation.Loadable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface FConfig {

    /**
     * By default, we don't serialize the super
     * class because it can lead to some
     * unexpected behavior.
     *
     * If you want to enforce the serialization
     * of the super class, set this to true.
     *
     * This is useful when you have a class
     * that extends another class that is
     * not a FConfig.
     *
     * If you want to serialize the super class,
     * set this to true, or use the annotation
     * {@link FConfig} on the super class.
     */
    SuperClassSerialization enforceSuperClassSerialization() default SuperClassSerialization.DEFAULT;

    /**
     * Should this field be used as the ID
     * when saving this Object?.
     *
     * If more than one field is marked as ID,
     * an error will be thrown.
     *
     * See {@link FCConfigurationManager#VALID_PRIMARY_KEYS}
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

    /**
     * Used to enforce a Specific type
     * of Loadable.
     *
     * This has 3 main uses:
     *  - When the field is a List or Set, to enforce the type of the elements
     *     (In these cases, by default we use the Generic type of these collections)
     *  - When the field is an Interface or a non_final class, to enforce the type of the implementation
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public @interface LoadableClass {
        Class value();
    }

    /**
     * Key used on this field/method.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public @interface Key {
        /**
         * The key used on this field/method.
         * If not set, the field name will be used.
         *
         * You can use the placeholder
         *      %field_name%
         *      %field_name_lowercase%
         */
        String value() default "%field_name%";

        /**
         * The prefix used on this field/method.
         */
        String prefix() default "";

        /**
         * If the key should be transformed to some specific case.
         *
         *  Cases:
         *  - NONE: No transformation
         *  - KEBAB_CASE: myField -> my-field
         *  - SNAKE_CASE: myField -> my_field
         *  - CAMEL_CASE: myField -> myField
         *  - UPPER_CAMEL_CASE: myField -> MyField
         */
        KeyTransformCase transformCase() default KeyTransformCase.NONE;
    }

    /**
     * Comment used on this field/method.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public @interface Comment {
        String value();
    }

    public static enum SuperClassSerialization {
        FORCED, // Serialize the super class even if it's not a FConfig
        DISABLED, // Don't serialize the super class, even if it's a FConfig
        DEFAULT // Serialize the super class only if it's a FConfig
    }

    public static enum KeyTransformCase {
        NONE,
        KEBAB_CASE,
        SNAKE_CASE,
        CAMEL_CASE,
        UPPER_CAMEL_CASE
    }
}
