package br.com.finalcraft.evernifecore.hytale.screen;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Where a core page meets the server: the player's world and {@code PageManager}. The page renderer never touches
 * either directly, which is what lets a test put a recorder here.
 */
interface HyPageTransport {

    /** The server's own pages, through each player's {@code PageManager}. */
    HyPageTransport PAGE_MANAGER = new PageManagerTransport();

    /** The world {@code viewer} stands in, or {@code null}: offline, between worlds, or not a Hytale player. Any thread. */
    @Nullable
    String worldOf(@Nonnull FPlayer viewer);

    /** The connection a page for {@code viewer} is built on; {@code null} only where the server is not involved. */
    @Nullable
    PlayerRef refOf(@Nonnull FPlayer viewer);

    /** Shows {@code page} to {@code viewer}, over whatever page they had. False when they are no longer in a world. World thread. */
    boolean open(@Nonnull FPlayer viewer, @Nonnull HyPage page);

    /** Sends {@code ui} as an update of {@code page}. World thread. */
    void update(@Nonnull HyPage page, @Nonnull UICommandBuilder ui);

    /** Takes {@code page} away when it is the one on screen; false when it no longer was. World thread. */
    boolean close(@Nonnull FPlayer viewer, @Nonnull HyPage page);

    final class PageManagerTransport implements HyPageTransport {

        private PageManagerTransport() {

        }

        @Nullable
        @Override
        public String worldOf(@Nonnull FPlayer viewer) {
            if (!(viewer instanceof HytaleFPlayer<?> player) || !player.isOnline()) {
                return null;
            }
            World world = player.getWorld();
            return world == null ? null : world.getName();
        }

        @Nullable
        @Override
        public PlayerRef refOf(@Nonnull FPlayer viewer) {
            return viewer instanceof HytaleFPlayer<?> player ? player.getPlayerRef() : null;
        }

        @Override
        public boolean open(@Nonnull FPlayer viewer, @Nonnull HyPage page) {
            PageManager pages = pagesOf(viewer);
            if (pages == null) {
                return false;
            }
            Ref<EntityStore> ref = refOf(viewer).getReference();
            pages.openCustomPage(ref, ref.getStore(), page);
            return true;
        }

        @Override
        public void update(@Nonnull HyPage page, @Nonnull UICommandBuilder ui) {
            page.push(ui);
        }

        @Override
        public boolean close(@Nonnull FPlayer viewer, @Nonnull HyPage page) {
            PageManager pages = pagesOf(viewer);
            if (pages == null || pages.getCustomPage() != page) {
                return false;
            }
            page.closeNow();
            return true;
        }

        @Nullable
        private PageManager pagesOf(FPlayer viewer) {
            PlayerRef playerRef = refOf(viewer);
            Ref<EntityStore> ref = playerRef == null ? null : playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                return null;
            }
            Store<EntityStore> store = ref.getStore();
            Player player = store == null ? null : store.getComponent(ref, Player.getComponentType());
            return player == null ? null : player.getPageManager();
        }

    }

}
