package com.github.orgonag.fbclan.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Value;
import lombok.AllArgsConstructor;

/** Separates HTTP delivery from the server's domain outcome. */
@Value
@AllArgsConstructor
public class ApiResult
{
    int httpStatus;
    JsonElement body;
    String error;
    String contentRange;
    public ApiResult(int status, JsonElement body, String error) { this(status, body, error, null); }
    public long totalRows()
    {
        try { return contentRange == null ? -1 : Long.parseLong(contentRange.substring(contentRange.lastIndexOf('/') + 1)); }
        catch (RuntimeException e) { return -1; }
    }

    public boolean successful()
    {
        if (httpStatus < 200 || httpStatus >= 300 || error != null) return false;
        return body == null || !body.isJsonObject() || !body.getAsJsonObject().has("status")
            || "ok".equals(Supabase.str(body.getAsJsonObject(), "status"));
    }
    public boolean retryable() { return httpStatus == 0 || httpStatus == 429 || httpStatus >= 500; }
    public String message()
    {
        if (error != null) return error;
        if (body != null && body.isJsonObject()) {
            JsonObject o = body.getAsJsonObject();
            String text = Supabase.str(o, "message");
            if (!text.isEmpty()) return text;
        }
        return "Request failed (HTTP " + httpStatus + ")";
    }
}
