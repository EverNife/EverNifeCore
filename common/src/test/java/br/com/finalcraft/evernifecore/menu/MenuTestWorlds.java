package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Menus;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** The screen world (with an inventory), the shop's logic world and the menus over both, opened around every test. */
abstract class MenuTestWorlds {

    @TempDir
    Path tempDir;

    ScreenWorld screens;
    LogicWorld shop;
    Menus menus;
    private final List<ECEventSubscription<?>> listeners = new ArrayList<>();

    @BeforeEach
    void openWorlds() {
        screens = ScreenWorld.open(tempDir, Menus::inventorySlots);
        shop = LogicWorld.open("FakeShop", tempDir.resolve("shop"));
        menus = Menus.in(screens, shop);
    }

    @AfterEach
    void closeWorlds() {
        listeners.forEach(ECEventSubscription::unsubscribe);
        menus.close();
        shop.close();
        screens.close();
    }

    /** Listens to the pre-event until the test ends. */
    void onPreEvent(Consumer<TransactionPreEvent> listener) {
        listeners.add(ECEventBus.global().subscribe(TransactionPreEvent.class, listener::accept));
    }
}
