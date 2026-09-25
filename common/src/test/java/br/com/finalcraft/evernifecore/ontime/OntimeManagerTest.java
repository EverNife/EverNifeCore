package br.com.finalcraft.evernifecore.ontime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A provider leaves with its plugin, and only ever takes itself out. */
class OntimeManagerTest {

    private final IOntimeProvider first = playerData -> 10;
    private final IOntimeProvider second = playerData -> 20;

    @AfterEach
    void teardown() {
        OntimeManager.removeProvider(OntimeManager.getProvider());
    }

    @Test
    void removingTheAnsweringProviderBringsBackTheCoresZero() {
        OntimeManager.setOntimeProvider(first);

        assertTrue(OntimeManager.removeProvider(first));
        assertEquals(0L, OntimeManager.getProvider().getOntime(null));
    }

    @Test
    void aReplacedProviderCannotEvictItsSuccessor() {
        OntimeManager.setOntimeProvider(first);
        OntimeManager.setOntimeProvider(second);

        assertFalse(OntimeManager.removeProvider(first));
        assertSame(second, OntimeManager.getProvider());
    }
}
