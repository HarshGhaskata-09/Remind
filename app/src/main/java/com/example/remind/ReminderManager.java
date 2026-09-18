package com.example.remind;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

public class ReminderManager {

    private static final String TAG = "ReminderManager";

    /** Cancel old alarm + schedule fresh one. If time passed, fires in 3s. */
    public static void reschedule(Context context, Task task) {
        cancel(context, task.getTaskId());
        schedule(context, task);
    }

    /** Cancel pending alarm for taskId */
    public static void cancel(Context context, String taskId) {
        try {
            Intent intent = new Intent(context, ReminderReceiver.class);
            // FLAG_NO_CREATE returns null if no existing PI — avoids creating a ghost alarm
            PendingIntent pi = PendingIntent.getBroadcast(context,
                    Math.abs(taskId.hashCode()), intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) {
                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                am.cancel(pi);
                pi.cancel();
                Log.d(TAG, "Cancelled alarm for taskId=" + taskId);
            }
        } catch (Exception e) {
            Log.e(TAG, "cancel: " + e.getMessage());
        }
    }

    /** Schedule alarm. If trigger time already passed → fires in 3 seconds. */
    public static void schedule(Context context, Task task) {
        // Ensure notification channel exists before scheduling
        ReminderReceiver.createChannel(context);

        long triggerAt = buildTriggerTime(task);
        long now = System.currentTimeMillis();

        if (triggerAt <= now) {
            // Time already passed — fire in 3s so user still gets notified
            triggerAt = now + 3_000L;
            Log.d(TAG, "Past time — firing in 3s: " + task.getTask());
        } else {
            Log.d(TAG, "Scheduling for: " + task.getTask()
                    + " in " + ((triggerAt - now) / 1000) + "s");
        }

        // Build intent WITHOUT setPackage() — setPackage breaks delivery on some OEMs
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("taskId",       task.getTaskId());
        intent.putExtra("taskName",     task.getTask());
        intent.putExtra("taskCategory", task.getCategory());

        int reqCode = Math.abs(task.getTaskId().hashCode());
        PendingIntent pi = PendingIntent.getBroadcast(context, reqCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                    Log.d(TAG, "setExactAndAllowWhileIdle scheduled");
                } else {
                    // Inexact fallback — still works, just ±few minutes off
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                    Log.d(TAG, "setAndAllowWhileIdle (inexact) scheduled");
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                Log.d(TAG, "setExactAndAllowWhileIdle (M+) scheduled");
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                Log.d(TAG, "setExact scheduled");
            }
        } catch (SecurityException se) {
            // Last resort fallback
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            Log.w(TAG, "SecurityException — fell back to set(): " + se.getMessage());
        }
    }

    /**
     * Parse "DD-MM-YYYY" + "HH:mm" → Calendar millis − 30s.
     * Returns now+3s on any error so app never crashes.
     */
    static long buildTriggerTime(Task task) {
        try {
            String dateStr = task.getDate();
            String timeStr = task.getTime();

            if (dateStr == null || timeStr == null || dateStr.isEmpty() || timeStr.isEmpty())
                return System.currentTimeMillis() + 3_000L;

            String[] dp = dateStr.trim().split("-");
            String[] tp = timeStr.trim().split(":");

            if (dp.length < 3 || tp.length < 2)
                return System.currentTimeMillis() + 3_000L;

            int day   = Integer.parseInt(dp[0].trim());
            int month = Integer.parseInt(dp[1].trim()) - 1;
            int year  = Integer.parseInt(dp[2].trim());
            int hour  = Integer.parseInt(tp[0].trim());
            int min   = Integer.parseInt(tp[1].trim());

            // Bounds check — prevents Calendar from silently rolling over
            if (year < 2020 || year > 2100) return System.currentTimeMillis() + 3_000L;
            if (month < 0   || month > 11)  return System.currentTimeMillis() + 3_000L;
            if (day < 1     || day > 31)    return System.currentTimeMillis() + 3_000L;
            if (hour < 0    || hour > 23)   return System.currentTimeMillis() + 3_000L;
            if (min < 0     || min > 59)    return System.currentTimeMillis() + 3_000L;

            Calendar c = Calendar.getInstance();
            c.set(Calendar.YEAR,         year);
            c.set(Calendar.MONTH,        month);
            c.set(Calendar.DAY_OF_MONTH, day);
            c.set(Calendar.HOUR_OF_DAY,  hour);
            c.set(Calendar.MINUTE,       min);
            c.set(Calendar.SECOND,       0);
            c.set(Calendar.MILLISECOND,  0);

            return c.getTimeInMillis() - 30_000L;

        } catch (Exception e) {
            Log.e(TAG, "buildTriggerTime: " + e.getMessage());
            return System.currentTimeMillis() + 3_000L;
        }
    }
}
