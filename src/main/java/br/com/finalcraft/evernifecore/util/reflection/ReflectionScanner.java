package br.com.finalcraft.evernifecore.util.reflection;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReflectionScanner {

    private final String targetPackage;
    private final ClassLoader classLoader;
    private final boolean ignoreInterfaces;
    private final boolean ignoreAnnotations;

    ReflectionScanner(String targetPackage, ClassLoader classLoader, boolean ignoreInterfaces, boolean ignoreAnnotations){

        if(targetPackage != null){

            this.targetPackage = targetPackage;
            this.classLoader = classLoader;
            this.ignoreAnnotations = ignoreAnnotations;
            this.ignoreInterfaces = ignoreInterfaces;

            return;
        }

        throw new NullPointerException("targetPackage can't be null");
    }

    public static ReflectionBuilder builder(){
        return new ReflectionBuilder();
    }

    @SafeVarargs
    public final HashSet<Class<?>> scanByAnnotation(Class<? extends Annotation>... annotations){
        HashSet<Class<?>> internalSet = new HashSet<>();

        for(Class<?> clazz : scan()){
            for(Class<? extends Annotation> annotation : annotations){
                if(!clazz.isAnnotationPresent(annotation)){
                    continue;
                }

                internalSet.add(clazz);
            }
        }

        return internalSet;
    }

    public final HashSet<Class<?>> scanWithFilter(Function<Class, Boolean> filter){
        HashSet<Class<?>> internalSet = new HashSet<>();

        for(Class<?> clazz : scan()){
            if (filter.apply(clazz)){
                internalSet.add(clazz);
            }
        }

        return internalSet;
    }

    public HashSet<Class<?>> scan(){

        HashSet<Class<?>> classes = new HashSet<>();

        ClassLoader cl = (classLoader != null) ? classLoader : getCurrentClassLoader();

        Enumeration<URL> data = null;

        try {
            data = cl.getResources(packageToPath(targetPackage));
        } catch (IOException e) { e.printStackTrace(); }

        if(data != null && data.hasMoreElements()){

            URL url = data.nextElement();
            URLConnection connection = null;

            while (url != null){

                try {
                    connection = url.openConnection();
                }
                catch (IOException ignored) {

                    if(data.hasMoreElements()){
                        url = data.nextElement();
                        continue;
                    }

                }

                if(connection != null){
                    if(connection instanceof JarURLConnection){
                        try { classes.addAll(handleJar((JarURLConnection) connection, targetPackage)); } catch (IOException | ClassNotFoundException e) { e.printStackTrace(); }
                    } else if ("file".equals(url.getProtocol())){
                        try { classes.addAll(handleFiles(connection, targetPackage)); } catch (IOException e) { e.printStackTrace(); }
                    }
                }

                if(data.hasMoreElements()){
                    url = data.nextElement();
                    continue;
                }

                url = null;
            }
        }

        return classes;
    }

    private String packageToPath(String name){
        return name.replace(".","/");
    }

    private String normalize(String name){
        return name.substring(0, name.length() - 6).replace("/",".");
    }

    private String noClass(String name){
        return name.substring(0, name.length() - 6);
    }

    private ClassLoader getCurrentClassLoader(){
        return Thread.currentThread().getContextClassLoader();
    }

    private HashSet<Class<?>> handleJar(JarURLConnection connection, String targetPackage) throws IOException, ClassNotFoundException {

        HashSet<Class<?>> hashSet = new HashSet<>();
        JarFile jarFile = connection.getJarFile();
        Enumeration<JarEntry> enumeration = jarFile.entries();

        JarEntry entry = null;

        if(enumeration.hasMoreElements()){
            entry = enumeration.nextElement();
        }

        while (entry != null){

            if(entry.getName().contains(".class")){

                String name = normalize(entry.getName());

                if(name.startsWith(targetPackage)){
                    Class<?> clazz = Class.forName(name);

                    if(!((clazz.isAnnotation() && ignoreAnnotations) || (clazz.isInterface() && ignoreInterfaces))){
                        hashSet.add(clazz);
                    }

                }

            }

            if(enumeration.hasMoreElements()){
                entry = enumeration.nextElement();
                continue;
            }

            entry = null;
        }

        return hashSet;
    }

    private HashSet<Class<?>> handleFiles(URLConnection connection, String targetPackage) throws IOException {

        HashSet<Class<?>> hashSet = new HashSet<>();

        File dir = new File(URLDecoder.decode(connection.getURL().getPath(), "UTF-8"));

        Stream<Path> filesWalk = Files.walk(dir.toPath());

        List<String> result = filesWalk
                .filter(file -> file.toFile().getName().endsWith(".class"))
                .map(x -> x.toFile().getName())
                .collect(Collectors.toList());

        if(!result.isEmpty()){
            for(String name : result){
                String fName = noClass(name);

                try {
                    Class<?> clazz = Class.forName(targetPackage + '.' + fName);

                    if(!((clazz.isAnnotation() && ignoreAnnotations) || (clazz.isInterface() && ignoreInterfaces))){
                        hashSet.add(clazz);
                    }

                } catch (NoClassDefFoundError | ClassNotFoundException ignored) { }

            }
        }

        return hashSet;

    }

    public static class ReflectionBuilder {

        protected String targetPackage;
        protected ClassLoader classLoader;
        protected boolean ignoreInterfaces = false;
        protected boolean ignoreAnnotations = false;

        public ReflectionBuilder setClassLoader(ClassLoader classLoader){
            this.classLoader = classLoader;

            return this;
        }

        public ReflectionBuilder setPackage(Package targetPackage){
            this.targetPackage = targetPackage.getName();

            return this;
        }

        public ReflectionBuilder setPackage(String targetPackage){
            this.targetPackage = targetPackage;

            return this;
        }

        public ReflectionBuilder ignoreInterfaces(boolean ignoreInterfaces){
            this.ignoreInterfaces = ignoreInterfaces;

            return this;
        }

        public ReflectionBuilder ignoreAnnotations(boolean ignoreAnnotations){
            this.ignoreAnnotations = ignoreAnnotations;

            return this;
        }

        public ReflectionScanner build(){
            return new ReflectionScanner(targetPackage, classLoader, ignoreInterfaces, ignoreAnnotations);
        }

    }

}