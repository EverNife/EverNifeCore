/**
 * The Bukkit half of the EverNifeCore test engine, published as {@code evernifecore-minecraft-tests}.
 *
 * <p>A plugin's Bukkit-side tests declare it next to {@code evernifecore-minecraft} and get what a
 * server would otherwise supply: the Jackson stack the core downloads at boot, relocated the same way,
 * the whole {@code evernifecore-common-tests} engine ({@code Platforms}, {@code Plugins},
 * {@code FinalCmdTestHarness}...) linked against the relocated core, and {@link
 * br.com.finalcraft.evernifecore.testing.minecraft.ItemWorld ItemWorld} - a described server with an
 * item factory, named worlds and the Bukkit config codecs. Do not also declare
 * {@code evernifecore-common-tests} or {@code evernifecore-common}: their classes are here already,
 * rewritten onto the names the fat jar carries.</p>
 *
 * <pre>{@code
 * testImplementation 'br.com.finalcraft:evernifecore-minecraft-tests:<version>'
 * }</pre>
 *
 * <p>There is no Hytale counterpart because {@code evernifecore-hytale} is published thin, with
 * canonical names and a POM that brings {@code evernifecore-common}: {@code evernifecore-common-tests}
 * already runs against it. The day a Hytale plugin needs a described server of its own, the answer is
 * an {@code evernifecore-hytale-tests} built here the way this one is - never a copy inside the
 * plugin.</p>
 */
package br.com.finalcraft.evernifecore.testing.minecraft;
