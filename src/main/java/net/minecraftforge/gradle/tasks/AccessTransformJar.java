package net.minecraftforge.gradle.tasks;

import com.google.common.collect.Lists;
import net.minecraftforge.gradle.util.caching.Cached;
import net.minecraftforge.gradle.util.caching.CachedTask;
import org.dimdev.accesstransform.AccessTransformationSet;
import org.dimdev.accesstransform.AccessTransformer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class AccessTransformJar extends CachedTask {
    @InputFile
    private Object inJar;

    private Object outJar;

    @InputFiles
    private ArrayList<Object> ats = Lists.newArrayList();

    @TaskAction
    public void doTask() throws IOException {
        getLogger().lifecycle("Applying access transformers...");

        AccessTransformationSet transformations = new AccessTransformationSet();
        for (File file : getAts()) {
            if (file.getName().endsWith(".jar")) {
            	try (JarFile jar = new JarFile(file)) {
            		ZipEntry entry = jar.getEntry("access_transformations.at");

                    if (entry != null) {
                    	getLogger().info("Found transformer in " + file);

                        try (Scanner scanner = new Scanner(jar.getInputStream(entry))) {
                            while (scanner.hasNextLine()) {
                                transformations.addMinimumAccessLevel(scanner.nextLine());
                            }
                        }
                    }
            	}
            } else {
            	getLogger().info("Found transformer in " + file);

                try (Scanner scanner = new Scanner(file)) {
                    while (scanner.hasNextLine()) {
                        transformations.addMinimumAccessLevel(scanner.nextLine());
                    }
                }
            }
        }

        AccessTransformer accessTransformer = new AccessTransformer(transformations);

        try (JarFile jar = new JarFile(getInJar());
             FileOutputStream os = new FileOutputStream(getOutJar());
             JarOutputStream out = new JarOutputStream(os)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                // Read the entry
                InputStream in = jar.getInputStream(entry);
                String name = entry.getName();
                byte[] data = readStream(in);

                // Remap classes
                if (name.endsWith(".class")) {
                    String className = name.substring(0, name.length() - 6);
                    data = accessTransformer.transformClass(className, data);
                }

                // Write the new entry
                JarEntry newEntry = new JarEntry(name);
                out.putNextEntry(newEntry);
                out.write(data);
                out.closeEntry();
            }
        }

        //Make sure there aren't any more transformations that failed to find their classes
        transformations.ensureClear();
    }

    private static byte[] readStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            outputStream.write(data, 0, bytesRead);
        }
        outputStream.flush();
        return outputStream.toByteArray();
    }


    public File getInJar() {
        return getProject().file(inJar);
    }

    public void setInJar(Object inJar) {
        this.inJar = inJar;
    }

    @Cached
    @OutputFile
    public File getOutJar() {
        return getProject().file(outJar);
    }

    public void setOutJar(Object outJar) {
        this.outJar = outJar;
    }

    public void addAts(Object... objs) {
        ats.addAll(Arrays.asList(objs));
    }

    public ConfigurableFileCollection getAts() {
        return getProject().files(ats.toArray());
    }
}
