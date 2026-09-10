package com.github.orgonag.fbclan.core;

import lombok.Value;

/** Immutable local identity snapshot. This is a client gate, not server authentication. */
@Value
public class Session
{
    long generation;
    String rsn;
    String profile;
    boolean verified;

    public boolean canUpload()
    {
        return verified && rsn != null && profile != null;
    }
}
