package com.example.remind;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class MainPagerAdapter extends FragmentStateAdapter {

    // Page indices
    public static final int PAGE_HOME      = 0;
    public static final int PAGE_NOTES     = 1;   // was PAGE_ALARM
    public static final int PAGE_TIMER     = 2;
    public static final int PAGE_STOPWATCH = 3;
    public static final int PAGE_PROFILE   = 4;

    private final HomeFragment      homeFragment      = new HomeFragment();
    private final NotesFragment     notesFragment     = new NotesFragment();
    private final TimerFragment     timerFragment     = new TimerFragment();
    private final StopwatchFragment stopwatchFragment = new StopwatchFragment();
    private final ProfileFragment   profileFragment   = new ProfileFragment();

    public MainPagerAdapter(@NonNull FragmentActivity fa) {
        super(fa);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case PAGE_NOTES:     return notesFragment;
            case PAGE_TIMER:     return timerFragment;
            case PAGE_STOPWATCH: return stopwatchFragment;
            case PAGE_PROFILE:   return profileFragment;
            default:             return homeFragment;
        }
    }

    @Override
    public int getItemCount() { return 5; }

    public HomeFragment  getHomeFragment()  { return homeFragment; }
    public NotesFragment getNotesFragment() { return notesFragment; }
    public TimerFragment getTimerFragment() { return timerFragment; }
}
