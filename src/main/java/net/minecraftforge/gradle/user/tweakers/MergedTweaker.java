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
package net.minecraftforge.gradle.user.tweakers;

import static net.minecraftforge.gradle.common.Constants.JAR_MERGED;
import static net.minecraftforge.gradle.common.Constants.MCP_INJECT;
import static net.minecraftforge.gradle.common.Constants.MCP_PATCHES_MERGED;
import static net.minecraftforge.gradle.common.Constants.TASK_MERGE_JARS;

public class MergedTweaker extends TweakerPlugin
{
    @Override
    protected String getJarName()
    {
        return "minecraft_merged";
    }

    @Override
    protected void createDecompTasks(String globalPattern, String localPattern)
    {
        super.makeDecompTasks(globalPattern, localPattern, delayedFile(JAR_MERGED), TASK_MERGE_JARS, delayedFile(MCP_PATCHES_MERGED), delayedFile(MCP_INJECT));
    }

    @Override
    protected boolean hasServerRun()
    {
        return true;
    }

    @Override
    protected boolean hasClientRun()
    {
        return true;
    }
}
