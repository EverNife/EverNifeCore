package br.com.finalcraft.evernifecore.placeholder.replacer;

import br.com.finalcraft.evernifecore.integration.placeholders.PAPIIntegration;
import br.com.finalcraft.evernifecore.util.commons.Tuple;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CompoundReplacer {

    private List<Tuple<RegexReplacer, Object>> REGEX_REPLACERS = new ArrayList<>();
    private Player usePAPI = null; //If not null, integrate with PlaceholderAPI

    public <O> CompoundReplacer appendReplacer(RegexReplacer<O> regexReplacer, O object){
        this.REGEX_REPLACERS.add(Tuple.of(regexReplacer, object));
        return this;
    }

    public CompoundReplacer usePAPI(@Nullable Player player){
        this.usePAPI = player;
        return this;
    }

    public String apply(String text) {
        for (Tuple<RegexReplacer, Object> tuple : REGEX_REPLACERS) {
            RegexReplacer replacer = tuple.getAlfa();
            Object watcher = tuple.getBeta();
            text = replacer.apply(text, watcher);
        }
        if (usePAPI != null){
            text = PAPIIntegration.parse(usePAPI, text);
        }
        return text;
    }

    public List<String> apply(List<String> texts){
        for (int i = 0; i < texts.size(); i++) {
            texts.set(i, apply(texts.get(i)));
        }
        return texts;
    }

    public static <O> CompoundReplacer from(RegexReplacer<O> regexReplacer, O object){
        return new CompoundReplacer().appendReplacer(regexReplacer, object);
    }

}
