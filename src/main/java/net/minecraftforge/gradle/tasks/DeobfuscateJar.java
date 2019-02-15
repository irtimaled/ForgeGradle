/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package net.minecraftforge.gradle.tasks;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;

import de.oceanlabs.mcp.mcinjector.lvt.LVTNaming;
import de.oceanlabs.mcp.mcinjector.MCInjector;

import groovy.lang.Closure;

import net.md_5.specialsource.*;
import net.md_5.specialsource.provider.JarProvider;
import net.md_5.specialsource.provider.JointProvider;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.caching.Cached;
import net.minecraftforge.gradle.util.caching.CachedTask;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class DeobfuscateJar extends CachedTask
{
    @InputFile
    @Optional
    private Object            fieldCsv;
    @InputFile
    @Optional
    private Object            methodCsv;

    @InputFile
    private Object            inJar;

    @InputFile
    private Object            srg;

    @InputFile
    private Object            exceptorCfg;

    @InputFile
    private Object            access;
    
    @InputFile
    private Object            constructors;
    
    @InputFile
    private Object            exceptions;

    @Input
    private boolean           failOnAtError = true;

    private Object            outJar;

    @InputFiles
    private ArrayList<Object> ats           = Lists.newArrayList();

    private Object            log;

    @TaskAction
    public void doTask() throws IOException
    {
        // make stuff into files.
        File tempObfJar = new File(getTemporaryDir(), "deobfed.jar"); // courtesy of gradle temp dir.
        File out = getOutJar();

        // make the ATs list.. its a Set to avoid duplication.
        Set<File> ats = new HashSet<File>();
        for (Object obj : this.ats)
        {
            ats.add(getProject().file(obj).getCanonicalFile());
        }

        // deobf
        getLogger().lifecycle("Applying SpecialSource...");
        deobfJar(getInJar(), tempObfJar, getSrg(), ats);

        File log = getLog();
        if (log == null)
            log = new File(getTemporaryDir(), "exceptor.log");

        // apply exceptor
        getLogger().lifecycle("Applying Exceptor...");
        applyExceptor(tempObfJar, out, getExceptorCfg(), log, ats);
    }

    private void deobfJar(File inJar, File outJar, File srg, Collection<File> ats) throws IOException
    {
        // load mapping
        JarMapping mapping = new JarMapping();
        mapping.loadMappings(srg);

        // load in ATs
        ErroringRemappingAccessMap accessMap = new ErroringRemappingAccessMap(new File[] { getMethodCsv(), getFieldCsv() });

        getLogger().info("Using AccessTransformers...");
        //Make SS shutup about access maps
        for (File at : ats)
        {
            getLogger().info("" + at);
            accessMap.loadAccessTransformer(at);
        }
        //        System.setOut(tmp);

        // make a processor out of the ATS and mappings.
        RemapperProcessor srgProcessor = new RemapperProcessor(null, mapping, null);

        RemapperProcessor atProcessor = new RemapperProcessor(null, null, accessMap);
        // make remapper
        JarRemapper remapper = new JarRemapper(srgProcessor, mapping, atProcessor);

        // load jar
        try (Jar input = Jar.init(inJar))
        {
            // ensure that inheritance provider is used
            JointProvider inheritanceProviders = new JointProvider();
            inheritanceProviders.add(new JarProvider(input));
            mapping.setFallbackInheritanceProvider(inheritanceProviders);

            // remap jar
            remapper.remapJar(input, outJar);

            // throw error for broken AT lines
            if (accessMap.brokenLines.size() > 0 && failOnAtError)
            {
                getLogger().error("{} Broken Access Transformer lines:", accessMap.brokenLines.size());
                for (String line : accessMap.brokenLines.values())
                {
                    getLogger().error(" ---  {}", line);
                }

                // TODO: add info for disabling

                throw new RuntimeException("Your Access Transformers are broken");
            }
        }
    }

    public void applyExceptor(File inJar, File outJar, File config, File log, Set<File> ats) throws IOException
    {
        getLogger().debug("INPUT: " + inJar);
        getLogger().debug("OUTPUT: " + outJar);
        getLogger().debug("CONFIG: " + config);
        getLogger().debug("ACCESS: " + getAccessCfg());
        getLogger().debug("CONSTRUCTOR: " + getConstructorCfg());
        getLogger().debug("EXCEPTION: " + getExceptionsCfg());
        getLogger().debug("LOG: " + log);
        getLogger().debug("PARAMS: true");

        new MCInjector(inJar.toPath(), outJar.toPath()).log(log.toPath())
				        .access(getAccessCfg().toPath())
				        .constructors(getConstructorCfg().toPath())
				        .exceptions(getExceptionsCfg().toPath())
				        .lvt(LVTNaming.LVT).process();
    }

    public File getExceptorCfg()
    {
        return getProject().file(exceptorCfg);
    }

    public void setExceptorCfg(Object exceptorCfg)
    {
        this.exceptorCfg = exceptorCfg;
    }

    public File getAccessCfg() {
    	return getProject().file(access);
    }

    public void setAccessCfg(Object accessCfg) {
    	access = accessCfg;
    }

    public File getConstructorCfg() {
    	return getProject().file(constructors);
    }

    public void setConstructorCfg(Object constructorCfg) {
    	constructors = constructorCfg;
    }

    public File getExceptionsCfg() {
    	return getProject().file(exceptions);
    }

    public void setExceptionsCfg(Object exceptionCfg) {
    	exceptions = exceptionCfg;
    }

    public boolean isFailOnAtError()
    {
        return failOnAtError;
    }

    public void setFailOnAtError(boolean failOnAtError)
    {
        this.failOnAtError = failOnAtError;
    }

    public File getInJar()
    {
        return getProject().file(inJar);
    }

    public void setInJar(Object inJar)
    {
        this.inJar = inJar;
    }

    public File getLog()
    {
        if (log == null)
            return null;
        else
            return getProject().file(log);
    }

    public void setLog(Object log)
    {
        this.log = log;
    }

    public File getSrg()
    {
        return getProject().file(srg);
    }

    public void setSrg(Object srg)
    {
        this.srg = srg;
    }

    /**
     * returns the actual output file depending on Clean status
     * @return File representing output jar
     */
    @Cached
    @OutputFile
    public File getOutJar()
    {
        return getProject().file(outJar);
    }

    public void setOutJar(Object outJar)
    {
        this.outJar = outJar;
    }

    /**
     * returns the actual output Object depending on Clean status
     * Unlike getOutputJar() this method does not resolve the files.
     * @return Object that will resolve to
     */
    @SuppressWarnings("serial")
    public Closure<File> getDelayedOutput()
    {
        return new Closure<File>(DeobfuscateJar.class) {
            public File call()
            {
                return getOutJar();
            }
        };
    }

    /**
     * adds an access transformer to the deobfuscation of this
     * @param obj access transformers
     */
    public void addAt(Object obj)
    {
        ats.add(obj);
    }

    /**
     * adds access transformers to the deobfuscation of this
     * @param objs access transformers
     */
    public void addAts(Object... objs)
    {
        for (Object object : objs)
        {
            ats.add(object);
        }
    }

    /**
     * adds access transformers to the deobfuscation of this
     * @param objs access transformers
     */
    public void addAts(Iterable<Object> objs)
    {
        for (Object object : objs)
        {
            ats.add(object);
        }
    }

    public FileCollection getAts()
    {
        return getProject().files(ats.toArray());
    }

    public File getFieldCsv()
    {
        return fieldCsv == null ? null : getProject().file(fieldCsv);
    }

    public void setFieldCsv(Object fieldCsv)
    {
        this.fieldCsv = fieldCsv;
    }

    public File getMethodCsv()
    {
        return methodCsv == null ? null : getProject().file(methodCsv);
    }

    public void setMethodCsv(Object methodCsv)
    {
        this.methodCsv = methodCsv;
    }

    private static final class ErroringRemappingAccessMap extends AccessMap
    {
        private final Map<String, String> renames     = Maps.newHashMap();
        public final Map<String, String>  brokenLines = Maps.newTreeMap();

        public ErroringRemappingAccessMap(File[] renameCsvs) throws IOException
        {
            super();

            for (File f : renameCsvs)
            {
                if (f == null)
                    continue;
                Files.readLines(f, Charsets.UTF_8, new LineProcessor<String>()
                {
                    @Override
                    public boolean processLine(String line) throws IOException
                    {
                        String[] pts = line.split(",");
                        if (!"searge".equals(pts[0]))
                        {
                            renames.put(pts[0], pts[1]);
                        }

                        return true;
                    }

                    @Override
                    public String getResult()
                    {
                        return null;
                    }
                });
            }
        }

        @Override
        public void loadAccessTransformer(File file) throws IOException
        {
            // because SS doesnt close its freaking reader...
            BufferedReader reader = Files.newReader(file, Constants.CHARSET);
            loadAccessTransformer(reader);
            reader.close();
        }

        @Override
        public void addAccessChange(String symbolString, String accessString)
        {
            String[] pts = symbolString.split(" ");
            if (pts.length >= 2)
            {
                int idx = pts[1].indexOf('(');

                String start = pts[1];
                String end = "";

                if (idx != -1)
                {
                    start = pts[1].substring(0, idx);
                    end = pts[1].substring(idx);
                }

                String rename = renames.get(start);
                if (rename != null)
                {
                    pts[1] = rename + end;
                }
            }
            String joinedString = Joiner.on('.').join(pts);
            super.addAccessChange(joinedString, accessString);
            // convert  package.class  to  package/class
            brokenLines.put(joinedString.replace('.', '/'), symbolString);
        }

        @Override
        protected void accessApplied(String key, int oldAccess, int newAccess)
        {
            // if the access' are equal, then the line is broken, and we dont want to remove it.\
            // or not... it still applied.. just applied twice somehow.. not an issue.
//            if (oldAccess != newAccess)
            {
                // key added before is in format: package/class{method/field sig}
                // and the key here is in format: package/class {method/field sig}
                brokenLines.remove(key.replace(" ", ""));
            }
        }
    }
}
