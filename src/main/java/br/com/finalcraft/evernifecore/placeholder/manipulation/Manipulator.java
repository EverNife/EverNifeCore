package br.com.finalcraft.evernifecore.placeholder.manipulation;

import br.com.finalcraft.evernifecore.placeholder.replacer.Closures;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Manipulator {

    private final String originalText;
    private final String prefix;
    private final Pattern pattern;
    private final List<String> closures = new ArrayList<>(); //Closures like '{playerName}'
    private final List<String> delimiters = new ArrayList<>(); //Stored delimiters as Pattern.quote(), like 'player_kills'

    public Manipulator(String originalText) {
        this.originalText = originalText;

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
        String TEXT_WITH_CUSTOM_DELIMITERS = matcher.replaceAll("\uFFFF");
        StringTokenizer stringTokenizer = new StringTokenizer(TEXT_WITH_CUSTOM_DELIMITERS, "\uFFFF", true);

        //Replace new delimiters with the original ones and mount an array
        StringBuilder regex = new StringBuilder("^");
        while (stringTokenizer.hasMoreTokens()){
            String token = stringTokenizer.nextToken();
            if (token.equals("\uFFFF")){
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

        return pattern.matcher(placeholder).find();
    }

    public Pattern getPattern() {
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
