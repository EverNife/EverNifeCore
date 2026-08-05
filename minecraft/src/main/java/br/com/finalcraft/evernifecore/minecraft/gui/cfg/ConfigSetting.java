package br.com.finalcraft.evernifecore.minecraft.gui.cfg;

import br.com.finalcraft.evernifecore.locale.FCLocale;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A field whose value comes from a config file. {@link SettingsScanner} seeds {@link #key()} with the
 * field's own value the first time and writes back whatever the file says from then on.
 *
 * <p>What the value must MEAN is declared beside it, with EveryConfig's semantic rules: jakarta's
 * constraints ({@code @Min}, {@code @Max}, {@code @NotBlank}, {@code @Pattern}, ...), EveryConfig's
 * {@code @Explicit}/{@code @OneOf}/{@code @Unique}, or an annotation of your own marked
 * {@code @ConfigRule}. Each one also documents itself in the file's comment.
 *
 * <pre>{@code
 * @ConfigSetting(key = "Settings.chance", comment = @FCLocale(text = "Drop chance, in percent"))
 * @Min(0) @Max(100)
 * public Double chance = 25.0;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigSetting {

    /** Where the value lives in the file, dotted. */
    String key();

    /** What the file says above the key, one entry per language; the plugin's language picks, and the
     *  first entry answers for a language nobody wrote. */
    FCLocale[] comment() default {};
}
