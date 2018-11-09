package net.minecraftforge.gradle.tasks;

import static net.minecraftforge.gradle.common.Constants.CONFIG_MCP_DATA;
import static net.minecraftforge.gradle.common.Constants.URL_FORGE_MAVEN;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.gradle.api.Project;
import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import net.minecraftforge.gradle.testsupport.TaskTest;

public class TestExtractMcpConfig extends TaskTest<ExtractMcpConfigTask> {
	private static final String CONFIG_VERSION = "1.13.1-20180919.183102";

	private static void attachDownload(Project project) {
		project.getConfigurations().maybeCreate(CONFIG_MCP_DATA);
		project.getRepositories().maven(repo -> {
            repo.setName("forge");
            repo.setUrl(URL_FORGE_MAVEN);
        });
		project.getDependencies().add(CONFIG_MCP_DATA, ImmutableMap.of(
	        "group", "de.oceanlabs.mcp",
	        "name", "mcp_config",
	        "version", CONFIG_VERSION,
	        //"classifier", "srg",
	        "ext", "zip"
        ));
	}

	@Test
	public void runTask() throws IOException {		
		File outDir = temporaryFolder.newFolder("extract");

		ExtractConfigTask extractMcpData = getTask(ExtractMcpConfigTask.class);
		attachDownload(extractMcpData.getProject());

        extractMcpData.setDestinationDir(outDir);
        extractMcpData.setConfig(CONFIG_MCP_DATA);
        extractMcpData.setDoesCache(true);

        extractMcpData.doTask();

        Assert.assertTrue(new File(outDir, "config/joined.srg").exists());
        try (BufferedReader in = new BufferedReader(new FileReader(new File(outDir, "config/joined.srg")))) {
        	//Take a quick look at the first 10 lines to double check it's not all rubbish
        	for (int read = 0; read < 10; read++) {
        		System.out.println(in.readLine());
        	}
        }
	}
}