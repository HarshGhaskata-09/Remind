package com.example.remind;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NoteReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "note_reminders";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String title  = intent.getStringExtra("title");
        String noteId = intent.getStringExtra("noteId");

        createChannel(ctx);

        Intent open = new Intent(ctx, HomeActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, noteId != null ? noteId.hashCode() : 0,
                open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_note)
                .setContentTitle("Note Reminder")
                .setContentText(title != null ? title : "You have a note reminder")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(noteId != null ? noteId.hashCode() : 1, nb.build());
    }

    static void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Note Reminders", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }
}
