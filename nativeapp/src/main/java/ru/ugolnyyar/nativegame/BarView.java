package ru.ugolnyyar.nativegame;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Полоска прогресса «жители / 50 000». */
public class BarView extends View {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float fraction = 0f, shown = 0f;

    public BarView(Context c) {
        super(c);
        bg.setColor(Color.parseColor("#1f2840"));
        fg.setColor(Color.parseColor("#4fd17a"));
    }

    public void setFraction(float f) {
        fraction = f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float h = getHeight(), w = getWidth(), rad = h / 2f;
        r.set(0, 0, w, h);
        canvas.drawRoundRect(r, rad, rad, bg);
        shown += (fraction - shown) * 0.15f;
        if (Math.abs(fraction - shown) > 0.0005f) postInvalidateOnAnimation(); else shown = fraction;
        if (shown > 0) {
            r.set(0, 0, Math.max(h, w * shown), h);
            canvas.drawRoundRect(r, rad, rad, fg);
        }
    }
}
