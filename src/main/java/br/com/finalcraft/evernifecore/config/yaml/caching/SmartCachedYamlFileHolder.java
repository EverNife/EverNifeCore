package br.com.finalcraft.evernifecore.config.yaml.caching;

import br.com.finalcraft.evernifecore.config.yaml.helper.CfgExecutor;
import br.com.finalcraft.evernifecore.config.yaml.helper.ConfigHelper;
import org.simpleyaml.configuration.file.YamlFile;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SmartCachedYamlFileHolder implements IHasYamlFile {

    private static final long MINUTES_OF_CACHE = 5;

    private YamlFile yamlFile;
    private long lastUsed = 0;

    //Restoration
    private File file;
    private String contentAsString;
    private boolean hasComments;

    private transient ScheduledFuture<?> scheduledFuture;

    public SmartCachedYamlFileHolder(YamlFile yamlFile) {
        this.yamlFile = yamlFile;
        this.hasComments = yamlFile.options().useComments();
        scheduleExpirationRunnable((int) Math.ceil(MINUTES_OF_CACHE / 2d), TimeUnit.MINUTES); //Do a Faster first lookup
    }

    @Override
    public synchronized YamlFile getYamlFile() {
        lastUsed = System.currentTimeMillis();
        if (this.yamlFile == null){
            yamlFile = ConfigHelper.createYamlFile(this.file);
            if (hasComments){
                yamlFile.options().useComments(hasComments);
            }
            try {
                yamlFile.loadFromString(contentAsString);
            } catch (IOException e) {
                throw new RuntimeException("Failed to Re-Read a SmartCached YamlFile, this should never happen!!!! ?!?!?!", e);
            }
            scheduleExpirationRunnable(MINUTES_OF_CACHE, TimeUnit.MINUTES);
        }
        return yamlFile;
    }

    public long getLastUsed() {
        return lastUsed;
    }

    private YamlFile innerGetYamlFile() {
        return this.yamlFile;
    }

    public synchronized void cacheToString(){
        this.file = yamlFile.getConfigurationFile();
        this.contentAsString = yamlFile.toString();
        this.yamlFile = null;//free for GC to collect
    }

    public void scheduleExpirationRunnable(long delay, TimeUnit timeUnit){
        if (scheduledFuture != null && !scheduledFuture.isCancelled() && !scheduledFuture.isDone()){
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }

        if (delay < 0){
            if (this.innerGetYamlFile() != null) {
                cacheToString();
            }
            return;
        }

        this.scheduledFuture = CfgExecutor.getScheduler().schedule(() -> {
            if (this.innerGetYamlFile() != null) {

                boolean usedAtLeastOnceOnTheLastThreeMinutes = System.currentTimeMillis() < getLastUsed() + TimeUnit.MINUTES.toMillis(MINUTES_OF_CACHE);

                if (usedAtLeastOnceOnTheLastThreeMinutes) {
                    //Let's do nothing, just check again in MINUTES_OF_CACHE minutes
                    scheduleExpirationRunnable(MINUTES_OF_CACHE, TimeUnit.MINUTES);
                } else {
                    cacheToString();
                }

            }
        }, delay, timeUnit);
    }
}
