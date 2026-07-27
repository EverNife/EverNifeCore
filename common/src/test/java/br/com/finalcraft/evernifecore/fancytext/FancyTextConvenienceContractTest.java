package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The FancyText convenience shortcuts: {@code appendLine} equals the {@code append("\n"+...)} it
 * replaces, and {@code send(List)} delivers the same as the varargs form.
 */
@ECoreTest
public class FancyTextConvenienceContractTest {


    @Test
    void appendLineEqualsAppendingANewlinePrefixedText() {
        String viaLine = FancyFormatter.of("base").appendLine("§ahello").toLegacyString();
        String viaAppend = FancyFormatter.of("base").append("\n§ahello").toLegacyString();
        assertEquals(viaAppend, viaLine, "appendLine(text) must equal append(\"\\n\" + text)");
    }

    @Test
    void appendLineFormatEqualsAppendingAFormattedNewlinePrefixedText() {
        String viaLine = FancyFormatter.of("base").appendLine("§acount %d name %s", 5, "Steve").toLegacyString();
        String viaAppend = FancyFormatter.of("base")
                .append(String.format("\n§acount %d name %s", 5, "Steve")).toLegacyString();
        assertEquals(viaAppend, viaLine, "appendLine(fmt, args) must equal append(String.format(\"\\n\"+fmt, args))");
    }

    @Test
    void sendListDeliversTheSameAsSendVarargs() {
        FancySegment message = new FancySegment("§aHello everyone");

        TestCommandSender viaList = new TestCommandSender("viaList");
        TestCommandSender viaVarargs = new TestCommandSender("viaVarargs");

        message.send(Collections.singletonList(viaList));
        message.send(viaVarargs);

        assertEquals(viaVarargs.getMessages(), viaList.getMessages(),
                "send(List) must deliver exactly what send(varargs) does");
    }

    @Test
    void sendListReachesEveryRecipient() {
        FancySegment message = new FancySegment("§bHi");
        TestCommandSender one = new TestCommandSender("one");
        TestCommandSender two = new TestCommandSender("two");

        message.send(Arrays.asList(one, two));

        assertEquals(1, one.getMessages().size(), "the first recipient must receive the message");
        assertEquals(1, two.getMessages().size(), "the second recipient must receive the message");
    }
}
