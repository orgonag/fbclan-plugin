package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.lfg.Activity;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Shared Swing helpers for the sidebar. Two rules keep BoxLayout honest:
 * every child uses LEFT_ALIGNMENT (mixed alignments shift siblings
 * sideways), and a child's max height is pinned to its preferred height
 * from the look-and-feel, never hard-coded smaller.
 */
public final class Ui
{
    public static final Color ONLINE = new Color(0x3F, 0xBF, 0x3F);
    public static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
    public static final Color GOLD = new Color(0xffd700);
    public static final int ICON = 20;
    // Card inner width: sidebar (242) minus the card's 8px side padding.
    private static final int WRAP_WIDTH = 226;
    private static final ImageIcon PLACEHOLDER = new ImageIcon(new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB));

    private Ui()
    {
    }

    // Fetch on the executor, apply on the EDT. Never block the EDT on network.
    public static <T> void async(ScheduledExecutorService executor, Supplier<T> fetch, Consumer<T> applyOnEdt)
    {
        async(executor, fetch, applyOnEdt, message -> {});
    }

    public static <T> void async(ScheduledExecutorService executor, Supplier<T> fetch, Consumer<T> applyOnEdt,
                                Consumer<String> errorOnEdt)
    {
        executor.submit(() -> {
            try
            {
                T result = fetch.get();
                SwingUtilities.invokeLater(() -> { errorOnEdt.accept(null); applyOnEdt.accept(result); });
            }
            catch (RuntimeException e)
            {
                SwingUtilities.invokeLater(() -> errorOnEdt.accept("Refresh failed. Previous data may be out of date."));
            }
        });
    }

    // ------------------------------------------------------------ containers

    // A vertical list inside a scroll pane that tracks the viewport width.
    public static JPanel scrollList(JPanel host)
    {
        JPanel list = new ScrollList();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        host.add(scroll, BorderLayout.CENTER);
        return list;
    }

    public static JPanel card()
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
            BorderFactory.createEmptyBorder(5, 8, 6, 8)));
        return card;
    }

    public static JPanel row(Color bg)
    {
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setBackground(bg);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    public static JPanel column(Color bg)
    {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBackground(bg);
        col.setAlignmentX(Component.LEFT_ALIGNMENT);
        return col;
    }

    public static <T extends JComponent> T pinHeight(T c)
    {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    // ------------------------------------------------------------ text

    public static JLabel html(String inner)
    {
        JLabel l = new JLabel("<html>" + inner + "</html>");
        l.setForeground(Color.WHITE);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    public static JLabel small(String text, Color color)
    {
        JLabel l = new JLabel(text);
        l.putClientProperty("html.disable", Boolean.TRUE);
        l.setForeground(color);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    public static JLabel note(String text)
    {
        JLabel l = small(text, MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        return l;
    }

    public static JLabel header(String text)
    {
        JLabel h = new JLabel(" " + text);
        h.setFont(FontManager.getRunescapeBoldFont());
        h.setForeground(ColorScheme.BRAND_ORANGE);
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        h.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
        return pinHeight(h);
    }

    public static JLabel empty(String text)
    {
        JLabel l = new JLabel(text);
        l.putClientProperty("html.disable", Boolean.TRUE);
        l.setForeground(MUTED);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        l.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        return l;
    }

    // Multi-line text wrapped to the sidebar width. A JTextArea, not an
    // HTML label: Swing's HTML layout under-measures the RuneScape font
    // and clips wrapped lines. Pre-sizing to the wrap width makes the
    // preferred height reflect the wrapped line count.
    public static JTextArea wrapped(String text, Color color)
    {
        JTextArea t = new JTextArea(text);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setEditable(false);
        t.setFocusable(false);
        t.setHighlighter(null);
        t.setOpaque(false);
        t.setForeground(color);
        t.setFont(FontManager.getRunescapeSmallFont());
        t.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        t.setSize(new Dimension(WRAP_WIDTH, Short.MAX_VALUE));
        t.setMaximumSize(new Dimension(Integer.MAX_VALUE, t.getPreferredSize().height));
        return t;
    }

    // Plain (non-HTML) text area for remote content: nothing to inject.
    public static JTextArea plain(String text)
    {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setForeground(MUTED);
        area.setFont(FontManager.getRunescapeFont());
        area.setBorder(null);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return area;
    }

    public static String esc(String s)
    {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String timeAgo(Instant then)
    {
        long minutes = Duration.between(then, Instant.now()).toMinutes();
        if (minutes < 1)
        {
            return "just now";
        }
        if (minutes < 60)
        {
            return minutes + " min ago";
        }
        if (minutes < 60 * 24)
        {
            return (minutes / 60) + "h ago";
        }
        return (minutes / (60 * 24)) + "d ago";
    }

    public static String timeAgo(String iso)
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

    // ------------------------------------------------------------ inputs

    public static JPanel labeled(String label, JComponent field)
    {
        JPanel row = row(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
        JLabel l = new JLabel(label);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(MUTED);
        l.setPreferredSize(new Dimension(80, 22));
        row.add(l, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return pinHeight(row);
    }

    public static JCheckBox checkbox(String text)
    {
        JCheckBox box = new JCheckBox(text);
        box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        box.setForeground(Color.WHITE);
        box.setFont(FontManager.getRunescapeSmallFont());
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        return box;
    }

    public static JSpinner spinner(int value, int min, int max, int step)
    {
        return new JSpinner(new SpinnerNumberModel(clamp(value, min, max), min, max, step));
    }

    public static JButton smallButton(String text)
    {
        JButton b = new JButton(text);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setMargin(new java.awt.Insets(1, 4, 1, 4));
        return b;
    }

    public static int clamp(int v, int min, int max)
    {
        return Math.max(min, Math.min(max, v));
    }

    public static void capLength(JTextField field, int max)
    {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter()
        {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException
            {
                if (string != null && fb.getDocument().getLength() + string.length() <= max)
                {
                    super.insertString(fb, offset, string, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException
            {
                int newLength = fb.getDocument().getLength() - length + (text == null ? 0 : text.length());
                if (newLength <= max)
                {
                    super.replace(fb, offset, length, text, attrs);
                }
            }
        });
    }

    // ------------------------------------------------------------ activities

    // "Category: Name" so the long dropdown scans; other values pass through.
    public static final class ActivityRenderer extends DefaultListCellRenderer
    {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
        {
            Object display = value;
            if (value instanceof Activity)
            {
                Activity a = (Activity) value;
                display = a.getCategory() == Activity.Category.GENERAL
                    ? a.getDisplayName() : a.getCategory().getDisplayName() + ": " + a.getDisplayName();
            }
            return super.getListCellRendererComponent(list, display, index, selected, focus);
        }
    }

    // The activity's item sprite from RuneLite's item cache, once loaded
    // (placeholder first so the row never reflows).
    public static JLabel icon(ItemManager items, Activity activity)
    {
        JLabel label = new JLabel(PLACEHOLDER);
        label.setPreferredSize(new Dimension(ICON, ICON));
        label.setToolTipText(activity.getDisplayName());
        AsyncBufferedImage sprite = items.getImage(activity.getIconItemId());
        sprite.onLoaded(() -> SwingUtilities.invokeLater(() -> {
            label.setIcon(new ImageIcon(ImageUtil.resizeImage(sprite, ICON, ICON)));
            label.repaint();
        }));
        return label;
    }

    // JPanel isn't Scrollable: inside a scroll pane it takes its preferred
    // width and long lines overflow. Tracking the viewport width makes
    // wrapped children wrap to the real panel width.
    private static class ScrollList extends JPanel implements Scrollable
    {
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
