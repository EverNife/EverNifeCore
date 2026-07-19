package br.com.finalcraft.evernifecore.config.uuids;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UUIDsControllerTest {

    // An unknown name has no stored UUID; normalizeName must echo the input instead of NPEing on
    // ConcurrentHashMap.get(null).
    @Test
    public void normalizeNameEchoesUnknownName() {
        assertEquals("NeverSeenName", UUIDsController.normalizeName("NeverSeenName"));
    }

    // A known name normalizes to its stored canonical casing regardless of the input casing.
    @Test
    public void normalizeNameReturnsCanonicalStoredForm() {
        UUID uuid = UUID.randomUUID();
        UUIDsController.addOrUpdateUUIDName(uuid, "SteVe");
        assertEquals("SteVe", UUIDsController.normalizeName("steve"));
    }
}
