package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.locale.FCLocale;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a screen's layout: the window it asks for, and the defaults its {@link IconData}
 * fields are seeded into the yml with.
 *
 * <p>The class is the file - {@code LojaLayout} answers {@code guis/LojaLayout.yml} under the owning
 * plugin's data folder - and everything here is a DEFAULT: whatever the admin writes in that file
 * wins.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ScreenLayout {

    /** The window title, in the language {@link #locale()} does not cover. */
    String title();

    /** Rows of nine, 1 to 6 - the height of the grid. */
    int rows() default 6;

    /** Whether PlaceholderAPI runs over the text of this screen, with the viewer as the subject. */
    boolean integrateToPAPI() default false;

    /** The title per language; {@code text} is the title, and a language nobody wrote falls back to
     *  {@link #title()}. */
    FCLocale[] locale() default {};
}
