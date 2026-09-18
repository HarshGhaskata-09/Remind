package com.example.remind;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String TAG        = "ReminderReceiver";
    private static final String CHANNEL_ID = "remind_tasks_v3";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive fired");

        String taskId       = intent.getStringExtra("taskId");
        String taskName     = intent.getStringExtra("taskName");
        String taskCategory = intent.getStringExtra("taskCategory");

        if (taskName == null || taskName.isEmpty())         taskName     = "Task Reminder";
        if (taskCategory == null || taskCategory.isEmpty()) taskCategory = "You have a task coming up!";

        Log.d(TAG, "name=" + taskName + "  cat=" + taskCategory);

        createChannel(context);

        // Tap → open HomeActivity
        Intent tap = new Intent(context, HomeActivity.class);
        tap.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int reqCode = taskId != null ? Math.abs(taskId.hashCode()) : 0;
        PendingIntent pi = PendingIntent.getActivity(context, reqCode, tap,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)          // flat white drawable — required
                .setContentTitle("⏰ " + taskName)
                .setContentText("Starting in 30 seconds · " + taskCategory)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Starting in 30 seconds · " + taskCategory))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setSound(sound)
                .setVibrate(new long[]{0, 400, 200, 400})
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pi);

        int notifId = taskId != null ? Math.abs(taskId.hashCode()) : 1;

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(notifId, builder.build());

        Log.d(TAG, "Notification posted id=" + notifId);
    }

    static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Only create if it doesn't exist yet
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;

        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Task Reminders", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Reminds you 30 seconds before a task");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 400, 200, 400});
        ch.enableLights(true);
        ch.setSound(sound, aa);
        ch.setShowBadge(true);
        nm.createNotificationChannel(ch);
        Log.d(TAG, "Channel created: " + CHANNEL_ID);
    }
}
