package com.github.orgonag.fbclan.drops;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public class DiscordWebhookService
{
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int GOLD_COLOR = 0xFFD700;

    private final OkHttpClient httpClient;

    public DiscordWebhookService(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    public void sendDropNotification(String webhookUrl, String rsn, String itemName, long geValue, String npcName)
    {
        if (webhookUrl == null || webhookUrl.isEmpty())
        {
            return;
        }

        if (!webhookUrl.startsWith("https://discord.com/api/webhooks/") && !webhookUrl.startsWith("https://discordapp.com/api/webhooks/"))
        {
            log.warn("Discord webhook URL does not appear to be a valid Discord webhook, skipping");
            return;
        }

        JsonObject embed = new JsonObject();
        embed.addProperty("title", rsn + " received a drop!");
        embed.addProperty("description", itemName + " (" + DropTrackingService.formatGp(geValue) + " GP) from " + npcName);
        embed.addProperty("color", GOLD_COLOR);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);

        JsonObject payload = new JsonObject();
        payload.add("embeds", embeds);

        RequestBody body = RequestBody.create(JSON, payload.toString());
        Request request = new Request.Builder()
            .url(webhookUrl)
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                log.warn("Discord webhook failed: {} {}", response.code(), response.message());
            }
        }
        catch (IOException e)
        {
            log.warn("Discord webhook error", e);
        }
    }
}
