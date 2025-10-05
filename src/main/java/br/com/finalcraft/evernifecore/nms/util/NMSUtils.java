package br.com.finalcraft.evernifecore.nms.util;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.nms.util.v1_12_R1.NMSUtils_v1_12_R1;
import br.com.finalcraft.evernifecore.nms.util.v1_16_R3.NMSUtils_v1_16_R3;
import br.com.finalcraft.evernifecore.nms.util.v1_20_R1.NMSUtils_v1_20_R1;
import br.com.finalcraft.evernifecore.nms.util.v1_20_R2.NMSUtils_v1_20_R2;
import br.com.finalcraft.evernifecore.nms.util.v1_21_R1.NMSUtils_v1_21_R1;
import br.com.finalcraft.evernifecore.nms.util.v1_7_R4.NMSUtils_v1_7_R4;
import br.com.finalcraft.evernifecore.version.MCDetailedVersion;
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
			else if (MCVersion.isEqual(MCVersion.v1_12)){
				instance = new NMSUtils_v1_12_R1();
			}
			else if (MCVersion.isEqual(MCVersion.v1_16)){
				instance = new NMSUtils_v1_16_R3();
			}
			else if (MCVersion.isEqual(MCDetailedVersion.v1_20_R1)){
				instance = new NMSUtils_v1_20_R1();
			}
			else if (MCVersion.isEqual(MCDetailedVersion.v1_20_R2)){
				instance = new NMSUtils_v1_20_R2();
			}
			else if (MCVersion.isEqual(MCVersion.v1_21)){
				instance = new NMSUtils_v1_21_R1();
			}
			if (instance != null){
				EverNifeCore.info("Successfully loaded " + instance.getClass().getSimpleName() +"!");
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