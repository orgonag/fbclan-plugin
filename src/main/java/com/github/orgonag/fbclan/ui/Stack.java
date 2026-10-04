package com.github.orgonag.fbclan.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * Children top to bottom at full width and preferred height, `gap` apart.
 * Nothing stretches or drifts sideways the way it can under BoxLayout.
 */
final class Stack implements LayoutManager
{
    private final int gap;

    Stack(int gap)
    {
        this.gap = gap;
    }

    @Override
    public Dimension preferredLayoutSize(Container parent)
    {
        Insets in = parent.getInsets();
        int width = parent.getWidth() - in.left - in.right;
        int height = 0;
        int widest = 0;
        int shown = 0;
        for (Component c : parent.getComponents())
        {
            if (!c.isVisible()) continue;
            if (width > 0) measureAt(c, width);
            Dimension d = c.getPreferredSize();
            height += d.height + (shown++ > 0 ? gap : 0);
            widest = Math.max(widest, d.width);
        }
        return new Dimension(widest + in.left + in.right, height + in.top + in.bottom);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent)
    {
        return preferredLayoutSize(parent);
    }

    @Override
    public void layoutContainer(Container parent)
    {
        Insets in = parent.getInsets();
        int width = parent.getWidth() - in.left - in.right;
        int y = in.top;
        for (Component c : parent.getComponents())
        {
            if (!c.isVisible()) continue;
            measureAt(c, width);
            int h = c.getPreferredSize().height;
            c.setBounds(in.left, y, width, h);
            y += h + gap;
        }
    }

    @Override
    public void addLayoutComponent(String name, Component comp)
    {
    }

    @Override
    public void removeLayoutComponent(Component comp)
    {
    }

    // Sizing to the real width first lets wrapped text report its true
    // wrapped height. A text area ignores the width while its height is 0
    // (fresh components), so give it room to measure in.
    private static void measureAt(Component c, int width)
    {
        c.setSize(width, c.getHeight() > 0 ? c.getHeight() : Short.MAX_VALUE);
    }
}
