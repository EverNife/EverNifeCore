package br.com.finalcraft.evernifecore.storage.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BackendDefinition#describeTarget()} must be safe to print in a log: none of the three ways
 * a password can reach a connection string may ever surface in the rendered target.
 */
class BackendDefinitionTest {

    @Test
    void describeTargetRedactsAUserPassEmbeddedInTheUrl() {
        BackendDefinition backend = BackendDefinition.sql("jdbc:mysql://u:s3cr3t@host/db", "root", "unrelated");

        String target = backend.describeTarget();

        assertEquals("jdbc:mysql://u:****@host/db (user 'root')", target);
        assertFalse(target.contains("s3cr3t"));
    }

    @Test
    void describeTargetRedactsAPasswordQueryParameter() {
        BackendDefinition backend = BackendDefinition.postgresql(
                "jdbc:postgresql://host/db?password=s3cr3t", "root", "unrelated");

        String target = backend.describeTarget();

        assertTrue(target.contains("?password=****"));
        assertFalse(target.contains("s3cr3t"));
    }

    @Test
    void describeTargetNeverPrintsThePlainPassField() {
        //the url itself carries no credential - the only place the password could leak from is the
        //separate 'pass' field, which describeTarget() must never reference
        BackendDefinition backend = BackendDefinition.sql("jdbc:mysql://host/db", "root", "SuperSecretPass123");

        String target = backend.describeTarget();

        assertFalse(target.contains("SuperSecretPass123"));
    }

    @Test
    void describeTargetOnMongoRedactsAndNamesTheDatabase() {
        BackendDefinition backend = BackendDefinition.mongo("mongodb://u:s3cr3t@host:27017", "ecore");

        String target = backend.describeTarget();

        assertEquals("mongodb://u:****@host:27017 (db 'ecore')", target);
        assertFalse(target.contains("s3cr3t"));
    }

    @Test
    void describeTargetOnAFileBackendNamesThePathAndFormat() {
        BackendDefinition backend = BackendDefinition.localFile("./plugins/EverNifeCore/Data",
                BackendDefinition.FileFormat.JSON);

        assertEquals("./plugins/EverNifeCore/Data (json)", backend.describeTarget());
    }

    @Test
    void describeTargetOnMemoryIsAFixedLabel() {
        assertEquals("in-memory (ephemeral)", BackendDefinition.memory().describeTarget());
    }
}
