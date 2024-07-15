package br.com.finalcraft.evernifecore.config.yaml.caching;

import br.com.finalcraft.evernifecore.config.yaml.helper.ConfigHelper;
import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import org.simpleyaml.configuration.file.YamlFile;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class SmartCachedYamlFileHolder implements IHasYamlFile {

    private static final long SECONDS_OF_CACHE = TimeUnit.MINUTES.toSeconds(5);

    private YamlFile yamlFile;
    private long lastUsed = 0;

    //Restoration
    private File file;
    private String contentAsString;
    private boolean hasComments;

    private final ReentrantLock lock = new ReentrantLock();

    public SmartCachedYamlFileHolder(YamlFile yamlFile) {
        this.yamlFile = yamlFile;
        this.hasComments = yamlFile.options().useComments();
        scheduleExpirationRunnable(TimeUnit.MINUTES.toSeconds(3));//Do a Faster first lookup
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
            scheduleExpirationRunnable(SECONDS_OF_CACHE);
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

    private void scheduleExpirationRunnable(long secondsToWait){
        FCScheduler.getScheduler().schedule(() -> {
            if (this.innerGetYamlFile() == null){
                return;//It means was cached to string manually
            }

            boolean usedAtLeastOnceOnTheLastThreeMinutes = System.currentTimeMillis() < getLastUsed() + TimeUnit.SECONDS.toMillis(SECONDS_OF_CACHE);

            if (usedAtLeastOnceOnTheLastThreeMinutes) {
                //Lets do nothing, just check again in 3 minutes
                scheduleExpirationRunnable(SECONDS_OF_CACHE);
            } else {
                cacheToString();
            }
        }, secondsToWait, TimeUnit.SECONDS);
    }
}
