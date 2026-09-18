package com.example.remind;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

/**
 * Reusable bottom sheet confirmation dialog — replaces all AlertDialog.Builder usages.
 * Usage:
 *   ConfirmSheet.show(ctx, "Delete?", "This cannot be undone.", "Delete", () -> doDelete());
 */
public class ConfirmSheet {

    public interface OnConfirm { void onConfirm(); }

    public static void show(Context ctx, String title, String message,
                            String okLabel, OnConfirm onConfirm) {
        BottomSheetDialog sheet = new BottomSheetDialog(ctx, R.style.BottomSheetStyle);
        View v = LayoutInflater.from(ctx).inflate(R.layout.bottom_sheet_confirm, null);
        sheet.setContentView(v);
        sheet.setCanceledOnTouchOutside(true);

        ((TextView) v.findViewById(R.id.tvConfirmTitle)).setText(title);
        ((TextView) v.findViewById(R.id.tvConfirmMessage)).setText(message);

        MaterialButton btnOk = v.findViewById(R.id.btnConfirmOk);
        btnOk.setText(okLabel);
        btnOk.setOnClickListener(sv -> {
            sheet.dismiss();
            onConfirm.onConfirm();
        });

        v.findViewById(R.id.btnConfirmCancel).setOnClickListener(sv -> sheet.dismiss());
        sheet.show();
    }
}
