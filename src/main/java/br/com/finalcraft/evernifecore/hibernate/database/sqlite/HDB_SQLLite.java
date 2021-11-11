package br.com.finalcraft.evernifecore.hibernate.database.sqlite;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.hibernate.database.HibernateAbstractDatabase;
import net.byteflux.libby.Library;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;

public class HDB_SQLLite extends HibernateAbstractDatabase {

    public HDB_SQLLite(Plugin plugin) {
        super(plugin);

        this.properties.setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC");             //SQLLite Driver
        this.properties.setProperty("hibernate.dialect","org.sqlite.hibernate.dialect.SQLiteDialect");   //SQLite Hibernate Dialect
    }

    @Override
    public void setupLibs() {
        super.setupLibs();//Setup Hibernate Default Libs

        EverNifeCore.getDependencyManager().loadLibrary(Arrays.asList(//TODO relocate this, as might be other plugins that use this
                Library.of("org.xerial:sqlite-jdbc:3.36.0.2"),          //SQLLite Driver
                Library.of("com.github.evernife:sqlite-dialect:master") //SQLite Hibernate Dialect TODO Replace with a specific version
        ));
    }

}
