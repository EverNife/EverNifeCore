package br.com.finalcraft.evernifecore.api.common.commandsender;

import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A raw message keeps what somebody typed: section-sign colours apply, an ampersand stays an ampersand. */
class SendRawMessageTest {

    @Test
    void anAmpersandIsTextAndASectionSignIsAColour() {
        TestCommandSender sender = new TestCommandSender("console");

        sender.sendRawMessage("§aTom & Jerry &cnot red");

        assertEquals(Collections.singletonList("§aTom & Jerry &cnot red"), sender.getMessages());
    }

    @Test
    void theColouredSendStillReadsTheAmpersand() {
        TestCommandSender sender = new TestCommandSender("console");

        sender.sendMessage("&cred");

        assertEquals(Collections.singletonList("§cred"), sender.getMessages(),
                "the contrast the raw send exists for");
    }
}
