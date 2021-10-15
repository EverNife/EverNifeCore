/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 */

package br.com.finalcraft.evernifecore.nms.util;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.nms.util.v1_12_R1.NMSUtils_v1_12_R1;
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
			}
		}catch (Throwable e){
			EverNifeCore.warning("Failed to create NMSUtils instance for " + MCVersion.getCurrent().name() + " mc version!");
			e.printStackTrace();
		}
	}




}