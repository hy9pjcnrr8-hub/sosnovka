package ru.ugolnyyar.nativegame;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.util.Random;

/** Падающий снег на фоне. */
public class SnowView extends View {
    private static final int N = 70;
    private final float[] x = new float[N], y = new float[N], r = new float[N], s = new float[N], d = new float[N];
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private boolean init = false;

    public SnowView(Context c) {
        super(c);
        p.setColor(Color.argb(140, 255, 255, 255));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float k = getResources().getDisplayMetrics().density;
        if (!init) {
            for (int i = 0; i < N; i++) {
                x[i] = rnd.nextFloat() * w;
                y[i] = rnd.nextFloat() * h;
                r[i] = (rnd.nextFloat() * 2f + .5f) * k;
                s[i] = (rnd.nextFloat() * .6f + .3f) * k;
                d[i] = (rnd.nextFloat() * .6f - .3f) * k;
            }
            init = true;
        }
        for (int i = 0; i < N; i++) {
            y[i] += s[i];
            x[i] += d[i];
            if (y[i] > h) { y[i] = -4; x[i] = rnd.nextFloat() * w; }
            if (x[i] < -5) x[i] = w + 4; else if (x[i] > w + 5) x[i] = -4;
            canvas.drawCircle(x[i], y[i], r[i], p);
        }
        postInvalidateOnAnimation();
    }
}
