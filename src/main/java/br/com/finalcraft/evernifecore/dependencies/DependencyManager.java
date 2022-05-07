/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package br.com.finalcraft.evernifecore.dependencies;

import net.byteflux.libby.LibraryManager;
import net.byteflux.libby.classloader.URLClassLoaderHelper;
import net.byteflux.libby.logging.adapters.JDKLogAdapter;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

public class DependencyManager extends LibraryManager {

    private final URLClassLoaderHelper classLoader;

    public DependencyManager() {
        super(new JDKLogAdapter(Logger.getLogger("DefaultDependencyManager")), new File("plugins/EverNifeCore/").toPath());
        classLoader = new URLClassLoaderHelper((URLClassLoader) DependencyManager.class.getClassLoader());
    }

    public DependencyManager(Plugin plugin) {
        super(new JDKLogAdapter((Objects.requireNonNull(plugin, "plugin")).getLogger()), plugin.getDataFolder().toPath());
        classLoader = new URLClassLoaderHelper((URLClassLoader) plugin.getClass().getClassLoader());
    }

    public DependencyManager(Plugin plugin, Path dataDirectory, String folderName) {
        super(new JDKLogAdapter((Objects.requireNonNull(plugin, "plugin")).getLogger()), dataDirectory, folderName);
        classLoader = new URLClassLoaderHelper((URLClassLoader) plugin.getClass().getClassLoader());
    }

    /**
     * Adds a file to the Bukkit plugin's classpath.
     *
     * @param file the file to add
     */
    @Override
    protected void addToClasspath(Path file) {
        this.classLoader.addToClasspath(file);
    }

}
