package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

/**
 * Where the value of an argument comes from. It is what tells a piece of metadata that fits every
 * family apart, so nothing has to fabricate a position for something that never had one.
 */
public enum ArgSource {

    /** Eats one token of the executable's own window - the only source that has an index. */
    POSITIONAL,

    /** {@code --name value}, readable anywhere after the path, so it sits at no position. */
    FLAG,

    /** Never appears on the command line at all: it is read off the invocation. */
    CONTEXTUAL,

    /** Not a command line to begin with - a config file, or a parser exercised on its own. */
    STANDALONE,
    ;

}
