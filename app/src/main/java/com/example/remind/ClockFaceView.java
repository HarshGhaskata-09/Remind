package com.example.remind;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * Compact analog stopwatch face.
 * Outer ring: 60-second sweep.
 * Inner sub-dial (bottom): 30-minute sweep.
 * Digital MM:SS.cc readout above center.
 */
public class ClockFaceView extends View {

    private final Paint pFace    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pRim     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTick    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNum     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pHand    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCenter  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDigit   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDigitSm = new Paint(Paint.ANTI_ALIAS_FLAG);

    private long elapsedMs = 0;
    private boolean dark;

    public ClockFaceView(Context ctx)                          { super(ctx); init(ctx); }
    public ClockFaceView(Context ctx, AttributeSet a)          { super(ctx, a); init(ctx); }
    public ClockFaceView(Context ctx, AttributeSet a, int d)   { super(ctx, a, d); init(ctx); }

    private void init(Context ctx) {
        dark = (ctx.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        pFace.setColor(dark ? 0xFF1E2D4E : 0xFFF2F2F2);
        pFace.setStyle(Paint.Style.FILL);

        pRim.setColor(dark ? 0xFF3A4A6A : 0xFFCCCCCC);
        pRim.setStyle(Paint.Style.STROKE);

        pTick.setStyle(Paint.Style.STROKE);
        pTick.setStrokeCap(Paint.Cap.ROUND);
        pTick.setColor(dark ? 0xFF7788AA : 0xFF999999);

        pNum.setTextAlign(Paint.Align.CENTER);
        pNum.setColor(dark ? 0xFFAABBDD : 0xFF666666);
        pNum.setTypeface(Typeface.DEFAULT);

        pHand.setStyle(Paint.Style.STROKE);
        pHand.setStrokeCap(Paint.Cap.ROUND);

        pCenter.setStyle(Paint.Style.FILL);
        pCenter.setColor(dark ? 0xFF8899BB : 0xFF888888);

        pDigit.setTextAlign(Paint.Align.CENTER);
        pDigit.setTypeface(Typeface.MONOSPACE);
        pDigit.setFakeBoldText(true);
        pDigit.setColor(dark ? 0xFFFFFFFF : 0xFF111111);

        pDigitSm.setTextAlign(Paint.Align.CENTER);
        pDigitSm.setTypeface(Typeface.MONOSPACE);
        pDigitSm.setColor(dark ? 0xFF8899BB : 0xFF888888);
    }

    public void setElapsed(long ms) {
        elapsedMs = ms;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        // Leave a small margin so the rim isn't clipped
        float R = Math.min(w, h) / 2f - 8f;

        // ── Face + rim ────────────────────────────────────────────────────────
        pRim.setStrokeWidth(R * 0.025f);
        canvas.drawCircle(cx, cy, R, pFace);
        canvas.drawCircle(cx, cy, R, pRim);

        // ── Outer tick marks ──────────────────────────────────────────────────
        for (int i = 0; i < 60; i++) {
            boolean major = (i % 5 == 0);
            float angle = (float) Math.toRadians(i * 6 - 90);
            float inner = major ? R * 0.80f : R * 0.87f;
            float outer = R * 0.94f;
            pTick.setStrokeWidth(major ? R * 0.018f : R * 0.010f);
            canvas.drawLine(
                    cx + (float) Math.cos(angle) * inner,
                    cy + (float) Math.sin(angle) * inner,
                    cx + (float) Math.cos(angle) * outer,
                    cy + (float) Math.sin(angle) * outer, pTick);
        }

        // ── Outer numbers (5,10…60) ───────────────────────────────────────────
        pNum.setTextSize(R * 0.11f);
        int[] outerNums = {60, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55};
        for (int i = 0; i < 12; i++) {
            float angle = (float) Math.toRadians(i * 30 - 90);
            float nr = R * 0.67f;
            float nx = cx + (float) Math.cos(angle) * nr;
            float ny = cy + (float) Math.sin(angle) * nr
                    - (pNum.descent() + pNum.ascent()) / 2f;
            canvas.drawText(String.valueOf(outerNums[i]), nx, ny, pNum);
        }

        // ── Sub-dial (lower center) ───────────────────────────────────────────
        float sR  = R * 0.26f;
        float sCy = cy + R * 0.32f;

        pRim.setStrokeWidth(R * 0.015f);
        canvas.drawCircle(cx, sCy, sR, pFace);
        canvas.drawCircle(cx, sCy, sR, pRim);

        for (int i = 0; i < 30; i++) {
            boolean major = (i % 5 == 0);
            float angle = (float) Math.toRadians(i * 12 - 90);
            float inner = major ? sR * 0.72f : sR * 0.83f;
            float outer = sR * 0.94f;
            pTick.setStrokeWidth(major ? R * 0.014f : R * 0.008f);
            canvas.drawLine(
                    cx + (float) Math.cos(angle) * inner,
                    sCy + (float) Math.sin(angle) * inner,
                    cx + (float) Math.cos(angle) * outer,
                    sCy + (float) Math.sin(angle) * outer, pTick);
        }

        pNum.setTextSize(sR * 0.30f);
        int[] subNums = {30, 5, 10, 15, 20, 25};
        for (int i = 0; i < 6; i++) {
            float angle = (float) Math.toRadians(i * 60 - 90);
            float nr = sR * 0.56f;
            float nx = cx + (float) Math.cos(angle) * nr;
            float ny = sCy + (float) Math.sin(angle) * nr
                    - (pNum.descent() + pNum.ascent()) / 2f;
            canvas.drawText(String.valueOf(subNums[i]), nx, ny, pNum);
        }

        // ── Time calculations ─────────────────────────────────────────────────
        long totalSec = elapsedMs / 1000;
        long cs       = (elapsedMs % 1000) / 10;   // centiseconds 0-99
        long sec      = totalSec % 60;
        long min30    = (totalSec / 60) % 30;

        float secAngle = (float) Math.toRadians(sec * 6 + cs * 0.06 - 90);
        float minAngle = (float) Math.toRadians(min30 * 12 - 90);

        // ── Second hand ───────────────────────────────────────────────────────
        pHand.setStrokeWidth(R * 0.022f);
        pHand.setColor(dark ? 0xFFCCDDFF : 0xFF444466);
        float handLen = R * 0.70f;
        float tailLen = R * 0.14f;
        canvas.drawLine(
                cx - (float) Math.cos(secAngle) * tailLen,
                cy - (float) Math.sin(secAngle) * tailLen,
                cx + (float) Math.cos(secAngle) * handLen,
                cy + (float) Math.sin(secAngle) * handLen, pHand);

        // ── Sub-dial minute hand ──────────────────────────────────────────────
        pHand.setStrokeWidth(R * 0.016f);
        pHand.setColor(dark ? 0xFFAABBDD : 0xFF666688);
        canvas.drawLine(cx, sCy,
                cx + (float) Math.cos(minAngle) * sR * 0.68f,
                sCy + (float) Math.sin(minAngle) * sR * 0.68f, pHand);

        // ── Center dots ───────────────────────────────────────────────────────
        canvas.drawCircle(cx, cy, R * 0.045f, pCenter);
        canvas.drawCircle(cx, sCy, R * 0.030f, pCenter);

        // ── Digital readout — sits between top and center ─────────────────────
        long dMin = totalSec / 60;
        long dSec = totalSec % 60;
        String main = String.format("%02d:%02d", dMin, dSec);
        String sub  = String.format(".%02d", cs);

        float digitY = cy - R * 0.22f;
        pDigit.setTextSize(R * 0.20f);
        pDigitSm.setTextSize(R * 0.14f);

        float mainW = pDigit.measureText(main);
        float subW  = pDigitSm.measureText(sub);
        float startX = cx - (mainW + subW) / 2f;

        canvas.drawText(main,
                startX + mainW / 2f,
                digitY - (pDigit.descent() + pDigit.ascent()) / 2f,
                pDigit);
        canvas.drawText(sub,
                startX + mainW + subW / 2f,
                digitY - (pDigitSm.descent() + pDigitSm.ascent()) / 2f + R * 0.025f,
                pDigitSm);
    }
}
