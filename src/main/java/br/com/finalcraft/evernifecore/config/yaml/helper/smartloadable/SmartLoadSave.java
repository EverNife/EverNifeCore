package br.com.finalcraft.evernifecore.config.yaml.helper.smartloadable;

import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;

import java.util.function.BiConsumer;
import java.util.function.Function;

public class SmartLoadSave<O> implements ISmartLoadSave<O>{

    private final Class clazz;

    private transient BiConsumer<ConfigSection, O> onConfigSave = null;
    private transient Function<ConfigSection, O> onConfigLoad = null;

    private transient Function<O, String> onStringSerialize = null;
    private transient Function<String, O> onStringDeserialize = null;

    private boolean acceptExtends = false;
    private boolean hasAlreadyBeenScanned = true;

    public SmartLoadSave(Class clazz) {
        this.clazz = clazz;
    }

    /**
     * If the acceptExtends flag is false, then return true if the class is the same as the class passed in, otherwise
     * return true if the class passed in is a subclass of the class.
     *
     * @param other The class to check against
     */
    public boolean match(Class other){
        return this.acceptExtends == false ? this.clazz == other : this.clazz.isAssignableFrom(other);
    }

    public boolean isAcceptExtends() {
        return acceptExtends;
    }

    public BiConsumer<ConfigSection, O> getOnConfigSave() {
        return onConfigSave;
    }

    public Function<ConfigSection, O> getOnConfigLoad() {
        return onConfigLoad;
    }

    public Function<O, String> getOnStringSerialize() {
        return onStringSerialize;
    }

    public Function<String, O> getOnStringDeserialize() {
        return onStringDeserialize;
    }

    public boolean hasAlreadyBeenScanned() {
        return hasAlreadyBeenScanned;
    }

    public boolean isSalvable(){
        return onConfigSave != null;
    }

    public boolean isLoadable(){
        return onConfigLoad != null;
    }

    public SmartLoadSave<O> setHasAlreadyBeenScanned(boolean hasAlreadyBeenScanned) {
        this.hasAlreadyBeenScanned = hasAlreadyBeenScanned;
        return this;
    }

    public SmartLoadSave<O> setOnConfigSave(BiConsumer<ConfigSection, O> onConfigSave) {
        this.onConfigSave = onConfigSave;
        return this;
    }

    public SmartLoadSave<O> setOnConfigLoad(Function<ConfigSection, O> onConfigLoad) {
        this.onConfigLoad = onConfigLoad;
        return this;
    }

    public SmartLoadSave<O> setOnStringSerialize(Function<O, String> onStringSerialize) {
        this.onStringSerialize = onStringSerialize;
        return this;
    }

    public SmartLoadSave<O> setOnStringDeserialize(Function<String, O> onStringDeserialize) {
        this.onStringDeserialize = onStringDeserialize;
        return this;
    }

    public SmartLoadSave<O> setAllowExtends(boolean acceptExtends) {
        this.acceptExtends = acceptExtends;
        return this;
    }

    //
    //
    //


    @Override
    public void onConfigSave(ConfigSection section, O value) {
        this.onConfigSave.accept(section, value);
    }

    @Override
    public O onConfigLoad(ConfigSection section) {
        return onConfigLoad.apply(section);
    }

    @Override
    public String onStringSerialize(O value) {
        return onStringSerialize.apply(value);
    }

    @Override
    public O onStringDeserialize(String serializedValue) {
        return onStringDeserialize.apply(serializedValue);
    }

    @Override
    public boolean canSerializeToStringList(){
        return onStringDeserialize != null && onStringSerialize != null;
    }

}
