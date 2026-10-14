package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.actor.ECEntityType;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.everyconfig.annotation.Comment;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Which actors a deny flag covers: {@link #getActors()} (empty = every actor) minus {@link #getExceptActors()}. An entry
 * is a word - an {@link ECEntityType#getWord()} ({@code player}, {@code fake-player}, {@code animal}, {@code monster},
 * {@code npc}, {@code other}) or an {@link Actor.Type#getWord()} ({@code entity}, {@code block}, {@code environment}) -
 * or else a type key, with {@code *} for any run of characters ({@code minecraft:creeper}, {@code pixelmon:*}).
 *
 * <p>A word speaks of the actor itself: an arrow is {@code other} even when a player shot it. Either list may be
 * written as text, the entries separated by commas ({@code actors=monster,environment}).</p>
 */
public class DenySettings extends FlagSettings {

    @Comment({"Who the denial covers: player, fake-player, animal, monster, npc, other, entity, block, environment,",
            "or a type key like minecraft:creeper - * stands for any run of characters (pixelmon:*). Empty covers everyone."})
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> actors = new ArrayList<>();

    @Comment("Actors taken out of what 'actors' covers, in the same form - the denial never applies to these.")
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> exceptActors = new ArrayList<>();

    public List<String> getActors() {
        return actors;
    }

    public void setActors(List<String> actors) {
        this.actors = split(actors);
    }

    public List<String> getExceptActors() {
        return exceptActors;
    }

    public void setExceptActors(List<String> exceptActors) {
        this.exceptActors = split(exceptActors);
    }

    /** Whether the denial covers {@code actor}. */
    public boolean covers(Actor actor) {
        return (actors.isEmpty() || anyMatches(actors, actor)) && !anyMatches(exceptActors, actor);
    }

    /** Whether any of {@code entries} - words, keys or key globs, as {@link DenySettings} reads them - names {@code actor}. */
    public static boolean anyMatches(List<String> entries, Actor actor) {
        for (String entry : entries) {
            if (matches(entry, actor)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String entry, Actor actor) {
        String word = entry.trim().toLowerCase(Locale.ROOT);
        for (ECEntityType type : ECEntityType.values()) {
            if (type.getWord().equals(word)) {
                return actor.getEntityType() == type;
            }
        }
        for (Actor.Type type : Actor.Type.values()) {
            if (type.getWord().equals(word)) {
                return actor.getType() == type;
            }
        }
        return actor.getKey().matches(word);
    }

    /** {@code [a, "b,c"]} as {@code [a, b, c]}: the text form of a list. */
    private static List<String> split(List<String> entries) {
        if (entries == null) {
            return new ArrayList<>();
        }
        List<String> split = new ArrayList<>();
        for (String entry : entries) {
            for (String part : entry.split(",")) {
                if (!part.trim().isEmpty()) {
                    split.add(part.trim());
                }
            }
        }
        return split;
    }

}
