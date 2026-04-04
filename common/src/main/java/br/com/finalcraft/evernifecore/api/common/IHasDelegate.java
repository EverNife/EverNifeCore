package br.com.finalcraft.evernifecore.api.common;

public interface IHasDelegate {

    public Object getDelegate();

    public default <DELEGATE> DELEGATE getDelegate(Class<DELEGATE> delegateClass) {
        if (!delegateClass.isAssignableFrom(this.getDelegate().getClass())){
            throw new IllegalArgumentException("Invalid Delegate Conversion: #" + this.getClass().getSimpleName() + " | Requested conversion target class [" + delegateClass.getName() + "] is not assignable from the exising real delegate " + this.getDelegate().getClass().getName());
        }
        return (DELEGATE) getDelegate();
    }

}
