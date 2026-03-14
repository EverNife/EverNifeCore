package br.com.finalcraft.evernifecore.hibernate.connection;

import br.com.finalcraft.evernifecore.hibernate.database.HibernateAbstractDatabase;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.cfg.Configuration;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.schema.TargetType;

import javax.persistence.metamodel.EntityType;
import java.util.EnumSet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class HibernateConnection<D extends HibernateAbstractDatabase> {

    private final SessionFactory factory;
    private final D database;
    private final ReentrantLock lock = new ReentrantLock(true);

    public HibernateConnection(D database, Configuration configuration) {
        this.database = database;
        this.factory = configuration.buildSessionFactory();
    }

    public SessionFactory getFactory() {
        return factory;
    }

    public void createTables(){
        MetadataSources metadata = new MetadataSources(factory.getSessionFactoryOptions().getServiceRegistry());

        for (EntityType<?> entity : factory.getMetamodel().getEntities()) {
            metadata.addAnnotatedClass(entity.getBindableJavaType());
        }

        EnumSet<TargetType> enumSet = EnumSet.of(TargetType.DATABASE);
        SchemaExport schemaExport = new SchemaExport();
        schemaExport.execute(enumSet,
                SchemaExport.Action.CREATE, //This will only CREATE the table
                metadata.buildMetadata());
    }


    public synchronized void executeQuery(Consumer<Session> consumer){
        lock.lock();
        Session session = factory.openSession();
        try {
            consumer.accept(session);
        }catch (Throwable e){
            e.printStackTrace();
        }finally {
            session.close();
            lock.unlock();
        }
    }


}
