package com.github.orgonag.fbclan.ui;

import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

/** One-of-N choice drawn as chips; `value()` is the current pick. */
final class Choice<T> extends JPanel
{
    private final List<T> values;
    private final List<Btn> buttons = new ArrayList<>();
    private T value;

    Choice(List<T> values, Function<T, String> label, T initial, Consumer<T> onPick)
    {
        super(new GridLayout(1, values.size(), 3, 0));
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

    void enable(T v, boolean enabled, String why)
    {
        Btn b = buttons.get(values.indexOf(v));
        b.setEnabled(enabled);
        b.setToolTipText(enabled ? null : why);
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
