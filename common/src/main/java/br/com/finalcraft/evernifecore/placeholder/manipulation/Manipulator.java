package br.com.finalcraft.evernifecore.placeholder.manipulation;

import br.com.finalcraft.evernifecore.placeholder.replacer.Closures;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A placeholder template with gaps. {@code {x}} is a plain gap: one or more of any character, cut by the literal that
 * follows it. {@code [x]} is a block: it starts at {@code [} and ends at the {@code ]} that balances it, so nothing
 * inside cuts it, and it is read by {@link PlaceholderArgs}. {@code [x?]} is a block that may be left out, together
 * with the {@code _} before it: {@code battle_win_[options?]} answers both {@code battle_win} and
 * {@code battle_win_[top=3]}.
 */
public class Manipulator {

    private final String originalText;
    private final String prefix;
    private final Pattern pattern;
    private final List<String> closures = new ArrayList<>(); //Closures like '{playerName}' or '[options]'
    private final List<String> delimiters = new ArrayList<>(); //Stored delimiters as Pattern.quote(), like 'player_kills'
    private final @Nullable List<Segment> segments;   //null for a template with no block: it keeps the regex path

    public Manipulator(String originalText) {
        this.originalText = originalText;

        if (originalText.indexOf('[') >= 0) {
            this.segments = segmentsOf(originalText);
            this.pattern = null;
            StringBuilder leading = new StringBuilder();
            for (Segment segment : segments) {
                if (segment.kind != Kind.LITERAL) {
                    break;
                }
                leading.append(segment.text);
            }
            this.prefix = leading.toString();
            for (Segment segment : segments) {
                if (segment.kind == Kind.LITERAL) {
                    delimiters.add(segment.text);
                } else {
                    closures.add(segment.key);
                }
            }
            return;
        }
        this.segments = null;

        final Pattern pattern = Closures.BRACKET.getPattern();
        final Matcher matcher = pattern.matcher(originalText);

        //First store all original delimiters on an array
        while (matcher.find()) {
            //Store closures, something like '{playerName}'
            closures.add(matcher.group());
        }

        if (closures.size() == 0) {
            throw new IllegalArgumentException(String.format("The OriginalText [%s] does not have even a single Matching Closure {}. Use something like 'player_kills_{playerName}' or '{playerName}_kills'", originalText));
        }

        this.prefix = pattern.split(originalText,2)[0]; //Prefix used for calling this Provider

        //Now replace the original delimiters with a fake unicode character and tokenize it
        String TEXT_WITH_CUSTOM_DELIMITERS = matcher.replaceAll("￿");
        StringTokenizer stringTokenizer = new StringTokenizer(TEXT_WITH_CUSTOM_DELIMITERS, "￿", true);

        //Replace new delimiters with the original ones and mount an array
        StringBuilder regex = new StringBuilder("^");
        while (stringTokenizer.hasMoreTokens()){
            String token = stringTokenizer.nextToken();
            if (token.equals("￿")){
                regex.append(".{1,}");//Anything, but at least Size 1
            }else {
                regex.append(Pattern.quote(token));
                delimiters.add(token);
            }
        }
        regex.append("$");

        this.pattern = Pattern.compile(regex.toString());
    }

    public boolean match(String placeholder){

        if (!this.prefix.isEmpty() && !placeholder.startsWith(this.prefix)){
            //Fast early lookup to prevent usage of the pattern from start
            return false;
        }

        if (segments != null) {
            return extract(placeholder) != null;
        }
        return pattern.matcher(placeholder).find();
    }

    /** Whether the template has a {@code [x]} block. */
    public boolean hasBlocks() {
        return segments != null;
    }

    /**
     * What each gap of a block template took from {@code placeholder}, by closure ({@code "{n}"}, or {@code "[x]"} -
     * without the {@code ?} - with the text between the brackets); an optional block left out is absent. Null when the
     * placeholder does not fit, a block that never closes included.
     */
    public @Nullable Map<String, String> extract(String placeholder) {
        if (segments == null) {
            throw new IllegalStateException("The template '" + originalText + "' has no [block]; its gaps are read by delimiters");
        }
        Map<String, String> found = new LinkedHashMap<>();
        return fit(placeholder, 0, 0, found) ? found : null;
    }

    private boolean fit(String text, int at, int segmentIndex, Map<String, String> found) {
        if (segmentIndex == segments.size()) {
            return at == text.length();
        }
        Segment segment = segments.get(segmentIndex);
        switch (segment.kind) {
            case LITERAL:
                return text.startsWith(segment.text, at) && fit(text, at + segment.text.length(), segmentIndex + 1, found);
            case GAP:
                //greedy like the '.{1,}' of a plain template: the longest run that lets the rest fit
                for (int end = text.length(); end > at; end--) {
                    if (fit(text, end, segmentIndex + 1, found)) {
                        found.put(segment.key, text.substring(at, end));
                        return true;
                    }
                }
                return false;
            default:
                int open = at + segment.separator.length();
                if (text.startsWith(segment.separator, at) && open < text.length() && text.charAt(open) == '[') {
                    int close = closingBracket(text, open);
                    if (close > 0 && fit(text, close + 1, segmentIndex + 1, found)) {
                        found.put(segment.key, text.substring(open + 1, close));
                        return true;
                    }
                }
                return segment.kind == Kind.OPTIONAL_BLOCK && fit(text, at, segmentIndex + 1, found);
        }
    }

    /** The index of the {@code ]} that balances the {@code [} at {@code open}, skipping quoted and escaped text; -1 when none. */
    static int closingBracket(String text, int open) {
        int depth = 0;
        char quote = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '[') {
                depth++;
            } else if (c == ']' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private enum Kind { LITERAL, GAP, BLOCK, OPTIONAL_BLOCK }

    private static final class Segment {
        final Kind kind;
        final String text;        //a literal's text
        final String key;         //a gap's or block's closure
        final String separator;   //what an optional block takes along when present: the '_' before it

        Segment(Kind kind, String text, String key, String separator) {
            this.kind = kind;
            this.text = text;
            this.key = key;
            this.separator = separator;
        }
    }

    private static List<Segment> segmentsOf(String template) {
        List<Segment> segments = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            char close = c == '{' ? '}' : c == '[' ? ']' : 0;
            if (close == 0) {
                literal.append(c);
                continue;
            }
            int end = template.indexOf(close, i + 1);
            if (end < 0) {
                throw new IllegalArgumentException("The template '" + template + "' opens a '" + c + "' at " + i + " and never closes it -"
                        + " write a gap as {name} and a block as [name] or [name?].");
            }
            String name = template.substring(i + 1, end);
            String separator = "";
            Kind kind;
            if (c == '{') {
                kind = Kind.GAP;
            } else if (name.endsWith("?")) {
                kind = Kind.OPTIONAL_BLOCK;
                name = name.substring(0, name.length() - 1);
                if (literal.length() > 0 && literal.charAt(literal.length() - 1) == '_') {
                    literal.setLength(literal.length() - 1);
                    separator = "_";
                }
            } else {
                kind = Kind.BLOCK;
            }
            if (literal.length() > 0) {
                segments.add(new Segment(Kind.LITERAL, literal.toString(), null, ""));
                literal.setLength(0);
            }
            segments.add(new Segment(kind, null, kind == Kind.GAP ? "{" + name + "}" : "[" + name + "]", separator));
            i = end;
        }
        if (literal.length() > 0) {
            segments.add(new Segment(Kind.LITERAL, literal.toString(), null, ""));
        }
        return segments;
    }

    /** The regex a plain template matches with; null for a template with a block, which is matched by {@link #extract}. */
    public @Nullable Pattern getPattern() {
        return pattern;
    }

    public String getOriginalText() {
        return originalText;
    }

    public String getPrefix() {
        return prefix;
    }

    public List<String> getClosures() {
        return closures;
    }

    public List<String> getDelimiters() {
        return delimiters;
    }
}
