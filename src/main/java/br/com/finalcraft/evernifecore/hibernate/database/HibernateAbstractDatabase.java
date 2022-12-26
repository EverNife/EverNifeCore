package br.com.finalcraft.evernifecore.hibernate.database;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.dependencies.util.LibraryFactory;
import br.com.finalcraft.evernifecore.hibernate.connection.HibernateConnection;
import org.bukkit.plugin.Plugin;
import org.hibernate.cfg.Configuration;

import java.util.Arrays;
import java.util.Properties;

public abstract class HibernateAbstractDatabase {

    protected final Plugin plugin;
    protected final Properties properties = new Properties();

    public HibernateAbstractDatabase(Plugin plugin) {
        this.plugin = plugin;
    }

    public void setupLibs() {
        //Maybe this should be relocated? i don't know! I do not know any other plugin that uses hibernate!
        EverNifeCore.instance.getDependencyManager().loadLibrary(Arrays.asList(//TODO Relocate
                LibraryFactory.of("org.hibernate:hibernate-core:5.4.30.Final"),
                LibraryFactory.of("javax.transaction:jta:1.1"),
                LibraryFactory.of("org.jboss.logging:jboss-logging:3.4.1.Final"),
                LibraryFactory.of("javax.persistence:javax.persistence-api:2.2"),
                LibraryFactory.of("org.javassist:javassist:3.27.0-GA"),
                LibraryFactory.of("net.bytebuddy:byte-buddy:1.10.19"),
                LibraryFactory.of("antlr:antlr:2.7.7"),
                LibraryFactory.of("org.jboss.spec.javax.transaction:jboss-transaction-api_1.2_spec:1.1.1.Final"),
                LibraryFactory.of("org.jboss:jandex:2.1.3.Final"),
                LibraryFactory.of("com.fasterxml:classmate:1.5.1"),
                LibraryFactory.of("org.dom4j:dom4j:2.1.3"),
                LibraryFactory.of("org.hibernate.common:hibernate-commons-annotations:5.1.2.Final"),
                LibraryFactory.of("org.glassfish.jaxb:jaxb-runtime:2.3.1")
        ));
    }

    public HibernateConnection createConnection(Configuration configuration){
        //Add Overriding values like dialect and driver
        configuration.getProperties().forEach((o, o2) -> configuration.setProperty(String.valueOf(o),String.valueOf(o2)));
        return new HibernateConnection(this, configuration);
    }

}
