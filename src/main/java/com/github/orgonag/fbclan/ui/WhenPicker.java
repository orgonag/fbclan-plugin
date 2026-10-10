package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.lfg.Party;
import java.awt.Component;
import java.awt.GridLayout;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

/**
 * When a post starts: ASAP, or Later with a day and a time (local zone,
 * 15-minute steps) no more than 7 days after the post was created.
 */
final class WhenPicker extends JPanel
{
    private static final String ASAP = "ASAP";
    private static final String LATER = "Later";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter SUMMARY = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

    /** Which start-time choices the host still has (the post being edited excluded). */
    static final class Limits
    {
        final boolean asapTaken;
        final boolean laterFull;

        Limits(boolean asapTaken, boolean laterFull)
        {
            this.asapTaken = asapTaken;
            this.laterFull = laterFull;
        }
    }

    private final ZoneId zone = ZoneId.systemDefault();
    private final Instant windowEnd;
    // The edited post's start time, else null.
    private final Instant original;
    private final Choice<String> choice;
    private final JComboBox<LocalDate> dayBox = new JComboBox<>();
    private final JComboBox<Integer> hourBox = new JComboBox<>();
    private final JComboBox<Integer> minuteBox = new JComboBox<>(new Integer[]{0, 15, 30, 45});
    private final JLabel summary = Theme.text("", Theme.SOFT);
    private final JPanel later = Theme.stack(10);

    // `editing` = the post being edited, else null.
    WhenPicker(Party editing, Limits limits)
    {
        super(new Stack(10));
        setOpaque(false);
        windowEnd = (editing == null ? Instant.now() : editing.getCreatedAt()).plus(Party.SCHEDULE_WINDOW);
        original = editing != null && editing.isScheduled() ? editing.getScheduledFor() : null;

        LocalDate end = windowEnd.atZone(zone).toLocalDate();
        for (LocalDate d = LocalDate.now(zone); !d.isAfter(end); d = d.plusDays(1)) dayBox.addItem(d);
        for (int h = 0; h < 24; h++) hourBox.addItem(h);
        dayBox.setRenderer(render(v -> {
            LocalDate today = LocalDate.now(zone);
            LocalDate d = (LocalDate) v;
            return d.equals(today) ? "Today" : d.equals(today.plusDays(1)) ? "Tomorrow" : DAY.format(d);
        }));
        hourBox.setRenderer(render(v -> String.format("%02d", v)));
        minuteBox.setRenderer(render(v -> String.format("%02d", v)));

        // An edited post keeps its own time selectable (even if it has passed
        // or isn't on a 15-minute mark); a new one defaults to the next whole
        // hour at least 30 minutes away.
        ZonedDateTime start = original != null ? original.atZone(zone)
            : ZonedDateTime.now(zone).plusMinutes(30).plusHours(1).withMinute(0).withSecond(0).withNano(0);
        if (original != null)
        {
            if (((DefaultComboBoxModel<LocalDate>) dayBox.getModel()).getIndexOf(start.toLocalDate()) < 0) dayBox.insertItemAt(start.toLocalDate(), 0);
            if (((DefaultComboBoxModel<Integer>) minuteBox.getModel()).getIndexOf(start.getMinute()) < 0) minuteBox.addItem(start.getMinute());
        }
        dayBox.setSelectedItem(start.toLocalDate());
        hourBox.setSelectedItem(start.getHour());
        minuteBox.setSelectedItem(original != null ? start.getMinute() : start.getMinute() - start.getMinute() % 15);
        dayBox.addActionListener(e -> summarize());
        hourBox.addActionListener(e -> summarize());
        minuteBox.addActionListener(e -> summarize());

        choice = new Choice<>(Arrays.asList(ASAP, LATER), s -> s, ASAP, s -> showLater());
        // A post older than the ASAP lifetime would expire on the next sweep; the server refuses it too.
        boolean tooOld = editing != null && Instant.now().isAfter(editing.getCreatedAt().plus(Party.ASAP_LIFETIME));
        choice.enable(ASAP, !limits.asapTaken && !tooOld,
            limits.asapTaken ? "You already have an ASAP party" : "Too old to switch to ASAP; post a new one");
        choice.enable(LATER, !limits.laterFull, "You already have " + Party.MAX_SCHEDULED + " scheduled parties");
        choice.set(editing != null ? (original != null ? LATER : ASAP) : (limits.asapTaken ? LATER : ASAP));

        JPanel time = new JPanel(new GridLayout(1, 2, 6, 0));
        time.setOpaque(false);
        time.add(hourBox);
        time.add(minuteBox);
        later.add(Theme.labeled("Day", dayBox));
        later.add(Theme.labeled("Time", time));
        later.add(summary);
        add(Theme.text("When", Theme.SUB));
        add(choice);
        add(later);
        showLater();
    }

    // The chosen start, or null for ASAP. Throws with a readable reason when unusable.
    Instant start()
    {
        if (!LATER.equals(choice.value())) return null;
        LocalDate day = (LocalDate) dayBox.getSelectedItem();
        if (day == null) throw new IllegalArgumentException("Pick a day.");
        Instant at = ZonedDateTime.of(day, LocalTime.of((Integer) hourBox.getSelectedItem(), (Integer) minuteBox.getSelectedItem()), zone).toInstant();
        // An unchanged time is fine even if it has passed (the server only checks a changed one).
        if (original != null && at.getEpochSecond() / 60 == original.getEpochSecond() / 60) return original;
        if (!at.isAfter(Instant.now().plusSeconds(60))) throw new IllegalArgumentException("Pick a start time in the future.");
        if (at.isAfter(windowEnd)) throw new IllegalArgumentException("Start time must be within 7 days of posting.");
        return at;
    }

    private void showLater()
    {
        later.setVisible(LATER.equals(choice.value()));
        summarize();
        revalidate();
        repaint();
    }

    // "Sat 5 Oct, 20:00 · in 2d 4h (your time)", or the reason it can't be used.
    // Also called when the host window redraws, so the countdown never goes stale.
    void summarize()
    {
        try
        {
            Instant at = start();
            if (at == null) return;
            summary.setForeground(Theme.SOFT);
            Duration until = Duration.between(Instant.now(), at);
            summary.setText(SUMMARY.withZone(zone).format(at) + (until.isNegative() ? " · already passed" : " · in " + LfgUi.span(until)) + " (your time)");
        }
        catch (IllegalArgumentException e)
        {
            summary.setForeground(Theme.RED);
            summary.setText(e.getMessage());
        }
    }

    private static ListCellRenderer<Object> render(Function<Object, String> text)
    {
        return new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
            {
                return super.getListCellRendererComponent(list, value == null ? null : text.apply(value), index, selected, focus);
            }
        };
    }
}
