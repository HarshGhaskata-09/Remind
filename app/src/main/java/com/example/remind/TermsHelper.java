package com.example.remind;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

public class TermsHelper {

    private static final String TERMS_TEXT =
        "1. Usage\n" +
        "This app is provided for personal task management. You agree to use it responsibly.\n\n" +
        "2. Data\n" +
        "Your tasks and profile data are stored securely in Firebase. We do not share your data with third parties.\n\n" +
        "3. Account\n" +
        "You are responsible for maintaining the security of your account credentials.\n\n" +
        "4. Privacy\n" +
        "We collect only the information necessary to provide the service (name, phone, email). " +
        "This data is used solely for account management.\n\n" +
        "5. Changes\n" +
        "We reserve the right to update these terms at any time. Continued use of the app constitutes acceptance.\n\n" +
        "6. Contact\n" +
        "For any questions, contact us through the app settings.";

    public static void show(Context ctx) {
        BottomSheetDialog sheet = new BottomSheetDialog(ctx, R.style.BottomSheetStyle);

        ScrollView sv = new ScrollView(ctx);
        sv.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout ll = new LinearLayout(ctx);
        ll.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 24);
        ll.setPadding(pad, pad, pad, dp(ctx, 48));
        ll.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_bottom_sheet));

        // Drag handle
        LinearLayout handleWrap = new LinearLayout(ctx);
        handleWrap.setGravity(Gravity.CENTER);
        handleWrap.setPadding(0, 0, 0, dp(ctx, 8));
        android.view.View handle = new android.view.View(ctx);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 4));
        handle.setLayoutParams(hlp);
        handle.setBackgroundColor(ctx.getColor(R.color.divider));
        handleWrap.addView(handle);
        ll.addView(handleWrap);

        // Title
        TextView title = new TextView(ctx);
        title.setText("Terms & Conditions");
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ctx.getColor(R.color.text_primary));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(ctx, 8), 0, dp(ctx, 16));
        ll.addView(title);

        // Divider
        android.view.View divider = new android.view.View(ctx);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1));
        dlp.bottomMargin = dp(ctx, 16);
        divider.setLayoutParams(dlp);
        divider.setBackgroundColor(ctx.getColor(R.color.divider));
        ll.addView(divider);

        // Content
        TextView content = new TextView(ctx);
        content.setText(TERMS_TEXT);
        content.setTextSize(14);
        content.setTextColor(ctx.getColor(R.color.text_secondary));
        content.setLineSpacing(dp(ctx, 4), 1f);
        ll.addView(content);

        sv.addView(ll);
        sheet.setContentView(sv);
        sheet.show();
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }
}
