package br.com.finalcraft.evernifecore.commands.finalcmd.annotations;

import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.locale.FCLocale;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface FinalCMD {

    String[] aliases();

    String usage() default "";

    String permission() default "";

    String context() default "";

    Class<? extends CMDAccessValidation>[] validation() default {};

    String helpHeader() default "";

    CMDHelpType useDefaultHelp() default CMDHelpType.FULL;

    FCLocale[] locales() default {};

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface SubCMD {
        String[] subcmd();

        String usage() default "";

        String permission() default "";

        String context() default "";

        Class<? extends CMDAccessValidation>[] validation() default {};

        FCLocale[] locales() default {};
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Ignore {
        //Used in cases where you want to ignore a method that is annotated on the father's class
    }

    /**
     * One segment of a command tree deeper than a {@link SubCMD}: a branch that owns children of its
     * own ({@code /lp user Steve permission set node.x}).
     * <p>
     * The MOUNT POINT declares the segment (labels, permission, validation, locales), the CLASS
     * declares the content, and the two can be written apart:
     * <ul>
     *     <li>on a TYPE - an inner class, for a small stateless branch;</li>
     *     <li>on a FIELD - the field's type is the branch, so the same class can be mounted more than
     *     once, with its own labels/permission each time, already constructed with state and generics.
     *     A null field is instantiated through the type's no-arg constructor.</li>
     * </ul>
     * A class carrying this annotation must not ALSO be mounted by a field: the segment would then be
     * declared twice.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD})
    public static @interface Node {
        String[] subcmd();

        String permission() default "";

        String context() default "";

        Class<? extends CMDAccessValidation>[] validation() default {};

        FCLocale[] locales() default {};
    }

    /**
     * The entry method of a {@link Node}: at most one per node class. Its {@code @Arg} parameters are
     * the tokens the node eats right after its own label, always all of them - a capture is never
     * optional, which is what keeps the traversal a single pass with no backtracking. The returned
     * object is the node's context, reachable from any descendant through {@link Arg.NodeCaptured}.
     * <p>
     * <b>Returning {@code null} aborts the whole dispatch, and the framework says nothing about it.</b>
     * That is the contract, not an oversight: a capture is the only code that knows WHY the segment it
     * owns cannot be entered ("that clan does not exist", "you do not own it"), so whoever answers null
     * is whoever has already told the sender. A capture that returns null without sending a message
     * leaves the sender staring at an unchanged screen.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Capture {
    }

    /**
     * Makes a {@link Node} itself executable: {@code /lp user Steve} runs this method instead of
     * printing the node's help. The node IS the label, so there is nothing to name here.
     * <p>
     * It cannot declare a POSITIONAL {@code @Arg}, and that is what keeps the traversal unambiguous:
     * with no positional argument of its own, every bare token after the node still has to be a
     * child's label. The other three sources of a parameter's value are fine, because none of them
     * claims a bare token - a contextual parameter and an {@link Arg.NodeCaptured} read no token at
     * all, and an {@code @Arg.Flag} is addressed by its own {@code --name}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Execute {
        String permission() default "";

        String context() default "";

        Class<? extends CMDAccessValidation>[] validation() default {};

        FCLocale[] locales() default {};
    }

}
