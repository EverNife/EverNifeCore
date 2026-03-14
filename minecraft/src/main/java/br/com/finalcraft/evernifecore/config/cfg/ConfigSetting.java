package br.com.finalcraft.evernifecore.config.cfg;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.locale.FCLocale;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
public @interface ConfigSetting {

    String key(); //YML key for the object

    FCLocale[] comment() default {}; //Locale comment for this key

    String context() default ""; //Context used for the argParser, like ArgParser limts:  Ex  Number '[0-100]'

    Class<? extends ArgParser> parser() default ArgParser.class;

}
