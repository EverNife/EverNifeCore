package br.com.finalcraft.evernifecore.minecraft.consolefilter;

import br.com.finalcraft.evernifecore.consolefilter.ECConsoleFilter;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Log4j hook denies what the registered provider hides, and asks the provider in place now, not the one it was built with. */
@ECoreTest
class ECBukkitConsoleFilterTest {

    private static LogEvent line(String text) {
        return Log4jLogEvent.newBuilder().setMessage(new SimpleMessage(text)).build();
    }

    @Test
    void theHookDeniesWhatTheProviderHides() {
        ECConsoleFilter consoleFilter = new ECConsoleFilter();
        ECBukkitConsoleFilter hook = new ECBukkitConsoleFilter(consoleFilter);

        assertEquals(Filter.Result.DENY, hook.filter(line("Steve issued server command: /ecdcmd 4f1c2b6e")));
        assertEquals(Filter.Result.NEUTRAL, hook.filter(line("Steve issued server command: /spawn")));

        consoleFilter.setProvider(message -> message.contains("/spawn"));

        assertEquals(Filter.Result.DENY, hook.filter(line("Steve issued server command: /spawn")),
                "a provider registered after the hook was installed is the one it asks");
    }
}
