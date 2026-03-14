package br.com.finalcraft.evernifecore.api.common;

import org.apache.commons.lang3.Validate;

public interface IFHasDelegate {

    public Object getDelegate();

    public default <DELEGATE> DELEGATE getDelegate(Class<DELEGATE> delegateClass) {
        if (!delegateClass.isAssignableFrom(this.getDelegate().getClass())){
            throw new IllegalArgumentException("Invalid Delegate Conversion: #" + this.getClass().getSimpleName() + " | Requested conversion target class [" + delegateClass.getName() + "] is not assignable from the exising real delegate " + this.getDelegate().getClass().getName());
        }
        Validate.isTrue(delegateClass.isAssignableFrom(this.getDelegate().getClass()), "");
        return (DELEGATE) getDelegate();
    }

}
