package com.github.orgonag.fbclan.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * One sidebar tab: a heading with a right-hand note, an optional control
 * strip, an error line, and a scrolling list. Refreshes are single-flight.
 */
abstract class Tab extends JPanel
{
    protected final ScheduledExecutorService executor;
    private final JPanel controls = Theme.stack(6);
    protected final JPanel list;
    protected final JLabel note = Theme.text("", Theme.SUB);
    private final JPanel top = Theme.stack(6);
    private final JTextArea error = Theme.wrap("", Theme.RED);
    private final AtomicBoolean refreshing = new AtomicBoolean();

    Tab(String title, ScheduledExecutorService executor)
    {
        super(new BorderLayout());
        this.executor = executor;
        setBackground(Theme.BG);
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 6, 8));
        top.add(Theme.row(Theme.heading(title), null, note));
        top.add(controls);
        top.add(error);
        controls.setVisible(false);
        error.setVisible(false);
        add(top, BorderLayout.NORTH);
        list = Theme.scroll(this);
    }

    // A control strip line under the heading.
    protected void control(Component c)
    {
        controls.add(c);
        controls.setVisible(true);
    }

    public abstract void refresh();

    // Fetch off the EDT, then render; a second call while one is in flight is dropped.
    protected <T> void load(Supplier<T> fetch, Consumer<T> render)
    {
        if (!refreshing.compareAndSet(false, true)) return;
        Supplier<T> once = () -> {
            try
            {
                return fetch.get();
            }
            finally
            {
                refreshing.set(false);
            }
        };
        Theme.async(executor, once, render, this::showError);
    }

    // EDT. Null clears.
    protected void showError(String message)
    {
        error.setText(message == null ? "" : message);
        error.setVisible(message != null);
        top.revalidate();
    }

    // EDT. Replace the list's contents. A form that is put back (the LFG
    // tab keeps its input rows across refreshes) keeps the keyboard focus.
    protected void fill(Runnable build)
    {
        Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        boolean ours = focused != null && SwingUtilities.isDescendingFrom(focused, list);
        // Mid-pick in a dropdown: leave it open; the next refresh catches up.
        if (ours && focused instanceof JComboBox && ((JComboBox<?>) focused).isPopupVisible()) return;
        list.removeAll();
        build.run();
        list.revalidate();
        list.repaint();
        if (ours && SwingUtilities.isDescendingFrom(focused, list)) focused.requestFocusInWindow();
    }
}
