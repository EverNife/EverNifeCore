package br.com.finalcraft.evernifecore.conformance;

import java.util.concurrent.Callable;

/** One check against the live server. A {@link #run()} that throws is reported as a FAIL naming the exception. */
public interface ConformanceCase {

    String getId();

    Verdict run() throws Exception;

    static ConformanceCase of(String id, Callable<Verdict> body) {
        return new ConformanceCase() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public Verdict run() throws Exception {
                return body.call();
            }
        };
    }
}
