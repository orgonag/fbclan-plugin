package com.github.orgonag.fbclan;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class FinalBossPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(FinalBossPlugin.class);
        RuneLite.main(args);
    }
}
