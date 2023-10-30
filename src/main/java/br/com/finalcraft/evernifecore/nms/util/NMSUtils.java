package br.com.finalcraft.evernifecore.nms.util;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.nms.util.v1_12_R1.NMSUtils_v1_12_R1;
import br.com.finalcraft.evernifecore.nms.util.v1_16_R3.NMSUtils_v1_16_R3;
import br.com.finalcraft.evernifecore.nms.util.v1_20_R2.NMSUtils_v1_20_R2;
import br.com.finalcraft.evernifecore.nms.util.v1_7_R4.NMSUtils_v1_7_R4;
import br.com.finalcraft.evernifecore.version.MCVersion;

public class NMSUtils {

	public static INMSUtils instance = null;
	private static boolean hasBeenInit = false;

	public static INMSUtils get() {
		if (!hasBeenInit){
			init();
			hasBeenInit = true;
		}
		return instance;
	}

	private static void init() {
		try {
			if (MCVersion.isEqual(MCVersion.v1_7_10)){
				instance = new NMSUtils_v1_7_R4();
			}
			if (MCVersion.isEqual(MCVersion.v1_12)){
				instance = new NMSUtils_v1_12_R1();
			}
			if (MCVersion.isEqual(MCVersion.v1_16)){
				instance = new NMSUtils_v1_16_R3();
			}
			if (MCVersion.isEqual(MCVersion.v1_20)){
				instance = new NMSUtils_v1_20_R2();
			}
			if (instance != null){
				EverNifeCore.info("Sucessfully loaded " + instance.getClass().getSimpleName() +"!");
				return;
			}
		}catch (Throwable e){
			EverNifeCore.warning("Failed to create NMSUtils instance for " + MCVersion.getCurrent().name() + " mc version!");
			e.printStackTrace();
			return;
		}
		EverNifeCore.warning("No NMSUtils instance found for " + MCVersion.getCurrent().name() + " mc version!");
	}




}