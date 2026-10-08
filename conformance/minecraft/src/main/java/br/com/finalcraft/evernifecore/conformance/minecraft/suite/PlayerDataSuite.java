package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link PDSection} of the player written with the storage backend this server really has, and read back after
 * a restart. The write half saves a fresh token and leaves it in {@code playerdata.token}; the read half asks for
 * the section only if it is present - a transient default would not be - and compares.
 */
public final class PlayerDataSuite implements ConformanceSuite {

    /** The id the plugin registers {@link Section} under. */
    public static final String SECTION_ID = "ecc_conformance";
    private static final String TOKEN_FILE = "playerdata.token";
    private static final long TIMEOUT_MILLIS = 10_000L;

    @Override
    public String getId() {
        return "playerdata";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Collections.emptyList();
    }

    @Override
    public List<ConformanceCase> getWriteCases(ConformanceContext ctx) {
        return Collections.singletonList(PlayerCases.later("write", ctx, player -> {
            String token = UUID.randomUUID().toString();
            CompletableFuture<Verdict> answer = PlayerController.getPDSection(player.getUniqueId(), Section.class)
                    .thenCompose(section -> {
                        section.setToken(token);
                        section.markDirty();
                        return section.forceSavePDSection();
                    })
                    .thenApply(saved -> {
                        try {
                            Files.write(tokenFile(ctx).toPath(), token.getBytes(StandardCharsets.UTF_8));
                        } catch (Exception e) {
                            return Verdict.fail("saved, but the token file failed: " + Verdict.describe(e));
                        }
                        return Verdict.pass("saved token=" + token + " backends=" + PlayerController.getEnabledBackendNames());
                    })
                    .exceptionally(thrown -> Verdict.fail(Verdict.describe(thrown)));
            return PlayerCases.within(answer, TIMEOUT_MILLIS);
        }));
    }

    @Override
    public List<ConformanceCase> getReadCases(ConformanceContext ctx) {
        return Collections.singletonList(PlayerCases.later("read", ctx, player -> {
            File file = tokenFile(ctx);
            if (!file.isFile()) {
                return CompletableFuture.completedFuture(Verdict.fail("no " + TOKEN_FILE + " - the write half never ran here"));
            }
            String expected = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
            CompletableFuture<Verdict> answer = PlayerController.getPlayerData(player.getUniqueId())
                    .thenCompose(data -> data.getPDSectionIfPresent(Section.class))
                    .thenApply(found -> {
                        if (!found.isPresent()) {
                            return Verdict.fail("the section is not stored (expected token=" + expected + ")");
                        }
                        String read = found.get().getToken();
                        return Verdict.of(expected.equals(read), "read token=" + read + " expected=" + expected);
                    })
                    .exceptionally(thrown -> Verdict.fail(Verdict.describe(thrown)));
            return PlayerCases.within(answer, TIMEOUT_MILLIS);
        }));
    }

    private static File tokenFile(ConformanceContext ctx) {
        return new File(ctx.getDataFolder(), TOKEN_FILE);
    }

    /** The section the suite writes; a plain Jackson POJO like any plugin's. */
    public static class Section extends PDSection {

        private String token;

        public Section() {
            //Jackson no-arg constructor
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}
