package br.com.finalcraft.evernifecore.minecraft.gui.view.prompt;

/**
 * Turns what the player typed into the value a prompt was asking for.
 *
 * <p>Throwing is how an answer is refused: the prompt says what went wrong, asks again, and the
 * refused attempt costs nothing but the message. The text of the exception is what the player reads,
 * so write it for them.</p>
 *
 * @param <T> what the prompt was asking for
 */
public interface PromptParser<T> {

    T parse(String input) throws Exception;

}
