package br.com.finalcraft.evernifecore.commands.finalcmd.executor;

import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.parameter.CMDParameterType;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLine;
import br.com.finalcraft.evernifecore.config.playerdata.PDSection;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import br.com.finalcraft.evernifecore.util.ReflectionUtil;
import br.com.finalcraft.evernifecore.util.commons.Tuple;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ExecutorInterpreter {

    public final Method method;
    public final Object executor;

    private static final CMDParameterType[] ALLOWED_CLASSES = new CMDParameterType[]{
            CMDParameterType.of(CommandSender.class).build(),
            CMDParameterType.of(String.class).build(),
            CMDParameterType.of(MultiArgumentos.class).build(),
            CMDParameterType.of(HelpContext.class).build(),
            CMDParameterType.of(HelpLine.class).build(),

            CMDParameterType.of(ItemStack.class)
                    .setOnlyPlayer(true)
                    .build(),

            CMDParameterType.of(Player.class)
                    .setOnlyPlayer(true)
                    .build(),

            CMDParameterType.of(PlayerData.class)
                    .setOnlyPlayer(true)
                    .build(),

            CMDParameterType.of(PDSection.class)
                    .setOnlyPlayer(true)
                    .setAllowExtends(true)
                    .build(),
    };

    public final Map<Integer, Tuple<CMDParameterType,Class>> methodArguments = new HashMap();

    public boolean playerArg = false;

    public ExecutorInterpreter(Method method, Object executor) {
        this.method = method;
        this.executor = executor;

        if (!method.isAccessible()) method.setAccessible(true);

        for (CMDParameterType parameterType : ALLOWED_CLASSES) {
            Integer index = ReflectionUtil.getArgIndex(method, parameterType.getClazz(), parameterType.isCheckExtends());
            if (index != null){
                Tuple<CMDParameterType, Class> tuple = Tuple.of(parameterType, ReflectionUtil.getArgAtIndex(method, index));

                methodArguments.put(index, tuple);

                if (parameterType.isOnlyPlayer()){
                    playerArg = true;
                }
            }
        }

        if (methodArguments.size() == 0) {
            throw new IllegalStateException("You tried to create a FinalCMD with a method that has no args at all!");
        }

    }

    public void invoke(CommandSender sender, String label, MultiArgumentos argumentos, HelpContext helpContext, HelpLine helpLine) throws IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {

        Object[] possibleArgs = new Object[]{label, argumentos, helpContext, helpLine};
        Object[] theArgs = new Object[methodArguments.size()];

        for (int index = 0; index < theArgs.length; index++) {

            Tuple<CMDParameterType, Class> tuple = methodArguments.get(index);

            CMDParameterType parameterType = tuple.getAlfa();
            Class parameterClass = tuple.getBeta();

            if (parameterType.getClazz() == CommandSender.class) { theArgs[index] = sender; continue; }
            if (parameterType.getClazz() == Player.class) { theArgs[index] = sender; continue; }
            if (parameterType.getClazz() == PlayerData.class) { theArgs[index] = PlayerController.getPlayerData((Player) sender); continue; }
            if (parameterType.getClazz() == PDSection.class) { theArgs[index] = PlayerController.getPDSection((Player) sender, parameterClass); continue; }
            if (parameterType.getClazz() == ItemStack.class) {
                theArgs[index] = FCBukkitUtil.getPlayersHeldItem((Player) sender);
                if (theArgs[index] == null){
                    FCMessageUtil.needsToBeHoldingItem(sender);
                    return;
                }
            }

            for (Object possibleArg : possibleArgs) {
                if (parameterType.isCheckExtends()){
                    if (parameterClass.isInstance(possibleArg)){
                        theArgs[index] = possibleArg;
                        break;
                    }
                }else if (parameterClass == possibleArg.getClass()){
                    theArgs[index] = possibleArg;
                    break;
                }
            }
        }

        method.invoke(executor, theArgs);
    }
}
