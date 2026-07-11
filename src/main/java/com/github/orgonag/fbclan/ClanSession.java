package com.github.orgonag.fbclan;

/**
 * Per-login session state shared by the feature coordinators: whether the
 * local player has been verified as a clan member, and their RSN.
 * Replaces the scattered "if (!verified || rsn == null)" guards that used
 * to live in FinalBossPlugin.
 * Volatile: rsn is written on the client thread (login capture) and
 * verified on the executor (verification result); reset() runs on the
 * client thread. Fields are read on all three of client thread,
 * executor, and EDT — capture getRsn() into a local BEFORE gating on
 * canUpload() so a concurrent reset() can't null it mid-method.
 */
public class ClanSession
{
    private volatile boolean verified = false;
    private volatile String rsn = null;

    public boolean isVerified()
    {
        return verified;
    }

    public void setVerified(boolean verified)
    {
        this.verified = verified;
    }

    public String getRsn()
    {
        return rsn;
    }

    public void setRsn(String rsn)
    {
        this.rsn = rsn;
    }

    // The shared upload gate: player data may only leave the client for a
    // verified member whose RSN is known.
    public boolean canUpload()
    {
        return verified && rsn != null;
    }

    public void reset()
    {
        verified = false;
        rsn = null;
    }
}
