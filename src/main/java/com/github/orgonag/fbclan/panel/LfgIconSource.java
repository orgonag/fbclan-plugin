package com.github.orgonag.fbclan.panel;

import javax.swing.JLabel;

/**
 * Supplies item sprites for LFG activity icons. The plugin backs this
 * with RuneLite's ItemManager (async, cached, fetched from RuneLite's
 * static item-icon CDN); tests and headless renders can supply their
 * own. Implementations set the label's icon (resized to SIZE) whenever
 * the sprite is available, on the EDT.
 */
public interface LfgIconSource
{
    int SIZE = 20;

    void apply(int itemId, JLabel target);

    static LfgIconSource none()
    {
        return (itemId, target) -> {};
    }
}
