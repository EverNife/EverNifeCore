package br.com.finalcraft.evernifecore.nms.util;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.nms.util.v1_12_R1.NMSUtils_v1_12_R1;
import br.com.finalcraft.evernifecore.nms.util.v1_16_R3.NMSUtils_v1_16_R3;
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
			switch (MCVersion.getCurrent()){
				case v1_7_R4:
					instance = new NMSUtils_v1_7_R4();
					return;
				case v1_12_R1:
					instance = new NMSUtils_v1_12_R1();
					return;
				case v1_16_R3:
					instance = new NMSUtils_v1_16_R3();
					return;
			}
		}catch (Throwable e){
			EverNifeCore.warning("Failed to create NMSUtils instance for " + MCVersion.getCurrent().name() + " mc version!");
			e.printStackTrace();
		}
	}




}