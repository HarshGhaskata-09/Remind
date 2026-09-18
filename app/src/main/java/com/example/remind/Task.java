package com.example.remind;

public class Task {
    private String taskId;
    private String task;
    private String time;
    private String date;
    private String category;
    private long createdAt; // epoch millis — used for newest-first sorting

    public Task() {}

    public Task(String taskId, String task, String time, String date, String category) {
        this.taskId     = taskId;
        this.task       = task;
        this.time       = time;
        this.date       = date;
        this.category   = category;
        this.createdAt  = System.currentTimeMillis();
    }

    public String getTaskId()              { return taskId; }
    public void   setTaskId(String v)      { this.taskId = v; }

    public String getTask()                { return task; }
    public void   setTask(String v)        { this.task = v; }

    public String getTime()                { return time; }
    public void   setTime(String v)        { this.time = v; }

    public String getDate()                { return date; }
    public void   setDate(String v)        { this.date = v; }

    public String getCategory()            { return category; }
    public void   setCategory(String v)    { this.category = v; }

    public long   getCreatedAt()           { return createdAt; }
    public void   setCreatedAt(long v)     { this.createdAt = v; }
}
