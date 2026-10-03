package com.github.orgonag.fbclan.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;

/**
 * The "Loot Feed" look: colour tokens, fonts, and the handful of
 * components every tab is built from. Containers use {@link Stack}, which
 * lays children out top to bottom at full width and preferred height, so
 * nothing stretches or drifts sideways the way BoxLayout does.
 */
final class Theme
{
    static final Color BG = new Color(0x232323);
    static final Color SURFACE = new Color(0x1A1A1A);
    static final Color RAISED = new Color(0x2B2B2B);
    static final Color LINE = new Color(0x3A3A3A);
    static final Color TEXT = Color.WHITE;
    static final Color SOFT = new Color(0xC8C8C8);
    static final Color SUB = new Color(0xA5A5A5);
    static final Color FAINT = new Color(0x7A7A7A);
    static final Color ACCENT = new Color(0xDC8A00);
    static final Color ACCENT_HI = new Color(0xFFB13B);
    static final Color ACCENT_BG = new Color(0x3A2A10);
    static final Color INK = new Color(0x161616);
    static final Color GOLD = new Color(0xFFD700);
    static final Color GREEN = new Color(0x3FBF3F);
    static final Color RED = new Color(0xE07070);

    /** Item tile colouring: how special a drop (or anything else) is. */
    enum Tier
    {
        COMMON(0x6A6A6A, 0x262626), RARE(0x4AA3FF, 0x1D2A38), MEGA(0xB07CFF, 0x2B2236), PET(0xFFB13B, 0x3A2A10);

        final Color edge;
        final Color fill;

        Tier(int edge, int fill)
        {
            this.edge = new Color(edge);
            this.fill = new Color(fill);
        }
    }

    private Theme()
    {
    }

    // ------------------------------------------------------------ threads

    // Fetch on the executor, apply on the EDT. Never block the EDT on network.
    static <T> void async(ScheduledExecutorService executor, Supplier<T> fetch, Consumer<T> apply, Consumer<String> error)
    {
        executor.submit(() -> {
            try
            {
                T result = fetch.get();
                SwingUtilities.invokeLater(() -> { error.accept(null); apply.accept(result); });
            }
            catch (RuntimeException e)
            {
                SwingUtilities.invokeLater(() -> error.accept("Refresh failed. Previous data may be out of date."));
            }
        });
    }

    // ------------------------------------------------------------ containers

    static JPanel stack(int gap)
    {
        JPanel p = new JPanel(new Stack(gap));
        p.setOpaque(false);
        return p;
    }

    // West / centre / east in one line; any part may be null.
    static JPanel row(Component west, Component center, Component east)
    {
        JPanel p = new JPanel(new BorderLayout(7, 0));
        p.setOpaque(false);
        if (west != null) p.add(west, BorderLayout.WEST);
        if (center != null) p.add(center, BorderLayout.CENTER);
        if (east != null) p.add(east, BorderLayout.EAST);
        return p;
    }

    static Card card(Color edge)
    {
        return new Card(SURFACE, edge);
    }

    // A scrolling list filling `host`'s centre; returns the list to fill.
    static JPanel scroll(JPanel host)
    {
        JPanel list = stack(5);
        Viewport view = new Viewport();
        view.add(list, BorderLayout.NORTH);
        JScrollPane pane = new JScrollPane(view);
        pane.setBorder(null);
        pane.getViewport().setBackground(BG);
        pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pane.getVerticalScrollBar().setUnitIncrement(16);
        host.add(pane, BorderLayout.CENTER);
        return list;
    }

    // ------------------------------------------------------------ text

    static JLabel text(String s, Color color)
    {
        JLabel l = new JLabel(s);
        l.putClientProperty("html.disable", Boolean.TRUE);
        l.setForeground(color);
        l.setFont(FontManager.getRunescapeSmallFont());
        return l;
    }

    static JLabel bold(String s, Color color)
    {
        JLabel l = text(s, color);
        l.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        return l;
    }

    static JLabel heading(String s)
    {
        JLabel l = text(s, ACCENT_HI);
        l.setFont(FontManager.getRunescapeBoldFont());
        return l;
    }

    static JLabel caps(String s)
    {
        JLabel l = bold(s.toUpperCase(java.util.Locale.ROOT), SUB);
        l.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        return l;
    }

    static JLabel right(JLabel l)
    {
        l.setHorizontalAlignment(SwingConstants.RIGHT);
        return l;
    }

    static JLabel centered(String s)
    {
        JLabel l = text(s, SUB);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setBorder(BorderFactory.createEmptyBorder(14, 0, 14, 0));
        return l;
    }

    // Small filled tag ("TOP DROP", "LATEST").
    static JLabel badge(String s)
    {
        JLabel l = new JLabel(s)
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = smooth(g);
                g2.setColor(ACCENT_HI);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setForeground(INK);
        l.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        l.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
        return l;
    }

    // Multi-line text wrapped to the parent width. A JTextArea, not HTML:
    // Swing's HTML layout under-measures the RuneScape font and clips lines.
    static JTextArea wrap(String s, Color color)
    {
        JTextArea t = new JTextArea(s);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setEditable(false);
        t.setFocusable(false);
        t.setHighlighter(null);
        t.setOpaque(false);
        t.setForeground(color);
        t.setFont(FontManager.getRunescapeSmallFont());
        t.setBorder(null);
        return t;
    }

    static String timeAgo(Instant then)
    {
        long minutes = Duration.between(then, Instant.now()).toMinutes();
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";
        if (minutes < 60 * 24) return (minutes / 60) + "h ago";
        return (minutes / (60 * 24)) + "d ago";
    }

    static String timeAgo(String iso)
    {
        try
        {
            return timeAgo(OffsetDateTime.parse(iso).toInstant());
        }
        catch (RuntimeException e)
        {
            return "";
        }
    }

    // ------------------------------------------------------------ controls

    static Btn button(String s, Btn.Kind kind, Runnable action)
    {
        return new Btn(s, kind, action);
    }

    // Left click anywhere on `c` (children without their own listeners included).
    static <T extends JComponent> T onClick(T c, Runnable action)
    {
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseReleased(MouseEvent e)
            {
                if (SwingUtilities.isLeftMouseButton(e) && c.contains(e.getPoint())) action.run();
            }
        });
        return c;
    }

    static JTextField field(String value, int max)
    {
        JTextField f = new JTextField(value);
        f.setFont(FontManager.getRunescapeSmallFont());
        ((AbstractDocument) f.getDocument()).setDocumentFilter(new DocumentFilter()
        {
            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException
            {
                if (fb.getDocument().getLength() - length + (text == null ? 0 : text.length()) <= max)
                {
                    super.replace(fb, offset, length, text, attrs);
                }
            }

            @Override
            public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr) throws BadLocationException
            {
                replace(fb, offset, 0, text, attr);
            }
        });
        return f;
    }

    // Label on the left, control on the right.
    static JPanel labeled(String label, Component control)
    {
        JLabel l = text(label, SOFT);
        l.setPreferredSize(new Dimension(84, 22));
        return row(l, control, null);
    }

    // An item sprite on a rounded tile coloured by tier.
    static JLabel tile(ItemManager items, int itemId, int quantity, Tier tier, int size)
    {
        JLabel l = new JLabel()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = smooth(g);
                g2.setColor(tier.fill);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.setColor(tier.edge);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setPreferredSize(new Dimension(size, size));
        items.getImage(itemId, Math.max(1, quantity), quantity > 1).addTo(l);
        return l;
    }

    // Party slots: one segment per seat (a bar past 12 seats).
    static JComponent seats(int filled, int capacity)
    {
        JComponent c = new JComponent()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = smooth(g);
                int w = getWidth();
                if (capacity > 12)
                {
                    g2.setColor(RAISED);
                    g2.fillRoundRect(0, 0, w, 6, 6, 6);
                    g2.setColor(GREEN);
                    g2.fillRoundRect(0, 0, Math.max(6, w * Math.min(filled, capacity) / capacity), 6, 6, 6);
                }
                else
                {
                    int gap = 3;
                    int seat = (w - gap * (capacity - 1)) / capacity;
                    for (int i = 0; i < capacity; i++)
                    {
                        int x = i * (seat + gap);
                        if (i < filled)
                        {
                            g2.setColor(GREEN);
                            g2.fillRoundRect(x, 0, seat, 6, 4, 4);
                        }
                        else
                        {
                            g2.setColor(LINE);
                            g2.drawRoundRect(x, 0, seat - 1, 5, 4, 4);
                        }
                    }
                }
                g2.dispose();
            }
        };
        c.setPreferredSize(new Dimension(10, 6));
        return c;
    }

    static Graphics2D smooth(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g2;
    }

    // ------------------------------------------------------------ classes

    /** A rounded surface with an optional outline. */
    static final class Card extends JPanel
    {
        private final Color fill;
        private Color edge;

        Card(Color fill, Color edge)
        {
            super(new Stack(5));
            this.fill = fill;
            this.edge = edge;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(7, 7, 7, 7));
        }

        void setEdge(Color edge)
        {
            this.edge = edge;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = smooth(g);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            if (edge != null)
            {
                g2.setColor(edge);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            }
            g2.dispose();
        }
    }

    /** A flat, self-painted button; CHIP and TAB kinds can be switched on. */
    static final class Btn extends JButton
    {
        enum Kind { PRIMARY, GHOST, DANGER, CHIP, TAB }

        private final Kind kind;
        private boolean on;

        Btn(String text, Kind kind, Runnable action)
        {
            super(text);
            this.kind = kind;
            setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(5, 9, 5, 9));
            addActionListener(e -> action.run());
            restyle();
        }

        void setOn(boolean on)
        {
            this.on = on;
            restyle();
            repaint();
        }

        boolean isOn()
        {
            return on;
        }

        private boolean filled()
        {
            return kind == Kind.PRIMARY || (kind == Kind.CHIP && on);
        }

        private void restyle()
        {
            setForeground(filled() ? INK : kind == Kind.DANGER ? RED : kind == Kind.TAB && on ? ACCENT_HI : SOFT);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = smooth(g);
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            int arc = kind == Kind.CHIP ? h : 6;
            boolean hover = isEnabled() && getModel().isRollover();
            Color fill = !isEnabled() ? RAISED
                : filled() ? (hover ? ACCENT_HI : ACCENT)
                : kind == Kind.TAB && on ? ACCENT_BG
                : hover ? RAISED : null;
            Color edge = filled() || !isEnabled() ? null
                : kind == Kind.DANGER ? new Color(0x5A2A2A)
                : kind == Kind.TAB && on ? ACCENT : LINE;
            if (fill != null)
            {
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w, h, arc, arc);
            }
            if (edge != null)
            {
                g2.setColor(edge);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w, h, arc, arc);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** One-of-N choice drawn as chips; `value()` is the current pick. */
    static final class Choice<T> extends JPanel
    {
        private final List<T> values;
        private final List<Btn> buttons = new ArrayList<>();
        private T value;

        Choice(List<T> values, Function<T, String> label, T initial, Consumer<T> onPick)
        {
            super(new java.awt.GridLayout(1, values.size(), 3, 0));
            setOpaque(false);
            this.values = values;
            for (T v : values)
            {
                Btn b = new Btn(label.apply(v), Btn.Kind.CHIP, () -> {
                    set(v);
                    onPick.accept(v);
                });
                b.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
                buttons.add(b);
                add(b);
            }
            set(initial);
        }

        T value()
        {
            return value;
        }

        void set(T v)
        {
            value = values.contains(v) ? v : values.get(0);
            for (int i = 0; i < values.size(); i++)
            {
                buttons.get(i).setOn(values.get(i).equals(value));
            }
        }
    }

    /** Children top to bottom, full width, preferred height, `gap` apart. */
    static final class Stack implements LayoutManager
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

        // Sizing to the real width first lets wrapped text report its true
        // wrapped height. A text area ignores the width while its height is
        // 0 (fresh components), so give it room to measure in.
        private static void measureAt(Component c, int width)
        {
            c.setSize(width, c.getHeight() > 0 ? c.getHeight() : Short.MAX_VALUE);
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
    }

    // Tracks the viewport width so wrapped rows wrap to the real panel width.
    private static final class Viewport extends JPanel implements Scrollable
    {
        Viewport()
        {
            super(new BorderLayout());
            setBackground(BG);
            setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int o, int d)
        {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int o, int d)
        {
            return 64;
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }
}
