package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.pageviwer.PageVizualization;
import com.google.common.collect.ImmutableList;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ArgParserPageVizualization extends ArgParser<PageVizualization> {

    private final ArgParserNumber argParserNumber;

    public ArgParserPageVizualization(ArgInfo argInfo) {
        super(argInfo);
        if (argInfo.getArgData().context().isEmpty()){
            argInfo.getArgData().context("[1:*]");//By default, the context start at 1 and goes to infinity
        }
        this.argParserNumber = new ArgParserNumber(new ArgInfo(
                Integer.class,
                argInfo.getArgData(),
                argInfo.getIndex(),
                argInfo.getRequirementType()
        ));
    }

    @Override
    public PageVizualization parserArgument(@NotNull CommandSender sender, @NotNull Argumento argumento) throws ArgParseException {

        if (argumento.equalsIgnoreCase("all") && sender.hasPermission(PermissionNodes.EVERNIFECORE_PAGEVIEWER_ALL)){
            return new PageVizualization(0, 0, true);
        }

        if (argumento.toString().contains("-") && sender.hasPermission(PermissionNodes.EVERNIFECORE_PAGEVIEWER_INTERVAL)){
            //Probably an interval, like 'page 1-5'
            String[] split = argumento.toString().split("-");
            if (split.length == 2){
                int page1 = argParserNumber.parserArgument(sender, new Argumento(split[0])).intValue();
                int page2 = argParserNumber.parserArgument(sender, new Argumento(split[1])).intValue();
                return new PageVizualization(Math.min(page1, page2), Math.max(page1, page2), false);
            }
            //If the split is not 2, then it's not an interval, so it will be parsed as a single page
        }

        Integer page = (Integer) argParserNumber.parserArgument(sender, argumento);

        if (!argInfo.isRequired() && page == null){
            return null;
        }

        return new PageVizualization(
                page,
                page,
                false
        );
    }

    @Override
    public @NotNull List<String> tabComplete(Context context) {

        return ImmutableList.of();

    }
}
