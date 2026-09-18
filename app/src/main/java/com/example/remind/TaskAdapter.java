package com.example.remind;

import android.animation.ObjectAnimator;
import android.app.DatePickerDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.CollectionReference;

import java.util.List;
import java.util.Calendar;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private List<Task> taskList;
    private final CollectionReference tasksRef;
    private final Context context;

    // How far the card slides before triggering edit (dp → px)
    private static final float SWIPE_THRESHOLD_DP = 80f;

    public TaskAdapter(List<Task> taskList, CollectionReference tasksRef, Context context) {
        this.taskList = taskList;
        this.tasksRef = tasksRef;
        this.context = context;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = taskList.get(position);
        holder.tvTask.setText(task.getTask());
        holder.tvTime.setText(to12h(task.getTime()));
        holder.tvDate.setText(task.getDate());
        holder.tvCategory.setText(task.getCategory());

        // Apply saved font style
        holder.tvTask.setTypeface(AdditionalSettingsActivity.getSavedTypeface(context));

        // Apply saved effect via RemindApp (fully opaque, no alpha bleed)
        RemindApp.applyEffect(holder.cardForeground, context);

        // Reset card position (important for recycled views)
        holder.cardForeground.setTranslationX(0f);

        holder.ivDelete.setOnClickListener(v -> showDeleteDialog(task, holder.getAdapterPosition()));
    }

    /** Attach swipe-to-edit to the RecyclerView */
    public void attachToRecyclerView(RecyclerView rv) {
        float threshold = SWIPE_THRESHOLD_DP * context.getResources().getDisplayMetrics().density;

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, 0) {

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) { return false; }

            @Override
            public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                return ItemTouchHelper.RIGHT; // only left→right swipe
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getAdapterPosition();
                if (pos == RecyclerView.NO_ID) return;
                showEditDialog(taskList.get(pos), pos);
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                // Animate back to 0 smoothly, then reset
                MaterialCardView card = ((TaskViewHolder) vh).cardForeground;
                if (card.getTranslationX() != 0f) {
                    animateBack(card, null);
                } else {
                    card.setTranslationX(0f);
                }
            }

            @Override
            public void onChildDraw(@NonNull android.graphics.Canvas c,
                                    @NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder vh,
                                    float dX, float dY,
                                    int actionState, boolean isActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    // Only move the foreground card, not the whole item
                    TaskViewHolder holder = (TaskViewHolder) vh;
                    // Clamp so card doesn't slide too far
                    float clampedDx = Math.min(dX, threshold * 1.5f);
                    holder.cardForeground.setTranslationX(clampedDx);
                    // Do NOT call super — prevents default full-item draw
                } else {
                    super.onChildDraw(c, rv, vh, dX, dY, actionState, isActive);
                }
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder vh) {
                return 0.25f; // 25% of item width triggers swipe
            }

            @Override
            public float getSwipeEscapeVelocity(float defaultValue) {
                return defaultValue * 0.6f; // easier to trigger with a flick
            }
        });

        helper.attachToRecyclerView(rv);
    }

    private void animateBack(View view, Runnable onEnd) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(view, "translationX", view.getTranslationX(), 0f);
        anim.setDuration(250);
        anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (onEnd != null) onEnd.run();
            }
        });
        anim.start();
    }

    private void showEditDialog(Task task, int position) {
        BottomSheetDialog sheet = new BottomSheetDialog(context, R.style.BottomSheetStyle);
        View v = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_task, null);
        sheet.setContentView(v);
        RemindApp.applyAll(v, context);

        TextView tvTitle           = v.findViewById(R.id.tvDialogTitle);
        TextInputEditText etTask   = v.findViewById(R.id.etTask);
        TextInputEditText etCategory = v.findViewById(R.id.etCategory);
        NumberPicker npHour        = v.findViewById(R.id.npHour);
        NumberPicker npMin         = v.findViewById(R.id.npMinute);
        TextView tvAM              = v.findViewById(R.id.tvAM);
        TextView tvPM              = v.findViewById(R.id.tvPM);
        MaterialButton btnChooseDate = v.findViewById(R.id.btnChooseDate);
        MaterialButton btnCancel   = v.findViewById(R.id.btnCancel);
        MaterialButton btnSave     = v.findViewById(R.id.btnSave);

        tvTitle.setText("Edit Task");
        etTask.setText(task.getTask());
        etCategory.setText(task.getCategory());

        // Parse existing time (stored as 24h "HH:mm")
        int initH24 = 0, initMin = 0;
        try {
            String[] tp = task.getTime().split(":");
            initH24 = Integer.parseInt(tp[0]);
            initMin = Integer.parseInt(tp[1]);
        } catch (Exception ignored) {}

        // If the task date is today and the stored time is already past, seed with now+1min
        boolean taskIsToday = isTodayDate(task.getDate());
        if (taskIsToday) {
            Calendar now = Calendar.getInstance();
            int nowH = now.get(Calendar.HOUR_OF_DAY);
            int nowM = now.get(Calendar.MINUTE);
            if (initH24 < nowH || (initH24 == nowH && initMin <= nowM)) {
                // Advance to now + 1 minute
                now.add(Calendar.MINUTE, 1);
                initH24 = now.get(Calendar.HOUR_OF_DAY);
                initMin = now.get(Calendar.MINUTE);
            }
        }

        final boolean[] isAM = {initH24 < 12};
        int initH12 = initH24 % 12;
        if (initH12 == 0) initH12 = 12;

        npHour.setMinValue(1);
        npHour.setMaxValue(12);
        npHour.setWrapSelectorWheel(true);
        npHour.setValue(initH12);

        npMin.setMinValue(0);
        npMin.setMaxValue(59);
        npMin.setFormatter(i -> String.format("%02d", i));
        npMin.setWrapSelectorWheel(true);
        npMin.setValue(initMin);

        updateAmPm(tvAM, tvPM, isAM[0]);
        tvAM.setOnClickListener(vv -> { isAM[0] = true;  updateAmPm(tvAM, tvPM, true); });
        tvPM.setOnClickListener(vv -> { isAM[0] = false; updateAmPm(tvAM, tvPM, false); });

        // Date
        final String[] selectedDate = {task.getDate()};
        btnChooseDate.setText(selectedDate[0]);
        // Check if the task's existing date is today
        final boolean[] isToday = {isTodayDate(selectedDate[0])};
        btnChooseDate.setOnClickListener(vv -> {
            int d = 1, m = 0, y = Calendar.getInstance().get(Calendar.YEAR);
            try {
                String[] dp = selectedDate[0].split("-");
                d = Integer.parseInt(dp[0]);
                m = Integer.parseInt(dp[1]) - 1;
                y = Integer.parseInt(dp[2]);
            } catch (Exception ignored) {}
            android.app.DatePickerDialog dpd = new android.app.DatePickerDialog(context,
                    (dp, yr, mo, day) -> {
                        selectedDate[0] = String.format("%02d-%02d-%d", day, mo + 1, yr);
                        btnChooseDate.setText(selectedDate[0]);
                        isToday[0] = isTodayDate(selectedDate[0]);

                        // If switched to today, check if current picker time is in the past
                        // and re-seed to now+1min if so
                        if (isToday[0]) {
                            int curH12 = npHour.getValue();
                            int curMin = npMin.getValue();
                            int curH24 = isAM[0]
                                    ? (curH12 == 12 ? 0 : curH12)
                                    : (curH12 == 12 ? 12 : curH12 + 12);
                            Calendar now = Calendar.getInstance();
                            int nowH = now.get(Calendar.HOUR_OF_DAY);
                            int nowM = now.get(Calendar.MINUTE);
                            if (curH24 < nowH || (curH24 == nowH && curMin <= nowM)) {
                                now.add(Calendar.MINUTE, 1);
                                int newH24 = now.get(Calendar.HOUR_OF_DAY);
                                int newMin = now.get(Calendar.MINUTE);
                                boolean newIsAM = newH24 < 12;
                                int newH12 = newH24 % 12;
                                if (newH12 == 0) newH12 = 12;
                                npHour.setValue(newH12);
                                npMin.setValue(newMin);
                                isAM[0] = newIsAM;
                                updateAmPm(tvAM, tvPM, newIsAM);
                            }
                        }
                    }, y, m, d);
            // Restrict: today to today+3 years
            Calendar todayCal = Calendar.getInstance();
            dpd.getDatePicker().setMinDate(todayCal.getTimeInMillis());
            Calendar maxCal = Calendar.getInstance();
            maxCal.add(Calendar.YEAR, 3);
            dpd.getDatePicker().setMaxDate(maxCal.getTimeInMillis());
            dpd.show();
        });

        sheet.setOnDismissListener(d -> notifyItemChanged(position));

        btnSave.setOnClickListener(vv -> {
            String name = etTask.getText().toString().trim();
            String cat  = etCategory.getText().toString().trim();
            if (name.isEmpty() || cat.isEmpty()) {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            int h12  = npHour.getValue();
            int mins = npMin.getValue();
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
                    Toast.makeText(context, "Please select a future time for today", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            String time24 = String.format("%02d:%02d", h24, mins);
            task.setTask(name);
            task.setTime(time24);
            task.setDate(selectedDate[0]);
            task.setCategory(cat);
            tasksRef.document(task.getTaskId()).set(task)
                    .addOnSuccessListener(u -> {
                        Toast.makeText(context, "Task updated", Toast.LENGTH_SHORT).show();
                        ReminderManager.reschedule(context, task);
                        sheet.dismiss();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(context, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });

        btnCancel.setOnClickListener(vv -> sheet.dismiss());
        sheet.show();
    }

    private void updateAmPm(TextView tvAM, TextView tvPM, boolean amSelected) {
        if (amSelected) {
            tvAM.setBackgroundResource(R.drawable.bg_chip_selected);
            tvAM.setTextColor(context.getColor(R.color.accent));
            tvPM.setBackgroundResource(R.drawable.bg_chip_unselected);
            tvPM.setTextColor(context.getColor(R.color.text_secondary));
        } else {
            tvPM.setBackgroundResource(R.drawable.bg_chip_selected);
            tvPM.setTextColor(context.getColor(R.color.accent));
            tvAM.setBackgroundResource(R.drawable.bg_chip_unselected);
            tvAM.setTextColor(context.getColor(R.color.text_secondary));
        }
    }

    private void showDeleteDialog(Task task, int position) {
        BottomSheetDialog sheet = new BottomSheetDialog(context, R.style.BottomSheetStyle);
        View v = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_delete, null);
        sheet.setContentView(v);
        RemindApp.applyAll(v, context); // apply font to delete sheet

        v.findViewById(R.id.btnDeleteConfirm).setOnClickListener(vv -> {
            taskList.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, taskList.size());
            tasksRef.document(task.getTaskId()).delete()
                    .addOnFailureListener(e ->
                            Toast.makeText(context, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            sheet.dismiss();
        });

        v.findViewById(R.id.btnKeepTask).setOnClickListener(vv -> sheet.dismiss());
        sheet.show();
    }

    /** Returns true if the given "dd-MM-yyyy" string is today */
    private boolean isTodayDate(String dateStr) {
        try {
            String[] dp = dateStr.split("-");
            int d = Integer.parseInt(dp[0]);
            int m = Integer.parseInt(dp[1]) - 1; // 0-based month
            int y = Integer.parseInt(dp[2]);
            Calendar today = Calendar.getInstance();
            return y == today.get(Calendar.YEAR)
                    && m == today.get(Calendar.MONTH)
                    && d == today.get(Calendar.DAY_OF_MONTH);
        } catch (Exception e) {
            return false;
        }
    }

    /** Converts stored "HH:mm" (24h) to "hh:mm AM/PM" for display */
    private String to12h(String time24) {
        try {
            String[] parts = time24.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            String ampm = h < 12 ? "AM" : "PM";
            int h12 = h % 12;
            if (h12 == 0) h12 = 12;
            return String.format("%02d:%02d %s", h12, m, ampm);
        } catch (Exception e) {
            return time24; // fallback: show as-is
        }
    }

    @Override
    public int getItemCount() { return taskList.size(); }

    public void updateTaskList(List<Task> newList) {
        this.taskList = newList;
        notifyDataSetChanged();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView tvTask, tvTime, tvDate, tvCategory;
        ImageView ivDelete;
        MaterialCardView cardForeground;

        TaskViewHolder(View itemView) {
            super(itemView);
            tvTask = itemView.findViewById(R.id.tvTask);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            ivDelete = itemView.findViewById(R.id.ivDelete);
            cardForeground = itemView.findViewById(R.id.cardForeground);
        }
    }
}
