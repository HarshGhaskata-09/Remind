package com.example.remind;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class Note {
    private String id;
    private String title;
    private String description;
    private boolean isPinned;
    private String color;       // hex string e.g. "#FFFFFF"
    private long timestamp;     // millis for sorting
    private long reminderTime;  // millis, 0 = no reminder

    // Required empty constructor for Firestore
    public Note() {}

    public Note(String id, String title, String description, boolean isPinned,
                String color, long timestamp, long reminderTime) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.isPinned = isPinned;
        this.color = color;
        this.timestamp = timestamp;
        this.reminderTime = reminderTime;
    }

    // Getters & Setters
    public String getId()                    { return id; }
    public void setId(String id)             { this.id = id; }

    public String getTitle()                 { return title; }
    public void setTitle(String title)       { this.title = title; }

    public String getDescription()           { return description; }
    public void setDescription(String d)     { this.description = d; }

    public boolean isPinned()                { return isPinned; }
    public void setPinned(boolean p)         { this.isPinned = p; }

    public String getColor()                 { return color; }
    public void setColor(String color)       { this.color = color; }

    public long getTimestamp()               { return timestamp; }
    public void setTimestamp(long t)         { this.timestamp = t; }

    public long getReminderTime()            { return reminderTime; }
    public void setReminderTime(long r)      { this.reminderTime = r; }
}
