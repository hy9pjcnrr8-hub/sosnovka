package ru.ugolnyyar.nativegame;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * Нарисованная панорама Угольного Яра. Каждый кадр читает состояние игры
 * из MainActivity и анимирует изменения: камера подлетает к тому, что
 * изменилось, здания растут, загораются огни, идёт дым.
 * Мир: 1000 × 420 условных единиц, земля на y = 340.
 */
public class CityView extends View {
    static final float WW = 1000, WH = 420, GROUND = 340;
    final MainActivity g;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final Path path = new Path();
    final Random rnd = new Random();
    float cw, ch, base = 1, T = 0, flash = 0, ga = 1f;
    long lastNs = 0;
    // камера
    float camX = 520, camY = 225, camZ = 1, tx = 520, ty = 225, tz = 1, until = 0;
    final Map<String, String> keys = new HashMap<>();
    final Map<String, Float> anims = new HashMap<>();
    final Map<String, Long> emitT = new HashMap<>();
    final ArrayList<float[]> glows = new ArrayList<>();
    final ArrayList<Part> parts = new ArrayList<>();
    final ArrayList<Floater> floats = new ArrayList<>();
    final ArrayList<float[]> snow = new ArrayList<>();
    final Map<Integer, Bitmap> glowCache = new HashMap<>();
    final Map<String, Integer> colors = new HashMap<>();
    final RectF rf = new RectF();
    boolean dirtyReset = true;

    static final int WARM = 0xFFFFC86E, HOT = 0xFFFF9646, COLD = 0xFF8CC8FF, REDL = 0xFFFF463C, GREENL = 0xFF6EFFAA, WHITE = 0xFFFFF5E1;

    static class Part { int type; float x, y, p, vx, vy, r, gr, life, t; int col; }
    static class Floater { String s; int c; float t; }

    public CityView(Context c, MainActivity game) {
        super(c);
        g = game;
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    // ───────────── API для игры (вызывать в UI-потоке) ─────────────
    public void reset() { keys.clear(); anims.clear(); parts.clear(); floats.clear(); emitT.clear(); until = 0; }
    public void focus(String key, float ms) {
        float[] s = spotFor(key);
        tx = 500 + (s[0] - 500) / s[2]; ty = s[1]; tz = s[3]; until = T + ms / 1000f;
    }
    public void addFloat(String s, int c) {
        Floater f = new Floater(); f.s = s; f.c = c; floats.add(f);
        if (floats.size() > 4) floats.remove(0);
    }

    // ───────────── утилиты ─────────────
    static float hash(int n) {
        n = (n << 13) ^ n;
        int v = n * (n * n * 15731 + 789221) + 1376312589;
        return (v & 0x7fffffff) / (float) 0x7fffffff;
    }
    int C(String hex) {
        Integer v = colors.get(hex);
        if (v == null) { v = Color.parseColor(hex); colors.put(hex, v); }
        return v;
    }
    int A(int c) { // применить глобальную прозрачность
        if (ga >= 1f) return c;
        return (Math.round(Color.alpha(c) * Math.max(0, ga)) << 24) | (c & 0x00FFFFFF);
    }
    boolean every(String id, float sec) {
        long n = (long) Math.floor(T / sec);
        Long o = emitT.get(id);
        if (o == null || o != n) { emitT.put(id, n); return true; }
        return false;
    }
    float prog(String key, float dur) {
        Float t0 = anims.get(key); if (t0 == null) return 1;
        float t = (T - t0) / dur; if (t >= 1) return 1; if (t < 0) return 0;
        return 1 - (float) Math.pow(1 - t, 3);
    }
    void R(float x, float y, float w, float h, int c) { p.setStyle(Paint.Style.FILL); p.setColor(A(c)); cv.drawRect(x, y, x + w, y + h, p); }
    void R(float x, float y, float w, float h, String c) { R(x, y, w, h, C(c)); }
    void poly(int c, float... pts) {
        path.reset(); path.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) path.lineTo(pts[i], pts[i + 1]);
        path.close(); p.setStyle(Paint.Style.FILL); p.setColor(A(c)); cv.drawPath(path, p);
    }
    void poly(String c, float... pts) { poly(C(c), pts); }
    void circ(float x, float y, float r, int c) { p.setStyle(Paint.Style.FILL); p.setColor(A(c)); cv.drawCircle(x, y, r, p); }
    void circ(float x, float y, float r, String c) { circ(x, y, r, C(c)); }
    void line(float x1, float y1, float x2, float y2, float w, int c) {
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(A(c)); cv.drawLine(x1, y1, x2, y2, p); p.setStyle(Paint.Style.FILL);
    }
    void line(float x1, float y1, float x2, float y2, float w, String c) { line(x1, y1, x2, y2, w, C(c)); }
    void text(String s, float x, float y, float size, String c) {
        p.setStyle(Paint.Style.FILL); p.setColor(A(C(c))); p.setTextSize(size); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = p.getFontMetrics();
        cv.drawText(s, x, y - (fm.ascent + fm.descent) / 2, p);
    }
    void glow(float x, float y, float r, int col, float a, float pl) { glows.add(new float[]{x, y, r, Float.intBitsToFloat(col), a, pl}); }

    Bitmap glowSprite(int col) {
        Bitmap b = glowCache.get(col);
        if (b != null) return b;
        b = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int r = Color.red(col), gg = Color.green(col), bb = Color.blue(col);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        q.setShader(new RadialGradient(64, 64, 64,
                new int[]{Color.argb(255, r, gg, bb), Color.argb(140, r, gg, bb), Color.argb(30, r, gg, bb), Color.argb(0, r, gg, bb)},
                new float[]{0, .18f, .5f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 128, 128, q);
        glowCache.put(col, b);
        return b;
    }

    void puff(float x, float y, float pl, int col, float big, float vy) {
        Part q = new Part(); q.type = 0; q.x = x; q.y = y; q.p = pl; q.vx = 4 + rnd.nextFloat() * 4; q.vy = vy - rnd.nextFloat() * 6;
        q.r = (3 + rnd.nextFloat() * 2) * big; q.gr = 6 * big; q.life = 4 + rnd.nextFloat() * 2; q.col = col; parts.add(q);
    }
    void sparkle(float x, float y, float pl) {
        for (int i = 0; i < 26; i++) {
            double a = rnd.nextFloat() * 6.28; float s = 20 + rnd.nextFloat() * 60;
            Part q = new Part(); q.type = 1; q.x = x; q.y = y; q.p = pl; q.vx = (float) Math.cos(a) * s; q.vy = (float) Math.sin(a) * s - 30;
            q.life = .8f + rnd.nextFloat() * .7f; q.r = 1 + rnd.nextFloat() * 1.4f; parts.add(q);
        }
        for (int i = 0; i < 10; i++) {
            Part q = new Part(); q.type = 2; q.x = x + (rnd.nextFloat() - .5f) * 60; q.y = y + 30; q.p = pl; q.vx = (rnd.nextFloat() - .5f) * 30; q.vy = -10 - rnd.nextFloat() * 10;
            q.r = 5 + rnd.nextFloat() * 6; q.life = 1.6f; parts.add(q);
        }
    }

    // ───────────── сезон ─────────────
    int month() { Calendar c = Calendar.getInstance(); c.set(2026, Calendar.NOVEMBER, 13); c.add(Calendar.DAY_OF_YEAR, g.day); return c.get(Calendar.MONTH); }
    boolean snowy, summer; int mon;

    // ───────────── состояние → ключи ─────────────
    Map<String, String> stateKeys() {
        Map<String, String> k = new HashMap<>();
        k.put("boiler", g.heat == null ? "off" : g.heat);
        k.put("road", String.valueOf(g.roadSet ? g.road : -1));
        k.put("light", g.light == null ? "off" : g.light);
        k.put("mine", (g.mine == null ? "?" : g.mine) + (g.mineOpen ? "+open" : ""));
        k.put("vent", g.ventLit ? "on" : "off");
        k.put("matvey", g.matveyVis ? "on" : "off");
        k.put("water", (g.water == null ? "off" : g.water) + (g.frozen ? "+ice" : ""));
        k.put("comm", g.comm == null ? "off" : g.comm);
        k.put("shops", g.shops == null ? "off" : g.shops);
        k.put("lift", g.lift == null || g.lift.equals("none") ? "off" : g.lift);
        k.put("ratrak", g.ratrak ? "on" : "off");
        k.put("hotel", g.hotel ? "on" : "off");
        k.put("renov", g.housingChoice == null ? "off" : g.housingChoice);
        int nF = Math.min(40, Math.max(12, g.factories)), nB = Math.max(12, g.blocks);
        for (int i = 0; i < nF; i++) k.put("fac" + i, i < g.factories ? "on" : "off");
        for (int i = 0; i < nB; i++) k.put("blk" + i, i < g.blocks ? "on" : "off");
        k.put("rail", g.rail == null ? "off" : g.rail); k.put("energy", g.energy == null ? "off" : g.energy); k.put("airport", g.airport == null ? "off" : g.airport);
        k.put("uni", g.uni ? "on" : "off"); k.put("med", g.med ? "on" : "off"); k.put("tram", g.tram ? "on" : "off");
        k.put("museum", g.museum ? "on" : (g.demolished ? "gone" : "off")); k.put("mineFate", g.mineFate == null ? "off" : g.mineFate);
        k.put("cup", g.cup ? "on" : "off"); k.put("filters", g.filters ? "on" : "off");
        for (int i = 0; i < 24; i++) k.put("mic" + i, i < g.micro ? "on" : "off");
        for (int i = 0; i < 12; i++) { k.put("sch" + i, i < g.schools ? "on" : "off"); k.put("hos" + i, i < g.hospitals ? "on" : "off"); k.put("par" + i, i < g.parks ? "on" : "off"); k.put("mal" + i, i < g.malls ? "on" : "off"); }
        k.put("sky", g.pop >= 200000 ? String.valueOf(Math.min(9, (g.pop - 200000) / 100000 + 1)) : "off");
        return k;
    }

    static final Map<String, float[]> SPOTS = new HashMap<>();
    static {
        SPOTS.put("boiler", new float[]{282, 250, 1, 1.9f}); SPOTS.put("road", new float[]{170, 360, 1, 1.5f});
        SPOTS.put("light", new float[]{330, 300, 1, 1.7f}); SPOTS.put("mine", new float[]{95, 250, 1, 1.8f});
        SPOTS.put("vent", new float[]{160, 315, 1, 2.6f}); SPOTS.put("matvey", new float[]{160, 320, 1, 2.8f});
        SPOTS.put("water", new float[]{205, 270, 1, 1.9f}); SPOTS.put("comm", new float[]{690, 220, .95f, 1.5f});
        SPOTS.put("shops", new float[]{420, 320, 1, 2.0f}); SPOTS.put("lift", new float[]{770, 210, .55f, 1.4f});
        SPOTS.put("ratrak", new float[]{770, 250, .55f, 1.6f}); SPOTS.put("hotel", new float[]{915, 300, 1, 1.9f});
        SPOTS.put("renov", new float[]{440, 280, 1, 1.4f}); SPOTS.put("town", new float[]{520, 260, 1, 1.0f});
        SPOTS.put("wide", new float[]{540, 235, 1, .62f}); SPOTS.put("rail", new float[]{-130, 340, 1, 1.4f});
        SPOTS.put("energy", new float[]{-400, 240, .8f, 1.2f}); SPOTS.put("hydro", new float[]{-280, 290, .7f, 1.4f}); SPOTS.put("wind", new float[]{200, 200, .35f, 1.0f});
        SPOTS.put("airport", new float[]{-80, 190, .5f, 1.1f}); SPOTS.put("uni", new float[]{1210, 280, 1, 1.5f}); SPOTS.put("med", new float[]{1060, 290, 1, 1.7f});
        SPOTS.put("north", new float[]{1450, 220, .9f, 1.0f}); SPOTS.put("tram", new float[]{500, 330, 1, 1.4f}); SPOTS.put("museum", new float[]{95, 230, 1, 1.5f});
        SPOTS.put("mineFate", new float[]{95, 260, 1, 1.6f}); SPOTS.put("cup", new float[]{770, 200, .55f, 1.3f}); SPOTS.put("filters", new float[]{400, 220, .78f, 1.0f});
        SPOTS.put("sky", new float[]{1400, 200, .84f, .9f});
    }
    float[] spotFor(String k) {
        if (k.equals("energy") && g.energy != null && !g.energy.equals("tec")) k = g.energy;
        float[] s = SPOTS.get(k);
        if (s != null) return s;
        if (k.startsWith("fac")) { float[] f = facPos(Integer.parseInt(k.substring(3))); return new float[]{f[0], 270, .78f, 1.3f}; }
        if (k.startsWith("blk")) { float[] b = blkPos(Integer.parseInt(k.substring(3))); return new float[]{b[0] + b[1] / 2, 260, .88f, 1.35f}; }
        if (k.startsWith("mic")) return new float[]{micPos(Integer.parseInt(k.substring(3))) + 60, 220, .9f, 1.1f};
        if (k.startsWith("sch")) return new float[]{SCH[Integer.parseInt(k.substring(3)) % 12] + 30, 300, 1, 1.8f};
        if (k.startsWith("hos")) return new float[]{HOS[Integer.parseInt(k.substring(3)) % 12] + 35, 300, 1, 1.8f};
        if (k.startsWith("par")) return new float[]{PAR[Integer.parseInt(k.substring(3)) % 12], 290, .97f, 1.6f};
        if (k.startsWith("mal")) return new float[]{MAL[Integer.parseInt(k.substring(3)) % 12] + 40, 300, 1, 1.7f};
        return SPOTS.get("town");
    }

    // ───────────── слои ─────────────
    Canvas cv;
    int baseSave;
    void layer(float pl) {
        cv.restoreToCount(baseSave);
        baseSave = cv.save();
        float S = base * camZ, cx = 500 + (camX - 500) * pl;
        cv.translate(cw / 2 - cx * S, ch / 2 - camY * S);
        cv.scale(S, S);
    }
    void screen() { cv.restoreToCount(baseSave); baseSave = cv.save(); }
    float[] toScreen(float x, float y, float pl) {
        float S = base * camZ, cx = 500 + (camX - 500) * pl;
        return new float[]{cw / 2 + (x - cx) * S, ch / 2 + (y - camY) * S};
    }

    LinearGradient skyG; int skyKey = -1;
    void drawSky() {
        screen();
        int key = (summer ? 1 : 0) + (snowy ? 2 : 0) + ((int) ch << 2);
        if (key != skyKey) {
            skyKey = key;
            if (summer) skyG = new LinearGradient(0, 0, 0, ch, new int[]{C("#151a3d"), C("#4a3566"), C("#c46a52"), C("#f0a36a")}, new float[]{0, .55f, .85f, 1}, Shader.TileMode.CLAMP);
            else if (snowy) skyG = new LinearGradient(0, 0, 0, ch, new int[]{C("#070b1d"), C("#16224a"), C("#33467c")}, new float[]{0, .6f, 1}, Shader.TileMode.CLAMP);
            else skyG = new LinearGradient(0, 0, 0, ch, new int[]{C("#0d1230"), C("#2b2f5e"), C("#7a5a7a")}, new float[]{0, .6f, 1}, Shader.TileMode.CLAMP);
        }
        p.setShader(skyG); p.setStyle(Paint.Style.FILL); p.setColor(Color.BLACK); cv.drawRect(0, 0, cw, ch, p); p.setShader(null);
        for (int i = 0; i < 90; i++) {
            float x = (hash(i * 7 + 1) * 1.4f - .2f) * cw - (camX - 500) * .03f * base, y = hash(i * 13 + 5) * ch * .55f;
            float a = .25f + .75f * Math.abs((float) Math.sin(T * (.5f + hash(i) * 1.5f) + i));
            ga = a * (summer ? .5f : 1); circ(x, y, hash(i * 3) * 1.1f * dp + .3f * dp, Color.WHITE);
        }
        ga = 1;
        if (snowy) {
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
            for (int k = 0; k < 3; k++) {
                float yb = ch * (.16f + k * .06f);
                path.reset(); path.moveTo(0, yb);
                for (float x = 0; x <= cw; x += 12) path.lineTo(x, yb + (float) Math.sin(x * .012f + T * .25f + k * 1.7f) * 14 * dp + (float) Math.sin(x * .03f - T * .4f) * 5 * dp);
                path.lineTo(cw, yb + 60 * dp); path.lineTo(0, yb + 60 * dp); path.close();
                p.setShader(new LinearGradient(0, yb - 10, 0, yb + 60 * dp, Color.argb((int) (255 * (.10f - k * .025f)), 90, 255, 170), Color.argb(0, 60, 140, 255), Shader.TileMode.CLAMP));
                cv.drawPath(path, p);
            }
            p.setShader(null); p.setXfermode(null);
        }
        float mx = cw * .82f - (camX - 500) * .04f * base, my = ch * .17f, mr = 70 * dp;
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        p.setShader(new RadialGradient(mx, my, mr, Color.argb(90, 255, 240, 200), Color.argb(0, 255, 240, 200), Shader.TileMode.CLAMP));
        cv.drawCircle(mx, my, mr, p);
        p.setShader(null); p.setXfermode(null);
        circ(mx, my, 14 * dp, "#f6efd6");
        circ(mx + 6 * dp, my - 4 * dp, 12 * dp, summer ? "#2a2850" : "#121a3c");
    }

    void drawFarHills() {
        layer(.3f);
        poly(snowy ? "#2a3763" : "#2a2e52", -1400, 340, -1100, 250, -800, 280, -600, 240, -400, 230, -200, 270, 0, 210, 220, 260, 420, 205, 640, 255, 860, 200, 1100, 250, 1400, 215, 1700, 260, 2000, 230, 2400, 270, 2400, 400, -1400, 400);
        if (snowy) {
            ga = .35f;
            poly("#cfd8ea", -400, 230, -360, 245, -330, 240, -300, 250, -200, 270, -280, 255);
            poly("#cfd8ea", 420, 205, 450, 220, 480, 216, 500, 228, 460, 225);
            poly("#cfd8ea", 860, 200, 890, 215, 920, 210, 950, 222, 900, 220);
            ga = 1;
        }
    }

    void drawMountain() {
        layer(.55f);
        poly(snowy ? "#33416e" : "#2f3a4e", 520, 340, 700, 210, 760, 180, 860, 92, 960, 170, 1050, 230, 1200, 340);
        if (snowy) {
            poly("#e6ecf6", 780, 160, 860, 92, 940, 155, 905, 150, 880, 170, 850, 150, 820, 172);
            ga = .55f; poly("#dfe7f3", 845, 110, 875, 110, 820, 340, 735, 340);
            ga = .25f; for (int k = 0; k < 6; k++) line(850 + k * 3, 115, 760 + k * 10, 338, .6f, "#9fb0cc");
            ga = 1;
        } else poly("#3d5a45", 845, 110, 875, 110, 820, 340, 735, 340);
        for (int i = 0; i < 46; i++) {
            float x = 560 + hash(i * 17) * 620;
            float top = 340 - (Math.abs(x - 860) < 260 ? (260 - Math.abs(x - 860)) * .85f : 0);
            if (x > 735 && x < 880 && hash(i * 3) > .2f) continue;
            float y = top + 20 + hash(i * 29) * Math.max(10, 340 - top - 30), h = 10 + hash(i * 5) * 9;
            if (y > 338) continue;
            poly("#132033", x, y - h, x - h * .45f, y, x + h * .45f, y);
            if (snowy) poly("#d7e0ee", x, y - h, x - h * .2f, y - h * .5f, x + h * .2f, y - h * .5f);
        }
        boolean hasLift = g.lift != null && !g.lift.equals("none");
        float lp = hasLift ? prog("lift", 2.2f) : 0;
        if (lp > 0) {
            float x1 = 790, y1 = 336, x2 = 868, y2 = 102;
            float ex = x1 + (x2 - x1) * lp, ey = y1 + (y2 - y1) * lp;
            for (int i = 0; i <= 5; i++) {
                float t = i / 5f; if (t > lp) break;
                float px = x1 + (x2 - x1) * t, py = y1 + (y2 - y1) * t;
                line(px, py, px, py - 18, 2.2f, "#565f78"); line(px - 6, py - 18, px + 6, py - 18, 2, "#565f78");
            }
            line(x1 - 4, y1 - 18, ex - 4, ey - 18, .8f, "#9aa4bd"); line(x1 + 4, y1 - 18, ex + 4, ey - 18, .8f, "#9aa4bd");
            if (lp >= 1) {
                for (int i = 0; i < 10; i++) {
                    float t = (T * .03f + i / 10f) % 1; boolean up = i % 2 == 0;
                    float tt = up ? t : 1 - t, dx = up ? -4 : 4;
                    float px = x1 + (x2 - x1) * tt + dx, py = y1 + (y2 - y1) * tt - 18;
                    line(px, py, px, py + 6, .6f, "#9aa4bd"); R(px - 2.5f, py + 6, 5, 2.4f, up ? "#e04a3c" : "#f4c84a");
                }
                R(x1 - 10, 326, 22, 14, "#3a2f2a"); R(x2 - 9, 94, 18, 10, "#3a2f2a");
                glow(x1, 330, 30, WARM, .5f, .55f); glow(x2, 98, 22, WARM, .4f, .55f);
                int crowd = Math.min(16, 3 + g.pop / 2500);
                String[] cs = {"#e04a3c", "#2c7be5", "#f4c84a", "#3ccf7a"};
                for (int i = 0; i < crowd; i++) {
                    float t = (T * (.035f + hash(i) * .02f) + hash(i * 9)) % 1;
                    float yy = 112 + t * 220, half = 12 + t * 40;
                    float xx = 860 - t * 75 + (float) Math.sin(t * 22 + i) * half * .6f;
                    R(xx - 1, yy - 4, 2, 4, cs[i % 4]); circ(xx, yy - 5, 1.1f, "#f1c7a5");
                }
            }
        }
        if (g.cup) { float cp = prog("cup", 2); for (int i = 0; i < 14; i++) { float t = i / 13f; glow(860 - t * 85 + 8, 112 + t * 225, 10, WHITE, .8f * cp, .55f); } }
        if (g.ratrak && snowy) {
            float rp = prog("ratrak", 1.5f), t = (T * .02f) % 1, yy = 300 - Math.abs((float) Math.sin(t * Math.PI)) * 150;
            float xx = 790 + (yy - 300) * -.25f;
            ga = rp;
            R(xx - 8, yy - 6, 16, 6, "#d63a2f"); R(xx - 4, yy - 10, 8, 4, "#e9eef6"); R(xx - 9, yy, 18, 3, "#1b1f2a");
            glow(xx + 8, yy - 3, 14, WHITE, .6f, .55f);
            ga = 1;
        }
    }

    static final float[][] FAC = {{300, 86, 70}, {440, 96, 78}, {180, 80, 64}, {580, 92, 80}, {720, 88, 72}, {60, 82, 66},
            {860, 90, 76}, {370, 70, 60}, {650, 74, 62}, {980, 84, 70}, {-60, 80, 64}, {520, 66, 58}};
    void drawFactories() {
        layer(.78f);
        int nFac = Math.min(g.factories, 40);
        for (int i = 0; i < nFac; i++) {
            float[] fp = facPos(i); float x = fp[0], w = fp[1], h = fp[2];
            float pr = prog("fac" + i, 2), hh = h * pr, top = GROUND - 40 - hh;
            R(x, top, w, hh, "#2b3350");
            for (int k = 0; k < w; k += 14) poly("#39436a", x + k, top, x + k + 14, top - 8 * pr, x + k + 14, top);
            for (int r = 0; r < 2; r++) for (int k = 6; k < w - 6; k += 9) {
                boolean lit = hash(i * 97 + k + r * 31) > .25f;
                R(x + k, top + 10 + r * 14, 6, 6, lit ? "#ffd27a" : "#1a2036");
                if (lit && hash(i + k) > .7f) glow(x + k + 3, top + 13 + r * 14, 10, WARM, .35f, .78f);
            }
            float sx = x + w - 14, sh = (70 + (i % 3) * 16) * pr;
            R(sx, top - sh, 8, sh, "#6a2f2b");
            for (int q = 6; q < sh - 4; q += 16) R(sx, top - sh + q, 8, 4, q % 32 < 16 ? "#e3e3e3" : "#c0392b");
            if (w > 80) {
                float s2 = sx - 22, h2 = sh * .7f; R(s2, top - h2, 7, h2, "#5c2b28");
                if (pr >= 1 && every("f2" + i, .7f + i * .03f)) puff(s2 + 3.5f, top - h2 - 2, .78f, Color.rgb(190, 196, 212), 1, -9);
            }
            if (pr >= 1 && every("f" + i, .5f + i * .03f)) puff(sx + 4, top - sh - 2, .78f, Color.rgb(200, 205, 220), 1.6f, -12);
            glow(sx + 4, top - sh, 10, REDL, .5f + .5f * (float) Math.sin(T * 3 + i), .78f);
            if (pr >= 1) glow(x + w / 2, top + 10, 50, HOT, .18f, .78f);
        }
    }

    void drawPylons() {
        layer(.9f);
        boolean lit = "trafo".equals(g.light);
        float[] xs = {-900, -640, -380, -120, 140, 400, 660, 920, 1180, 1440, 1700, 1960};
        for (float x : xs) {
            float b = GROUND - 20, t = 200;
            line(x - 10, b, x, t, 1.6f, "#4a5470"); line(x + 10, b, x, t, 1.6f, "#4a5470");
            line(x - 16, t + 12, x + 16, t + 12, 1.6f, "#4a5470"); line(x - 12, t + 26, x + 12, t + 26, 1.4f, "#4a5470");
            line(x - 7, b - 40, x + 7, b - 70, 1, "#4a5470"); line(x + 7, b - 40, x - 7, b - 70, 1, "#4a5470");
        }
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.6f); p.setColor(C(lit ? "#8fb6ff" : "#3b4560"));
        float[] ox = {-16, 16, -12};
        for (int w = 0; w < 3; w++) {
            path.reset();
            for (int i = 0; i < xs.length - 1; i++) {
                float y = 212 + w * 7 + (w == 2 ? 14 : 0);
                path.moveTo(xs[i] + ox[w], y); path.quadTo((xs[i] + xs[i + 1]) / 2, y + 16, xs[i + 1] + ox[w], y);
            }
            cv.drawPath(path, p);
        }
        p.setStyle(Paint.Style.FILL);
        if (lit && hash((int) Math.floor(T * 2)) > .93f) glow(400 + hash((int) Math.floor(T * 2) + 1) * 400, 222, 26, COLD, .6f, .9f);
    }

    static final float[][] NEWB = {{400, 62, 176}, {490, 58, 158}, {585, 64, 192}, {680, 58, 168}, {310, 58, 150}, {775, 62, 184},
            {230, 58, 162}, {860, 58, 150}, {150, 62, 178}, {945, 58, 160}, {70, 58, 148}, {1025, 62, 170}};
    void drawNewBlocks(float litF) {
        layer(.88f);
        for (int i = 0; i < Math.max(NEWB.length, g.blocks); i++) {
            if (i >= g.blocks) continue;
            float[] bp = blkPos(i); float x = bp[0], w = bp[1], h = bp[2];
            float pr = prog("blk" + i, 2.4f);
            drawBlock(x, GROUND - 14, w, h, 2000 + i * 131, litF, true, pr, i % 3, .88f, false);
            if (pr < 1) {
                float cx = x + w + 6, ct = GROUND - 14 - h - 30;
                line(cx, GROUND - 14, cx, ct, 2, "#f4c84a"); line(cx - 40, ct + 4, cx + 14, ct + 4, 2, "#f4c84a");
                float hookX = cx - 30 + (float) Math.sin(T) * 6; line(hookX, ct + 4, hookX, ct + 30, .6f, "#cccccc"); R(hookX - 4, ct + 30, 8, 5, "#8a5a2a");
                glow(cx, ct, 10, REDL, .8f * (Math.sin(T * 6) > 0 ? 1 : .2f), .88f);
            }
        }
    }

    static final String[][] PAL = {{"#d9b08c", "#c49a74"}, {"#9fb7c9", "#89a3b8"}, {"#c7a3a8", "#b48d93"}};
    void drawBlock(float x, float by, float w, float h, int seed, float litF, boolean fresh, float grow, int palette, float lp, boolean snowRoof) {
        float hh = h * grow, top = by - hh;
        String[] pal = fresh ? PAL[palette] : new String[]{"#2a3150", "#232a45"};
        R(x, top, w, hh, pal[0]);
        R(x + w * .62f, top, w * .38f, hh, pal[1]);
        R(x - 2, top - 3, w + 4, 4, fresh ? "#5b4a44" : "#1b2138");
        if (snowRoof) R(x - 2, top - 5, w + 4, 3, "#e8eef7");
        float lightsOn = g.light != null ? 1 : .55f;
        int cols = Math.max(3, (int) Math.floor(w / 13)), rows = Math.max(4, (int) Math.floor(h / 22));
        float cwid = (w - 10) / cols, rh = (h - 12) / rows;
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            float wy = by - h + 8 + r * rh;
            if (wy < top + 2) continue;
            float wx = x + 5 + c * cwid + cwid * .2f, ww = cwid * .6f, wh = rh * .55f;
            float rnk = hash(seed + r * 31 + c * 7);
            if (rnk < litF) {
                float flick = "diesel".equals(g.light) ? .85f + .15f * (float) Math.sin(T * 9 + rnk * 50) : (g.light == null ? .7f + .3f * (float) Math.sin(T * 3 + rnk * 40) : 1);
                ga = flick * lightsOn; R(wx, wy, ww, wh, (rnk * 7) % 1 > .3f ? "#ffcf72" : "#ffe3a8"); ga = 1;
                if ((rnk * 13) % 1 < .3f) glow(wx + ww / 2, wy + wh / 2, 10, WARM, .45f * flick * lightsOn, lp);
            } else {
                R(wx, wy, ww, wh, fresh ? "#2a3048" : "#121729");
                if (!fresh && rnk > .86f) { line(wx, wy, wx + ww, wy + wh, .6f, "#3a3020"); line(wx + ww, wy, wx, wy + wh, .6f, "#3a3020"); }
            }
        }
        if (grow >= 1) for (int d = 0; d < Math.max(1, (int) Math.floor(w / 45)); d++) {
            float dx = x + 10 + d * 40; R(dx, by - 10, 8, 10, fresh ? "#5a3d2e" : "#151a2c");
            if (fresh || litF > .3f) glow(dx + 4, by - 12, 10, WARM, .35f, lp);
        }
    }

    static final float[][] OLD = {{360, 78, 96}, {446, 72, 86}, {526, 80, 104}, {716, 74, 92}, {798, 70, 84}};

    void drawTown(float litF) {
        layer(1);
        boolean mineOpen = g.mineOpen, flooded = "flooded".equals(g.mine);
        float mx = 95, mb = GROUND;
        String mc = flooded ? "#3a3440" : "#8a2f2b";
        if (g.demolished) drawBlock(mx - 40, GROUND, 84, 120, 4242, litF, true, 1, 2, 1, snowy);
        else {
        line(mx - 32, mb, mx, 168, 4, mc); line(mx + 32, mb, mx, 168, 4, mc);
        line(mx - 22, 270, mx + 22, 270, 3, mc); line(mx - 14, 222, mx + 14, 222, 3, mc);
        line(mx - 26, 300, mx + 18, 236, 2.4f, mc); line(mx + 26, 300, mx - 18, 236, 2.4f, mc);
        cv.save(); cv.translate(mx, 176); cv.rotate(mineOpen ? (float) Math.toDegrees(T * 1.6f) : 0);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(C(mc)); cv.drawCircle(0, 0, 14, p); p.setStyle(Paint.Style.FILL);
        for (int k = 0; k < 4; k++) { cv.rotate(45); line(-14, 0, 14, 0, 1.5f, mc); }
        cv.restore();
        cv.save(); cv.translate(mx, 150);
        poly(mineOpen ? "#ff5a4f" : "#5a2a2a", 0, -9, 2.6f, -3, 9, -3, 4, 1.5f, 6, 8, 0, 4, -6, 8, -4, 1.5f, -9, -3, -2.6f, -3);
        cv.restore();
        if (mineOpen || g.museum) glow(mx, 147, 26, REDL, .9f + .1f * (float) Math.sin(T * 2), 1);
        }
        R(mx - 40, GROUND - 40, 80, 40, "#2c3352"); R(mx - 40, GROUND - 43, 80, 4, "#1d2340");
        if (snowy) R(mx - 40, GROUND - 46, 80, 3, "#e8eef7");
        for (int k = 0; k < 4; k++) R(mx - 32 + k * 18, GROUND - 32, 9, 12, mineOpen ? "#ffcf72" : "#151a2c");
        if (mineOpen) glow(mx, GROUND - 26, 40, WARM, .5f, 1);
        if (!mineOpen && !flooded) {
            R(mx - 20, GROUND - 16, 40, 16, "#3a4060");
            for (int k = 0; k < 8; k++) line(mx - 18 + k * 5, GROUND - 16, mx - 18 + k * 5, GROUND, .8f, "#59607e");
            R(mx - 16, GROUND - 26, 32, 8, "#b8322b"); text("ВХОД ЗАПРЕЩЁН", mx, GROUND - 22, 3, "#ffffff");
        }
        if (flooded) { ga = .7f; poly("#2c4f7a", mx - 60, GROUND, mx - 50, GROUND - 6, mx + 60, GROUND - 5, mx + 66, GROUND); ga = 1; glow(mx, GROUND - 4, 30, COLD, .25f, 1); }

        // будка вентилятора
        float vx = 160; boolean vlit = g.ventLit;
        R(vx - 18, GROUND - 30, 36, 30, "#3a4262"); poly("#2a3150", vx - 21, GROUND - 30, vx, GROUND - 42, vx + 21, GROUND - 30);
        if (snowy) poly("#e8eef7", vx - 21, GROUND - 30, vx, GROUND - 42, vx + 21, GROUND - 30, vx + 14, GROUND - 32, vx, GROUND - 39, vx - 14, GROUND - 32);
        cv.save(); cv.translate(vx - 6, GROUND - 16); cv.rotate(vlit ? (float) Math.toDegrees(T * 5) : 0);
        circ(0, 0, 9, "#1e2338");
        for (int k = 0; k < 5; k++) { cv.rotate(72); poly("#59607e", 0, 0, 8, -2, 8, 2); }
        cv.restore();
        R(vx + 6, GROUND - 22, 8, 8, vlit ? "#ffd27a" : "#141a2c");
        R(vx + 6, GROUND - 52, 4, 12, "#4a4a55");
        if (vlit) {
            glow(vx + 10, GROUND - 18, 26, WARM, 1, 1);
            if (every("vent", .6f)) puff(vx + 8, GROUND - 54, 1, Color.rgb(210, 214, 226), .7f, -10);
        }
        if (g.matveyVis) {
            float mp = prog("matvey", 1.6f), px = vx + 30, py = GROUND - 2 + (1 - mp) * 14;
            cv.save(); cv.clipRect(px - 10, GROUND - 40, px + 10, GROUND);
            R(px - 4, py - 14, 8, 12, "#d2683a"); circ(px, py - 17, 3.4f, "#f1c7a5"); R(px - 3, py - 15, 6, 3.5f, "#eeeeee");
            poly("#f4c84a", px - 4.5f, py - 19, px, py - 23, px + 4.5f, py - 19); circ(px + 3.5f, py - 20, 1.1f, "#ffffff");
            cv.restore();
            if (mp > .5f) {
                p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD)); ga = .25f * mp;
                poly("#ffe9b0", px + 4, py - 20, px + 60, py - 34, px + 60, py - 2);
                ga = 1; p.setXfermode(null);
                glow(px + 3.5f, py - 20, 12, WHITE, mp, 1);
            }
        }

        // водонапорная башня
        float wx = 208; String wst = g.water == null ? "off" : g.water;
        line(wx - 10, GROUND, wx - 6, 262, 2, "#4a5470"); line(wx + 10, GROUND, wx + 6, 262, 2, "#4a5470");
        line(wx - 8, 300, wx + 8, 300, 1.4f, "#4a5470");
        float wp = wst.equals("off") ? 0 : prog("water", 1.6f);
        R(wx - 13, 236, 26, 26, wst.equals("off") ? "#3a4060" : "#5b8fb8"); poly("#2a3150", wx - 15, 236, wx, 226, wx + 15, 236);
        if (snowy) poly("#e8eef7", wx - 15, 236, wx, 226, wx + 15, 236, wx + 8, 234, wx, 229, wx - 8, 234);
        if (!wst.equals("off") && !wst.equals("truck")) {
            ga = wp; R(wx - 13, 236 + 26 * (1 - wp), 26, 26 * wp, "#6fb4e6"); ga = 1;
            glow(wx, 249, 26, COLD, .35f * wp, 1);
            float depth = wst.equals("deep") ? 14 : 6;
            ga = .6f * wp; line(wx, GROUND + depth, wx + 340 * wp, GROUND + depth, 1.4f, g.frozen ? "#a0e0ff" : "#4ab0ff"); ga = 1;
        }
        if (g.frozen) { for (int k = 0; k < 6; k++) poly("#cfefff", wx - 12 + k * 4.8f, 262, wx - 10 + k * 4.8f, 262 + 4 + hash(k) * 6, wx - 8 + k * 4.8f, 262); text("✕", wx, 249, 10, "#ff5a4f"); }
        if (wst.equals("off")) R(wx - 9, 246, 18, 4, "#b8322b");

        // котельная
        float bx = 250; String bst = g.heat == null ? "off" : g.heat;
        R(bx, GROUND - 46, 66, 46, "#33395a"); R(bx, GROUND - 49, 66, 4, "#21263f");
        if (snowy) R(bx, GROUND - 52, 66, 3, "#e8eef7");
        boolean bOn = bst.equals("coal") || bst.equals("elec");
        for (int k = 0; k < 3; k++) R(bx + 8 + k * 20, GROUND - 36, 12, 14, bOn ? "#ff9a4a" : "#141a2c");
        if (bOn) for (int k = 0; k < 3; k++) glow(bx + 14 + k * 20, GROUND - 29, 16, HOT, .55f + .2f * (float) Math.sin(T * 4 + k), 1);
        float chx = bx + 46;
        R(chx, 150, 12, GROUND - 46 - 150, "#5c2b28");
        for (int k = 0; k < 5; k++) R(chx, 160 + k * 28, 12, 6, k % 2 == 1 ? "#dcdcdc" : "#c0392b");
        if (bst.equals("coal")) { if (every("boil", .3f)) puff(chx + 6, 146, 1, Color.rgb(225, 230, 240), 1.6f, -16); glow(chx + 6, 150, 18, HOT, .4f, 1); }
        if (bst.equals("elec")) { R(bx - 18, GROUND - 22, 16, 22, "#4a5a7a"); glow(bx - 10, GROUND - 14, 16, COLD, .6f + .3f * (float) Math.sin(T * 8), 1); if (every("elec", .9f)) puff(chx + 6, 146, 1, Color.rgb(240, 244, 250), .7f, -9); }
        if (bst.equals("stove")) for (int i = 0; i < OLD.length; i++) if (every("st" + i, .8f + i * .07f)) puff(OLD[i][0] + OLD[i][1] * .3f, GROUND - OLD[i][2] - 8, 1, Color.rgb(150, 150, 160), .6f, -8);

        // подстанция
        float sx = 322; String lst = g.light == null ? "off" : g.light;
        R(sx, GROUND - 24, 30, 24, "#3d4566"); R(sx + 10, GROUND - 18, 10, 18, lst.equals("off") ? "#0b0e18" : "#59607e");
        if (lst.equals("off")) text("⚠", sx + 15, GROUND - 30, 8, "#f4c84a");
        if (lst.equals("trafo")) glow(sx + 15, GROUND - 12, 22, COLD, .55f + .25f * (float) Math.sin(T * 5), 1);
        if (lst.equals("diesel")) { R(sx - 26, GROUND - 14, 22, 14, "#6b5a2a"); if (every("dsl", .4f)) puff(sx - 10, GROUND - 16, 1, Color.rgb(80, 80, 90), .5f, -6); glow(sx - 15, GROUND - 8, 12, HOT, .4f, 1); }

        // старые пятиэтажки
        boolean renov = "people".equals(g.housingChoice), tourists = "tourists".equals(g.housingChoice);
        for (int i = 0; i < OLD.length; i++) {
            float x = OLD[i][0], w = OLD[i][1], h = OLD[i][2];
            boolean fresh = renov && i < 3, tour = tourists && i < 2;
            float pr = (fresh || tour) ? prog("renov", 2.2f) : 1;
            drawBlock(x, GROUND, w, h, 100 + i * 57, litF, (fresh || tour) && pr > .5f, 1, tour ? 1 : i % 3, 1, snowy);
            if ((fresh || tour) && pr < 1) {
                ga = 1 - pr; R(x - 4, GROUND - h - 8, w + 8, h + 8, Color.argb(128, 140, 150, 170));
                for (int k = 0; k < h; k += 12) line(x - 4, GROUND - k, x + w + 4, GROUND - k, .6f, "#8a7a5a");
                ga = 1;
            }
        }
        // ДК
        float dx = 612, dw = 92;
        R(dx, GROUND - 58, dw, 58, "#3a4064"); poly("#2b3150", dx - 6, GROUND - 58, dx + dw / 2, GROUND - 76, dx + dw + 6, GROUND - 58);
        if (snowy) poly("#e8eef7", dx - 6, GROUND - 58, dx + dw / 2, GROUND - 76, dx + dw + 6, GROUND - 58, dx + dw - 6, GROUND - 60, dx + dw / 2, GROUND - 72, dx + 6, GROUND - 60);
        for (int k = 0; k < 6; k++) R(dx + 8 + k * 15, GROUND - 52, 5, 50, "#545c84");
        boolean dkLit = g.light != null;
        R(dx + 20, GROUND - 70, dw - 40, 9, dkLit ? "#f4c84a" : "#2a2f48"); text("ДК «ШАХТЁР»", dx + dw / 2, GROUND - 65.5f, 4.2f, dkLit ? "#2a1d05" : "#555b78");
        if (dkLit) { glow(dx + dw / 2, GROUND - 66, 40, WARM, .55f, 1); for (int k = 0; k < 5; k++) R(dx + 14 + k * 15, GROUND - 40, 6, 22, "#ffd27a"); }
        if ("sat".equals(g.comm)) {
            ga = prog("comm", 1.4f); cv.save(); cv.translate(dx + dw - 14, GROUND - 66); cv.rotate(-28.6f);
            p.setColor(A(C("#cfd6e6"))); rf.set(-8, -4, 8, 4); cv.drawOval(rf, p); cv.restore(); ga = 1;
        }
        if ("tower".equals(g.comm)) {
            layer(.95f);
            float pr = prog("comm", 2), tx2 = 700, th = 210 * pr;
            line(tx2 - 9, GROUND - 60, tx2, GROUND - 60 - th, 1.6f, "#c94a3a"); line(tx2 + 9, GROUND - 60, tx2, GROUND - 60 - th, 1.6f, "#c94a3a");
            for (float y = 0; y < th - 10; y += 16) { float ww = 9 * (1 - y / 210); line(tx2 - ww, GROUND - 60 - y, tx2 + ww, GROUND - 76 - y, .7f, "#d9d9d9"); }
            if (pr >= 1) {
                boolean blink = (T % 1.4f) < .7f;
                glow(tx2, GROUND - 270, 20, REDL, blink ? 1 : .15f, .95f);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.8f);
                for (int k = 0; k < 3; k++) {
                    float ph = (T * .6f + k / 3f) % 1, rr = 8 + ph * 40;
                    p.setColor(Color.argb((int) (128 * (1 - ph)), 108, 198, 255));
                    rf.set(tx2 - rr, GROUND - 268 - rr, tx2 + rr, GROUND - 268 + rr);
                    cv.drawArc(rf, -143, 107, false, p);
                }
                p.setStyle(Paint.Style.FILL);
            }
            layer(1);
        }
        // гостиница
        float hx = 880, hw = 76, hh = 92; boolean hotel = g.hotel;
        float hp = hotel ? prog("hotel", 2) : 0;
        drawBlock(hx, GROUND, hw, hh, 777, hotel ? Math.max(litF, .75f) : litF * .4f, hp > .5f, 1, 0, 1, snowy);
        R(hx + 14, GROUND - hh - 16, hw - 28, 12, hp > .5f ? "#c0392b" : "#2a2f48");
        text("ГОСТИНИЦА «ЯР»", hx + hw / 2, GROUND - hh - 10, 4, hp > .5f ? "#ffffff" : "#555b78");
        if (hp > .5f) glow(hx + hw / 2, GROUND - hh - 10, 40, REDL, .6f, 1);
        // магазины
        if ("own".equals(g.shops) || "subsidy".equals(g.shops)) {
            float pr = prog("shops", 1.4f);
            String[] labels = "own".equals(g.shops) ? new String[]{"ПЕКАРНЯ", "АПТЕКА", "СТРИЖКИ"} : new String[]{"МАГАЗИН"};
            int[] idx = "own".equals(g.shops) ? new int[]{0, 1, 2} : new int[]{1};
            for (int j = 0; j < labels.length; j++) {
                float[] b = OLD[idx[j]]; String label = labels[j];
                float sx2 = b[0] + 4, sw = b[1] - 8;
                R(sx2, GROUND - 22, sw, 22, "#3b2a22"); R(sx2 + 3, GROUND - 16, sw - 6, 14, "#ffe2a6");
                ga = pr; R(sx2, GROUND - 30, sw, 8, label.equals("АПТЕКА") ? "#2bb673" : "#d6452f"); text(label, sx2 + sw / 2, GROUND - 26, 4.5f, "#ffffff"); ga = 1;
                glow(sx2 + sw / 2, GROUND - 14, 30, WARM, .6f * pr, 1); glow(sx2 + sw / 2, GROUND - 26, 22, label.equals("АПТЕКА") ? GREENL : REDL, .5f * pr, 1);
            }
        }
    }

    void drawStreet() {
        layer(1);
        int road = g.roadSet ? g.road : -1;
        R(-1200, GROUND, 3400, 10, snowy ? "#c9d3e4" : "#4a4f5e");
        String rc = road == 2 ? "#2a2d38" : road == 1 ? (snowy ? "#9aa6bc" : "#6b6556") : (snowy ? "#dfe6f1" : "#5a5245");
        R(-1200, GROUND + 10, 3400, 26, rc);
        if (road == 2) for (int x = -1200; x < 2200; x += 30) R(x, GROUND + 22, 15, 2, "#f4c84a");
        if (road <= 0 && snowy) for (int i = 0; i < 60; i++) { float x = -1150 + i * 55 + hash(i) * 20; poly("#f2f6fb", x, GROUND + 36, x + 14, GROUND + 18 + hash(i * 3) * 6, x + 34, GROUND + 36); }
        if (road == 1) for (int x = -1200; x < 2200; x += 22) { R(x, GROUND + 37, 2, 10, "#6a4b30"); R(x - 1, GROUND + 39, 22, 2, "#6a4b30"); R(x - 1, GROUND + 43, 22, 2, "#6a4b30"); }
        boolean lampsOn = g.light != null;
        float[] bnd = bounds(); float LB = bnd[0], RB = bnd[1];
        for (int x = (int) LB + 30; x < RB; x += 92) {
            line(x, GROUND + 4, x, GROUND - 30, 1.4f, "#3a4262"); line(x, GROUND - 30, x + 7, GROUND - 32, 1.4f, "#3a4262");
            if (lampsOn) {
                float fl = "diesel".equals(g.light) ? .8f + .2f * (float) Math.sin(T * 13 + x) : 1;
                circ(x + 8, GROUND - 31, 1.8f, "#fff2c8"); glow(x + 8, GROUND - 30, 42, WARM, .9f * fl, 1); glow(x + 8, GROUND - 31, 10, WHITE, .9f * fl, 1);
                p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD)); ga = .1f * fl;
                poly("#ffd890", x + 6, GROUND - 30, x - 8, GROUND + 10, x + 24, GROUND + 10);
                ga = 1; p.setXfermode(null);
            }
        }
        // транспорт: kind 0 автобус, 1 машина, 2 самосвал, 3 водовозка, 4 сани
        ArrayList<float[]> veh = new ArrayList<>();
        if (road >= 1) {
            veh.add(new float[]{0, 34, 0, 1, 0});
            int cars = Math.min(g.act >= 2 ? 22 : 9, 1 + g.pop / (g.act >= 2 ? 9000 : 4500));
            for (int i = 0; i < cars; i++) veh.add(new float[]{1, 40 + hash(i) * 30, hash(i * 7) * 1400, i % 2 == 1 ? -1 : 1, i % 5});
        }
        if (g.mineOpen) for (int i = 0; i < 2; i++) veh.add(new float[]{2, 26, i * 700, 1, 0});
        if ("truck".equals(g.water)) veh.add(new float[]{3, 18, 300, -1, 0});
        if (road <= 0) veh.add(new float[]{4, 12, 200, 1, 0});
        String[] carCols = {"#d6452f", "#2c7be5", "#e7e7e7", "#3ccf7a", "#f4c84a"};
        for (float[] v : veh) {
            int kind = (int) v[0]; float dir = v[3];
            float vspan = RB - LB + 500;
            float x = ((T * v[1] + v[2]) % vspan) + LB - 250; if (dir < 0) x = RB + LB - x;
            float y = GROUND + (dir > 0 ? 30 : 20);
            cv.save(); cv.translate(x, y); if (dir < 0) cv.scale(-1, 1);
            if (kind == 0) {
                R(-26, -15, 52, 13, "#f4b324"); R(-26, -4, 52, 3, "#c98a10");
                for (int k = 0; k < 6; k++) R(-22 + k * 7.5f, -12, 5, 5, "#ffe7a8");
                text("ВАХТА", 0, -1.5f, 2.6f, "#5a3a00"); circ(-16, -1, 3, "#111111"); circ(16, -1, 3, "#111111");
            } else if (kind == 1) {
                String col = carCols[(int) v[4]];
                R(-9, -7, 18, 5, col); R(-5, -10, 10, 4, col); R(-4, -9.5f, 8, 3, "#9ec4e8"); circ(-5.5f, -1.5f, 1.8f, "#111111"); circ(5.5f, -1.5f, 1.8f, "#111111");
            } else if (kind == 2) {
                R(-20, -14, 26, 10, "#f4c84a"); poly("#1a1a1a", -20, -14, -16, -18, 4, -18, 6, -14); R(7, -13, 10, 9, "#e0a020"); R(10, -12, 5, 4, "#9ec4e8");
                circ(-14, -2, 3, "#111111"); circ(0, -2, 3, "#111111"); circ(12, -2, 3, "#111111");
            } else if (kind == 3) {
                p.setColor(A(C("#5b8fb8"))); rf.set(-17, -15, 9, -3); cv.drawOval(rf, p); text("ВОДА", -4, -9, 2.6f, "#ffffff");
                R(9, -13, 9, 10, "#c0392b"); circ(-12, -1.5f, 2.6f, "#111111"); circ(12, -1.5f, 2.6f, "#111111");
            } else {
                R(-6, -6, 12, 3, "#7a4e2a"); R(-3, -12, 4, 6, "#2c7be5"); circ(-1, -14, 2, "#f1c7a5");
            }
            cv.restore();
            if (kind != 4) {
                float off = kind == 0 ? 26 : 10;
                glow(x + dir * off, y - 5, 16, WHITE, .7f, 1);
                glow(x - dir * off, y - 5, 8, REDL, .6f, 1);
            }
        }
        // люди
        int nppl = Math.min(g.act >= 2 ? 110 : 46, 2 + g.pop / (g.act >= 2 ? 2500 : 900));
        float pspan = RB - LB + 200;
        String[] jc = {"#d6452f", "#2c7be5", "#f4c84a", "#3ccf7a", "#e67e22", "#9b59b6"};
        for (int i = 0; i < nppl; i++) {
            float sp = 6 + hash(i * 5) * 8, dir = hash(i * 11) > .5f ? 1 : -1;
            float x = ((T * sp + hash(i * 3) * pspan) % pspan) + LB - 100; if (dir < 0) x = RB + LB - x;
            float y = GROUND + 7 + hash(i * 13) * 2, bob = Math.abs((float) Math.sin(T * 6 + i)) * .8f;
            String col = jc[i % 6];
            R(x - 1.6f, y - 8 - bob, 3.2f, 6, col); R(x - 1.4f, y - 2 - bob, 1.2f, 2.4f, "#1b1f2a"); R(x + .2f, y - 2 - bob, 1.2f, 2.4f, "#1b1f2a");
            circ(x, y - 9.6f - bob, 1.6f, "#f1c7a5"); R(x - 1.8f, y - 11.6f - bob, 3.6f, 1.6f, snowy ? "#5a4a3a" : col);
        }
        R(-1400, GROUND + 36, 3800, 300, snowy ? "#e9eff8" : "#22372a");
        if (snowy) {
            p.setColor(C("#f5f8fc"));
            for (int i = 0; i < 80; i++) { float x = -1150 + i * 45; float rx = 30 + hash(i) * 20, ry = 6 + hash(i * 3) * 4; rf.set(x - rx, GROUND + 40 - ry, x + rx, GROUND + 40 + ry); cv.drawArc(rf, 180, 180, true, p); }
        }
    }


    // ════════════════ ЧАСТЬ II: новые районы ════════════════
    float[] bounds() { return g.act >= 2 ? new float[]{-820, 1900} : new float[]{0, 1000}; }
    float LITF = .5f;
    static final float[] SCH = {1400, 975, 1815, 1500, 1060, 1745, 1340, 1880, 1190, 1630, 1270, 1560};
    static final float[] HOS = {1300, 1885, 1045, 1660, 1480, 1220, 1770, 1100, 1590, 1370, 1700, 1150};
    static final float[] PAR = {1520, 1130, 1760, 1300, 1480, 1850, 1010, 1650, 1240, 1400, 1700, 1070};
    static final float[] MAL = {1720, 1000, 1850, 1240, 1480, 1600, 1120, 1780, 1360, 1060, 1540, 1900};
    static final float[] MICX = {1060, 1230, 1400, 1570, 1740, 1145, 1315, 1485, 1655, 975};
    float[] facPos(int i) { if (i < FAC.length) return FAC[i]; int j = i - FAC.length; return new float[]{-280 + (j * 137) % 2160, 76 + (j % 4) * 6, 60 + (j % 3) * 9}; }
    float[] blkPos(int i) { if (i < NEWB.length) return NEWB[i]; int j = i - NEWB.length; return new float[]{-260 + (j * 89) % 2140, 56 + (j % 3) * 4, 150 + (j * 37) % 50}; }
    float micPos(int i) { return MICX[i % MICX.length] + ((i / MICX.length) % 3) * 28 - 28; }

    void sign(float x, float y, float w, String label, String bg, String fg, int glowCol, float a) {
        ga = a; R(x, y, w, 9, bg); text(label, x + w / 2, y + 4.6f, 4.2f, fg); ga = 1;
        glow(x + w / 2, y + 4.5f, w * .6f, glowCol, .45f * a, 1);
    }

    void drawWind() {
        if (!"wind".equals(g.energy)) return;
        layer(.35f);
        float p0 = prog("energy", 2.5f);
        for (int i = 0; i < 9; i++) {
            float x = -160 + i * 90, bs = 252 + (float) Math.sin(i * 1.7) * 10, h = 70 * p0;
            line(x, bs, x, bs - h, 2, "#cfd6e6");
            if (p0 < 1) continue;
            cv.save(); cv.translate(x, bs - h); cv.rotate((float) Math.toDegrees(T * 1.4f + i));
            for (int b = 0; b < 3; b++) { cv.rotate(120); poly("#e8eef7", 0, 0, 2.5f, -2, 30, -1, 30, 1, 2.5f, 2); }
            cv.restore();
            circ(x, bs - h, 2.2f, "#e8eef7");
            glow(x, bs - h - 3, 8, REDL, (T + i * .3f) % 1.6f < .8f ? .9f : .15f, .35f);
        }
    }
    void drawAirport() {
        String a = g.airport; if (a == null) return;
        layer(.5f);
        float ap = prog("airport", 2);
        R(-330, GROUND - 6, 420, 5, "#3a4060");
        for (int i = 0; i < 20; i++) glow(-320 + i * 21, GROUND - 4, 5, ((int) Math.floor(T * 8) + i) % 20 == 0 ? WHITE : WARM, .6f * ap, .5f);
        float tx = -80; R(tx - 4, GROUND - 70 * ap, 8, 70 * ap, "#4a5470"); R(tx - 12, GROUND - 82 * ap, 24, 13, "#2f3757");
        R(tx - 10, GROUND - 80 * ap, 20, 7, "#9ad8ff"); glow(tx, GROUND - 77 * ap, 22, COLD, .5f * ap, .5f);
        glow(tx, GROUND - 88 * ap, 12, (T % 1.2f) < .6f ? GREENL : WHITE, ap, .5f);
        if (ap < 1) return;
        if (a.equals("full")) {
            float t = (T % 16) / 16, px = 1300 - t * 1700, py = 60 + t * 200 + (float) Math.sin(t * 6) * 4;
            cv.save(); cv.translate(px, py); cv.rotate(3.4f);
            poly("#d9dfeb", -22, 0, 20, -2, 26, 0, 20, 2); poly("#b9c2d6", -2, 0, 6, -12, 10, -12, 6, 0); poly("#b9c2d6", -2, 0, 6, 10, 10, 10, 6, 0); poly("#c0392b", 18, -1, 24, -8, 26, -8, 24, 0);
            cv.restore();
            glow(px + 26, py, 10, (T % 1) < .1f ? WHITE : REDL, .9f, .5f); glow(px + 8, py - 12, 7, REDL, .8f, .5f); glow(px + 8, py + 10, 7, GREENL, .8f, .5f);
            if (t > .02f) { ga = .18f; line(px + 26, py, px + 200, py - 26, 2, "#dfe6f1"); ga = 1; }
        } else {
            float t = (T % 22) / 22, hx = -800 + t * 2600, hy = 120 + (float) Math.sin(T * .7f) * 10;
            R(hx - 8, hy - 3, 16, 6, "#e8eef7"); R(hx + 6, hy - 1, 10, 2, "#e8eef7"); line(hx - 14, hy - 6, hx + 14, hy - 6, 1, "#999aaa");
            glow(hx, hy, 8, REDL, (T % .8f) < .4f ? 1 : .2f, .5f);
        }
    }
    void drawHydro() {
        if (!"hydro".equals(g.energy)) return;
        layer(.7f);
        float p0 = prog("energy", 2.5f);
        poly(snowy ? "#c8d4e8" : "#2c4f7a", -700, GROUND - 50, -120, GROUND - 50, -120, GROUND, -700, GROUND);
        ga = p0;
        poly("#7d8496", -400, GROUND - 52, -170, GROUND - 52, -155, GROUND + 5, -415, GROUND + 5);
        for (int i = 0; i < 6; i++) { R(-390 + i * 38, GROUND - 40, 20, 40, "#5f6678"); ga = .55f * p0; R(-388 + i * 38, GROUND - 2, 16, 8 + (float) Math.sin(T * 5 + i) * 2, "#9ad8ff"); ga = p0; }
        ga = 1;
        for (int i = 0; i < 12; i++) glow(-395 + i * 20, GROUND - 54, 8, WARM, .6f * p0, .7f);
        glow(-285, GROUND, 70, COLD, .25f * p0, .7f);
    }
    void drawTEC() {
        if (!"tec".equals(g.energy)) return;
        layer(.8f);
        float p0 = prog("energy", 2.5f);
        for (int k = 0; k < 2; k++) {
            float cx = -470 + k * 95, b = GROUND - 30, h = 120 * p0, w = 34;
            path.reset(); path.moveTo(cx - w, b); path.quadTo(cx - w * .45f, b - h * .55f, cx - w * .62f, b - h);
            path.lineTo(cx + w * .62f, b - h); path.quadTo(cx + w * .45f, b - h * .55f, cx + w, b); path.close();
            p.setStyle(Paint.Style.FILL); p.setColor(C("#59617c")); cv.drawPath(path, p); R(cx - w, b - 8, w * 2, 3, "#c0392b");
            if (p0 >= 1 && every("tec" + k, .18f)) puff(cx + (rnd.nextFloat() - .5f) * 20, b - h, .8f, Color.rgb(235, 238, 246), 3.2f, -16);
            glow(cx, b - h, 40, HOT, .12f, .8f);
        }
        float sh = 150 * p0;
        R(-320, GROUND - 30 - sh, 10, sh, "#6a2f2b");
        for (int q = 8; q < sh - 4; q += 18) R(-320, GROUND - 30 - sh + q, 10, 5, q % 36 < 18 ? "#e3e3e3" : "#c0392b");
        glow(-315, GROUND - 30 - sh, 10, REDL, .5f + .5f * (float) Math.sin(T * 3), .8f);
        if (p0 >= 1 && every("tecs", .4f)) puff(-315, GROUND - 32 - sh, .8f, Color.rgb(160, 160, 170), 1.6f, -12);
    }
    void drawSkyscrapers() {
        int n = g.pop >= 200000 ? Math.min(9, (g.pop - 200000) / 100000 + 1) : 0;
        if (n == 0) return;
        layer(.84f);
        float[] xs = {1180, 1290, 1400, 1510, 1080, 1620, 980, 1720, 1340};
        for (int i = 0; i < n; i++) {
            float pr = i == n - 1 ? prog("sky", 2.5f) : 1;
            float x = xs[i], w = 46 + (i % 3) * 8, h = (230 + (i * 47) % 90) * pr, top = GROUND - 30 - h;
            p.setShader(new LinearGradient(x, top, x + w, top, new int[]{C("#2b4a7a"), C("#3f6aa6"), C("#22385e")}, null, Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL); cv.drawRect(x, top, x + w, top + h, p); p.setShader(null);
            for (float y = top + 6; y < GROUND - 34; y += 7) { ga = .35f + .4f * hash(i * 100 + (int) y); R(x + 3, y, w - 6, 2.2f, "#9ad8ff"); }
            ga = 1;
            line(x + w / 2, top, x + w / 2, top - 30 * pr, 1.4f, "#cfd6e6");
            glow(x + w / 2, top - 30 * pr, 12, REDL, (T + i * .4f) % 1.4f < .7f ? 1 : .2f, .84f);
            glow(x + w / 2, top + h / 2, 70, COLD, .12f, .84f);
        }
        if (g.pop >= 1000000) {
            float x = 1450, h = 380, top = GROUND - 30 - h;
            poly("#4a7ac0", x - 30, GROUND - 30, x - 18, top + 60, x, top, x + 18, top + 60, x + 30, GROUND - 30);
            line(x, top, x, top - 60, 2, "#e8eef7"); glow(x, top - 60, 26, WHITE, .5f + .5f * (float) Math.sin(T * 2), .84f);
            text("ЯР-СИТИ", x, top + 90, 7, "#ffffff");
        }
    }
    void drawMicro() {
        int n = Math.min(24, g.micro); if (n == 0) return;
        layer(.9f);
        for (int i = 0; i < n; i++) {
            float x0 = micPos(i), pr = prog("mic" + i, 2.6f);
            for (int t = 0; t < 3; t++) {
                float x = x0 + t * 52, w = 44, h = 190 + ((i + t) % 3) * 22;
                drawBlock(x, GROUND - 22, w, h, 5000 + i * 77 + t * 13, LITF, true, pr, (i + t) % 3, .9f, false);
                if (pr >= 1 && "dev".equals(g.north)) R(x - 1, GROUND - 22 - h - 6, w + 2, 4, "#7a2a2a");
            }
            if (pr < 1) { float cx = x0 + 160, ct = GROUND - 22 - 240; line(cx, GROUND - 22, cx, ct, 2.4f, "#f4c84a"); line(cx - 60, ct + 4, cx + 16, ct + 4, 2.4f, "#f4c84a"); glow(cx, ct, 10, REDL, Math.sin(T * 6) > 0 ? 1 : .2f, .9f); }
        }
    }
    static final int[] PARK_COLS = {0xFFFFC86E, 0xFFFF6EA0, 0xFF6EC8FF};
    void drawCivic(float litF) {
        layer(1);
        for (int i = 0; i < Math.min(12, g.schools); i++) {
            float x = SCH[i], pr = prog("sch" + i, 1.8f);
            drawBlock(x, GROUND, 64, 56, 9000 + i * 7, Math.max(litF, .6f), true, pr, 1, 1, snowy);
            if (pr >= 1) sign(x + 10, GROUND - 68, 44, "ШКОЛА", "#2c7be5", "#ffffff", COLD, 1);
        }
        for (int i = 0; i < Math.min(12, g.hospitals); i++) {
            float x = HOS[i], pr = prog("hos" + i, 1.8f); boolean big = i == 0 && g.med;
            float w = big ? 96 : 70, h = big ? 92 : 70;
            R(x, GROUND - h * pr, w, h * pr, "#e6ebf3"); R(x + w * .62f, GROUND - h * pr, w * .38f, h * pr, "#cfd6e2");
            for (int r = 0; r < (int) (h / 16); r++) for (int c = 0; c < (int) (w / 14); c++) if (GROUND - h * pr + 6 + r * 16 < GROUND - 10) R(x + 5 + c * 14, GROUND - h * pr + 6 + r * 16, 8, 8, hash(i * 50 + r * 9 + c) < .7f ? "#bfe3ff" : "#7e8aa6");
            if (pr >= 1) { R(x + w / 2 - 7, GROUND - h - 18, 14, 4, "#e23b3b"); R(x + w / 2 - 2, GROUND - h - 23, 4, 14, "#e23b3b"); glow(x + w / 2, GROUND - h - 16, 22, REDL, .8f, 1); }
        }
        if (g.uni) {
            float x = 1130, pr = prog("uni", 2.2f), w = 160, h = 84 * pr;
            R(x, GROUND - h, w, h, "#d9c7a7"); poly("#b9a17c", x - 8, GROUND - h, x + w / 2, GROUND - h - 26 * pr, x + w + 8, GROUND - h);
            for (int k = 0; k < 8; k++) R(x + 10 + k * 19, GROUND - h + 22, 6, h - 22, "#efe3cc");
            circ(x + w / 2, GROUND - h - 30 * pr, 14 * pr, "#8fa8c8");
            for (int k = 0; k < 7; k++) R(x + 16 + k * 19, GROUND - h + 8, 8, 8, "#ffd27a");
            if (pr >= 1) sign(x + 22, GROUND - h - 14, w - 44, "ГОРНЫЙ ИНСТИТУТ", "#6b4e2e", "#ffffff", WARM, 1);
            glow(x + w / 2, GROUND - 30, 80, WARM, .35f * pr, 1);
        }
        for (int i = 0; i < Math.min(12, g.parks); i++) {
            float x = PAR[i], pr = prog("par" + i, 1.8f);
            for (int t = 0; t < 7; t++) {
                float tx = x - 60 + t * 20, th = (16 + hash(i * 9 + t) * 10) * pr;
                poly(snowy ? "#2a4a3a" : "#2f6a43", tx, GROUND - th - 6, tx - 8, GROUND - 4, tx + 8, GROUND - 4);
                if (snowy) poly("#e8eef7", tx, GROUND - th - 6, tx - 4, GROUND - th + 2, tx + 4, GROUND - th + 2);
                glow(tx, GROUND - th + 4, 8, PARK_COLS[t % 3], .55f * pr, 1);
            }
            if (i == 0 && pr > .3f) {
                float cx = x + 90, cy = GROUND - 62, r = 50 * pr;
                line(cx - 18, GROUND, cx, cy, 2, "#9aa4bd"); line(cx + 18, GROUND, cx, cy, 2, "#9aa4bd");
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.4f); p.setColor(C("#c9d1e3")); cv.drawCircle(cx, cy, r, p); p.setStyle(Paint.Style.FILL);
                String[] cc = {"#d6452f", "#f4c84a", "#2c7be5", "#3ccf7a"};
                for (int k = 0; k < 12; k++) {
                    double a = T * .25 + k * .5236; float kx = cx + (float) Math.cos(a) * r, ky = cy + (float) Math.sin(a) * r;
                    line(cx, cy, kx, ky, .5f, "#9aa4bd"); R(kx - 3, ky, 6, 5, cc[k % 4]);
                    glow(kx, ky + 2, 7, WARM, .6f, 1);
                }
                glow(cx, cy, 60, 0xFFFF78C8, .12f, 1);
            }
        }
        String[] mc = {"#d6452f", "#9b59b6", "#16a085"};
        for (int i = 0; i < Math.min(12, g.malls); i++) {
            float x = MAL[i], pr = prog("mal" + i, 1.8f), w = 92, h = 50 * pr;
            p.setShader(new LinearGradient(x, GROUND - h, x, GROUND, C("#3b6aa0"), C("#1d3352"), Shader.TileMode.CLAMP));
            cv.drawRect(x, GROUND - h, x + w, GROUND, p); p.setShader(null);
            for (int k = 0; k < 6; k++) R(x + 4 + k * 15, GROUND - h + 6, 11, h - 12, Color.argb(115, 170, 220, 255));
            if (pr >= 1) sign(x + 16, GROUND - h - 11, w - 32, "ТЦ «ЯР»", mc[i % 3], "#ffffff", 0xFFFF78C8, 1);
            glow(x + w / 2, GROUND - h / 2, 50, COLD, .3f * pr, 1);
        }
        if ("it".equals(g.mineFate)) {
            float pr = prog("mineFate", 2); ga = pr;
            R(30, GROUND - 58, 52, 18, "#18324f");
            for (int k = 0; k < 6; k++) R(33 + k * 8, GROUND - 55, 5, 12, ((int) Math.floor(T * 4) + k) % 3 != 0 ? "#4ab0ff" : "#9ad8ff");
            ga = 1; sign(36, GROUND - 70, 40, "ЯР-IT", "#0e5aa8", "#ffffff", COLD, pr);
            glow(56, GROUND - 50, 40, COLD, .5f * pr, 1);
        }
        if (g.museum) {
            float pr = prog("museum", 2);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD)); ga = .12f * pr;
            for (float sx : new float[]{40, 150}) { float a = (float) Math.sin(T * .6f + sx) * .25f; float dx = (float) Math.sin(a) * 300; poly("#fff2c8", sx, GROUND, sx + dx - 30, -100, sx + dx + 30, -100); }
            ga = 1; p.setXfermode(null);
            glow(95, 200, 90, WARM, .25f * pr, 1);
            sign(60, GROUND - 56, 70, "МУЗЕЙ ШАХТЫ", "#8a2f2b", "#ffffff", REDL, pr);
        }
    }
    void drawRail() {
        String r = g.rail; if (r == null) return;
        layer(1);
        float[] bb = bounds(); float LB = bb[0], RB = bb[1], y = GROUND + 58, pr = prog("rail", 2.4f);
        float end = LB - 100 + (RB - LB + 200) * pr;
        R(LB - 100, y + 2, end - LB + 100, 6, snowy ? "#b9c3d6" : "#5a5245");
        for (float x = LB - 100; x < end; x += 9) R(x, y + 1, 3, 5, "#6a4b30");
        line(LB - 100, y + 1, end, y + 1, 1, "#8a93a8"); line(LB - 100, y + 5, end, y + 5, 1, "#8a93a8");
        if (r.equals("electric")) { for (float x = LB - 60; x < end; x += 70) { line(x, y, x, y - 30, 1, "#59607e"); line(x, y - 28, x + 8, y - 28, 1, "#59607e"); } line(LB - 100, y - 26, end, y - 26, .5f, "#8a93a8"); }
        float sx = -200;
        R(sx, y - 46, 110, 46, "#c9b48f"); poly("#8a5a3a", sx - 6, y - 46, sx + 55, y - 62, sx + 116, y - 46);
        for (int k = 0; k < 5; k++) R(sx + 10 + k * 20, y - 36, 10, 16, "#ffd27a");
        sign(sx + 30, y - 58, 50, "ЯР", "#2a3150", "#f4c84a", WARM, pr);
        glow(sx + 55, y - 30, 60, WARM, .4f * pr, 1);
        R(sx - 40, y - 4, 190, 4, "#9aa4bd");
        if (pr < 1) return;
        boolean el = r.equals("electric");
        float per = el ? 14 : 26, t = (T % per) / per, span = RB - LB + 600, tx = LB - 300 + t * span;
        int cars = el ? 5 : 8;
        for (int c = 0; c < cars; c++) {
            float cx = tx - c * 44;
            if (el) {
                R(cx - 20, y - 16, 40, 14, c == 0 ? "#d6452f" : "#cfd6e6"); R(cx - 20, y - 8, 40, 3, "#d6452f");
                for (int k = 0; k < 5; k++) R(cx - 17 + k * 7.5f, y - 14, 5, 4, "#ffe7a8");
                if (c % 2 == 0) { line(cx - 4, y - 16, cx + 2, y - 26, .8f, "#59607e"); if (hash((int) Math.floor(T * 9) + c) > .85f) glow(cx + 2, y - 26, 10, COLD, 1, 1); }
            } else {
                if (c == 0) { R(cx - 20, y - 18, 40, 16, "#2f7a4a"); R(cx + 8, y - 22, 10, 6, "#2f7a4a"); if (every("loco", .3f)) puff(cx - 10, y - 22, 1, Color.rgb(120, 120, 130), 1, -12); }
                else { poly("#5a3a2a", cx - 20, y - 14, cx + 20, y - 14, cx + 16, y - 2, cx - 16, y - 2); R(cx - 18, y - 16, 36, 3, "#1a1a1a"); }
            }
            circ(cx - 12, y - 1, 2, "#111111"); circ(cx + 12, y - 1, 2, "#111111");
        }
        glow(tx + 22, y - 10, 30, WHITE, .9f, 1);
    }
    void drawTram() {
        if (!g.tram) return;
        layer(1);
        float[] bb = bounds(); float LB = bb[0], RB = bb[1], span = RB - LB + 300;
        for (int i = 0; i < 2; i++) {
            float dir = i == 1 ? -1 : 1, t = (T * 22 + i * span / 2) % span;
            float x = dir > 0 ? LB - 150 + t : RB + 150 - t, y = GROUND + 18;
            R(x - 22, y - 14, 44, 13, "#d6452f"); R(x - 22, y - 9, 44, 3, "#f2f2f2");
            for (int k = 0; k < 6; k++) R(x - 19 + k * 7, y - 13, 5, 4, "#ffe7a8");
            line(x, y - 14, x + 6 * dir, y - 24, .8f, "#59607e");
            if (hash((int) Math.floor(T * 7) + i) > .8f) glow(x + 6 * dir, y - 24, 12, COLD, 1, 1);
            glow(x + 22 * dir, y - 6, 16, WHITE, .7f, 1);
        }
        line(-1200, GROUND + 7, 2200, GROUND + 7, .4f, "#8a93a8");
    }
    void drawSmog() {
        double s = g.smog; if (s < 8) return;
        screen();
        float a = (float) Math.min(.5, s / 160);
        p.setShader(new LinearGradient(0, ch * .2f, 0, ch * .8f, new int[]{Color.argb(0, 120, 100, 80), Color.argb((int) (255 * a), 140, 118, 90), Color.argb((int) (255 * a * .6f), 90, 80, 70)}, new float[]{0, .5f, 1}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, cw, ch, p); p.setShader(null);
    }

    void drawParts(float dt) {
        Iterator<Part> it = parts.iterator();
        float lastP = -1;
        while (it.hasNext()) {
            Part q = it.next(); q.t += dt;
            if (q.t > q.life) { it.remove(); continue; }
            q.x += q.vx * dt; q.y += q.vy * dt;
            if (q.p != lastP) { layer(q.p); lastP = q.p; }
            float k = q.t / q.life;
            if (q.type == 0) {
                q.vx += 3 * dt;
                ga = (1 - k) * .55f * Math.min(1, q.t * 3);
                circ(q.x, q.y, q.r + q.gr * k, q.col);
            } else if (q.type == 1) {
                q.vy += 60 * dt;
                ga = 1 - k; circ(q.x, q.y, q.r, "#fff3c0");
                glow(q.x, q.y, 8, WARM, (1 - k) * .8f, q.p);
            } else {
                ga = (1 - k) * .35f; circ(q.x, q.y, q.r * (1 + k), "#c9cfdc");
            }
        }
        ga = 1;
        while (parts.size() > 500) parts.remove(0);
    }

    void drawGlows() {
        screen();
        gp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        for (float[] q : glows) {
            float a = q[4]; if (a <= .01f) continue;
            float[] s = toScreen(q[0], q[1], q[5]);
            float rr = q[2] * base * camZ;
            if (s[0] < -rr || s[0] > cw + rr || s[1] < -rr || s[1] > ch + rr) continue;
            gp.setAlpha((int) (255 * Math.min(1, a)));
            rf.set(s[0] - rr, s[1] - rr, s[0] + rr, s[1] + rr);
            cv.drawBitmap(glowSprite(Float.floatToRawIntBits(q[3])), null, rf, gp);
        }
        gp.setXfermode(null); gp.setAlpha(255);
    }

    void post() {
        screen();
        if (Build.VERSION.SDK_INT >= 29) {
            p.setBlendMode(BlendMode.SOFT_LIGHT);
            p.setShader(new LinearGradient(0, 0, 0, ch, summer ? Color.argb(64, 255, 140, 90) : Color.argb(56, 60, 110, 255), Color.argb(46, 255, 170, 90), Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, cw, ch, p);
            p.setShader(null); p.setBlendMode(null);
        }
        float rmin = Math.min(cw, ch) * .3f, rmax = Math.max(cw, ch) * .8f;
        p.setShader(new RadialGradient(cw / 2, ch * .55f, rmax, new int[]{0, 0, Color.argb(166, 3, 5, 14)}, new float[]{0, rmin / rmax, 1}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, cw, ch, p); p.setShader(null);
        if (flash > 0) { p.setColor(Color.argb((int) (flash * 64), 255, 236, 190)); cv.drawRect(0, 0, cw, ch, p); }
        float fh = 30 * dp;
        p.setShader(new LinearGradient(0, ch - fh, 0, ch, Color.argb(0, 13, 18, 32), Color.argb(230, 13, 18, 32), Shader.TileMode.CLAMP));
        cv.drawRect(0, ch - fh, cw, ch, p); p.setShader(null);
    }

    void drawSnow(float dt) {
        screen();
        float amt = snowy ? 1 : (mon == 4 || mon == 8 ? .25f : 0);
        if (amt == 0) return;
        int n = (int) (snow.size() * amt);
        for (int i = 0; i < n; i++) {
            float[] s = snow.get(i);
            s[1] += s[3] * dt; s[0] += (s[4] + (float) Math.sin(T + s[5]) * 6 * dp) * dt;
            if (s[1] > ch) { s[1] = -3; s[0] = rnd.nextFloat() * cw; }
            if (s[0] < -3) s[0] = cw + 2; else if (s[0] > cw + 3) s[0] = -2;
            ga = .4f + s[2] / dp * .25f; circ(s[0], s[1], s[2], Color.WHITE);
        }
        ga = 1;
    }

    void drawFloats(float dt) {
        screen();
        for (int i = floats.size() - 1; i >= 0; i--) {
            Floater f = floats.get(i); f.t += dt;
            if (f.t > 2.6f) { floats.remove(i); continue; }
            float k = f.t / 2.6f, y = ch * .42f - k * 40 * dp - i * 4 * dp;
            float a = k < .15f ? k / .15f : 1 - Math.max(0, (k - .6f) / .4f);
            p.setTextSize(Math.min(26 * dp, cw / 15)); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextAlign(Paint.Align.CENTER);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5 * dp); p.setColor(Color.argb((int) (140 * a), 0, 0, 0));
            cv.drawText(f.s, cw / 2, y, p);
            p.setStyle(Paint.Style.FILL); p.setColor((Math.round(255 * a) << 24) | (f.c & 0xFFFFFF));
            p.setShadowLayer(18 * dp, 0, 0, f.c); cv.drawText(f.s, cw / 2, y, p); p.clearShadowLayer();
        }
    }

    float dp = 2;
    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cw = w; ch = h; base = ch / 430f;
        dp = getResources().getDisplayMetrics().density;
        snow.clear();
        int n = (int) (cw * ch / (2600 * dp * dp));
        for (int i = 0; i < n; i++) snow.add(new float[]{rnd.nextFloat() * cw, rnd.nextFloat() * ch, (rnd.nextFloat() * 1.8f + .4f) * dp, (rnd.nextFloat() * 22 + 12) * dp, (rnd.nextFloat() * 10 - 5) * dp, rnd.nextFloat() * 6});
    }

    @Override
    protected void onDraw(Canvas canvas) {
        postInvalidateOnAnimation();
        if (cw <= 0 || ch <= 0 || g.chapter == null) return;
        long now = System.nanoTime();
        float dt = lastNs == 0 ? .016f : Math.min(.05f, (now - lastNs) / 1e9f);
        lastNs = now; T += dt;
        cv = canvas;

        Map<String, String> k = stateKeys();
        for (Map.Entry<String, String> e : k.entrySet()) {
            String key = e.getKey(), val = e.getValue(), old = keys.get(key);
            if (old == null) { keys.put(key, val); continue; }
            if (!old.equals(val)) {
                keys.put(key, val);
                if (val.equals("off") || val.equals("-1")) continue;
                anims.put(key, T + .5f);
                focus(key, 4200);
                final float[] s = spotFor(key);
                postDelayed(() -> { sparkle(s[0], s[1], s[2]); flash = 1; }, 500);
            }
        }
        flash = Math.max(0, flash - dt * 1.8f);

        float[] bd = bounds();
        if (T > until) { tz = 1; ty = 260; float vw0 = cw / (base * camZ); tx = (bd[0] + bd[1]) / 2 + (float) Math.sin(T * (g.act >= 2 ? .025f : .045f)) * Math.max(0, (bd[1] - bd[0]) / 2 - vw0 / 2 + 40) * .95f; }
        float e = 1 - (float) Math.exp(-dt * 2.2f);
        camX += (tx - camX) * e; camY += (ty - camY) * e; camZ += (tz - camZ) * e;
        float viewW = cw / (base * camZ), viewH = ch / (base * camZ);
        camX = Math.max(bd[0] + viewW / 2 - 40, Math.min(bd[1] + 40 - viewW / 2, camX));
        float maxY = GROUND + 78 - viewH / 2, minY = viewH / 2 + 30;
        camY = minY > maxY ? maxY : Math.max(minY, Math.min(maxY, camY));
        if (viewW > bd[1] - bd[0] + 80) camX = (bd[0] + bd[1]) / 2;

        mon = month(); snowy = mon >= 9 || mon <= 3; summer = mon >= 5 && mon <= 7;
        float litF = g.act >= 2 ? Math.min(.96f, .1f + .86f * (float) Math.pow(Math.min(1, g.pop / (double) Math.max(1, g.housing)), 1.5)) : Math.min(.96f, .015f + .95f * (float) Math.pow(g.pop / 50000f, .6));
        LITF = litF;
        glows.clear();
        baseSave = canvas.save();
        drawSky();
        drawFarHills();
        drawWind();
        drawAirport();
        drawMountain();
        drawHydro();
        drawFactories();
        drawTEC();
        drawSkyscrapers();
        drawPylons();
        drawNewBlocks(litF);
        drawMicro();
        drawTown(litF);
        drawCivic(litF);
        drawStreet();
        drawTram();
        drawRail();
        drawParts(dt);
        drawGlows();
        drawSmog();
        post();
        drawSnow(dt);
        drawFloats(dt);
        canvas.restoreToCount(baseSave);
    }
}
