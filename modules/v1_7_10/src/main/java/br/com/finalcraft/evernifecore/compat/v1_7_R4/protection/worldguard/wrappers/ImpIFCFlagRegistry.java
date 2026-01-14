package br.com.finalcraft.evernifecore.compat.v1_7_R4.protection.worldguard.wrappers;

import br.com.finalcraft.evernifecore.protection.worldguard.IFCFlagRegistry;
import br.com.finalcraft.evernifecore.unsafereflecton.UnsafeUtil;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.SneakyThrows;
import org.bukkit.plugin.java.JavaPlugin;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ImpIFCFlagRegistry implements IFCFlagRegistry {

    private static boolean modifiedWorldguard = false;
    private static Field flagsListField = null;
    private static boolean shouldUseUnsafe = false;
    public static boolean errorOnFlagRegistering;

    public ImpIFCFlagRegistry() {
        try {
            //We have a MODIFIED WorldGuard.jar with the extra method 'addFlag'
            DefaultFlag.class.getDeclaredMethod("addFlag", Flag.class);
            modifiedWorldguard = true;
            return;
        }catch (Throwable e){
            //ignore
        }
        try {
            flagsListField = DefaultFlag.class.getDeclaredField("flagsList");
            flagsListField.setAccessible(true);

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(flagsListField, flagsListField.getModifiers() & ~Modifier.FINAL);
        }catch (Throwable e1){
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                if (theUnsafe != null){
                    shouldUseUnsafe = true;
                    return;
                }
            }catch (Throwable e2){
                errorOnFlagRegistering = true;
                JavaPlugin everNifeCore = JavaPlugin.getProvidingPlugin(ImpIFCFlagRegistry.class);
                everNifeCore.getLogger().severe("There was an error Injecting ImpIFCFlagRegistry reflection.");
                everNifeCore.getLogger().severe("This error will prevent EverNifeCore from registering WorldGuardFlags!");
                everNifeCore.getLogger().severe("This error can possibly be solved by using a custom version of WorldGuard for 1.7.10, Ask EverNife for it!");
                everNifeCore.getLogger().severe("First Error when trying to use Normal Reflection\n\n");
                e1.printStackTrace();
                everNifeCore.getLogger().severe("\n\nSecond Error when trying to use sun.unsafe.UNSAFE.class \n\n");
                e2.printStackTrace();
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
                if (shouldUseUnsafe){
                    UnsafeUtil.setField(flagsListField, null, theNewFlagsArray);
                }else {
                    flagsListField.set(null,theNewFlagsArray);
                }

            }catch (Exception e){
                throw new RuntimeException(e);
            }
        }
    }

    @SneakyThrows
    private Flag<?>[] getFlagList(){
        return modifiedWorldguard
                ? DefaultFlag.getFlags()
                : (Flag<?>[]) flagsListField.get(null);
    }


    @Override
    public @Nullable Flag<?> get(String name) {
        for (Flag<?> flag : this.getFlagList()) {
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
        return this.getFlagList().length;
    }

    @Nonnull
    @Override
    public Iterator<Flag<?>> iterator() {
        return Arrays.asList(this.getFlagList()).iterator();
    }

    @Override
    public Object getHandle() {
        return null;
    }
}
