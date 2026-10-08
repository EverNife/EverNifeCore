package br.com.finalcraft.evernifecore.conformance;

import java.util.function.Consumer;

/**
 * Writes the {@code ENCTEST {json}} log lines a test harness reads off the server log: one flat JSON object per
 * event, keys {@code v,e,t,cid} first and then the payload, in a fixed order, escaped to pure ASCII so no log
 * encoding can mangle it. This is the whole protocol - nothing here depends on whoever reads it.
 */
public final class MarkerLine {

    public static final String PREFIX = "ENCTEST";
    public static final int VERSION = 1;

    private final Consumer<String> sink;

    /** @param sink one call per line - a logger's {@code info}, so a line is never split by another thread */
    public MarkerLine(Consumer<String> sink) {
        this.sink = sink;
    }

    public synchronized void emit(Json event) {
        sink.accept(PREFIX + " " + event.finish());
    }

    /** A single-line JSON object in insertion order. */
    public static final class Json {

        private static final char[] HEX = "0123456789abcdef".toCharArray();

        private final StringBuilder sb = new StringBuilder("{");

        /** The envelope every event starts with. */
        public static Json event(String event, long epochMillis, String cid) {
            return new Json().num("v", VERSION).str("e", event).num("t", epochMillis).str("cid", cid);
        }

        public Json str(String name, String value) {
            key(name);
            if (value == null) {
                sb.append("null");
            } else {
                sb.append('"');
                escape(sb, value);
                sb.append('"');
            }
            return this;
        }

        public Json num(String name, long value) {
            key(name);
            sb.append(value);
            return this;
        }

        public Json bool(String name, boolean value) {
            key(name);
            sb.append(value);
            return this;
        }

        /** The object so far, closed; the builder itself stays open. */
        public String finish() {
            return sb + "}";
        }

        private void key(String name) {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append('"');
            escape(sb, name);
            sb.append("\":");
        }

        static void escape(StringBuilder out, String s) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"': out.append("\\\""); break;
                    case '\\': out.append("\\\\"); break;
                    case '\n': out.append("\\n"); break;
                    case '\r': out.append("\\r"); break;
                    case '\t': out.append("\\t"); break;
                    case '\b': out.append("\\b"); break;
                    case '\f': out.append("\\f"); break;
                    default:
                        if (c < 0x20 || c > 0x7e) {
                            out.append("\\u").append(HEX[(c >> 12) & 0xF]).append(HEX[(c >> 8) & 0xF])
                                    .append(HEX[(c >> 4) & 0xF]).append(HEX[c & 0xF]);
                        } else {
                            out.append(c);
                        }
                }
            }
        }
    }
}
