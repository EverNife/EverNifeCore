package br.com.finalcraft.evernifecore.placeholder.replacer;

import br.com.finalcraft.evernifecore.integration.placeholders.PAPIIntegration;
import br.com.finalcraft.evernifecore.util.commons.Tuple;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CompoundReplacer {

    private List<Tuple<RegexReplacer, Object>> REGEX_REPLACERS = new ArrayList<>();
    private Player papiUser = null; //If not null, integrate with PlaceholderAPI

    public <O> CompoundReplacer appendReplacer(RegexReplacer<O> regexReplacer, O object){
        this.REGEX_REPLACERS.add(Tuple.of(regexReplacer, object));
        return this;
    }

    public CompoundReplacer appendReplacer(CompoundReplacer other){
        this.REGEX_REPLACERS.addAll(other.REGEX_REPLACERS);
        if (this.papiUser == null){
            this.papiUser = other.papiUser;
        }
        return this;
    }

    public CompoundReplacer usePAPI(@Nullable Player player){
        this.papiUser = player;
        return this;
    }

    public String apply(String text) {
        for (Tuple<RegexReplacer, Object> tuple : REGEX_REPLACERS) {
            RegexReplacer replacer = tuple.getLeft();
            Object watcher = tuple.getRight();
            text = replacer.apply(text, watcher);
        }
        if (papiUser != null){
            text = PAPIIntegration.parse(papiUser, text);
        }
        return text;
    }

    public List<String> apply(List<String> texts){
        if (isEmpty()) return texts; //Early Return to prevent wasted time

        for (int i = 0; i < texts.size(); i++) {
            texts.set(i, apply(texts.get(i)));
        }
        return texts;
    }

    public static <O> CompoundReplacer from(RegexReplacer<O> regexReplacer, O object){
        return new CompoundReplacer().appendReplacer(regexReplacer, object);
    }

    public boolean isEmpty(){
        return !hasPAPIUser() && REGEX_REPLACERS.isEmpty();
    }

    public boolean hasPAPIUser(){
        return papiUser != null;
    }

    public CompoundReplacer clone() {
        return new CompoundReplacer().appendReplacer(this);
    }

    public List<Tuple<RegexReplacer, Object>> getRegexReplacers() {
        return REGEX_REPLACERS;
    }

    public @Nullable Player getPapiUser() {
        return papiUser;
    }
}
