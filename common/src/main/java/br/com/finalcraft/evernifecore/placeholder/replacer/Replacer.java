package br.com.finalcraft.evernifecore.placeholder.replacer;

import java.util.ArrayList;
import java.util.List;

public interface Replacer<O extends Object> {

    public String apply(final String text, final O object);

    public default List<String> apply(List<String> texts, final O object){
        List<String> result = new ArrayList<>(texts.size());
        for (String text : texts) {
            result.add(apply(text, object));
        }
        return result;
    }

}