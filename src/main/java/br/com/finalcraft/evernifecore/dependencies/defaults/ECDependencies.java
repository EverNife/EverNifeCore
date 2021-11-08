package br.com.finalcraft.evernifecore.dependencies.defaults;

import br.com.finalcraft.evernifecore.dependencies.util.LibraryUtil;
import net.byteflux.libby.Library;

import java.util.Arrays;
import java.util.List;

//TODO properly create this class
public class ECDependencies {

    public static Library getMariaDBClientDriver(){
        return LibraryUtil.fromString("org.mariadb.jdbc:mariadb-java-client:3.0.2-rc");
    }

    public static List<Library> getSQLLiteClientDriverAndConnectr(){
        return Arrays.asList(
                LibraryUtil.fromString("org.xerial:sqlite-jdbc:3.36.0.2"),
                LibraryUtil.fromString("com.github.gwenn:sqlite-dialect:master") //TODO Replace if a specific version
        );
    }

    public static List<Library> getHibernateDependencies(){ // hibernate-core:5.4.30.Final
        return Arrays.asList(
                LibraryUtil.fromString("org.hibernate:hibernate-core:5.4.30.Final"),
                LibraryUtil.fromString("javax.transaction:jta:1.1"),
                LibraryUtil.fromString("org.jboss.logging:jboss-logging:3.4.1.Final"),
                LibraryUtil.fromString("javax.persistence:javax.persistence-api:2.2"),
                LibraryUtil.fromString("org.javassist:javassist:3.27.0-GA"),
                LibraryUtil.fromString("net.bytebuddy:byte-buddy:1.10.19"),
                LibraryUtil.fromString("antlr:antlr:2.7.7"),
                LibraryUtil.fromString("org.jboss.spec.javax.transaction:jboss-transaction-api_1.2_spec:1.1.1.Final"),
                LibraryUtil.fromString("org.jboss:jandex:2.1.3.Final"),
                LibraryUtil.fromString("com.fasterxml:classmate:1.5.1"),
                LibraryUtil.fromString("org.dom4j:dom4j:2.1.3"),
                LibraryUtil.fromString("org.hibernate.common:hibernate-commons-annotations:5.1.2.Final"),
                LibraryUtil.fromString("org.glassfish.jaxb:jaxb-runtime:2.3.1")
        );
    }

}
