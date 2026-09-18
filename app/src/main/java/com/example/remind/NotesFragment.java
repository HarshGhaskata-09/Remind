package com.example.remind;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotesFragment extends Fragment {

    private RecyclerView rvNotes;
    private NoteAdapter adapter;
    private LinearLayout emptyView;
    private ImageButton btnToggleGrid;

    private final List<Note> allNotes      = new ArrayList<>(); // full list from Firestore
    private final List<Note> filteredNotes = new ArrayList<>(); // shown in RecyclerView

    private CollectionReference notesRef;
    private boolean isGrid = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvNotes        = view.findViewById(R.id.rvNotes);
        emptyView      = view.findViewById(R.id.emptyNotes);
        btnToggleGrid  = view.findViewById(R.id.btnToggleGrid);
        TextInputEditText etSearch = view.findViewById(R.id.etSearch);

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        notesRef = FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("notes");

        // Adapter
        adapter = new NoteAdapter(filteredNotes, requireContext(), new NoteAdapter.OnNoteClick() {
            @Override
            public void onClick(Note note) {
                openAddEdit(note);
            }
            @Override
            public void onLongClick(Note note) {
                confirmDelete(note);
            }
        });

        rvNotes.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvNotes.setAdapter(adapter);

        // Swipe gestures
        attachSwipe();

        // Grid toggle
        btnToggleGrid.setOnClickListener(v -> {
            isGrid = !isGrid;
            adapter.setGrid(isGrid);
            rvNotes.setLayoutManager(isGrid
                    ? new GridLayoutManager(requireContext(), 2)
                    : new LinearLayoutManager(requireContext()));
        });

        // Search
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Real-time Firestore listener
        loadNotes();
    }

    /** Open AddEditNoteActivity — null note = add mode */
    public void openAddEdit(@Nullable Note note) {
        Intent i = new Intent(requireContext(), AddEditNoteActivity.class);
        if (note != null) i.putExtra("noteId", note.getId());
        startActivity(i);
    }

    // ── Firestore ──────────────────────────────────────────────────

    private void loadNotes() {
        notesRef.addSnapshotListener((snapshots, e) -> {
            if (e != null || snapshots == null) return;
            allNotes.clear();
            for (QueryDocumentSnapshot doc : snapshots) {
                Note n = doc.toObject(Note.class);
                if (n.getId() == null) n.setId(doc.getId());
                allNotes.add(n);
            }
            // Sort: pinned first, then newest
            Collections.sort(allNotes, (a, b) -> {
                if (a.isPinned() != b.isPinned()) return a.isPinned() ? -1 : 1;
                return Long.compare(b.getTimestamp(), a.getTimestamp());
            });
            // Re-apply current search filter
            TextInputEditText et = getView() == null ? null : getView().findViewById(R.id.etSearch);
            String q = (et != null && et.getText() != null) ? et.getText().toString() : "";
            filter(q);
        });
    }

    private void filter(String query) {
        filteredNotes.clear();
        String q = query.toLowerCase().trim();
        for (Note n : allNotes) {
            if (q.isEmpty()
                    || (n.getTitle() != null && n.getTitle().toLowerCase().contains(q))
                    || (n.getDescription() != null && n.getDescription().toLowerCase().contains(q))) {
                filteredNotes.add(n);
            }
        }
        adapter.notifyDataSetChanged();
        updateEmpty();
    }

    private void updateEmpty() {
        boolean empty = filteredNotes.isEmpty();
        rvNotes.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    // ── Delete ─────────────────────────────────────────────────────

    private void confirmDelete(Note note) {
        ConfirmSheet.show(requireContext(),
                "Delete note?",
                "This note will be permanently deleted.",
                "Delete",
                () -> deleteNote(note));
    }

    private void deleteNote(Note note) {
        notesRef.document(note.getId()).delete()
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Delete failed", Toast.LENGTH_SHORT).show());
    }

    // ── Swipe gestures ─────────────────────────────────────────────

    private void attachSwipe() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) { return false; }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                int pos = vh.getAdapterPosition();
                if (pos < 0 || pos >= filteredNotes.size()) return;
                Note note = filteredNotes.get(pos);

                if (dir == ItemTouchHelper.RIGHT) {
                    // Swipe right → pin/unpin
                    boolean newPin = !note.isPinned();
                    notesRef.document(note.getId()).update("pinned", newPin)
                            .addOnFailureListener(e ->
                                    Toast.makeText(requireContext(), "Failed", Toast.LENGTH_SHORT).show());
                    Toast.makeText(requireContext(),
                            newPin ? "Note pinned" : "Note unpinned", Toast.LENGTH_SHORT).show();
                    adapter.notifyItemChanged(pos); // restore card
                } else {
                    // Swipe left → delete
                    confirmDelete(note);
                    adapter.notifyItemChanged(pos); // restore card while dialog shows
                }
            }
        }).attachToRecyclerView(rvNotes);
    }
}
