/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2018 Minecraft Forge
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.specs.Spec;

import net.minecraftforge.gradle.util.ExtractionVisitor;

public class ExtractMcpConfigTask extends ExtractConfigTask {
	public static class Extractor extends ExtractionVisitor {
		public Extractor(File outDir, boolean emptyDirs, Spec<FileTreeElement> spec) {
			super(outDir, emptyDirs, spec);
		}

		@Override
		public void visitFile(FileVisitDetails details) {
			if (!spec.isSatisfiedBy(details)) return;

			File destination = new File(outputDir, details.getPath());
	        destination.getParentFile().mkdirs();

	        switch (details.getRelativePath().toString()) {
		        case "config.json":
		        	//We don't need the config.json, but at some point we will need an astyle.cfg (which doesn't come in the zip)
		        	//So let's take the opportunity to create one now to save any trouble later
		        	try (BufferedWriter out = new BufferedWriter(new FileWriter(new File(outputDir, "astyle.cfg")))) {
		        		//https://github.com/MinecraftForge/MCPConfig/blob/master/config/astyle.cfg
		        		out.write("style=allman");
		        		out.newLine();
		        		out.newLine();
		        		out.write("add-brackets");
		        		out.newLine();
		        		out.write("break-closing-brackets");
		        		out.newLine();
		        		out.newLine();
		        		out.write("indent-switches");
		        		out.newLine();
		        		out.newLine();
		        		out.write("max-instatement-indent=40");
		        		out.newLine();
		        		out.newLine();
		        		out.write("pad-oper");
		        		out.newLine();
		        		out.write("pad-header");
		        		out.newLine();
		        		out.write("unpad-paren");
		        		out.newLine();
		        		out.newLine();
		        		out.write("break-blocks");
		        		out.newLine();
		        		out.newLine();
		        		out.write("delete-empty-lines");
		        		out.newLine();
		        	} catch (IOException e) {
		        		throw new UncheckedIOException("Error writing astyle.cfg" + details.getName(), e);
		        	}
		        	break;

		        case "config/joined.tsrg":
		        	//To save anything getting confused with tiny srg, let's convert the mappings into normal srg
		        	try (BufferedReader in = new BufferedReader(new InputStreamReader(details.open()));
		        			BufferedWriter out = new BufferedWriter(new FileWriter(new File(destination.getParentFile(), "joined.srg")))) {
		        		Map<String, String> classes = new HashMap<>();
		        		Map<String, String> methods = new HashMap<>();
		        		Map<String, String> fields = new HashMap<>();

		        		String currentObf = null;
		        		String currentDeobf = null;
						for (String line = in.readLine(); line != null; line = in.readLine()) {
							if (line.charAt(0) != '\t') {
								String[] parts = line.split(" ");
								if (parts.length != 2)
									throw new IllegalStateException("Unexpected line split: " + Arrays.toString(parts) + " in " + details.getName());

								currentObf = parts[0];
								currentDeobf = parts[1];
								classes.put(currentObf, currentDeobf);
							} else {
								String[] parts = line.substring(1).split(" ");
								switch (parts.length) {
									case 2: //Field
										fields.put(currentObf + '/' + parts[0], currentDeobf + '/' + parts[1]);
										break;

									case 3: //Method
										methods.put(currentObf + '/' + parts[0] + ' ' + parts[1], currentDeobf + '/' + parts[2]);
										break;

									default:
										throw new IllegalStateException("Unexpected line split: " + Arrays.toString(parts) + " in " + details.getName());
								}
							}
						}

						Pattern classFinder = Pattern.compile("L([^;]+);");
						for (Entry<String, String> entry : methods.entrySet()) {
							String obf = entry.getKey();
							String desc = obf.substring(obf.lastIndexOf(' ') + 1);

							StringBuffer buf = new StringBuffer();
					        Matcher matcher = classFinder.matcher(desc);
					        while (matcher.find()) {
					            matcher.appendReplacement(buf, Matcher.quoteReplacement('L' + classes.getOrDefault(matcher.group(1), matcher.group(1)) + ';'));
					        }
					        matcher.appendTail(buf);

					        entry.setValue(entry.getValue() + ' ' + buf.toString());
						}

						for (Entry<String, String> entry : classes.entrySet()) {
							out.write("CL: " + entry.getKey() + ' ' + entry.getValue());
							out.newLine();
						}
						for (Entry<String, String> entry : fields.entrySet()) {
							out.write("FD: " + entry.getKey() + ' ' + entry.getValue());
							out.newLine();
						}
						for (Entry<String, String> entry : methods.entrySet()) {
							out.write("MD: " + entry.getKey() + ' ' + entry.getValue());
							out.newLine();
						}
					} catch (IOException e) {
						throw new UncheckedIOException("Error converting " + details.getName(), e);
					}
		        	break;

		        default:
		        	details.copyTo(destination);
	        }
		}
	}

	@Override
	protected FileVisitor makeExtractor(File outDir, boolean emptyDirs, Spec<FileTreeElement> spec) {
		return new Extractor(outDir, emptyDirs, spec);
	}
}