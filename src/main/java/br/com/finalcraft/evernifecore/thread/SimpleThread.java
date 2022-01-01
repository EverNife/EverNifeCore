package br.com.finalcraft.evernifecore.thread;

import org.bukkit.plugin.java.JavaPlugin;

public abstract class SimpleThread {

    private final JavaPlugin javaPlugin = JavaPlugin.getProvidingPlugin(this.getClass());
    protected final Thread thread = new Thread(){
        @Override
        public void run() {
            try {
                SimpleThread.this.javaPlugin.getLogger().info("Starting Thread: " + this.getName());
                SimpleThread.this.onStart();
                SimpleThread.this.run();
            } catch (InterruptedException exception) {
                SimpleThread.this.onShutdown();
                SimpleThread.this.javaPlugin.getLogger().info("Thread Shutdown: " + this.getName());
            }
        }
    };

    public SimpleThread() {
        thread.setName("[" + this.getClass().getSimpleName() + "]");
        thread.setDaemon(true);
    }

    public SimpleThread(String name) {
        thread.setName(name);
        thread.setDaemon(true);
    }

    public void start(){
        if (!thread.isAlive()){
            thread.start();
        }else if (allowRestart()){
            shutdown();
            start();
        }
    }

    public void shutdown(){
        this.thread.interrupt();
    }

    public abstract void run() throws InterruptedException;

    protected void onShutdown(){

    }

    protected void onStart(){

    }

    public boolean allowRestart(){
        return false;
    }

}
