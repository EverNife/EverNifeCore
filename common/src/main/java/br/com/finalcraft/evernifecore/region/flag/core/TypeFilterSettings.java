package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.actor.TypeKey;
import br.com.finalcraft.everyconfig.annotation.Comment;

import java.util.ArrayList;
import java.util.List;

/**
 * Which blocks, items or creatures a deny flag covers, by type key: {@link #getOnly()} (empty = every key) minus
 * {@link #getExcept()}. An entry matches as {@link TypeKey#matches} reads it - {@code minecraft:*chest*}, {@code modid:*}.
 * Who acts is narrowed by the {@link DenySettings} it extends.
 */
public class TypeFilterSettings extends DenySettings {

    @Comment({"The block, item or entity ids this flag covers, like minecraft:chest; * stands for any run of characters",
            "(minecraft:*chest*, modid:*). Empty covers every id."})
    private List<String> only = new ArrayList<>();

    @Comment("Ids taken out of what 'only' covers, in the same form - the flag never applies to these.")
    private List<String> except = new ArrayList<>();

    public List<String> getOnly() {
        return only;
    }

    public void setOnly(List<String> only) {
        this.only = only == null ? new ArrayList<>() : only;
    }

    public List<String> getExcept() {
        return except;
    }

    public void setExcept(List<String> except) {
        this.except = except == null ? new ArrayList<>() : except;
    }

    /** Whether the flag covers the block, item or entity {@code key}. */
    public boolean matches(TypeKey key) {
        return (only.isEmpty() || anyMatches(only, key)) && !anyMatches(except, key);
    }

    private static boolean anyMatches(List<String> patterns, TypeKey key) {
        for (String pattern : patterns) {
            if (key.matches(pattern)) {
                return true;
            }
        }
        return false;
    }
}
