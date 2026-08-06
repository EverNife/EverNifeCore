package br.com.finalcraft.evernifecore.minecraft.gui.layout;

import br.com.finalcraft.evernifecore.locale.FCLocale;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@link Icon} field of a layout: where it goes and who sees it, as the DEFAULT the yml is
 * seeded with. The field's name is the key under which the admin finds it in the file.
 *
 * <p>Appearance and position live here; behaviour does not. A layout never carries an
 * {@code onClick} - the menu that opens the layout does.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface IconData {

    /** Raw, 0-based slots. An empty array is an icon that starts switched off. */
    int[] slot();

    /** Checked against whoever is LOOKING at the screen. Empty means everyone sees it. */
    String permission() default "";

    /** Paints on the background layer, underneath the content of the screen. */
    boolean background() default false;

    /** Who wins a slot two icons claim - lowest first, the field name breaking a tie. */
    int order() default 0;

    /** Name and lore per language; {@code text} is the name and {@code hover} the lore. Declaring any
     *  language forbids naming the same icon through the builder - pick one channel. */
    FCLocale[] locale() default {};
}
