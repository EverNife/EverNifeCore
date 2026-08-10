package br.com.finalcraft.evernifecore.actionbar;

import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What actually lands on a player's action bar, and what never does.
 *
 * <p>The manager behind it ticks on its own thread, so nothing here waits on that clock: the first
 * message starts it and every later one is emitted in place by {@code addMessage}, which is the path a
 * second sender takes on a live server anyway. An extra tick landing mid-test can only repeat the
 * message the queue already answers with, never invent another - so every assertion below is about
 * which text can be on screen at all, not about how many times it was written.</p>
 */
class ActionBarDeliveryTest {

    private ECoreTestWorld world;
    private TestPlatform platform;
    private TestFPlayerSender player;

    @BeforeEach
    void setup() {
        player = new TestFPlayerSender("Steve");
    }

    @AfterEach
    void teardown() {
        if (world != null) {
            //terminates the manager's repeating task before the platform it sends through goes away
            player.online(true);
            ActionBarAPI.clear(player);
            ActionBarAPI.clearReferences(player.getUniqueId());
            world.close();
        }
    }

    /** @param supported whether this server can show an action bar at all - 1.7.10 without NecroTempus cannot. */
    private void serverWithActionBar(boolean supported) {
        world = Platforms.strict().actionBarSupported(supported).install();
        platform = world.platform();
    }

    private List<String> textsOnScreen() {
        List<String> texts = new ArrayList<>();
        for (TestPlatform.ActionBarSend send : platform.getActionBars()) {
            texts.add(send.text);
        }
        return texts;
    }

    private String lastTextOnScreen() {
        List<String> texts = textsOnScreen();
        assertFalse(texts.isEmpty(), "nothing was ever put on the action bar");
        return texts.get(texts.size() - 1);
    }

    private static ActionBarMessage lasting(String id, String text, int priority) {
        return ActionBarMessage.of(text).setBarID(id).setPriority(priority).setSeconds(30).build();
    }

    @Test
    void aServerWithoutAnActionBarIsNeverSentOne() {
        serverWithActionBar(false);

        ActionBarAPI.send(player, lasting("greeting", "§ewelcome", 0));
        ActionBarAPI.send(player, lasting("greeting", "§ewelcome", 0));

        assertTrue(platform.getActionBars().isEmpty(),
                "a server that cannot show an action bar must not be handed one: " + textsOnScreen());
    }

    @Test
    void aServerWithAnActionBarGetsTheText() {
        serverWithActionBar(true);

        ActionBarAPI.send(player, lasting("greeting", "§ewelcome", 0));
        ActionBarAPI.send(player, lasting("greeting", "§ewelcome", 0));

        assertEquals("§ewelcome", lastTextOnScreen());
    }

    /** Two messages at once is the normal case - the player has one bar and the loudest claim wins it. */
    @Test
    void theHighestPriorityMessageIsTheOneOnScreen() {
        serverWithActionBar(true);

        ActionBarAPI.send(player, lasting("mana", "§9mana 40/40", 0));
        ActionBarAPI.send(player, lasting("combat", "§cunder attack", 10));

        assertEquals("§cunder attack", lastTextOnScreen(),
                "the low-priority bar is still queued, but it is not the one being shown");
    }

    /** Nothing tells the manager the player left; it is the tick itself that has to notice. */
    @Test
    void aPlayerWhoLeftIsSentNothingMore() {
        serverWithActionBar(true);
        ActionBarAPI.send(player, lasting("mana", "§9mana 40/40", 0));

        player.online(false);
        ActionBarAPI.send(player, lasting("combat", "§cunder attack", 10));

        assertFalse(textsOnScreen().contains("§cunder attack"),
                "a player who is gone has no screen to write to: " + textsOnScreen());
    }

    @Test
    void clearingPutsAnEmptyBarOnScreen() {
        serverWithActionBar(true);
        ActionBarAPI.send(player, lasting("mana", "§9mana 40/40", 0));

        ActionBarAPI.clear(player);

        assertTrue(textsOnScreen().contains(""),
                "an action bar goes away by being overwritten with nothing: " + textsOnScreen());
    }

    @Test
    void aBarLastsTheWholeTimeItWasAskedFor() {
        long askedFor = 3000L;

        long before = System.currentTimeMillis();
        ActionBarMessage message = ActionBarMessage.of("§ethree seconds").setSeconds(3).build();
        long after = System.currentTimeMillis();

        //a tick is 50ms, so 3 seconds is 60 ticks; the window below is only the clock moving while
        //the builder ran, which is why both bounds are anchored on the two readings around it
        assertTrue(message.getTimeToEnd() >= before + askedFor,
                "three seconds has to last three seconds, and this one ends "
                        + (before + askedFor - message.getTimeToEnd()) + "ms early");
        assertTrue(message.getTimeToEnd() <= after + askedFor,
                "and it must not last longer than it was asked for either");
    }

    @Test
    void ticksAndSecondsAgreeOnHowLongTheyAre() {
        ActionBarMessage bySeconds = ActionBarMessage.of("§a").setSeconds(1).build();
        ActionBarMessage byTicks = ActionBarMessage.of("§a").setTicks(20).build();

        assertEquals(bySeconds.getTimeToEnd(), byTicks.getTimeToEnd(), 20d,
                "one second and twenty ticks are the same duration; if they drift, one of the two "
                        + "conversions is wrong");
    }
}
