package br.com.finalcraft.evernifecore.commands.finalcmd.executor;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.CMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.FinalCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.SubCMDData;
import br.com.finalcraft.evernifecore.util.commons.Tuple;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CustomizeContext {

    private final MethodData<FinalCMDData> mainMethod;
    private final List<MethodData<SubCMDData>> subMethods;
    private final List<MethodData> allMethods;

    public CustomizeContext(MethodData<FinalCMDData> mainMethod, List<MethodData<SubCMDData>> subMethods) {
        this.mainMethod = mainMethod;
        this.subMethods = ImmutableList.copyOf(subMethods);

        List<MethodData> list = new ArrayList<>();
        list.add(mainMethod);
        list.addAll(subMethods);

        this.allMethods = ImmutableList.copyOf(list);;
    }

    public MethodData<FinalCMDData> getMainMethod() {
        return mainMethod;
    }

    public FinalCMDData getFinalCMDData(){
        return mainMethod.getData();
    }

    public List<SubCMDData> getSubCMDDataList(){
        return subMethods.stream().map(MethodData::getData).collect(Collectors.toList());
    }

    public List<MethodData<SubCMDData>> getSubMethods() {
        return subMethods;
    }

    public List<MethodData> getAllMethods() {
        return allMethods;
    }

    public List<CMDData> getAllCMDDataList() {
        return allMethods.stream().map(MethodData::getData).collect(Collectors.toList());
    }

    public void replace(String placeholder, String value){
        //Replace a regex on every single CMDData's Locales and ArgData
        for (MethodData methodData : allMethods) {
            methodData.getData().replace(placeholder, value);

            for (Tuple<ArgData, Class> tuple : (List<Tuple<ArgData, Class>>) methodData.getArgDataList()) {
                ArgData argData = tuple.getAlfa();
                argData.replace(placeholder, value);
            }
        }
    }
}
