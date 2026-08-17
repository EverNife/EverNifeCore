package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.integration.placeholders.PAPIIntegration;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PAPIIntegration} is the common-side door plugins guard on, and the only thing behind it is
 * the platform. It once answered a hardcoded {@code false}, which silently disabled every guarded
 * registration on every server - no error, no log. So what is pinned here is the delegation itself:
 * the answer has to track the installed platform in both directions.
 */
@ECoreTest
class PAPIIntegrationDelegationTest {

    @Test
    void isPresentAnswersWhatThePlatformAnswers(TestPlatform platform) {
        platform.papiPresent = true;
        assertTrue(PAPIIntegration.isPresent());

        platform.papiPresent = false;
        assertFalse(PAPIIntegration.isPresent());
    }
}
