package br.com.finalcraft.evernifecore.placeholder.replacer;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.integration.placeholders.PAPIIntegration;
import br.com.finalcraft.everylibs.commons.Tuple;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CompoundReplacer {

    //Heterogeneous on purpose: each entry pairs a replacer with the one object it was registered
    //with, and different entries answer to different object types. appendReplacer is the only way in,
    //and its signature is what guarantees the pairing that apply() then relies on.
    private List<Tuple<RegexReplacer<?>, Object>> regexReplacers = new ArrayList<>();
    private FPlayer papiUser = null; //If not null, integrate with PlaceholderAPI

    public CompoundReplacer() {

    }

    public static <O> CompoundReplacer from(RegexReplacer<O> regexReplacer, O object){
        return new CompoundReplacer().appendReplacer(regexReplacer, object);
    }

    public <O> CompoundReplacer appendReplacer(RegexReplacer<O> regexReplacer, O object){
        this.regexReplacers.add(Tuple.<RegexReplacer<?>, Object>of(regexReplacer, object));
        return this;
    }

    public CompoundReplacer appendReplacer(CompoundReplacer other){
        this.regexReplacers.addAll(other.regexReplacers);
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
        for (Tuple<RegexReplacer<?>, Object> tuple : regexReplacers) {
            //The single point where the pairing is taken on trust: the wildcard cannot express "this
            //replacer's own type", but appendReplacer only ever stores a matching pair.
            @SuppressWarnings("unchecked")
            RegexReplacer<Object> replacer = (RegexReplacer<Object>) tuple.getLeft();
            text = replacer.apply(text, tuple.getRight());
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
        return !hasPAPIUser() && regexReplacers.isEmpty();
    }

    public boolean hasPAPIUser(){
        return papiUser != null;
    }

    public CompoundReplacer copy() {
        return new CompoundReplacer().appendReplacer(this);
    }

    public List<Tuple<RegexReplacer<?>, Object>> getRegexReplacers() {
        return regexReplacers;
    }

    public @Nullable FPlayer getPapiUser() {
        return papiUser;
    }
}
