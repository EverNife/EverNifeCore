package br.com.finalcraft.evernifecore.compat.v1_7_R4.protection.worldguard.wrappers;

import br.com.finalcraft.evernifecore.protection.worldguard.IFCFlagRegistry;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ImpIFCFlagRegistry implements IFCFlagRegistry {

    private static boolean modifiedWorldguard = false;
    private static Field flagsListField = null;

    public ImpIFCFlagRegistry() {
        try {
           DefaultFlag.class.getDeclaredMethod("addFlag", Flag.class);
            modifiedWorldguard = true;
        }catch (Exception e){

        }
        if (modifiedWorldguard == false){
            try {
                flagsListField = DefaultFlag.class.getDeclaredField("flagsList");
                flagsListField.setAccessible(true);

                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(flagsListField, flagsListField.getModifiers() & ~Modifier.FINAL);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    @Override
    public void register(Flag<?> flag) {
        if (modifiedWorldguard){
            DefaultFlag.addFlag(flag);
        }else {
            try {
                //Create a copy of old array and increase its size by one
                Flag<?>[] oldFlagsArray = (Flag<?>[]) flagsListField.get(null);
                Flag<?>[] theNewFlagsArray = Arrays.copyOf(oldFlagsArray, oldFlagsArray.length + 1);
                //Add the new flag at the end of the new array
                theNewFlagsArray[theNewFlagsArray.length - 1] = flag;

                //Reset the final field of DefautlFlags.flags
                flagsListField.set(null,theNewFlagsArray);
            }catch (Exception e){
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public @Nullable Flag<?> get(String name) {
        for (Flag<?> flag : DefaultFlag.getFlags()) {
            if (flag.getName().equalsIgnoreCase(name)){
                return flag;
            }
        }
        return null;
    }

    @Override
    public List<Flag<?>> getAll() {
        List<Flag<?>> flags = new ArrayList<>();
        iterator().forEachRemaining(flag -> flags.add(flag));
        return flags;
    }

    @Override
    public int size() {
        return DefaultFlag.getFlags().length;
    }

    @NotNull
    @Override
    public Iterator<Flag<?>> iterator() {
        return Arrays.asList(DefaultFlag.getFlags()).iterator();
    }

    @Override
    public Object getHandle() {
        return null;
    }
}
