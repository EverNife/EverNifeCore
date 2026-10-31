package br.com.finalcraft.evernifecore.screen.prompt;

/**
 * Which window a question is put through. {@link PromptRegistry} tries them in this declaration order when the spec
 * prefers none: the richest first, chat last because chat is the one every platform can have.
 */
public enum PromptKind {

    PAGE, ANVIL, CHAT

}
