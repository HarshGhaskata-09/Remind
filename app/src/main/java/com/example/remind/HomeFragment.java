package com.example.remind;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class HomeFragment extends Fragment {

    private RecyclerView recyclerView;
    private TaskAdapter taskAdapter;
    private LinearLayout emptyView;
    private final List<Task> taskList = new ArrayList<>();
    private CollectionReference tasksRef;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerViewTasks);
        emptyView = view.findViewById(R.id.emptyStateLayout);

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        tasksRef = FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("tasks");

        LinearLayoutManager llm = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(llm);
        taskAdapter = new TaskAdapter(taskList, tasksRef, requireContext());
        recyclerView.setAdapter(taskAdapter);
        taskAdapter.attachToRecyclerView(recyclerView);

        loadTasks();
    }

    public void openAddTaskDialog() {
        if (getContext() == null) return;
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext(), R.style.BottomSheetStyle);
        android.view.View v = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_task, null);
        sheet.setContentView(v);

        RemindApp.applyAll(v, requireContext());

        android.widget.TextView tvTitle    = v.findViewById(R.id.tvDialogTitle);
        TextInputEditText etTask           = v.findViewById(R.id.etTask);
        TextInputEditText etCategory       = v.findViewById(R.id.etCategory);
        android.widget.NumberPicker npHour = v.findViewById(R.id.npHour);
        android.widget.NumberPicker npMin  = v.findViewById(R.id.npMinute);
        android.widget.TextView tvAM       = v.findViewById(R.id.tvAM);
        android.widget.TextView tvPM       = v.findViewById(R.id.tvPM);
        MaterialButton btnChooseDate       = v.findViewById(R.id.btnChooseDate);
        MaterialButton btnCancel           = v.findViewById(R.id.btnCancel);
        MaterialButton btnSave             = v.findViewById(R.id.btnSave);

        tvTitle.setText("Add Task");

        // --- Hour picker: 1–12 ---
        npHour.setMinValue(1);
        npHour.setMaxValue(12);
        npHour.setWrapSelectorWheel(true);

        // --- Minute picker: 00–59 with leading zero ---
        npMin.setMinValue(0);
        npMin.setMaxValue(59);
        npMin.setFormatter(i -> String.format("%02d", i));
        npMin.setWrapSelectorWheel(true);

        // Seed with current time + 1 minute (so default is always a valid future time)
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, 1);
        int nowHour24 = cal.get(Calendar.HOUR_OF_DAY);
        int nowMin    = cal.get(Calendar.MINUTE);
        final boolean[] isAM = {nowHour24 < 12};
        int initHour12 = nowHour24 % 12;
        if (initHour12 == 0) initHour12 = 12;
        npHour.setValue(initHour12);
        npMin.setValue(nowMin);
        updateAmPm(tvAM, tvPM, isAM[0]);

        // AM / PM tap
        tvAM.setOnClickListener(vv -> { isAM[0] = true;  updateAmPm(tvAM, tvPM, true); });
        tvPM.setOnClickListener(vv -> { isAM[0] = false; updateAmPm(tvAM, tvPM, false); });

        // --- Date picker ---
        final Calendar today = Calendar.getInstance();
        final String[] date = {String.format("%02d-%02d-%d",
                cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))};
        // Track if selected date is today (for time restriction)
        final boolean[] isToday = {true};
        btnChooseDate.setText(date[0]);
        btnChooseDate.setOnClickListener(vv -> {
            android.app.DatePickerDialog dpd = new android.app.DatePickerDialog(getContext(),
                    (dp, y, m, d) -> {
                        date[0] = String.format("%02d-%02d-%d", d, m + 1, y);
                        btnChooseDate.setText(date[0]);
                        isToday[0] = isTodayDate(y, m, d);
                    },
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
            // Restrict: today to today+3 years
            dpd.getDatePicker().setMinDate(today.getTimeInMillis());
            Calendar maxCal = Calendar.getInstance();
            maxCal.add(Calendar.YEAR, 3);
            dpd.getDatePicker().setMaxDate(maxCal.getTimeInMillis());
            dpd.show();
        });

        btnSave.setOnClickListener(vv -> {
            String name = etTask.getText().toString().trim();
            String cat  = etCategory.getText().toString().trim();
            if (name.isEmpty() || cat.isEmpty()) {
                Toast.makeText(getContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            // Convert picker values → 24h for storage/alarm
            int h12   = npHour.getValue();
            int mins  = npMin.getValue();
            int h24;
            if (isAM[0]) {
                h24 = (h12 == 12) ? 0 : h12;
            } else {
                h24 = (h12 == 12) ? 12 : h12 + 12;
            }
            // Validate: if today is selected, time must not be in the past
            if (isToday[0]) {
                Calendar now = Calendar.getInstance();
                int nowH = now.get(Calendar.HOUR_OF_DAY);
                int nowM = now.get(Calendar.MINUTE);
                if (h24 < nowH || (h24 == nowH && mins <= nowM)) {
                    Toast.makeText(getContext(), "Please select a future time for today", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            String time24    = String.format("%02d:%02d", h24, mins);
            String taskId    = tasksRef.document().getId();
            Task newTask     = new Task(taskId, name, time24, date[0], cat);
            tasksRef.document(taskId).set(newTask)
                    .addOnSuccessListener(u -> {
                        Toast.makeText(getContext(), "Task added", Toast.LENGTH_SHORT).show();
                        setReminder(taskId, newTask);
                        sheet.dismiss();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        });

        btnCancel.setOnClickListener(vv -> sheet.dismiss());
        sheet.show();
    }

    private void updateAmPm(android.widget.TextView tvAM, android.widget.TextView tvPM, boolean amSelected) {
        if (amSelected) {
            tvAM.setBackgroundResource(R.drawable.bg_chip_selected);
            tvAM.setTextColor(requireContext().getColor(R.color.accent));
            tvPM.setBackgroundResource(R.drawable.bg_chip_unselected);
            tvPM.setTextColor(requireContext().getColor(R.color.text_secondary));
        } else {
            tvPM.setBackgroundResource(R.drawable.bg_chip_selected);
            tvPM.setTextColor(requireContext().getColor(R.color.accent));
            tvAM.setBackgroundResource(R.drawable.bg_chip_unselected);
            tvAM.setTextColor(requireContext().getColor(R.color.text_secondary));
        }
    }

    /** Returns true if the given year/month(0-based)/day is today */
    private boolean isTodayDate(int year, int month, int day) {
        Calendar today = Calendar.getInstance();
        return year == today.get(Calendar.YEAR)
                && month == today.get(Calendar.MONTH)
                && day == today.get(Calendar.DAY_OF_MONTH);
    }

    private void loadTasks() {
        tasksRef.addSnapshotListener((snapshots, e) -> {
            if (e != null) { Log.e("Firestore", e.getMessage()); return; }
            taskList.clear();
            if (snapshots != null) {
                for (QueryDocumentSnapshot doc : snapshots) {
                    Task task = doc.toObject(Task.class);
                    if (task.getTaskId() == null) task.setTaskId(doc.getId());
                    taskList.add(task);
                }
            }
            // Sort: newest added first, oldest last
            Collections.sort(taskList, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
            taskAdapter.updateTaskList(taskList);
            updateUI();
        });
    }

    private void setReminder(String taskId, Task task) {
        ReminderManager.schedule(requireContext(), task);
    }

    private void updateUI() {
        if (taskList.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }
}
