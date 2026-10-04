package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.drops.DropLogger;
import com.github.orgonag.fbclan.drops.DropRules;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.concurrent.ScheduledExecutorService;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * A pop-out for one drop: its details and, when it has one, the
 * screenshot scaled to fit. One window, reused; the image is fetched,
 * size-checked, decoded and scaled off the EDT.
 */
@Singleton
public class DropViewer
{
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_SIDE = 8192;
    private static final long MAX_PIXELS = 24_000_000; // a 6K frame; ~96 MB decoded
    private static final int FIT_W = 760;
    private static final int FIT_H = 460;

    private final OkHttpClient http;
    private final ScheduledExecutorService executor;
    private final ItemManager items;
    private JDialog dialog;
    private String showing;

    @Inject
    public DropViewer(OkHttpClient http, ScheduledExecutorService executor, ItemManager items)
    {
        this.http = http;
        this.executor = executor;
        this.items = items;
    }

    // EDT. `anchor` is what was clicked; its window owns the dialog.
    void show(Component anchor, DropLogTab.Drop d)
    {
        boolean fresh = dialog == null;
        if (fresh)
        {
            dialog = new JDialog(SwingUtilities.getWindowAncestor(anchor));
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.getRootPane().registerKeyboardAction(e -> close(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
            dialog.addWindowListener(new WindowAdapter()
            {
                @Override
                public void windowClosed(WindowEvent e)
                {
                    dialog = null;
                    showing = null;
                }
            });
        }
        dialog.setTitle("Final Boss · Drop");
        showing = d.screenshot;

        JPanel header = Theme.stack(1);
        header.add(Theme.bold(d.name + (d.quantity > 1 ? " x " + d.quantity : ""), Theme.TEXT));
        header.add(Theme.text(d.rsn + " · " + d.npc + " · " + Theme.timeAgo(d.at), Theme.SUB));
        JPanel value = Theme.stack(1);
        value.add(Theme.right(Theme.bold(d.value > 0 ? DropRules.formatGp(d.value) + " GP" : "", Theme.GOLD)));
        value.add(Theme.right(Theme.text(d.rarity > 0 ? DropRules.formatRarity(d.rarity) : "", d.tier().edge)));

        JLabel image = Theme.text(d.hasScreenshot() ? "Loading screenshot..." : "No screenshot for this drop.", Theme.SUB);
        image.setHorizontalAlignment(SwingConstants.CENTER);
        Card frame = new Card(Theme.SURFACE, Theme.LINE);
        frame.setLayout(new BorderLayout());
        frame.add(image, BorderLayout.CENTER);
        frame.setPreferredSize(new Dimension(FIT_W + 16, (d.hasScreenshot() ? FIT_H : 60) + 16));

        JPanel body = Theme.stack(10);
        body.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        body.add(Theme.row(Theme.tile(items, d.itemId(), d.quantity, d.tier(), 40), header, value));
        body.add(frame);
        if (d.hasScreenshot())
        {
            body.add(Theme.row(null, null, Theme.button("Open in browser", Btn.Kind.GHOST, () -> LinkBrowser.browse(d.screenshot))));
            load(d.screenshot, image);
        }
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(body, BorderLayout.NORTH);
        dialog.setContentPane(root);
        dialog.pack();
        // Centre once; later drops keep wherever the user moved it.
        if (fresh) dialog.setLocationRelativeTo(dialog.getOwner());
        dialog.setVisible(true);
        dialog.toFront();
    }

    // EDT. Plugin shutdown / logout.
    void close()
    {
        if (dialog != null) dialog.dispose();
    }

    private void load(String url, JLabel target)
    {
        Theme.async(executor, () -> fetch(url), img -> {
            if (!url.equals(showing)) return;
            target.setText(img == null ? "Screenshot unavailable — try Open in browser." : null);
            target.setIcon(img == null ? null : new ImageIcon(img));
        }, message -> { if (message != null && url.equals(showing)) target.setText("Screenshot unavailable — try Open in browser."); });
    }

    // Executor. The URL is checked again here: only the plugin's own bucket.
    private BufferedImage fetch(String url)
    {
        if (!DropLogger.isScreenshot(url))
        {
            return null;
        }
        try (Response response = http.newCall(new Request.Builder().url(url).build()).execute())
        {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null || body.contentLength() > MAX_BYTES) return null;
            byte[] bytes = readCapped(body.byteStream());
            BufferedImage img = bytes == null ? null : decode(bytes);
            if (img == null) return null;
            double scale = Math.min(1.0, Math.min((double) FIT_W / img.getWidth(), (double) FIT_H / img.getHeight()));
            return scale >= 1.0 ? img : ImageUtil.resizeImage(img, (int) (img.getWidth() * scale), (int) (img.getHeight() * scale), true);
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private static byte[] readCapped(InputStream in) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        for (int n; (n = in.read(buf)) != -1; )
        {
            out.write(buf, 0, n);
            if (out.size() > MAX_BYTES) return null;
        }
        return out.toByteArray();
    }

    // Reads the header first so a small file can't claim a huge canvas.
    private static BufferedImage decode(byte[] bytes) throws IOException
    {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes)))
        {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try
            {
                reader.setInput(in);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w > MAX_SIDE || h > MAX_SIDE || (long) w * h > MAX_PIXELS) return null;
                return reader.read(0);
            }
            finally
            {
                reader.dispose();
            }
        }
    }
}
