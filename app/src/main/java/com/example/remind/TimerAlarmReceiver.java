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
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Receives AlarmManager broadcasts when a background timer finishes.
 * Works even when the app is killed.
 */
public class TimerAlarmReceiver extends BroadcastReceiver {

    private static final String TAG        = "TimerAlarmReceiver";
    public  static final String CHANNEL_ID = "timer_channel_v1";
    public  static final String EXTRA_TIMER_ID    = "timer_id";
    public  static final String EXTRA_TIMER_TITLE = "timer_title";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String timerId    = intent.getStringExtra(EXTRA_TIMER_ID);
            String timerTitle = intent.getStringExtra(EXTRA_TIMER_TITLE);
            if (timerTitle == null || timerTitle.isEmpty()) timerTitle = "Timer";

            Log.d(TAG, "Timer finished: " + timerTitle + " id=" + timerId);

            createChannel(context);
            showNotification(context, timerId, timerTitle);
            vibrate(context);

            // Mark timer as completed in Firestore
            if (timerId != null) markCompleted(timerId);

        } catch (Exception e) {
            Log.e(TAG, "onReceive error: " + e.getMessage());
        }
    }

    private void showNotification(Context context, String timerId, String title) {
        try {
            // Tap → open HomeActivity on Timer tab
            Intent tap = new Intent(context, HomeActivity.class);
            tap.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            tap.putExtra("openTab", 2); // PAGE_TIMER
            int reqCode = timerId != null ? Math.abs(timerId.hashCode()) : 0;
            PendingIntent pi = PendingIntent.getActivity(context, reqCode, tap,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_timer)
                    .setContentTitle("⏰ Timer Finished!")
                    .setContentText("\"" + title + "\" has completed.")
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText("\"" + title + "\" has completed."))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setSound(sound)
                    .setVibrate(new long[]{0, 500, 200, 500, 200, 500})
                    .setAutoCancel(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentIntent(pi);

            NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            int notifId = timerId != null ? Math.abs(timerId.hashCode()) : 1001;
            nm.notify(notifId, builder.build());

        } catch (Exception e) {
            Log.e(TAG, "showNotification error: " + e.getMessage());
        }
    }

    private void vibrate(Context context) {
        try {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            long[] pattern = {0, 500, 200, 500, 200, 500};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                v.vibrate(pattern, -1);
            }
        } catch (Exception e) {
            Log.e(TAG, "vibrate error: " + e.getMessage());
        }
    }

    private void markCompleted(String timerId) {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) return;
            FirebaseFirestore.getInstance()
                    .collection("users").document(user.getUid())
                    .collection("timers").document(timerId)
                    .update("state", TimerModel.STATE_COMPLETED,
                            "remainingMillis", 0L)
                    .addOnFailureListener(e -> Log.e(TAG, "markCompleted failed: " + e.getMessage()));
        } catch (Exception e) {
            Log.e(TAG, "markCompleted error: " + e.getMessage());
        }
    }

    /** Create notification channel — safe to call multiple times. */
    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return;

            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Timer Alerts", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Notifies when a countdown timer finishes");
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 500, 200, 500});
            ch.enableLights(true);
            ch.setSound(sound, aa);
            ch.setShowBadge(true);
            nm.createNotificationChannel(ch);
        } catch (Exception e) {
            Log.e(TAG, "createChannel error: " + e.getMessage());
        }
    }
}
