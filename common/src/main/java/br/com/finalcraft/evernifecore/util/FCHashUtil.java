package br.com.finalcraft.evernifecore.util;

public class FCHashUtil {

    private static final long STAFFORD_MIX13_CONST_1 = 0xBE98F273B7E6C52BL;

    private static final long STAFFORD_MIX13_CONST_2 = 0x94D049BB1331112BL;

    private static final long CUSTOM_MIX_CONST_3 = 0xA6E92D8D5E7C6B7DL;

    private static final long NON_ZERO_GOLDEN_RATIO = 0x9E3779B97F4A7C15L;

    public static long hash(String s) {
        if (s == null) {
            return 0L;
        }

        long h = NON_ZERO_GOLDEN_RATIO;

        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= STAFFORD_MIX13_CONST_1;
            h = (h >>> 27) ^ h;
        }

        // Finalization (same philosophy as your hash(long))
        h *= STAFFORD_MIX13_CONST_2;
        h ^= (h >>> 31);

        return h;
    }

    /**
     * Computes a 64-bit hash of a single {@code long} value.
     *
     * @param v {@code long} value to be hashed
     * @return  64-bit hash
     */
    public static long hash(long v) {
        // Step 1: XOR the value with its own version shifted 30 bits to the right,
        // followed by multiplication by the primary constant C1.
        // The 30-bit shift is large enough to mix high and low bits,
        // and multiplication by C1 propagates this mixing across all 64 bits.
        v = (v >>> 30 ^ v) * STAFFORD_MIX13_CONST_1;

        // Step 2: Repeat the technique with a 27-bit shift and secondary constant C2.
        // The smaller shift (27) complements the previous one, covering bit regions
        // that were not as strongly affected in the first step.
        v = (v >>> 27 ^ v) * STAFFORD_MIX13_CONST_2;

        // Step 3: Final XOR without multiplication — equalizes any residual
        // asymmetries between high and low bits, completing the finalization.
        v = v >>> 31 ^ v;
        return v;
    }

    public static long hash(long l1, long l2) {
        l1 = (hash(l1) >>> 30 ^ l1) * STAFFORD_MIX13_CONST_1;
        l1 = hash(l2) >>> 31 ^ l1;
        return l1;
    }

    public static long hash(long l1, long l2, long l3) {
        l1 = (hash(l1) >>> 30 ^ l1) * STAFFORD_MIX13_CONST_1;
        l1 = (hash(l2) >>> 27 ^ l1) * STAFFORD_MIX13_CONST_2;
        l1 = hash(l3) >>> 31 ^ l1;
        return l1;
    }

    public static long hash(long l1, long l2, long l3, long l4) {
        l1 = (hash(l1) >>> 30 ^ l1) * STAFFORD_MIX13_CONST_1;
        l1 = (hash(l2) >>> 27 ^ l1) * STAFFORD_MIX13_CONST_2;
        l1 = (hash(l3) >>> 30 ^ l1) * CUSTOM_MIX_CONST_3;
        l1 = hash(l4) >>> 31 ^ l1;
        return l1;
    }

}
