package com.example.remind;

import android.content.Context;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RecyclerView adapter for the timer list.
 * Each running timer gets its own CountDownTimer for live UI updates.
 */
public class TimerAdapter extends RecyclerView.Adapter<TimerAdapter.TimerViewHolder> {

    private static final String TAG = "TimerAdapter";

    public interface TimerActionListener {
        void onStart(TimerModel timer);
        void onPause(TimerModel timer);
        void onResume(TimerModel timer);
        void onReset(TimerModel timer);
        void onDeleteRequest(TimerModel timer);   // shows confirmation sheet
        void onDelete(TimerModel timer);           // actual delete (called after confirm)
        void onEdit(TimerModel timer);             // shows edit sheet
    }

    private final List<TimerModel>      timerList;
    private final Context               context;
    private final TimerActionListener   listener;

    // Active CountDownTimers keyed by timer ID — prevents memory leaks
    private final Map<String, CountDownTimer> activeCountdowns = new HashMap<>();

    public TimerAdapter(List<TimerModel> timerList, Context context, TimerActionListener listener) {
        this.timerList = timerList;
        this.context   = context;
        this.listener  = listener;
    }

    @NonNull
    @Override
    public TimerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_timer, parent, false);
        return new TimerViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TimerViewHolder holder, int position) {
        try {
            TimerModel timer = timerList.get(position);
            bindTimer(holder, timer);
        } catch (Exception e) {
            Log.e(TAG, "onBindViewHolder error: " + e.getMessage());
        }
    }

    private void bindTimer(@NonNull TimerViewHolder h, TimerModel timer) {
        // Cancel any existing countdown for this timer slot
        cancelCountdown(timer.getId());

        h.tvTitle.setText(timer.getTitle() != null ? timer.getTitle() : "Timer");

        long total     = timer.getDurationMillis();
        long remaining = timer.computeRemaining();

        // Progress bar (0–100)
        int progress = total > 0 ? (int) ((remaining * 100) / total) : 0;
        h.progressBar.setMax(100);
        h.progressBar.setProgress(progress);

        // Time display
        h.tvTime.setText(formatMillis(remaining));

        // State label + button visibility
        switch (timer.getState() != null ? timer.getState() : TimerModel.STATE_IDLE) {

            case TimerModel.STATE_IDLE:
                h.tvStatus.setText("Ready");
                h.tvStatus.setTextColor(context.getColor(R.color.text_secondary));
                h.btnStart.setVisibility(View.VISIBLE);
                h.btnPause.setVisibility(View.GONE);
                h.btnResume.setVisibility(View.GONE);
                h.btnReset.setVisibility(View.GONE);
                break;

            case TimerModel.STATE_RUNNING:
                h.tvStatus.setText("Running");
                h.tvStatus.setTextColor(context.getColor(R.color.success));
                h.btnStart.setVisibility(View.GONE);
                h.btnPause.setVisibility(View.VISIBLE);
                h.btnResume.setVisibility(View.GONE);
                h.btnReset.setVisibility(View.VISIBLE);
                // Start live countdown
                startCountdown(h, timer);
                break;

            case TimerModel.STATE_PAUSED:
                h.tvStatus.setText("Paused");
                h.tvStatus.setTextColor(context.getColor(R.color.warning));
                h.btnStart.setVisibility(View.GONE);
                h.btnPause.setVisibility(View.GONE);
                h.btnResume.setVisibility(View.VISIBLE);
                h.btnReset.setVisibility(View.VISIBLE);
                break;

            case TimerModel.STATE_COMPLETED:
                h.tvStatus.setText("Done ✓");
                h.tvStatus.setTextColor(context.getColor(R.color.accent));
                h.tvTime.setText("00:00:00");
                h.progressBar.setProgress(0);
                h.btnStart.setVisibility(View.GONE);
                h.btnPause.setVisibility(View.GONE);
                h.btnResume.setVisibility(View.GONE);
                h.btnReset.setVisibility(View.VISIBLE);
                break;
        }

        // Button clicks
        h.btnStart.setOnClickListener(v  -> { if (listener != null) listener.onStart(timer); });
        h.btnPause.setOnClickListener(v  -> { if (listener != null) listener.onPause(timer); });
        h.btnResume.setOnClickListener(v -> { if (listener != null) listener.onResume(timer); });
        h.btnReset.setOnClickListener(v  -> { if (listener != null) listener.onReset(timer); });
        h.btnDelete.setOnClickListener(v -> { if (listener != null) listener.onDeleteRequest(timer); });

        // Edit only allowed when idle / paused / completed
        String state = timer.getState() != null ? timer.getState() : TimerModel.STATE_IDLE;
        boolean canEdit = !TimerModel.STATE_RUNNING.equals(state);
        h.btnEdit.setAlpha(canEdit ? 1f : 0.3f);
        h.btnEdit.setOnClickListener(v -> {
            if (listener == null) return;
            if (canEdit) {
                listener.onEdit(timer);
            } else {
                // visual feedback — can't edit while running
                android.widget.Toast.makeText(context,
                        "Pause the timer before editing", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Starts a CountDownTimer that ticks every second to update the UI row. */
    private void startCountdown(@NonNull TimerViewHolder h, TimerModel timer) {
        long remaining = timer.computeRemaining();
        if (remaining <= 0) return;

        CountDownTimer cdt = new CountDownTimer(remaining, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                try {
                    long total = timer.getDurationMillis();
                    int prog   = total > 0 ? (int) ((millisUntilFinished * 100) / total) : 0;
                    h.tvTime.setText(formatMillis(millisUntilFinished));
                    h.progressBar.setProgress(prog);
                } catch (Exception e) {
                    Log.e(TAG, "onTick error: " + e.getMessage());
                }
            }

            @Override
            public void onFinish() {
                try {
                    h.tvTime.setText("00:00:00");
                    h.progressBar.setProgress(0);
                    h.tvStatus.setText("Done ✓");
                    h.tvStatus.setTextColor(context.getColor(R.color.accent));
                    h.btnPause.setVisibility(View.GONE);
                    h.btnReset.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    Log.e(TAG, "onFinish error: " + e.getMessage());
                }
            }
        }.start();

        activeCountdowns.put(timer.getId(), cdt);
    }

    private void cancelCountdown(String timerId) {
        if (timerId == null) return;
        CountDownTimer cdt = activeCountdowns.remove(timerId);
        if (cdt != null) cdt.cancel();
    }

    /** Cancel all active countdowns — call from Fragment.onDestroyView() */
    public void cancelAll() {
        for (CountDownTimer cdt : activeCountdowns.values()) {
            if (cdt != null) cdt.cancel();
        }
        activeCountdowns.clear();
    }

    @Override
    public void onViewRecycled(@NonNull TimerViewHolder holder) {
        super.onViewRecycled(holder);
        // CountDownTimers are keyed by timer ID, not view holder — no action needed here
    }

    @Override
    public int getItemCount() { return timerList != null ? timerList.size() : 0; }

    public void updateList(List<TimerModel> newList) {
        // Use a fresh copy — do NOT clear the same reference that was passed in
        timerList.clear();
        if (newList != null) timerList.addAll(newList);
        notifyDataSetChanged();
    }

    /** Format milliseconds → "HH:MM:SS" */
    public static String formatMillis(long millis) {
        if (millis < 0) millis = 0;
        long totalSec = millis / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    static class TimerViewHolder extends RecyclerView.ViewHolder {
        TextView    tvTitle, tvTime, tvStatus;
        ProgressBar progressBar;
        ImageButton btnStart, btnPause, btnResume, btnReset, btnDelete, btnEdit;

        TimerViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle     = itemView.findViewById(R.id.tvTimerTitle);
            tvTime      = itemView.findViewById(R.id.tvTimerTime);
            tvStatus    = itemView.findViewById(R.id.tvTimerStatus);
            progressBar = itemView.findViewById(R.id.timerProgress);
            btnStart    = itemView.findViewById(R.id.btnTimerStart);
            btnPause    = itemView.findViewById(R.id.btnTimerPause);
            btnResume   = itemView.findViewById(R.id.btnTimerResume);
            btnReset    = itemView.findViewById(R.id.btnTimerReset);
            btnDelete   = itemView.findViewById(R.id.btnTimerDelete);
            btnEdit     = itemView.findViewById(R.id.btnTimerEdit);
        }
    }
}
