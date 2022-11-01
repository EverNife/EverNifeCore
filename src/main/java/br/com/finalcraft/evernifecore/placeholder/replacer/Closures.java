package br.com.finalcraft.evernifecore.placeholder.replacer;

import java.util.regex.Pattern;

public enum Closures {
    BRACKET('{', '}'),
    PERCENT('%', '%');

    private final char head;
    private final char tail;
    private final Pattern pattern;

    Closures(final char head, final char tail) {
        this.head = head;
        this.tail = tail;
        pattern = Pattern.compile(String
                .format("\\%s((?<identifier>[a-zA-Z0-9]+)_){0,1}(?<parameters>[^%s%s]+)\\%s",
                        this.head,
                        this.head,
                        this.tail,
                        this.tail
                )
        );
    }

    public String quote(String text) {
        return head + text + tail;
    }

    public char getHead() {
        return head;
    }

    public char getTail() {
        return tail;
    }

    public Pattern getPattern() {
        return pattern;
    }
}
