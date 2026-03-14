package br.com.finalcraft.evernifecore.placeholder.replacer;

import java.util.List;

public interface Replacer<O extends Object> {

    public String apply(final String text, final O object);

    public default List<String> apply(List<String> texts, final O object){
        for (int i = 0; i < texts.size(); i++) {
            texts.set(i, apply(texts.get(i), object));
        }
        return texts;
    }

}