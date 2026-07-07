package com.github.orgonag.fbclan.drops;

import com.github.orgonag.fbclan.util.SupabaseClient;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Uploads drop screenshots to the clan's Supabase storage bucket.
 *
 * Only runs when the user has enabled BOTH "Enable Drop Logging" and
 * "Screenshot Drops" (both off by default) and a drop meets their configured
 * GP threshold. The image is the client frame RuneLite was already rendering,
 * optionally annotated with the player's party member names.
 */
@Slf4j
public class DropScreenshotService
{
    static final String BUCKET = "drop-screenshots";

    // Prefix every legitimate screenshot URL starts with. DropLogPanel uses
    // this to refuse to open screenshot links that point anywhere else.
    public static String publicBucketPrefix()
    {
        return SupabaseClient.publicStorageUrl(BUCKET, "");
    }

    private final OkHttpClient httpClient;

    public DropScreenshotService(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    /**
     * Annotates, encodes, and uploads a screenshot.
     * Returns the public URL of the stored image, or null on failure.
     */
    public String upload(Image frame, String rsn, List<String> partyNames, int itemId)
    {
        try
        {
            BufferedImage annotated = annotate(frame, partyNames);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(annotated, "png", out);

            String path = buildPath(rsn, System.currentTimeMillis(), itemId);
            if (!SupabaseClient.uploadFile(httpClient, BUCKET, path, out.toByteArray(), "image/png"))
            {
                return null;
            }
            return SupabaseClient.publicStorageUrl(BUCKET, path);
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to upload drop screenshot", e);
            return null;
        }
    }

    static BufferedImage annotate(Image frame, List<String> partyNames)
    {
        BufferedImage image = new BufferedImage(
            frame.getWidth(null), frame.getHeight(null), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.drawImage(frame, 0, 0, null);

        String partyLine = formatPartyLine(partyNames);
        if (partyLine != null)
        {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            FontMetrics fm = g.getFontMetrics();
            int textX = 10;
            int textY = 10 + fm.getAscent();

            g.setColor(new Color(0, 0, 0, 160));
            g.fillRect(textX - 4, 8, fm.stringWidth(partyLine) + 8, fm.getHeight() + 4);

            g.setColor(Color.WHITE);
            g.drawString(partyLine, textX, textY);
        }

        g.dispose();
        return image;
    }

    static String formatPartyLine(List<String> partyNames)
    {
        if (partyNames == null || partyNames.isEmpty())
        {
            return null;
        }
        return "Party members: " + String.join(", ", partyNames);
    }

    static String buildPath(String rsn, long epochMillis, int itemId)
    {
        String safeRsn = rsn.replaceAll("[^A-Za-z0-9_-]", "_");
        return safeRsn + "/" + epochMillis + "_" + itemId + ".png";
    }
}
