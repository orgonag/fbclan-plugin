package com.github.orgonag.fbclan.core;

/**
 * The plugin's one chat colour: every line it prints (welcome, LFG
 * notifications, !lfg replies) is purple so players can tell it apart from
 * game messages. Remote text inside must already be tag-free.
 */
public final class Chat
{
    private static final String PURPLE = "a020f0";

    private Chat()
    {
    }

    public static String purple(String line)
    {
        return "<col=" + PURPLE + ">" + line + "</col>";
    }
}
