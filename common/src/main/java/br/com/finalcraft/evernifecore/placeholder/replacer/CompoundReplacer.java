package br.com.finalcraft.evernifecore.placeholder.replacer;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.integration.placeholders.PAPIIntegration;
import br.com.finalcraft.everylibs.commons.Tuple;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CompoundReplacer {

    private List<Tuple<RegexReplacer, Object>> REGEX_REPLACERS = new ArrayList<>();
    private FPlayer papiUser = null; //If not null, integrate with PlaceholderAPI

    public CompoundReplacer() {

    }

    public static <O> CompoundReplacer from(RegexReplacer<O> regexReplacer, O object){
        return new CompoundReplacer().appendReplacer(regexReplacer, object);
    }

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

    public CompoundReplacer usePAPI(@Nullable FPlayer player){
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
        //Always return a fresh list, never the caller's - a shared source (e.g. a cached data part)
        //must not be corrupted by a replace pass.
        if (isEmpty()) return new ArrayList<>(texts); //nothing to replace, but still a copy

        List<String> result = new ArrayList<>(texts.size());
        for (String text : texts) {
            result.add(apply(text));
        }
        return result;
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

    public @Nullable FPlayer getPapiUser() {
        return papiUser;
    }
}
