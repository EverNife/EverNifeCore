package br.com.finalcraft.evernifecore.blockdata;

/** The block value the block-store tests store: a plain Jackson POJO, the shape a plugin would use. */
public class Marker {

    private String owner;
    private int amount;

    public Marker() {
    }

    public Marker(String owner, int amount) {
        this.owner = owner;
        this.amount = amount;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "Marker{" + owner + ", " + amount + "}";
    }
}
