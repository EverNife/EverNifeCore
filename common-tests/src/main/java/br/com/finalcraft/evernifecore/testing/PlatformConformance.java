package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.version.FCPlatformType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What every {@link IPlatform} has to hold, whoever implements it - the doubles in this library,
 * the Bukkit one, the Hytale one.
 *
 * <p>It reports instead of throwing, so the same checks run inside a JUnit test and on a live
 * server, where a thrown exception would be the wrong answer.</p>
 *
 * <p>The first check exists because of a defect no unit test could have caught: the classpath
 * probe that tells Bukkit from Hytale apart asked for a resource path that no jar contains, so a
 * real Bukkit server reported itself as Hytale and quietly rewrote characters in every rendered
 * message. A mock has no classpath shape to get wrong - only a real platform can answer this.</p>
 */
public final class PlatformConformance {

    private PlatformConformance() {
    }

    /** @return one line per violation, empty when the platform conforms. */
    public static List<String> check(IPlatform platform) {
        List<String> failures = new ArrayList<String>();

        String providerId = call(platform, "getPlatformProviderId", failures, new Call<String>() {
            @Override
            public String run(IPlatform target) {
                return target.getPlatformProviderId();
            }
        });

        if (providerId != null) {
            if (providerId.trim().isEmpty()) {
                failures.add("getPlatformProviderId is blank - it is persisted inside account rows and must be stable");
            }
            if ("minecraft".equals(providerId) && !FCPlatformType.isMinecraft()) {
                failures.add("platform says 'minecraft' but the classpath probe detected "
                        + FCPlatformType.getCurrent().getName() + " - one of the two is lying");
            }
            if ("hytale".equals(providerId) && !FCPlatformType.isHytale()) {
                failures.add("platform says 'hytale' but the classpath probe detected "
                        + FCPlatformType.getCurrent().getName() + " - one of the two is lying");
            }
        }

        call(platform, "getOnlinePlayers", failures, new Call<Object>() {
            @Override
            public Object run(IPlatform target) {
                if (target.getOnlinePlayers() == null) {
                    throw new IllegalStateException("returned null; an empty list is the way to say 'nobody is online'");
                }
                return null;
            }
        });

        call(platform, "getChatAdapter", failures, new Call<Object>() {
            @Override
            public Object run(IPlatform target) {
                return target.getChatAdapter();   //null is allowed; throwing is not
            }
        });

        call(platform, "runOnMainThread", failures, new Call<Object>() {
            @Override
            public Object run(IPlatform target) {
                //deferring to the next tick is fine; handing back no future at all is not, because
                //every caller in the codebase chains on it
                if (target.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                    }
                }) == null) {
                    throw new IllegalStateException("returned null instead of a future");
                }
                return null;
            }
        });

        return Collections.unmodifiableList(failures);
    }

    /** Runs the whole suite and returns a single line fit for a console command. */
    public static String summarize(IPlatform platform) {
        List<String> failures = check(platform);
        if (failures.isEmpty()) {
            return "platform conformance: OK";
        }
        StringBuilder summary = new StringBuilder("platform conformance: " + failures.size() + " problem(s)");
        for (String failure : failures) {
            summary.append("\n - ").append(failure);
        }
        return summary.toString();
    }

    private interface Call<T> {
        T run(IPlatform target);
    }

    private static <T> T call(IPlatform platform, String method, List<String> failures, Call<T> call) {
        try {
            return call.run(platform);
        } catch (Throwable thrown) {
            failures.add(method + " threw " + thrown.getClass().getSimpleName() + ": " + thrown.getMessage());
            return null;
        }
    }
}
