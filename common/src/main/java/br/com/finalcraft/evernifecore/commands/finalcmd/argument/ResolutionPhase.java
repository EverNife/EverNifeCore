package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

/**
 * When a contextual parameter resolves, relative to every parameter that comes off a token. There are
 * only two moments because there are only two answers that matter: before the tokens, so a token's own
 * parser can read what the invocation produced, or after them, so the invocation's parser can read what
 * was typed.
 */
public enum ResolutionPhase {

    /** No opinion - the parameter leaves the choice to its parser. Never an effective phase. */
    PARSER_DEFAULT,

    /** Before any token: flags, captures and positionals all see what this resolved. */
    BEFORE_ARGUMENTS,

    /** After every token: this parser sees the flags, the captures and the positionals. */
    AFTER_ARGUMENTS

}
