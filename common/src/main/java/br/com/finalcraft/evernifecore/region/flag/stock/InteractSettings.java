package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.region.flag.FlagSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Which blocks or entities an interact flag covers, by platform-neutral id: {@link #getOnly()} (empty = every id) minus
 * {@link #getExcept()}. An entry matches by equality or with {@code *} standing for any run of characters -
 * {@code minecraft:*chest*}, {@code modid:*}.
 */
public class InteractSettings extends FlagSettings {

    private List<String> only = new ArrayList<>();
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

    /** Whether the flag covers the block or entity {@code id}. */
    public boolean matches(String id) {
        return (only.isEmpty() || anyMatches(only, id)) && !anyMatches(except, id);
    }

    private static boolean anyMatches(List<String> patterns, String id) {
        String lowerId = id.toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (globMatches(pattern.toLowerCase(Locale.ROOT), lowerId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean globMatches(String pattern, String id) {
        if (pattern.indexOf('*') < 0) {
            return pattern.equals(id);
        }
        List<String> literals = new ArrayList<>();
        for (String literal : pattern.split("\\*", -1)) {
            literals.add(Pattern.quote(literal));
        }
        return Pattern.matches(String.join(".*", literals), id);
    }
}
