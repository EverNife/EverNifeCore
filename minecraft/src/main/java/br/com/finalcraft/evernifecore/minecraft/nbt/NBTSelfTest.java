package br.com.finalcraft.evernifecore.minecraft.nbt;

import de.tr7zw.changeme.nbtapi.NBTContainer;
import de.tr7zw.changeme.nbtapi.utils.MinecraftVersion;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime smoke test for the NBT-API integration.
 *
 * NBT-API reflects into the server's NMS classes; on Forge/Bukkit hybrids with remapped NMS that
 * mapping can fail while the plugin otherwise starts fine, leaving item/GUI NBT quietly broken.
 * This runs the core compound operations so any breakage surfaces as a clear PASS/FAIL at startup.
 * The report always lists all {@link #STEPS}: the failing one shows the reason and every later one
 * is marked {@code ERROR}. {@link #run()} never throws - a missing NMS (a headless unit-test JVM)
 * is reported as {@link Result#isEnvironmentUnavailable()} so callers can treat it as skipped.
 */
public final class NBTSelfTest {

    private static final String[] STEPS = {
            "create compound A",
            "write + read-back fields",
            "edit existing field",
            "nested compound",
            "merge into compound B",
            "remove key / independence",
            "serialize round-trip",
    };

    private NBTSelfTest() {
    }

    @Getter
    @AllArgsConstructor
    public static final class Result {
        private final boolean success;
        private final int passed;
        private final List<String> steps;
        private final Throwable error;
        /**
         * True when NBT-API could not be linked at all (no NMS/server, e.g. a headless unit-test
         * JVM), as opposed to a genuine malfunction - callers may treat this as "skipped".
         */
        private final boolean environmentUnavailable;
        private final String detectedNmsVersion;

        public String getSummary() {
            if (environmentUnavailable) {
                return "SKIPPED - NBT-API not available in this environment (no NMS/server)";
            }
            return (success ? "PASSED" : "FAILED") + " (" + passed + "/" + STEPS.length + " checks)";
        }
    }

    /** Thrown internally to abort the sequence on the first failed check. */
    private static final class CheckFailure extends RuntimeException {
        CheckFailure(String message) {
            super(message);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new CheckFailure(message);
        }
    }

    /** Exercises NBT-API end to end and reports the outcome. Never throws. */
    public static Result run() {
        int passed = 0;
        String nms = "?";
        try {
            NBTContainer a;
            try {
                a = new NBTContainer("{}"); // fails to link here on a headless JVM (no craftbukkit)
            } catch (LinkageError | Exception e) {
                boolean unavailable = isLinkageProblem(e);
                return report(0, (unavailable ? "UNAVAILABLE" : "ERROR") + " (" + describe(e) + ")", e, unavailable, nms);
            }
            try {
                nms = String.valueOf(MinecraftVersion.getVersion());
            } catch (Throwable ignored) {
                // informational only - keep "?"
            }
            passed++; // 1. create compound A

            a.setString("name", "EverNifeCore");
            a.setInteger("level", 42);
            a.setDouble("ratio", 3.5D);
            a.setBoolean("flag", true);
            check("EverNifeCore".equals(a.getString("name")), "getString(name) mismatch");
            check(Integer.valueOf(42).equals(a.getInteger("level")), "getInteger(level) mismatch");
            check(Double.valueOf(3.5D).equals(a.getDouble("ratio")), "getDouble(ratio) mismatch");
            check(Boolean.TRUE.equals(a.getBoolean("flag")), "getBoolean(flag) mismatch");
            passed++; // 2. write + read-back fields

            a.setInteger("level", 99);
            check(Integer.valueOf(99).equals(a.getInteger("level")), "edited getInteger(level) mismatch");
            passed++; // 3. edit existing field

            a.getOrCreateCompound("nested").setString("child", "ok");
            check("ok".equals(a.getOrCreateCompound("nested").getString("child")), "nested child mismatch");
            passed++; // 4. nested compound

            NBTContainer b = new NBTContainer("{}");
            b.mergeCompound(a);
            check("EverNifeCore".equals(b.getString("name")), "merged name mismatch");
            check(Integer.valueOf(99).equals(b.getInteger("level")), "merged level mismatch");
            check("ok".equals(b.getOrCreateCompound("nested").getString("child")), "merged nested mismatch");
            passed++; // 5. merge into compound B

            b.removeKey("flag");
            check(!b.hasKey("flag"), "removeKey(flag) did not remove the key from B");
            check(a.hasKey("flag"), "removeKey(flag) leaked into A (compounds are not independent)");
            passed++; // 6. remove key / independence

            NBTContainer roundTrip = new NBTContainer(a.toString());
            check("EverNifeCore".equals(roundTrip.getString("name")), "round-trip name mismatch");
            check(Integer.valueOf(99).equals(roundTrip.getInteger("level")), "round-trip level mismatch");
            passed++; // 7. serialize round-trip

            return report(passed, null, null, false, nms);
        } catch (CheckFailure f) {
            return report(passed, "FAIL: " + f.getMessage(), f, false, nms);
        } catch (LinkageError | Exception e) {
            boolean unavailable = isLinkageProblem(e);
            return report(passed, (unavailable ? "UNAVAILABLE" : "ERROR") + ": " + describe(e), e, unavailable, nms);
        }
    }

    /**
     * Builds the report: the first {@code passed} steps are OK, the step at index {@code passed}
     * (when {@code failure} is non-null) shows the reason, and any later step is marked ERROR.
     */
    private static Result report(int passed, String failure, Throwable error, boolean unavailable, String nms) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < STEPS.length; i++) {
            String status = i < passed ? "OK" : (i == passed && failure != null ? failure : "ERROR");
            lines.add(line(i + 1, STEPS[i], status));
        }
        return new Result(passed == STEPS.length, passed, lines, error, unavailable, nms);
    }

    /** Formats one dotted-leader line, e.g. {@code "4. nested compound ...... OK"}. */
    private static String line(int number, String label, String status) {
        StringBuilder sb = new StringBuilder().append(number).append(". ").append(label).append(' ');
        while (sb.length() < 31) {
            sb.append('.');
        }
        return sb.append(' ').append(status).toString();
    }

    private static boolean isLinkageProblem(Throwable t) {
        for (; t != null; t = t.getCause()) {
            if (t instanceof LinkageError || t instanceof ClassNotFoundException) {
                return true;
            }
        }
        return false;
    }

    private static String describe(Throwable t) {
        return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
    }
}
