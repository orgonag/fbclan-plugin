package com.github.orgonag.fbclan.announcements;

/**
 * One clan announcement as synced from the Google Sheet. Immutable.
 * Fields are never null; blank means the sheet cell was empty.
 */
public class Announcement
{
    private final String date;   // YYYY-MM-DD from posted_at
    private final String title;
    private final String body;   // may contain newlines (preserved from the sheet)

    public Announcement(String date, String title, String body)
    {
        this.date = date;
        this.title = title;
        this.body = body;
    }

    public String getDate()
    {
        return date;
    }

    public String getTitle()
    {
        return title;
    }

    public String getBody()
    {
        return body;
    }
}
