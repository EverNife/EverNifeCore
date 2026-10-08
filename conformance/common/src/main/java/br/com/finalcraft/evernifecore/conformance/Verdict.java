package br.com.finalcraft.evernifecore.conformance;

import java.util.concurrent.Callable;

/**
 * What one {@link ConformanceCase} found. The detail always carries the value that was read, so a line in the
 * log is evidence on its own and nobody has to rerun the case to learn what the server answered.
 */
public final class Verdict {

    public enum Kind { PASS, FAIL, REFUSED, SKIPPED }

    private final Kind kind;
    private final String detail;
    private final boolean refusalExpected;

    private Verdict(Kind kind, String detail, boolean refusalExpected) {
        this.kind = kind;
        this.detail = detail == null ? "" : detail;
        this.refusalExpected = refusalExpected;
    }

    public static Verdict pass(String detail) {
        return new Verdict(Kind.PASS, detail, false);
    }

    public static Verdict fail(String detail) {
        return new Verdict(Kind.FAIL, detail, false);
    }

    public static Verdict of(boolean ok, String detail) {
        return ok ? pass(detail) : fail(detail);
    }

    /** The call threw; {@code expected} says whether this server was supposed to refuse it. */
    public static Verdict refused(boolean expected, String detail) {
        return new Verdict(Kind.REFUSED, detail, expected);
    }

    /** Not run here - no player, no economy. Counted apart, never a pass nor a failure. */
    public static Verdict skipped(String why) {
        return new Verdict(Kind.SKIPPED, why, false);
    }

    /**
     * Runs {@code body} on a feature that exists only from {@code since} on. Where it works, its own verdict
     * stands; where it does not, the only right answer is a refusal - a call that quietly succeeds there is a
     * failure too, since it means the version table is wrong. The detail always names the expectation, so a
     * {@code REFUSED} is auditable from the report alone.
     */
    public static Verdict since(boolean worksHere, String since, Callable<Verdict> body) {
        //expected= answers "is a refusal expected here", the same question REFUSED carries
        String expectation = "expected=" + !worksHere + " since=" + since;
        try {
            Verdict verdict = body.call();
            if (worksHere) {
                return new Verdict(verdict.kind, verdict.detail + " " + expectation, verdict.refusalExpected);
            }
            return fail(verdict.detail + " " + expectation + " but the call went through");
        } catch (Throwable thrown) {
            return refused(!worksHere, expectation + " " + describe(thrown));
        }
    }

    /** {@code <class>: <message>}, the shape every thrown failure is reported in. */
    public static String describe(Throwable thrown) {
        return thrown.getClass().getSimpleName() + ": " + thrown.getMessage();
    }

    public Kind getKind() {
        return kind;
    }

    public String getDetail() {
        return detail;
    }

    public boolean isRefusalExpected() {
        return refusalExpected;
    }

    /** PASS and an expected refusal; SKIPPED is neither ok nor failed and is asked through {@link #getKind()}. */
    public boolean isOk() {
        return kind == Kind.PASS || (kind == Kind.REFUSED && refusalExpected);
    }
}
