package br.com.finalcraft.evernifecore.placeholder.replacer;

import java.util.List;
import java.util.regex.Pattern;

public interface Replacer<O extends Object> {

    public String apply(final String text, final O object);

    public default List<String> apply(List<String> texts, final O object){
        for (int i = 0; i < texts.size(); i++) {
            texts.set(i, apply(texts.get(i), object));
        }
        return texts;
    }

    enum Closure {
        BRACKET('{', '}'),
        PERCENT('%', '%');

        public final char head;
        public final char tail;
        public final Pattern pattern;

        Closure(final char head, final char tail) {
            this.head = head;
            this.tail = tail;
            pattern = Pattern.compile(String
                    .format("\\%s((?<identifier>[a-zA-Z0-9]+)_)(?<parameters>[^%s%s]+)\\%s",
                            this.head,
                            this.head,
                            this.tail,
                            this.tail
                    )
            );
        }

    }

}