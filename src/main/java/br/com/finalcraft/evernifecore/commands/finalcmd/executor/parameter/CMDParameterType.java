package br.com.finalcraft.evernifecore.commands.finalcmd.executor.parameter;

public class CMDParameterType<T> {

    private final Class<T> clazz;
    private final boolean checkExtends;
    private final boolean onlyPlayer;

    public CMDParameterType(Class<T> clazz, boolean checkExtends, boolean onlyPlayer) {
        this.clazz = clazz;
        this.checkExtends = checkExtends;
        this.onlyPlayer = onlyPlayer;
    }

    public static <T> Builder<T> of(Class<T> clazz){
        return new Builder<>(clazz);
    }

    public Class<T> getClazz() {
        return clazz;
    }

    public boolean isCheckExtends() {
        return checkExtends;
    }

    public boolean isOnlyPlayer() {
        return onlyPlayer;
    }

    public static class Builder<T>{

        private final Class<T> clazz;
        private boolean allowExtends = false;
        private boolean onlyPlayer = false;

        private Builder(Class<T> clazz) {
            this.clazz = clazz;
        }

        public Builder<T> setAllowExtends(boolean allowExtends) {
            this.allowExtends = allowExtends;
            return this;
        }

        public Builder<T> setOnlyPlayer(boolean onlyPlayer) {
            this.onlyPlayer = onlyPlayer;
            return this;
        }

        public CMDParameterType<T> build(){
            return new CMDParameterType<>(this.clazz, this.allowExtends, this.onlyPlayer);
        }
    }

    @Override
    public String toString() {
        return "CMDParameterType{" +
                "clazz=" + clazz +
                ", checkExtends=" + checkExtends +
                ", onlyPlayer=" + onlyPlayer +
                '}';
    }
}
