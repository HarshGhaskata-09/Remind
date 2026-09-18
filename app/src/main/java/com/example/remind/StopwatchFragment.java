package com.example.remind;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class StopwatchFragment extends Fragment {

    private ClockFaceView clockFace;
    private FloatingActionButton fabPlay, fabReset;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRunning = false;
    private long startTime = 0, elapsed = 0;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (clockFace != null)
                clockFace.setElapsed(System.currentTimeMillis() - startTime + elapsed);
            handler.postDelayed(this, 16);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stopwatch, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        clockFace = view.findViewById(R.id.clockFace);
        fabPlay   = view.findViewById(R.id.fabSwPlay);
        fabReset  = view.findViewById(R.id.fabSwReset);

        // Start at zero
        clockFace.setElapsed(0);

        fabPlay.setOnClickListener(v -> {
            if (isRunning) pause(); else start();
        });

        fabReset.setOnClickListener(v -> reset());
    }

    private void start() {
        isRunning = true;
        startTime = System.currentTimeMillis();
        fabPlay.setImageResource(android.R.drawable.ic_media_pause);
        // Show reset button when watch is running
        fabReset.setVisibility(View.VISIBLE);
        handler.post(ticker);
    }

    private void pause() {
        isRunning = false;
        elapsed += System.currentTimeMillis() - startTime;
        handler.removeCallbacks(ticker);
        fabPlay.setImageResource(android.R.drawable.ic_media_play);
        // Keep reset visible while paused so user can reset
    }

    private void reset() {
        // Stop ticker
        isRunning = false;
        handler.removeCallbacks(ticker);
        // Reset state
        elapsed = 0;
        startTime = 0;
        // Reset UI
        clockFace.setElapsed(0);
        fabPlay.setImageResource(android.R.drawable.ic_media_play);
        // Hide reset button — back to clean state
        fabReset.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(ticker);
    }
}
