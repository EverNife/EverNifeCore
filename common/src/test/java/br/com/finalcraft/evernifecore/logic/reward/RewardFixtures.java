package br.com.finalcraft.evernifecore.logic.reward;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Reads the reward fixtures under {@code /rewards} and the inline JSON the reward tests write. */
final class RewardFixtures {

    private RewardFixtures() {
    }

    /** {@code /rewards/<name>} from the test classpath, as YAML. */
    static JsonNode yaml(String name) {
        try (InputStream in = RewardFixtures.class.getResourceAsStream("/rewards/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("/rewards/" + name + " is not on the test classpath");
            }
            return new YAMLMapper().readTree(in);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** JSON written with single quotes. */
    static JsonNode node(String singleQuotedJson) {
        try {
            return new ObjectMapper().readTree(singleQuotedJson.replace('\'', '"'));
        } catch (IOException unreadable) {
            throw new IllegalArgumentException(unreadable);
        }
    }

    static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
}
