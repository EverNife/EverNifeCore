package br.com.finalcraft.evernifecore.hibernate;

import br.com.finalcraft.evernifecore.hibernate.database.HibernateAbstractDatabase;
import br.com.finalcraft.evernifecore.hibernate.database.sqlite.HDB_SQLLite;
import org.bukkit.plugin.Plugin;

public class DatabaseFactory<D extends HibernateAbstractDatabase> {

    public static DatabaseFactory<HDB_SQLLite> SQL_LITE = new DatabaseFactory<>(HDB_SQLLite.class);

    protected final Class<D> DATABASE;

    protected DatabaseFactory(Class<D> DATABASE) {
        this.DATABASE = DATABASE;
    }

    public D createDatabase(Plugin plugin) throws Exception{
        D d = DATABASE.getConstructor(Plugin.class).newInstance(plugin);
        d.setupLibs();
        return d;
    }


}
