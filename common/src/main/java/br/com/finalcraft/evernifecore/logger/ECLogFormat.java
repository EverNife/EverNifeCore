package br.com.finalcraft.evernifecore.logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

/**
 * The message formatter of the logging stack: {@code {}} placeholders filled left to right.
 *
 * <p>It never throws. A log call reports a problem - it must not become one - so every way this
 * could fail (a placeholder with no argument, an argument with no placeholder, a hostile
 * {@code toString()}) has a defined, readable output instead of an exception:</p>
 *
 * <pre>
 * format("a {} c", "b")             -> "a b c"
 * format("a {} {}", "b")            -> "a b {}"          //no argument left: the placeholder stays literal
 * format("a {}", "b", "c")          -> "a b [c]"         //surplus arguments are appended
 * format("a \\{} b", "x")           -> "a {} b [x]"      //escaped: literal, consumes nothing
 * format("a \\\\{} b", "x")         -> "a \\x b"         //escaped backslash: the placeholder is real
 * format("{}", new int[]{1, 2})     -> "[1, 2]"
 * format("{}", hostileToString)     -> "&lt;toString failed: com.foo.Bar&gt;"
 * format("fail: {}", exception)     -> "fail: java.io.IOException: ..."   //consumed: no stack trace
 * format("fail", exception)         -> "fail" + newline + stack trace     //trailing: stack trace
 * </pre>
 *
 * <p>A trailing {@link Throwable} that no placeholder consumed is the failure the line is about, so
 * its stack trace is appended - at any level, which is why no {@code severe(String, Throwable)}
 * overload is needed anywhere.</p>
 */
public final class ECLogFormat {

    private static final String PLACEHOLDER = "{}";

    private ECLogFormat() {
    }

    public static String format(String message, Object... params) {
        try {
            return doFormat(message, params);
        } catch (Throwable formatterFailure) {
            //last resort: the individual guards below already cover every known hostile input, but a
            //caller losing its message is worse than a caller losing the arguments
            return String.valueOf(message);
        }
    }

    private static String doFormat(String message, Object[] params) {
        String text = message == null ? "null" : message;
        if (params == null || params.length == 0) {
            return text;
        }

        StringBuilder out = new StringBuilder(text.length() + 32);
        int consumed = 0;
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '\\' && isPlaceholderAt(text, index + 1)) {
                out.append(PLACEHOLDER);
                index += 3;
                continue;
            }
            if (current == '\\' && index + 1 < text.length() && text.charAt(index + 1) == '\\'
                    && isPlaceholderAt(text, index + 2)) {
                out.append('\\');
                index += 2;
                continue;
            }
            if (isPlaceholderAt(text, index)) {
                out.append(consumed < params.length ? render(params[consumed++]) : PLACEHOLDER);
                index += 2;
                continue;
            }
            out.append(current);
            index++;
        }

        int last = params.length - 1;
        Throwable trailing = consumed <= last && params[last] instanceof Throwable ? (Throwable) params[last] : null;
        int surplusEnd = trailing != null ? last : params.length;
        for (int i = consumed; i < surplusEnd; i++) {
            out.append(i == consumed ? " [" : ", ").append(render(params[i]));
        }
        if (consumed < surplusEnd) {
            out.append(']');
        }
        if (trailing != null) {
            out.append(System.lineSeparator()).append(stackTraceOf(trailing));
        }
        return out.toString();
    }

    private static boolean isPlaceholderAt(String text, int index) {
        return index >= 0 && index + 1 < text.length() && text.charAt(index) == '{' && text.charAt(index + 1) == '}';
    }

    private static String render(Object param) {
        if (param == null) {
            return "null";
        }
        try {
            if (param.getClass().isArray()) {
                return arrayToString(param);
            }
            String text = param.toString();
            return text == null ? "null" : text;
        } catch (Throwable hostileToString) {
            return "<toString failed: " + param.getClass().getName() + ">";
        }
    }

    private static String arrayToString(Object array) {
        if (array instanceof Object[]) return Arrays.deepToString((Object[]) array);
        if (array instanceof byte[]) return Arrays.toString((byte[]) array);
        if (array instanceof short[]) return Arrays.toString((short[]) array);
        if (array instanceof int[]) return Arrays.toString((int[]) array);
        if (array instanceof long[]) return Arrays.toString((long[]) array);
        if (array instanceof float[]) return Arrays.toString((float[]) array);
        if (array instanceof double[]) return Arrays.toString((double[]) array);
        if (array instanceof char[]) return Arrays.toString((char[]) array);
        if (array instanceof boolean[]) return Arrays.toString((boolean[]) array);
        return String.valueOf(array);
    }

    /**
     * The trace goes into the message the adapter receives, so it lands in the server log next to
     * what it is about instead of on stdout, where a log file, a level and a timestamp never reach it.
     */
    public static String stackTraceOf(Throwable cause) {
        try {
            StringWriter trace = new StringWriter();
            cause.printStackTrace(new PrintWriter(trace));
            return trace.toString();
        } catch (Throwable hostileThrowable) {
            return "<stack trace unavailable: " + cause.getClass().getName() + ">";
        }
    }
}
