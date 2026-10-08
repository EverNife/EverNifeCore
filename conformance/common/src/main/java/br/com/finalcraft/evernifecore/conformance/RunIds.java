package br.com.finalcraft.evernifecore.conformance;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

/** Run and correlation ids: sortable by time, unique enough for one server's reports folder. */
public final class RunIds {

    private RunIds() {
    }

    /** {@code yyyyMMdd-HHmmss-<4hex>} */
    public static String next() {
        return of(new Date(), ThreadLocalRandom.current().nextInt(0x10000));
    }

    static String of(Date when, int random) {
        return new SimpleDateFormat("yyyyMMdd-HHmmss").format(when) + "-" + String.format("%04x", random & 0xFFFF);
    }

    /** A {@code cid} for a run whose caller did not pass one. */
    public static String nextCid() {
        return "ecc-" + String.format("%08x", ThreadLocalRandom.current().nextInt());
    }
}
