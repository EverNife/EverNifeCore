package br.com.finalcraft.evernifecore.thread;

import org.bukkit.plugin.java.JavaPlugin;

public abstract class SimpleThread {

    protected final JavaPlugin javaPlugin = JavaPlugin.getProvidingPlugin(this.getClass());
    protected transient Thread thread;
    protected final String threadName;

    protected boolean silent = false;

    private transient boolean executeOnShutdown = true;
    private transient boolean executeOnStart = true;

    protected Thread getOrCreateThread(){
        if (thread == null || !thread.isAlive()){
            thread = new Thread(){
                @Override
                public void run() {
                    try {
                        if (!isSilent()){
                            SimpleThread.this.javaPlugin.getLogger().info("Starting Thread: " + this.getName());
                        }
                        if (executeOnStart){
                            SimpleThread.this.onStart();
                        }
                        SimpleThread.this.run();
                    } catch (InterruptedException exception) {
                        if (executeOnShutdown){
                            SimpleThread.this.onShutdown();
                        }
                        if (!silent){
                            SimpleThread.this.javaPlugin.getLogger().info("Thread Shutdown: " + this.getName());
                        }
                    }
                }
            };
            thread.setName(this.getThreadName());
            thread.setDaemon(true);
        }
        return thread;
    }

    public SimpleThread(String threadName) {
        this.threadName = threadName;
    }

    public SimpleThread() {
        this.threadName = "[" + this.getClass().getSimpleName() + "]";
    }

    public String getThreadName() {
        return threadName;
    }

    public void start(boolean executeOnStart){
        this.executeOnStart = executeOnStart;
        if (!getOrCreateThread().isAlive()){
            getOrCreateThread().start();
        } else if (allowRestart()){
            shutdown();
            start(executeOnStart);
        }
    }

    public void start(){
        start(true);
    }

    public void shutdown(){
        shutdown(true);
    }

    public void shutdown(boolean executeOnShutdown){
        this.executeOnShutdown = executeOnShutdown;
        if (getOrCreateThread().isAlive()){
            getOrCreateThread().interrupt();
        }
    }

    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    public boolean isSilent() {
        return silent;
    }

    protected abstract void run() throws InterruptedException;

    protected void onShutdown(){

    }

    protected void onStart(){

    }

    protected boolean allowRestart(){
        return false;
    }

}
