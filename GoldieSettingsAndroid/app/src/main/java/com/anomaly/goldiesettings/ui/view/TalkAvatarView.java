package com.anomaly.goldiesettings.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.anomaly.goldiesettings.convai.ConvaiEmotion;
import com.anomaly.goldiesettings.convai.ConvaiStatus;

/**
 * Vector-drawn avatar for the talk page. Positions scale relative to
 * the view's actual size so the head/body stay aligned at any aspect
 * ratio.
 */
public class TalkAvatarView extends View {

    private volatile boolean male;
    private volatile boolean running;
    private volatile ConvaiStatus status = ConvaiStatus.IDLE;
    private volatile ConvaiEmotion emotion = ConvaiEmotion.NEUTRAL;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::onTick;

    private int count = 0;

    private final Paint facePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pupil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mouth = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint brow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cheek = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hair = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint collar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tear = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public TalkAvatarView(Context context) { super(context); init(); }
    public TalkAvatarView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public TalkAvatarView(Context context, @Nullable AttributeSet attrs, int d) { super(context, attrs, d); init(); }

    private void init() {
        setBackgroundColor(Color.rgb(0x14, 0x18, 0x24));
        facePaint.setColor(Color.rgb(0xFA, 0xD7, 0xBD));
        eyeOutline.setColor(Color.rgb(0x1F, 0x1F, 0x1F));
        eyeOutline.setStyle(Paint.Style.STROKE);
        eyeOutline.setStrokeWidth(dp(2.5f));
        eyeOutline.setStrokeCap(Paint.Cap.ROUND);
        eyeFill.setColor(Color.WHITE);
        pupil.setColor(Color.rgb(0x1F, 0x1F, 0x1F));
        mouth.setColor(Color.rgb(0x6E, 0x2A, 0x3A));
        mouth.setStyle(Paint.Style.STROKE);
        mouth.setStrokeCap(Paint.Cap.ROUND);
        brow.setColor(Color.rgb(0x1F, 0x1F, 0x1F));
        brow.setStyle(Paint.Style.STROKE);
        brow.setStrokeCap(Paint.Cap.ROUND);
        cheek.setColor(Color.argb(0x88, 0xF8, 0xB7, 0xC8));
        hair.setColor(Color.rgb(0x3D, 0x29, 0x14));
        bodyPaint.setColor(Color.rgb(0xA9, 0x91, 0xD8));
        collar.setColor(Color.rgb(0xE8, 0x4A, 0x8A));
        text.setColor(Color.WHITE);
        text.setFakeBoldText(true);
        tear.setColor(Color.rgb(0x6F, 0xB7, 0xE0));
    }

    public void setGender(boolean male) {
        this.male = male;
        bodyPaint.setColor(male ? Color.rgb(0x3C, 0x8D, 0x8E) : Color.rgb(0xA9, 0x91, 0xD8));
        collar.setColor(male ? Color.rgb(0xCB, 0x2C, 0x2C) : Color.rgb(0xE8, 0x4A, 0x8A));
        hair.setColor(male ? Color.rgb(0x1B, 0x1B, 0x1B) : Color.rgb(0x3D, 0x29, 0x14));
        invalidate();
    }

    public void setRunning(boolean run) {
        running = run;
        handler.removeCallbacks(tick);
        if (run) handler.postDelayed(tick, 120);
    }

    public void setStatus(ConvaiStatus s) { this.status = s; }
    public void setEmotion(ConvaiEmotion e) { this.emotion = e; }

    private void onTick() {
        if (!running) return;
        count = (count + 1) % 1000;
        invalidate();
        handler.postDelayed(tick, 120);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        canvas.drawColor(Color.rgb(0x14, 0x18, 0x24));

        // Layout in view-relative terms. Head fits in the upper half.
        float headR = Math.min(w, h) * 0.22f;
        float cx = w / 2f;
        float headCY = h * 0.34f;

        // Neck — a narrow trapezoid from chin to shoulders.
        float neckTop = headCY + headR * 0.85f;
        float neckBot = headCY + headR * 1.35f;
        float neckHW = headR * 0.30f;
        path.reset();
        path.moveTo(cx - neckHW, neckTop);
        path.lineTo(cx - neckHW * 1.15f, neckBot);
        path.lineTo(cx + neckHW * 1.15f, neckBot);
        path.lineTo(cx + neckHW, neckTop);
        path.close();
        canvas.drawPath(path, facePaint);

        // Body / shoulders — wide rounded trapezoid, fills lower half.
        float shoulderY = neckBot;
        float bodyBot = h + dp(8);
        path.reset();
        path.moveTo(cx - headR * 1.8f, bodyBot);
        path.quadTo(cx - headR * 1.5f, shoulderY + headR * 0.2f, cx, shoulderY);
        path.quadTo(cx + headR * 1.5f, shoulderY + headR * 0.2f, cx + headR * 1.8f, bodyBot);
        path.close();
        canvas.drawPath(path, bodyPaint);

        // Collar / bow / tie
        if (male) {
            float ty = shoulderY + dp(4);
            path.reset();
            path.moveTo(cx, ty);
            path.lineTo(cx - neckHW * 0.7f, ty + dp(20));
            path.lineTo(cx, ty + dp(44));
            path.lineTo(cx + neckHW * 0.7f, ty + dp(20));
            path.close();
            canvas.drawPath(path, collar);
        } else {
            float by = shoulderY + dp(14);
            float bw = dp(18);
            path.reset();
            path.moveTo(cx - bw, by);
            path.quadTo(cx - bw * 0.4f, by - dp(8), cx, by);
            path.quadTo(cx - bw * 0.4f, by + dp(8), cx - bw, by + dp(8));
            path.close();
            canvas.drawPath(path, collar);
            path.reset();
            path.moveTo(cx + bw, by);
            path.quadTo(cx + bw * 0.4f, by - dp(8), cx, by);
            path.quadTo(cx + bw * 0.4f, by + dp(8), cx + bw, by + dp(8));
            path.close();
            canvas.drawPath(path, collar);
            canvas.drawCircle(cx, by + dp(4), dp(5), collar);
        }

        // Head (drawn over neck)
        canvas.drawCircle(cx, headCY, headR, facePaint);

        // Hair back / side
        path.reset();
        if (male) {
            path.moveTo(cx - headR * 1.05f, headCY + headR * 0.3f);
            path.quadTo(cx - headR * 1.1f, headCY - headR * 0.4f, cx - headR * 0.4f, headCY - headR * 1.05f);
            path.quadTo(cx, headCY - headR * 1.15f, cx + headR * 0.4f, headCY - headR * 1.05f);
            path.quadTo(cx + headR * 1.1f, headCY - headR * 0.4f, cx + headR * 1.05f, headCY + headR * 0.3f);
            path.lineTo(cx + headR * 0.7f, headCY - headR * 0.1f);
            path.quadTo(cx, headCY - headR * 0.95f, cx - headR * 0.7f, headCY - headR * 0.1f);
            path.close();
        } else {
            // long hair frames the face down to neck
            path.moveTo(cx - headR * 1.15f, headCY + headR * 1.0f);
            path.quadTo(cx - headR * 1.25f, headCY, cx - headR * 1.05f, headCY - headR * 0.3f);
            path.quadTo(cx - headR * 0.4f, headCY - headR * 1.1f, cx, headCY - headR * 1.1f);
            path.quadTo(cx + headR * 0.4f, headCY - headR * 1.1f, cx + headR * 1.05f, headCY - headR * 0.3f);
            path.quadTo(cx + headR * 1.25f, headCY, cx + headR * 1.15f, headCY + headR * 1.0f);
            path.lineTo(cx + headR * 0.6f, headCY + headR * 0.8f);
            path.quadTo(cx + headR * 0.4f, headCY - headR * 0.1f, cx + headR * 0.2f, headCY - headR * 0.1f);
            path.lineTo(cx - headR * 0.2f, headCY - headR * 0.1f);
            path.quadTo(cx - headR * 0.4f, headCY - headR * 0.1f, cx - headR * 0.6f, headCY + headR * 0.8f);
            path.close();
        }
        canvas.drawPath(path, hair);

        // Bangs (front fringe over forehead)
        path.reset();
        float bangY = headCY - headR * 0.55f;
        float bangH = headR * 0.45f;
        if (male) {
            path.moveTo(cx - headR * 0.95f, bangY);
            path.quadTo(cx, headCY - headR * 1.05f, cx + headR * 0.95f, bangY);
            path.lineTo(cx + headR * 0.7f, bangY + bangH * 0.6f);
            path.quadTo(cx, bangY + bangH * 0.3f, cx - headR * 0.7f, bangY + bangH * 0.6f);
            path.close();
        } else {
            // swept side bangs
            path.moveTo(cx - headR * 0.95f, bangY);
            path.quadTo(cx - headR * 0.7f, headCY - headR * 1.0f, cx - headR * 0.2f, bangY + bangH * 0.4f);
            path.lineTo(cx - headR * 0.05f, bangY + bangH * 0.5f);
            path.quadTo(cx + headR * 0.4f, headCY - headR * 1.05f, cx + headR * 0.95f, bangY);
            path.lineTo(cx + headR * 0.7f, bangY + bangH * 0.6f);
            path.quadTo(cx + headR * 0.2f, bangY + bangH * 0.1f, cx - headR * 0.7f, bangY + bangH * 0.6f);
            path.close();
        }
        canvas.drawPath(path, hair);

        // Brows + eyes + mouth
        float eyeY = headCY - headR * 0.05f;
        drawBrows(canvas, cx, eyeY, headR);
        drawEyes(canvas, cx, eyeY, headR);

        // Cheeks (only when happy/neutral, not when angry)
        if (emotion == ConvaiEmotion.HAPPY || emotion == ConvaiEmotion.NEUTRAL) {
            canvas.drawCircle(cx - headR * 0.55f, headCY + headR * 0.25f, headR * 0.13f, cheek);
            canvas.drawCircle(cx + headR * 0.55f, headCY + headR * 0.25f, headR * 0.13f, cheek);
        }

        drawMouth(canvas, cx, headCY + headR * 0.5f, headR);

        // Status text
        String label;
        switch (status) {
            case LISTENING: label = "聆听中"; break;
            case THINKING: label = "思考中"; break;
            case ANSWERING: label = "回答中"; break;
            case INTERRUPTED: label = "已打断"; break;
            case ANSWER_FINISHED: label = "回答完毕"; break;
            default: label = "待机中";
        }
        text.setTextSize(dp(16));
        canvas.drawText(label, dp(16), h - dp(16), text);
    }

    private void drawBrows(Canvas canvas, float cx, float cy, float r) {
        float off = r * 0.32f;
        float dy = -r * 0.30f;
        brow.setStrokeWidth(dp(2.5f));
        path.reset();
        switch (emotion) {
            case ANGRY:
                path.moveTo(cx - off - r * 0.18f, cy + dy);
                path.lineTo(cx - off + r * 0.18f, cy + dy - r * 0.06f);
                path.moveTo(cx + off - r * 0.18f, cy + dy - r * 0.06f);
                path.lineTo(cx + off + r * 0.18f, cy + dy);
                break;
            case SAD:
                path.moveTo(cx - off - r * 0.18f, cy + dy - r * 0.06f);
                path.lineTo(cx - off + r * 0.18f, cy + dy);
                path.moveTo(cx + off - r * 0.18f, cy + dy);
                path.lineTo(cx + off + r * 0.18f, cy + dy - r * 0.06f);
                break;
            case DOUBT:
                path.moveTo(cx - off - r * 0.18f, cy + dy);
                path.lineTo(cx - off + r * 0.18f, cy + dy);
                path.moveTo(cx + off - r * 0.18f, cy + dy);
                path.lineTo(cx + off + r * 0.18f, cy + dy - r * 0.08f);
                break;
            default:
                path.moveTo(cx - off - r * 0.18f, cy + dy);
                path.lineTo(cx - off + r * 0.18f, cy + dy);
                path.moveTo(cx + off - r * 0.18f, cy + dy);
                path.lineTo(cx + off + r * 0.18f, cy + dy);
        }
        canvas.drawPath(path, brow);
    }

    private void drawEyes(Canvas canvas, float cx, float cy, float r) {
        float off = r * 0.32f;
        float eyeRx = r * 0.18f;
        float eyeRy = r * 0.24f;

        if (status == ConvaiStatus.IDLE || status == ConvaiStatus.ANSWER_FINISHED) {
            // closed sleepy eyes
            eyeOutline.setStrokeWidth(dp(3));
            path.reset();
            path.moveTo(cx - off - eyeRx, cy);
            path.quadTo(cx - off, cy + eyeRy * 0.7f, cx - off + eyeRx, cy);
            path.moveTo(cx + off - eyeRx, cy);
            path.quadTo(cx + off, cy + eyeRy * 0.7f, cx + off + eyeRx, cy);
            canvas.drawPath(path, eyeOutline);
            return;
        }
        if (status == ConvaiStatus.INTERRUPTED) {
            eyeOutline.setStrokeWidth(dp(3));
            path.reset();
            path.moveTo(cx - off - eyeRx, cy);
            path.lineTo(cx - off + eyeRx, cy);
            path.moveTo(cx + off - eyeRx, cy);
            path.lineTo(cx + off + eyeRx, cy);
            canvas.drawPath(path, eyeOutline);
            return;
        }
        if (status == ConvaiStatus.LISTENING || status == ConvaiStatus.THINKING) {
            // blink every 12 ticks
            if (count % 12 == 1) {
                eyeOutline.setStrokeWidth(dp(3));
                path.reset();
                path.moveTo(cx - off - eyeRx, cy);
                path.lineTo(cx - off + eyeRx, cy);
                path.moveTo(cx + off - eyeRx, cy);
                path.lineTo(cx + off + eyeRx, cy);
                canvas.drawPath(path, eyeOutline);
                return;
            }
        }

        if (emotion == ConvaiEmotion.HAPPY) {
            // arc smiling eyes
            eyeOutline.setStrokeWidth(dp(3));
            path.reset();
            path.moveTo(cx - off - eyeRx, cy + eyeRy * 0.15f);
            path.quadTo(cx - off, cy - eyeRy * 1.2f, cx - off + eyeRx, cy + eyeRy * 0.15f);
            path.moveTo(cx + off - eyeRx, cy + eyeRy * 0.15f);
            path.quadTo(cx + off, cy - eyeRy * 1.2f, cx + off + eyeRx, cy + eyeRy * 0.15f);
            canvas.drawPath(path, eyeOutline);
            return;
        }
        if (emotion == ConvaiEmotion.SAD) {
            // droopy oval + tear
            eyeOutline.setStrokeWidth(dp(2.5f));
            canvas.drawOval(new RectF(cx - off - eyeRx, cy - eyeRy * 0.5f, cx - off + eyeRx, cy + eyeRy * 0.5f), eyeFill);
            canvas.drawOval(new RectF(cx - off - eyeRx, cy - eyeRy * 0.5f, cx - off + eyeRx, cy + eyeRy * 0.5f), eyeOutline);
            canvas.drawOval(new RectF(cx + off - eyeRx, cy - eyeRy * 0.5f, cx + off + eyeRx, cy + eyeRy * 0.5f), eyeFill);
            canvas.drawOval(new RectF(cx + off - eyeRx, cy - eyeRy * 0.5f, cx + off + eyeRx, cy + eyeRy * 0.5f), eyeOutline);
            // small tear under left eye
            canvas.drawCircle(cx - off, cy + eyeRy * 1.3f, eyeRx * 0.35f, tear);
            return;
        }
        if (emotion == ConvaiEmotion.ANGRY) {
            eyeOutline.setStrokeWidth(dp(3));
            path.reset();
            path.moveTo(cx - off - eyeRx, cy - eyeRy * 0.3f);
            path.lineTo(cx - off + eyeRx, cy + eyeRy * 0.2f);
            path.moveTo(cx + off + eyeRx, cy - eyeRy * 0.3f);
            path.lineTo(cx + off - eyeRx, cy + eyeRy * 0.2f);
            canvas.drawPath(path, eyeOutline);
            return;
        }
        if (emotion == ConvaiEmotion.DOUBT) {
            // one wide one narrow
            eyeOutline.setStrokeWidth(dp(2.5f));
            canvas.drawOval(new RectF(cx - off - eyeRx, cy - eyeRy, cx - off + eyeRx, cy + eyeRy), eyeFill);
            canvas.drawOval(new RectF(cx - off - eyeRx, cy - eyeRy, cx - off + eyeRx, cy + eyeRy), eyeOutline);
            canvas.drawCircle(cx - off, cy + eyeRy * 0.1f, eyeRx * 0.4f, pupil);
            canvas.drawOval(new RectF(cx + off - eyeRx * 0.6f, cy - eyeRy * 0.6f, cx + off + eyeRx * 0.6f, cy + eyeRy * 0.6f), eyeFill);
            canvas.drawOval(new RectF(cx + off - eyeRx * 0.6f, cy - eyeRy * 0.6f, cx + off + eyeRx * 0.6f, cy + eyeRy * 0.6f), eyeOutline);
            return;
        }
        // NEUTRAL
        eyeOutline.setStrokeWidth(dp(2.5f));
        canvas.drawOval(new RectF(cx - off - eyeRx, cy - eyeRy, cx - off + eyeRx, cy + eyeRy), eyeFill);
        canvas.drawOval(new RectF(cx - off - eyeRx, cy - eyeRy, cx - off + eyeRx, cy + eyeRy), eyeOutline);
        canvas.drawOval(new RectF(cx + off - eyeRx, cy - eyeRy, cx + off + eyeRx, cy + eyeRy), eyeFill);
        canvas.drawOval(new RectF(cx + off - eyeRx, cy - eyeRy, cx + off + eyeRx, cy + eyeRy), eyeOutline);
        // pupils
        float px = 0, py = 0;
        if (status == ConvaiStatus.LISTENING) {
            py = -eyeRy * 0.2f;
        } else if (status == ConvaiStatus.THINKING) {
            py = eyeRy * 0.3f;
            px = eyeRx * 0.25f * ((count % 4) - 1.5f);
        }
        canvas.drawCircle(cx - off + px, cy + py, eyeRx * 0.45f, pupil);
        canvas.drawCircle(cx + off + px, cy + py, eyeRx * 0.45f, pupil);
    }

    private void drawMouth(Canvas canvas, float cx, float my, float r) {
        float mw = r * 0.40f;
        mouth.setStrokeWidth(dp(3));
        path.reset();
        switch (emotion) {
            case HAPPY:
                path.moveTo(cx - mw, my);
                path.quadTo(cx, my + r * 0.25f, cx + mw, my);
                break;
            case SAD:
                path.moveTo(cx - mw, my + r * 0.10f);
                path.quadTo(cx, my - r * 0.10f, cx + mw, my + r * 0.10f);
                break;
            case ANGRY:
                path.moveTo(cx - mw, my);
                path.quadTo(cx, my - r * 0.20f, cx + mw, my);
                break;
            case DOUBT:
                path.moveTo(cx - mw * 0.5f, my);
                path.lineTo(cx + mw * 0.5f, my);
                break;
            default:
                path.moveTo(cx - mw * 0.8f, my);
                path.quadTo(cx, my + r * 0.10f, cx + mw * 0.8f, my);
        }
        canvas.drawPath(path, mouth);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
