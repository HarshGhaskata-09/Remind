package com.example.remind;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class TimerFragment extends Fragment implements TimerAdapter.TimerActionListener {

    private static final String TAG = "TimerFragment";

    private RecyclerView           recyclerView;
    private TimerAdapter           adapter;
    private View                   emptyView;

    private CollectionReference  timersRef;
    private ListenerRegistration listenerReg;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_timer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            recyclerView = view.findViewById(R.id.rvTimers);
            emptyView    = view.findViewById(R.id.emptyTimerView);

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) { showEmpty(true); return; }

            timersRef = FirebaseFirestore.getInstance()
                    .collection("users").document(user.getUid())
                    .collection("timers");

            adapter = new TimerAdapter(new ArrayList<>(), requireContext(), this);
            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            recyclerView.setAdapter(adapter);

            TimerAlarmReceiver.createChannel(requireContext());
            listenTimers();
        } catch (Exception e) {
            Log.e(TAG, "onViewCreated: " + e.getMessage());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listenerReg != null) listenerReg.remove();
        if (adapter != null) adapter.cancelAll();
    }

    private void listenTimers() {
        listenerReg = timersRef
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "listen error: " + e.getMessage());
                        return;
                    }
                    // Build a fresh list — never mutate the adapter's internal list directly
                    List<TimerModel> fresh = new ArrayList<>();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            try {
                                TimerModel t = doc.toObject(TimerModel.class);
                                if (t.getId() == null) t.setId(doc.getId());
                                fresh.add(t);
                            } catch (Exception ex) {
                                Log.e(TAG, "parse error: " + ex.getMessage());
                            }
                        }
                    }
                    // Sort newest first
                    Collections.sort(fresh, (a, b) -> {
                        if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    });
                    Log.d(TAG, "Timers loaded: " + fresh.size());
                    if (adapter != null) {
                        adapter.updateList(fresh);
                    }
                    showEmpty(fresh.isEmpty());
                });
    }

    public void openAddTimerSheet() {
        showTimerOptionsSheet();
    }

    private void showTimerOptionsSheet() {
        if (getContext() == null) return;
        try {
            BottomSheetDialog sheet = new BottomSheetDialog(requireContext(), R.style.BottomSheetStyle);
            View v = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_timer_options, null);
            sheet.setContentView(v);

            MaterialButton btnNew    = v.findViewById(R.id.btnNewTimer);
            MaterialButton btnRandom = v.findViewById(R.id.btnRandomTimer);

            btnNew.setOnClickListener(vv -> { sheet.dismiss(); showAddTimerSheet(); });
            btnRandom.setOnClickListener(vv -> { sheet.dismiss(); addRandomTimer(); });
            sheet.show();
        } catch (Exception e) { Log.e(TAG, "showTimerOptionsSheet: " + e.getMessage()); }
    }

    private void showAddTimerSheet() {
        if (getContext() == null) return;
        try {
            BottomSheetDialog sheet = new BottomSheetDialog(requireContext(), R.style.BottomSheetStyle);
            View v = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_add_timer, null);
            sheet.setContentView(v);

            TextInputEditText etTitle = v.findViewById(R.id.etTimerTitle);
            NumberPicker npHour       = v.findViewById(R.id.npTimerHour);
            NumberPicker npMin        = v.findViewById(R.id.npTimerMin);
            NumberPicker npSec        = v.findViewById(R.id.npTimerSec);
            MaterialButton btnAdd     = v.findViewById(R.id.btnAddTimer);
            MaterialButton btnCancel  = v.findViewById(R.id.btnCancelTimer);

            npHour.setMinValue(0); npHour.setMaxValue(23); npHour.setWrapSelectorWheel(true);
            npMin.setMinValue(0);  npMin.setMaxValue(59);  npMin.setWrapSelectorWheel(true);
            npSec.setMinValue(0);  npSec.setMaxValue(59);  npSec.setWrapSelectorWheel(true);
            npHour.setFormatter(i -> String.format("%02d", i));
            npMin.setFormatter(i  -> String.format("%02d", i));
            npSec.setFormatter(i  -> String.format("%02d", i));
            npMin.setValue(1);

            btnAdd.setOnClickListener(vv -> {
                try {
                    String title = etTitle.getText() != null
                            ? etTitle.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(title)) {
                        etTitle.setError("Enter a title"); etTitle.requestFocus(); return;
                    }
                    long ms = ((npHour.getValue() * 3600L)
                             + (npMin.getValue()  * 60L)
                             +  npSec.getValue()) * 1000L;
                    if (ms <= 0) {
                        Toast.makeText(getContext(), "Set a duration > 0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    addTimer(title, ms);
                    sheet.dismiss();
                } catch (Exception e) {
                    Log.e(TAG, "btnAdd: " + e.getMessage());
                }
            });
            btnCancel.setOnClickListener(vv -> sheet.dismiss());
            sheet.show();
        } catch (Exception e) {
            Log.e(TAG, "showAddTimerSheet: " + e.getMessage());
        }
    }

    private void addTimer(String title, long durationMs) {
        if (timersRef == null) return;
        try {
            String id = timersRef.document().getId();
            timersRef.document(id).set(new TimerModel(id, title, durationMs))
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            Log.e(TAG, "addTimer: " + e.getMessage());
        }
    }

    private void saveTimer(TimerModel timer) {
        if (timersRef == null || timer.getId() == null) return;
        timersRef.document(timer.getId()).set(timer)
                .addOnFailureListener(e -> Log.e(TAG, "saveTimer: " + e.getMessage()));
    }

    @Override
    public void onStart(TimerModel timer) {
        try {
            timer.setRemainingMillis(timer.getDurationMillis());
            timer.setStartedAtMillis(System.currentTimeMillis());
            timer.setState(TimerModel.STATE_RUNNING);
            saveTimer(timer);
            scheduleAlarm(timer);
        } catch (Exception e) { Log.e(TAG, "onStart: " + e.getMessage()); }
    }

    @Override
    public void onPause(TimerModel timer) {
        try {
            timer.setRemainingMillis(timer.computeRemaining());
            timer.setStartedAtMillis(0);
            timer.setState(TimerModel.STATE_PAUSED);
            cancelAlarm(timer);
            saveTimer(timer);
        } catch (Exception e) { Log.e(TAG, "onPause: " + e.getMessage()); }
    }

    @Override
    public void onResume(TimerModel timer) {
        try {
            timer.setStartedAtMillis(System.currentTimeMillis());
            timer.setState(TimerModel.STATE_RUNNING);
            saveTimer(timer);
            scheduleAlarm(timer);
        } catch (Exception e) { Log.e(TAG, "onResume: " + e.getMessage()); }
    }

    @Override
    public void onReset(TimerModel timer) {
        try {
            cancelAlarm(timer);
            timer.setRemainingMillis(timer.getDurationMillis());
            timer.setStartedAtMillis(0);
            timer.setState(TimerModel.STATE_IDLE);
            saveTimer(timer);
        } catch (Exception e) { Log.e(TAG, "onReset: " + e.getMessage()); }
    }

    @Override
    public void onDelete(TimerModel timer) {
        // actual delete — called after user confirms in the sheet
        try {
            cancelAlarm(timer);
            if (timersRef != null && timer.getId() != null) {
                timersRef.document(timer.getId()).delete()
                        .addOnFailureListener(e ->
                                Toast.makeText(getContext(), "Delete failed", Toast.LENGTH_SHORT).show());
            }
        } catch (Exception e) { Log.e(TAG, "onDelete: " + e.getMessage()); }
    }

    @Override
    public void onDeleteRequest(TimerModel timer) {
        if (getContext() == null) return;
        try {
            BottomSheetDialog sheet = new BottomSheetDialog(requireContext(), R.style.BottomSheetStyle);
            View v = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_delete_timer, null);
            sheet.setContentView(v);

            android.widget.TextView tvMsg = v.findViewById(R.id.tvDeleteTimerMsg);
            String title = timer.getTitle() != null ? timer.getTitle() : "this timer";
            tvMsg.setText("Are you sure you want to delete \"" + title + "\"?");

            MaterialButton btnConfirm = v.findViewById(R.id.btnDeleteTimerConfirm);
            MaterialButton btnKeep    = v.findViewById(R.id.btnKeepTimer);

            btnConfirm.setOnClickListener(vv -> { sheet.dismiss(); onDelete(timer); });
            btnKeep.setOnClickListener(vv -> sheet.dismiss());
            sheet.show();
        } catch (Exception e) { Log.e(TAG, "onDeleteRequest: " + e.getMessage()); }
    }

    @Override
    public void onEdit(TimerModel timer) {
        if (getContext() == null) return;
        try {
            BottomSheetDialog sheet = new BottomSheetDialog(requireContext(), R.style.BottomSheetStyle);
            View v = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_edit_timer, null);
            sheet.setContentView(v);

            TextInputEditText etTitle = v.findViewById(R.id.etEditTimerTitle);
            NumberPicker npHour       = v.findViewById(R.id.npEditTimerHour);
            NumberPicker npMin        = v.findViewById(R.id.npEditTimerMin);
            NumberPicker npSec        = v.findViewById(R.id.npEditTimerSec);
            MaterialButton btnSave    = v.findViewById(R.id.btnSaveEditTimer);
            MaterialButton btnCancel  = v.findViewById(R.id.btnCancelEditTimer);

            npHour.setMinValue(0); npHour.setMaxValue(23); npHour.setWrapSelectorWheel(true);
            npMin.setMinValue(0);  npMin.setMaxValue(59);  npMin.setWrapSelectorWheel(true);
            npSec.setMinValue(0);  npSec.setMaxValue(59);  npSec.setWrapSelectorWheel(true);
            npHour.setFormatter(i -> String.format("%02d", i));
            npMin.setFormatter(i  -> String.format("%02d", i));
            npSec.setFormatter(i  -> String.format("%02d", i));

            // Pre-fill existing values
            etTitle.setText(timer.getTitle());
            long durSec = timer.getDurationMillis() / 1000;
            npHour.setValue((int) (durSec / 3600));
            npMin.setValue((int) ((durSec % 3600) / 60));
            npSec.setValue((int) (durSec % 60));

            btnSave.setOnClickListener(vv -> {
                try {
                    String newTitle = etTitle.getText() != null
                            ? etTitle.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(newTitle)) {
                        etTitle.setError("Enter a title"); etTitle.requestFocus(); return;
                    }
                    long ms = ((npHour.getValue() * 3600L)
                             + (npMin.getValue()  * 60L)
                             +  npSec.getValue()) * 1000L;
                    if (ms <= 0) {
                        Toast.makeText(getContext(), "Set a duration > 0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    timer.setTitle(newTitle);
                    timer.setDurationMillis(ms);
                    timer.setRemainingMillis(ms);
                    timer.setStartedAtMillis(0);
                    timer.setState(TimerModel.STATE_IDLE);
                    saveTimer(timer);
                    sheet.dismiss();
                } catch (Exception e) { Log.e(TAG, "btnSave edit: " + e.getMessage()); }
            });
            btnCancel.setOnClickListener(vv -> sheet.dismiss());
            sheet.show();
        } catch (Exception e) { Log.e(TAG, "onEdit: " + e.getMessage()); }
    }

    /** Random timer: 1–60 min with auto-generated title */
    public void addRandomTimer() {
        if (timersRef == null || getContext() == null) return;
        try {
            Random rnd = new Random();
            int minutes = rnd.nextInt(60) + 1;
            int seconds = rnd.nextInt(60);
            long ms = (minutes * 60L + seconds) * 1000L;

            String[] adjectives = {"Quick", "Swift", "Short", "Focused", "Power", "Turbo", "Chill"};
            String[] nouns      = {"Break", "Sprint", "Session", "Round", "Boost", "Pause", "Focus"};
            String title = adjectives[rnd.nextInt(adjectives.length)]
                         + " " + nouns[rnd.nextInt(nouns.length)]
                         + " (" + String.format("%02d:%02d", minutes, seconds) + ")";

            addTimer(title, ms);
            Toast.makeText(getContext(), "Random timer: " + title, Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Log.e(TAG, "addRandomTimer: " + e.getMessage()); }
    }

    private void scheduleAlarm(TimerModel timer) {
        if (getContext() == null) return;
        try {
            long triggerAt = System.currentTimeMillis() + timer.computeRemaining();
            Intent intent = new Intent(requireContext(), TimerAlarmReceiver.class);
            intent.putExtra(TimerAlarmReceiver.EXTRA_TIMER_ID,    timer.getId());
            intent.putExtra(TimerAlarmReceiver.EXTRA_TIMER_TITLE, timer.getTitle());
            int reqCode = Math.abs(timer.getId().hashCode());
            PendingIntent pi = PendingIntent.getBroadcast(requireContext(), reqCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager am = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } catch (Exception e) { Log.e(TAG, "scheduleAlarm: " + e.getMessage()); }
    }

    private void cancelAlarm(TimerModel timer) {
        if (getContext() == null || timer.getId() == null) return;
        try {
            Intent intent = new Intent(requireContext(), TimerAlarmReceiver.class);
            int reqCode = Math.abs(timer.getId().hashCode());
            PendingIntent pi = PendingIntent.getBroadcast(requireContext(), reqCode, intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) {
                AlarmManager am = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
                if (am != null) am.cancel(pi);
                pi.cancel();
            }
        } catch (Exception e) { Log.e(TAG, "cancelAlarm: " + e.getMessage()); }
    }

    private void showEmpty(boolean empty) {
        if (recyclerView == null || emptyView == null) return;
        recyclerView.setVisibility(empty ? View.GONE    : View.VISIBLE);
        emptyView.setVisibility(empty    ? View.VISIBLE : View.GONE);
    }
}
