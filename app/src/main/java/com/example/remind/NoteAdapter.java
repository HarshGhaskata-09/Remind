package com.example.remind;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteVH> {

    public interface OnNoteClick {
        void onClick(Note note);
        void onLongClick(Note note);
    }

    private final List<Note> notes;
    private final Context ctx;
    private final OnNoteClick listener;
    private boolean isGrid = false;

    private static final SimpleDateFormat SDF =
            new SimpleDateFormat("MMM d, yyyy  hh:mm a", Locale.getDefault());

    public NoteAdapter(List<Note> notes, Context ctx, OnNoteClick listener) {
        this.notes = notes;
        this.ctx = ctx;
        this.listener = listener;
    }

    public void setGrid(boolean grid) {
        this.isGrid = grid;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NoteVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_note, parent, false);
        return new NoteVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteVH h, int position) {
        Note note = notes.get(position);

        h.tvTitle.setText(note.getTitle() == null || note.getTitle().isEmpty()
                ? "(No title)" : note.getTitle());
        h.tvDesc.setText(note.getDescription() == null ? "" : note.getDescription());
        h.tvTime.setText(SDF.format(new Date(note.getTimestamp())));

        // Pin icon
        h.ivPin.setVisibility(note.isPinned() ? View.VISIBLE : View.GONE);

        // Reminder icon
        h.ivReminder.setVisibility(note.getReminderTime() > 0 ? View.VISIBLE : View.GONE);

        // Card background color
        try {
            String c = note.getColor();
            if (c != null && !c.isEmpty()) {
                h.card.setCardBackgroundColor(Color.parseColor(c));
            } else {
                h.card.setCardBackgroundColor(ctx.getColor(R.color.bg_card));
            }
        } catch (Exception e) {
            h.card.setCardBackgroundColor(ctx.getColor(R.color.bg_card));
        }

        // Grid mode: limit description lines
        h.tvDesc.setMaxLines(isGrid ? 3 : 6);

        h.card.setOnClickListener(v -> listener.onClick(note));
        h.card.setOnLongClickListener(v -> { listener.onLongClick(note); return true; });
    }

    @Override
    public int getItemCount() { return notes.size(); }

    static class NoteVH extends RecyclerView.ViewHolder {
        MaterialCardView card;
        TextView tvTitle, tvDesc, tvTime;
        ImageView ivPin, ivReminder;

        NoteVH(@NonNull View v) {
            super(v);
            card       = v.findViewById(R.id.cardNote);
            tvTitle    = v.findViewById(R.id.tvNoteTitle);
            tvDesc     = v.findViewById(R.id.tvNoteDesc);
            tvTime     = v.findViewById(R.id.tvNoteTime);
            ivPin      = v.findViewById(R.id.ivPin);
            ivReminder = v.findViewById(R.id.ivReminder);
        }
    }
}
