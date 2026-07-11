package com.github.orgonag.fbclan;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

// NOT a JUnit test: this is the standard RuneLite plugin-template dev
// launcher, wired to `./gradlew run` via build.gradle's mainClass.
public class FinalBossPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(FinalBossPlugin.class);
        RuneLite.main(args);
    }
}
