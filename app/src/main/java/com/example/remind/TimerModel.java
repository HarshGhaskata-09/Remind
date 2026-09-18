package com.example.remind;

import com.google.firebase.Timestamp;

/**
 * Firestore model for a countdown timer.
 * Collection: users/{uid}/timers
 */
public class TimerModel {

    // Timer states
    public static final String STATE_IDLE      = "idle";
    public static final String STATE_RUNNING   = "running";
    public static final String STATE_PAUSED    = "paused";
    public static final String STATE_COMPLETED = "completed";

    private String    id;
    private String    title;
    private long      durationMillis;   // total duration set by user
    private long      remainingMillis;  // remaining when paused / started
    private String    state;            // idle | running | paused | completed
    private long      startedAtMillis;  // System.currentTimeMillis() when last started
    private Timestamp createdAt;

    // Required empty constructor for Firestore deserialization
    public TimerModel() {}

    public TimerModel(String id, String title, long durationMillis) {
        this.id              = id;
        this.title           = title;
        this.durationMillis  = durationMillis;
        this.remainingMillis = durationMillis;
        this.state           = STATE_IDLE;
        this.startedAtMillis = 0;
        this.createdAt       = Timestamp.now();
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String    getId()              { return id; }
    public String    getTitle()           { return title; }
    public long      getDurationMillis()  { return durationMillis; }
    public long      getRemainingMillis() { return remainingMillis; }
    public String    getState()           { return state; }
    public long      getStartedAtMillis() { return startedAtMillis; }
    public Timestamp getCreatedAt()       { return createdAt; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setId(String id)                          { this.id = id; }
    public void setTitle(String title)                    { this.title = title; }
    public void setDurationMillis(long durationMillis)    { this.durationMillis = durationMillis; }
    public void setRemainingMillis(long remainingMillis)  { this.remainingMillis = remainingMillis; }
    public void setState(String state)                    { this.state = state; }
    public void setStartedAtMillis(long startedAtMillis)  { this.startedAtMillis = startedAtMillis; }
    public void setCreatedAt(Timestamp createdAt)         { this.createdAt = createdAt; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Live remaining millis — accounts for elapsed time since last start. */
    public long computeRemaining() {
        if (STATE_RUNNING.equals(state) && startedAtMillis > 0) {
            long elapsed = System.currentTimeMillis() - startedAtMillis;
            return Math.max(0, remainingMillis - elapsed);
        }
        return remainingMillis;
    }

    public boolean isRunning()   { return STATE_RUNNING.equals(state); }
    public boolean isPaused()    { return STATE_PAUSED.equals(state); }
    public boolean isCompleted() { return STATE_COMPLETED.equals(state); }
    public boolean isIdle()      { return STATE_IDLE.equals(state); }
}
