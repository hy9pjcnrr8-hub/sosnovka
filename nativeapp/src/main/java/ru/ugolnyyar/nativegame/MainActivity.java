package ru.ugolnyyar.nativegame;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * «Угольный Яр» — нативная версия без WebView.
 * Сюжет идёт в отдельном потоке и ждёт ответа игрока через очередь;
 * весь интерфейс рисуется обычными View в главном потоке.
 */
public class MainActivity extends Activity {

    // ── цвета ──
    static final int BG = Color.parseColor("#0d1220");
    static final int PANEL = Color.parseColor("#151c2e");
    static final int LINE = Color.parseColor("#26304a");
    static final int TEXT = Color.parseColor("#e8ecf4");
    static final int DIM = Color.parseColor("#8a93a8");
    static final int GOLD = Color.parseColor("#f4c84a");
    static final int RED = Color.parseColor("#ff5a4f");
    static final int GREEN = Color.parseColor("#4fd17a");
    static final int BLUE = Color.parseColor("#6cc6ff");
    static final String C_GOLD = "#f4c84a", C_RED = "#ff5a4f", C_GREEN = "#4fd17a", C_BLUE = "#6cc6ff", C_DIM = "#8a93a8";

    // ── виды текста ──
    static final int P = 0, H = 1, BIG = 2, BIGR = 3, STAMP = 4, QUOTE = 5, DIMK = 6, DELTA = 7, GOLDK = 8, REDK = 9, GREENK = 10;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final BlockingQueue<Object> answers = new LinkedBlockingQueue<>();

    private LinearLayout logBox, ctl;
    private ScrollView scroll;
    private TextView hud1, hud2, hud3;
    private BarView bar;
    CityView city;
    int stageH; LinearLayout colView;
    private Thread gameThread;
    /** Скрытый режим для автотеста: игра сама выбирает варианты. */
    volatile boolean autoplay = false;
    /** Скрытый тест: старт сразу в Части III. */
    volatile boolean a3test = false;
    void setupA3Test() {
        act = 3; step = 13; deadline = -1; goal = 1000000; month = 1; year = 1; day = 2100;
        pop = 760000; budget = 5_000_000_000.0; trust = 95; housing = 900000; jobs = 700000;
        factories = 60; blocks = 20; micro = 30; schools = 55; hospitals = 20; parks = 10; malls = 10; capBonus = 6;
        heat = "coal"; road = 2; roadSet = true; light = "trafo"; mine = "alive"; mineOpen = true; water = "deep"; comm = "tower"; shops = "own";
        lift = "winch"; ratrak = true; hotel = true; housingChoice = "people"; matvey = true;
        rail = "electric"; airport = "full"; uni = true; med = true; tram = true; cup = true; energy = "hydro"; museum = true; north = "dev";
        income.put("Заводы", 120_000_000L);
        done.add("free");
        chapter = "ГОРОД";
    }

    // ───────────────────────── интерфейс ─────────────────────────

    int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    static CharSequence html(String s) {
        return Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY);
    }

    GradientDrawable box(int fill, int stroke, float radius, int strokeW) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (strokeW > 0) d.setStroke(dp(strokeW), stroke);
        return d;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // сцена: нарисованный город + HUD поверх
        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(Color.parseColor("#070b1d"));
        city = new CityView(this, this);
        stage.addView(city, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout hud = new LinearLayout(this);
        hud.setOrientation(LinearLayout.VERTICAL);
        hud.setPadding(dp(10), dp(8), dp(10), dp(14));
        hud.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(215, 7, 10, 22), Color.argb(110, 7, 10, 22), Color.argb(0, 7, 10, 22)}));
        hud1 = hudText(hud);
        hud2 = hudText(hud);
        hud3 = hudText(hud);
        hud3.setVisibility(View.GONE);
        bar = new BarView(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(5));
        blp.topMargin = dp(6);
        hud.addView(bar, blp);
        stage.addView(hud, new FrameLayout.LayoutParams(-1, -2));
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int stageH = Math.max(dp(230), Math.min(dp(460), (int) (screenH * 0.44f)));
        col.addView(stage, new LinearLayout.LayoutParams(-1, stageH));
        this.stageH = stageH; colView = col;

        // лог
        scroll = new ScrollView(this);
        logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        logBox.setPadding(dp(16), dp(8), dp(16), dp(10));
        scroll.addView(logBox, new ScrollView.LayoutParams(-1, -2));
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // кнопки
        View sep2 = new View(this);
        sep2.setBackgroundColor(LINE);
        col.addView(sep2, new LinearLayout.LayoutParams(-1, 1));
        ctl = new LinearLayout(this);
        ctl.setOrientation(LinearLayout.VERTICAL);
        ctl.setPadding(dp(12), dp(10), dp(12), dp(12));
        ctl.setBackgroundColor(Color.argb(235, 13, 18, 32));
        col.addView(ctl, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);

        autoplay = getIntent() != null && getIntent().getBooleanExtra("autoplay", false);
        a3test = getIntent() != null && getIntent().getBooleanExtra("a3test", false);
        gameThread = new Thread(this::gameLoop, "game");
        gameThread.start();
    }

    TextView hudText(LinearLayout parent) {
        TextView t = new TextView(this);
        t.setTextColor(DIM);
        t.setTextSize(11.5f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setShadowLayer(dp(3), 0, 0, Color.BLACK);
        t.setPadding(0, dp(1), 0, dp(1));
        parent.addView(t);
        return t;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gameThread != null) gameThread.interrupt();
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("Выйти из игры?")
                .setMessage("Прогресс текущей партии не сохранится.")
                .setPositiveButton("Выйти", (d, i) -> finish())
                .setNegativeButton("Остаться", null)
                .show();
    }

    void addLine(String text, int kind) {
        TextView t = new TextView(this);
        t.setText(html(text));
        t.setTextColor(TEXT);
        t.setTextSize(15);
        t.setLineSpacing(0, 1.12f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        View view = t;
        switch (kind) {
            case H:
                t.setText(html(text.toUpperCase()));
                t.setTextColor(GOLD);
                t.setTextSize(20);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setGravity(Gravity.CENTER);
                t.setShadowLayer(0, 0, dp(2), Color.BLACK);
                lp.topMargin = dp(4);
                lp.bottomMargin = dp(14);
                break;
            case BIG:
            case BIGR:
                t.setTextColor(kind == BIG ? GOLD : RED);
                t.setTextSize(32);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setGravity(Gravity.CENTER);
                t.setShadowLayer(0, 0, dp(3), Color.BLACK);
                break;
            case STAMP: {
                t.setTextColor(RED);
                t.setTextSize(20);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setGravity(Gravity.CENTER);
                t.setPadding(dp(18), dp(8), dp(18), dp(8));
                t.setBackground(box(Color.TRANSPARENT, RED, 6, 4));
                t.setRotation(-6);
                LinearLayout wrap = new LinearLayout(this);
                wrap.setGravity(Gravity.CENTER);
                wrap.setPadding(0, dp(10), 0, dp(10));
                wrap.addView(t, new LinearLayout.LayoutParams(-2, -2));
                view = wrap;
                break;
            }
            case QUOTE:
                t.setText(html("«" + text + "»"));
                t.setTextColor(BLUE);
                t.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));
                t.setPadding(dp(12), dp(2), 0, dp(2));
                GradientDrawable border = new GradientDrawable();
                border.setColor(Color.argb(30, 108, 198, 255));
                border.setCornerRadius(dp(4));
                t.setBackground(border);
                break;
            case DIMK:
                t.setTextColor(DIM);
                t.setTextSize(14);
                break;
            case DELTA:
                t.setTextSize(14);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                lp.topMargin = -dp(4);
                lp.bottomMargin = dp(8);
                break;
            case GOLDK: t.setTextColor(GOLD); t.setTypeface(Typeface.DEFAULT_BOLD); break;
            case REDK: t.setTextColor(RED); t.setTypeface(Typeface.DEFAULT_BOLD); break;
            case GREENK: t.setTextColor(GREEN); t.setTypeface(Typeface.DEFAULT_BOLD); break;
            default: break;
        }
        view.setAlpha(0f);
        view.setTranslationY(dp(8));
        logBox.addView(view, lp);
        view.animate().alpha(1f).translationY(0).setDuration(320).start();
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    Button makeButton(String text, String costLine, int costColor, boolean enabled, int style) { return makeButton(text, null, costLine, costColor, enabled, style); }
    Button makeButton(String text, String sub, String costLine, int costColor, boolean enabled, int style) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setGravity(style == 0 ? (Gravity.START | Gravity.CENTER_VERTICAL) : Gravity.CENTER);
        b.setPadding(dp(13), dp(10), dp(13), dp(10));
        b.setStateListAnimator(null);
        String full = escape(text);
        if (sub != null) full = "<b>" + full + "</b><br><small><font color='#8a93a8'>" + escape(sub) + "</font></small>";
        if (costLine != null) {
            full += "<br><font color='" + String.format("#%06X", 0xFFFFFF & costColor) + "'><b><small>" + costLine + "</small></b></font>";
        }
        b.setText(html(full));
        if (style == 1) {          // главная кнопка «Дальше»
            b.setBackground(box(GOLD, GOLD, 12, 0));
            b.setTextColor(Color.parseColor("#1a1405"));
            b.setTypeface(Typeface.DEFAULT_BOLD);
        } else if (style == 2) {   // «Завершить месяц»
            b.setBackground(box(PANEL, GOLD, 12, 1));
            b.setTextColor(GOLD);
            b.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            b.setBackground(box(PANEL, LINE, 12, 1));
            b.setTextColor(TEXT);
        }
        b.setEnabled(enabled);
        if (!enabled) b.setAlpha(0.45f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(7);
        b.setLayoutParams(lp);
        return b;
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ───────────────────────── мост между потоками ─────────────────────────

    static class Quit extends RuntimeException {}
    static class Bankrupt extends RuntimeException {}

    void post(Runnable r) { ui.post(r); }

    void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { throw new Quit(); }
    }

    Object take() {
        try { return answers.take(); } catch (InterruptedException e) { throw new Quit(); }
    }

    void say(String text, int kind, long wait) {
        hud();
        post(() -> addLine(text, kind));
        sleep(autoplay ? wait / 4 : wait);
    }
    void say(String text) { say(text, P, 260); }
    void say(String text, int kind) {
        long w = (kind == H) ? 350 : (kind == BIG || kind == BIGR) ? 400 : (kind == STAMP) ? 450 : (kind == QUOTE) ? 320 : 260;
        say(text, kind, w);
    }

    void clear() { post(() -> logBox.removeAllViews()); }
    void focus(String key) { post(() -> city.focus(key, 6000)); }

    void pause(String label) {
        answers.clear();
        if (autoplay) { sleep(700); clear(); return; }
        post(() -> {
            ctl.removeAllViews();
            Button b = makeButton(label, null, 0, true, 1);
            b.setOnClickListener(v -> { ctl.removeAllViews(); answers.offer(1); });
            ctl.addView(b);
        });
        take();
        clear();
    }
    void pause() { pause("Дальше →"); }

    static class Opt {
        final String text, key, sub; final long cost; final int style;
        Opt(String text, long cost, String key) { this(text, null, cost, key, 0); }
        Opt(String text, long cost, String key, int style) { this(text, null, cost, key, style); }
        Opt(String text, String sub, long cost, String key) { this(text, sub, cost, key, 0); }
        Opt(String text, String sub, long cost, String key, int style) { this.text = text; this.sub = sub; this.cost = cost; this.key = key; this.style = style; }
    }

    String choose(String title, Opt[] opts, String[] caps) { return choose(title, opts, caps, false); }
    String choose(String title, Opt[] opts, String[] caps, boolean grid) {
        hud();
        answers.clear();
        final long budgetNow = (long) budget;
        post(() -> {
            ctl.removeAllViews();
            if (caps != null) {
                LinearLayout row = null;
                for (int i = 0; i < caps.length; i++) {
                    if (i % 3 == 0) {
                        row = new LinearLayout(this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                        rlp.bottomMargin = dp(6);
                        ctl.addView(row, rlp);
                    }
                    TextView c = new TextView(this);
                    c.setText(html(caps[i]));
                    c.setTextColor(DIM);
                    c.setTextSize(11.5f);
                    c.setPadding(dp(8), dp(5), dp(8), dp(5));
                    c.setBackground(box(PANEL, LINE, 10, 1));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                    if (i % 3 > 0) lp.leftMargin = dp(6);
                    row.addView(c, lp);
                }
                if (caps.length % 3 != 0) for (int i = caps.length % 3; i < 3; i++) { View sp = new View(this); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f); lp.leftMargin = dp(6); row.addView(sp, lp); }
            }
            TextView q = new TextView(this);
            q.setText("▶ " + title);
            q.setTextColor(TEXT);
            q.setTextSize(15);
            q.setTypeface(Typeface.DEFAULT_BOLD);
            q.setPadding(0, 0, 0, dp(7));
            ctl.addView(q);
            ScrollView sv = new ScrollView(this);
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            sv.addView(list);
            LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(-1, -2);
            ctl.addView(sv, svlp);
            LinearLayout gridRow = null; int inRow = 0;
            java.util.List<Button> pinnedList = new java.util.ArrayList<>();
            for (Opt o : opts) {
                boolean afford = o.cost <= 0 || budgetNow >= o.cost;
                String costLine = null;
                int costColor = GOLD;
                if (o.cost >= 100_000_000_000L) { costLine = "недоступно"; costColor = RED; afford = false; }
                else if (o.cost > 0) {
                    costLine = money(o.cost) + (afford ? "" : " · не хватает");
                    costColor = afford ? GOLD : RED;
                } else if (o.cost < 0) {
                    costLine = "+" + money(-o.cost);
                    costColor = GREEN;
                }
                Button b = makeButton(o.text, o.sub, costLine, costColor, afford, o.style);
                b.setOnClickListener(v -> { ctl.removeAllViews(); answers.offer(o); });
                if (o.key.startsWith("end")) { pinnedList.add(b); continue; }
                if (grid) {
                    b.setTextSize(13.5f);
                    b.setPadding(dp(10), dp(8), dp(10), dp(8));
                    if (inRow == 0) { gridRow = new LinearLayout(this); gridRow.setOrientation(LinearLayout.HORIZONTAL); list.addView(gridRow, new LinearLayout.LayoutParams(-1, -2)); }
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
                    lp.bottomMargin = dp(6); if (inRow == 1) lp.leftMargin = dp(6);
                    gridRow.addView(b, lp);
                    inRow = (inRow + 1) % 2;
                } else list.addView(b);
            }
            if (grid && inRow == 1) { View sp = new View(this); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f); lp.leftMargin = dp(6); gridRow.addView(sp, lp); }
            if (!pinnedList.isEmpty()) {
                LinearLayout prow = new LinearLayout(this);
                prow.setOrientation(LinearLayout.HORIZONTAL);
                for (int pi = 0; pi < pinnedList.size(); pi++) {
                    Button pb = pinnedList.get(pi);
                    if (pinnedList.size() > 1) { pb.setTextSize(14); pb.setPadding(dp(8), dp(10), dp(8), dp(10)); }
                    LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(0, -2, 1f);
                    if (pi > 0) blp2.leftMargin = dp(7);
                    prow.addView(pb, blp2);
                }
                LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
                plp.topMargin = dp(6); plp.bottomMargin = 0;
                ctl.addView(prow, plp);
            }
            final int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.34f);
            sv.post(() -> {
                int h = Math.min(sv.getHeight(), maxH);
                int avail = colView.getHeight() - stageH - dp(44);
                int over = ctl.getHeight() - (sv.getHeight() - h) - avail;
                if (over > 0) h = Math.max(dp(64), h - over);
                if (h < sv.getHeight()) { svlp.height = h; sv.setLayoutParams(svlp); }
            });
        });
        Opt o;
        if (autoplay) {
            sleep(600);
            Opt pick = null;
            if (title.startsWith("Что строим")) {
                String want;
                if (act >= 2 && schools < needSchools()) want = "school";
                else if (act >= 2 && hospitals < needHosp()) want = "hosp";
                else want = support() <= housing ? "factory" : (north != null ? "micro" : "block");
                for (Opt x : opts) if (x.key.equals(want) && x.cost < 100_000_000_000L && budget >= x.cost) pick = x;
                if (pick == null) for (Opt x : opts) if ((x.key.equals("block") || x.key.equals("factory")) && x.cost < 100_000_000_000L && budget >= x.cost) pick = x;
                if (pick == null) pick = opts[opts.length - 1];
            } else for (Opt x : opts) if (x.cost <= 0 || budget >= x.cost) { pick = x; break; }
            final Opt fp = pick;
            post(() -> ctl.removeAllViews());
            o = fp;
        } else o = (Opt) take();
        budget -= o.cost;
        hud();
        return o.key;
    }
    String choose(String title, Opt... opts) { return choose(title, opts, null); }

    // ───────────────────────── состояние ─────────────────────────

    static final int DEADLINE = 365, GOAL = 50000;

    volatile int day, pop, trust, factories, blocks, ads;
    double budget;
    long housing, jobs;
    final Map<String, Long> income = new LinkedHashMap<>();
    volatile String chapter, heat, light, water, comm, shops, lift, housingChoice, mine;
    int road;
    volatile boolean matvey, mineOpen, frozen, ms10, ms25, roadSet, ratrak, hotel, ventLit, matveyVis;
    // Часть II и дальше
    volatile int act = 1, goal = GOAL, deadline = DEADLINE, step = 0, month = 0, year = 1;
    volatile int schools, hospitals, parks, malls, micro, capBonus;
    volatile double smog;
    volatile String rail, energy, airport, mineFate, north;
    volatile boolean uni, med, tram, museum, demolished, cup, filters;
    final java.util.Set<String> done = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    final Map<String, Integer> lastEv = new java.util.HashMap<>();

    void newGame() {
        day = 0; budget = 12_000_000; pop = 312; trust = 50; chapter = "ПРОЛОГ";
        housing = 0; jobs = 0; factories = 0; blocks = 0; ads = 0;
        income.clear();
        heat = light = water = comm = shops = housingChoice = mine = null;
        lift = "none"; road = 0;
        matvey = mineOpen = frozen = ms10 = ms25 = false;
        roadSet = ratrak = hotel = ventLit = matveyVis = false;
        act = 1; goal = GOAL; deadline = DEADLINE; step = 0; month = 0; year = 1;
        schools = hospitals = parks = malls = micro = capBonus = 0; smog = 0;
        rail = energy = airport = mineFate = north = null;
        uni = med = tram = museum = demolished = cup = filters = false;
        done.clear(); lastEv.clear();
        a3metro = a3bridge = a3tower = a3games = a3matvey = null; a3vsm = false; a3gamesDay = 0; a3boost = 0;
        post(() -> city.reset());
    }

    int daysLeft() { return deadline < 0 ? 99999 : Math.max(0, deadline - day); }
    long monthly() {
        long s = 0;
        for (long v : income.values()) s += v;
        return s + pop * 25L;
    }
    boolean can(long x) { return budget >= x; }

    static String fmt(double n) {
        long v = Math.round(Math.abs(n));
        String s = Long.toString(v);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) sb.append(' ');
            sb.append(s.charAt(i));
        }
        return (n < 0 && v != 0 ? "−" : "") + sb;
    }
    static String money(double n) { return fmt(n) + " ₽"; }
    static String col(String color, String s) { return "<font color='" + color + "'><b>" + s + "</b></font>"; }

    void hud() {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.NOVEMBER, 13);
        c.add(Calendar.DAY_OF_YEAR, day);
        String date = String.format("%02d.%02d.%02d", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR) % 100);
        long inc = monthly();
        String when = deadline < 0 ? date + " · ГОД " + year : date + " · " + daysLeft() + " ДН.";
        String l1 = col(C_RED, when) + "   " + col(C_GOLD, chapter) + "   " + col(C_BLUE, "♥ " + trust) + (smog > 15 ? "   " + col("#c9a27a", "🌫 " + Math.round(smog)) : "");
        String l2 = col(C_GREEN, "👥 " + fmt(pop) + " / " + fmt(goal)) + "   " + col(C_GOLD, money(budget)) + " " + col(inc >= 0 ? C_GREEN : C_RED, (inc >= 0 ? "+" : "") + fmt(inc) + "/мес");
        String l3 = "";
        float frac = Math.min(1f, pop / (float) goal);
        post(() -> {
            hud1.setText(html(l1));
            hud2.setText(html(l2));
            hud3.setText(html(l3));
            bar.setFraction(frac);
        });
    }

    void advance(int days, boolean quiet) {
        days = Math.min(days, daysLeft());
        budget += monthly() * days / 30.0;
        day += days;
        if (!quiet && days > 0) say("… прошло " + days + " дн.", DIMK, 200);
        hud();
        if (budget < 0) throw new Bankrupt();
    }
    void advance(int days) { advance(days, false); }

    void addPop(long n, String why) {
        int old = pop;
        pop = (int) Math.max(0, pop + n);
        int d = pop - old;
        if (d != 0) { final String fs = (d > 0 ? "+" : "−") + fmt(Math.abs(d)) + " жителей"; final int fc = d > 0 ? GREEN : RED; post(() -> city.addFloat(fs, fc)); }
        if (d != 0) say(col(d > 0 ? C_GREEN : C_RED, (d > 0 ? "+" : "−") + fmt(Math.abs(d)) + " жителей") + " <font color='" + C_DIM + "'>" + why + "</font>", DELTA, 300);
    }

    void addTrust(int n) {
        trust = Math.max(0, Math.min(100, trust + n));
        say(col(n > 0 ? C_BLUE : C_RED, "доверие жителей " + (n > 0 ? "+" : "") + n), DELTA, 250);
    }

    // ───────────────────────── главы ─────────────────────────

    void prologue() {
        clear();
        hud();
        say("Угольный Яр", H);
        say("ЖИТЕЛЕЙ", DIMK);
        say("50 000", BIG);
        say("Столько людей жило здесь, когда работала шахта «Северная».");
        say("312", BIGR);
        say("Всего триста двенадцать. Тёмные окна, пустые подъезды.");
        say("ЗАКРЫТО · 2014", STAMP);
        say("Шахту закрыли. Работы нет. Молодые уехали. Через год, говорят, никто не останется.");
        say("Тебя назначили главой посёлка. Условие губернатора: <b>за 365 дней вернуть городу 50 000 жителей</b>. Иначе Угольный Яр расселят и сотрут с карты.");
        say("На счету посёлка: " + col(C_GOLD, money(budget)) + ".");
        pause("Начать →");
    }

    void ch1() {
        chapter = "1. ТЕПЛО";
        focus("boiler");
        say("Часть 1 · Тепло", H);
        say("Ноябрь. −34°. Котельная стоит с 2014-го, в квартирах — как на улице.");
        say("На складе шахты лежит гора угля, которую так и не вывезли:");
        say("100 000 т угля", BIG);
        heat = choose("Как дать людям тепло?",
                new Opt("Восстановить старую угольную котельную (уголь со склада бесплатный)", 5_200_000, "coal"),
                new Opt("Поставить электрокотлы (быстрее, но дорогое электричество)", 3_000_000, "elec"),
                new Opt("Раздать буржуйки и дрова — пусть греются сами", 600_000, "stove"));
        if (heat.equals("coal")) {
            advance(37);
            say("Трубы гудят. Над котельной впервые за 12 лет поднимается дым.");
            say("Дед Матвей, бывший горный мастер, стоит у окна в шапке-ушанке:");
            say("Двенадцать лет не слышал, как она шумит…", QUOTE);
            addTrust(15);
        } else if (heat.equals("elec")) {
            advance(25);
            income.put("Электрокотлы", -250_000L);
            say("В квартирах тепло, но счёт за электричество кусается.");
            addTrust(5);
        } else {
            advance(10);
            say("Люди топят буржуйки, в подъездах пахнет дымом. Несколько семей не выдерживают.");
            addPop(-60, "уехали от холода");
            addTrust(-15);
        }
        pause();
    }

    void ch2() {
        chapter = "2. ДОРОГА";
        focus("road");
        say("Часть 2 · Дорога", H);
        say("До трассы — 60 километров. Зимой дорогу заметает так, что даже вахтовый автобус не проходит неделями.");
        say("60 КМ", BIG);
        String k = choose("Что делать с дорогой?",
                new Opt("Положить новый асфальт на все 60 км", 4_500_000, "asphalt"),
                new Opt("Отсыпать грейдером и поставить снегозащитные щиты", 1_500_000, "shields"),
                new Opt("Оставить зимник как есть", 0, "none"));
        road = k.equals("asphalt") ? 2 : k.equals("shields") ? 1 : 0;
        roadSet = true;
        if (road == 2) {
            advance(45);
            say("Ровная чёрная лента от трассы до самого посёлка.");
        } else if (road == 1) {
            advance(30);
            say("Вдоль дороги встают снегозащитные щиты. Метель бьётся о них — а дорога остаётся чистой.");
        } else {
            advance(5);
            income.put("Доставка по зимнику", -100_000L);
            say("Дорога снова под снегом. Продукты везут втридорога.");
            addTrust(-5);
        }
        if (road >= 1) {
            say("К посёлку подъезжает жёлтый автобус с табличкой «ВАХТА».");
            addPop(road == 1 ? 100 : 140, "приехали работать");
        }
        pause();
    }

    void ch3() {
        chapter = "3. СВЕТ";
        focus("light");
        say("Часть 3 · Свет", H);
        say("Свет в посёлке гаснет каждый вечер. Высокая линия ЛЭП цела — значит, дело в подстанции.");
        say("Открываешь дверь трансформаторной будки…");
        say("⚠ ВНУТРИ — ПУСТО", BIGR);
        say("Медь, провода, трансформатор — всё давно сдано на металлолом.");
        light = choose("Как вернуть свет?",
                new Opt("Купить и поставить новый трансформатор", 2_400_000, "trafo"),
                new Opt("Дизель-генераторы (дёшево, но солярка каждый месяц)", 700_000, "diesel"));
        if (light.equals("trafo")) {
            advance(30);
            say("Щелчок рубильника — и в окнах ДК «Шахтёр» загорается свет.");
            addTrust(10);
        } else {
            advance(10);
            income.put("Солярка", -250_000L);
            say("Генераторы тарахтят на весь посёлок, но свет есть.");
        }
        addPop(128, "вернулись, узнав, что есть свет");
        pause();
    }

    void ch4() {
        chapter = "4. ШАХТА?";
        focus("mine");
        say("Часть 4 · Шахта?", H);
        say("Шахта «Северная» закрыта двенадцать лет. Ворота заварены, табличка «ВХОД ЗАПРЕЩЁН».");
        say("А этой ночью, в 02:14, ты возвращаешься из райцентра и видишь у вентиляционного ствола…");
        ventLit = true;
        say("…тёплый свет в окне. И дым из трубы.", GOLDK);
        pause();
        say("Ты подходишь к двери. Дёргаешь ручку — открыто.");
        say("На столе — толстая тетрадь: <b>«ЖУРНАЛ ВЕНТИЛЯТОРА»</b>. Последняя запись — всего несколько часов назад:");
        say("23:40", BIGR);
        say("Насосы в норме. Смену сдал.", QUOTE);
        say("В полу — <b>люк</b>. Снизу доносится гул насосов. Кто-то спускается туда каждую ночь. Двенадцать лет.");
        String k = choose("Что будешь делать?",
                new Opt("Дождаться у люка и встретить того, кто выйдет", 0, "meet"),
                new Opt("Вызвать полицию — посторонним в шахте не место", 0, "police"),
                new Opt("Уйти и сделать вид, что ничего не видел", 0, "ignore"));
        advance(1, true);
        if (k.equals("meet")) {
            matveyVis = true;
            say("03:00. Люк скрипит. Из темноты поднимается каска с фонарём. Свет падает на лицо…");
            say("ДЕД МАТВЕЙ", BIG);
            say("Если насосы встанут — шахту затопит за неделю. Я её двенадцать лет держу. Думал, вернутся люди…", QUOTE);
            say("Шахта жива. Всё это время её спасал один старик.");
            mine = "alive"; matvey = true;
            addTrust(15);
        } else if (k.equals("police")) {
            ventLit = false;
            say("Приезжает наряд. Матвея — а это был он — выводят под руки. Ночью насосы никто не включает.");
            say("ШАХТА ЗАТОПЛЕНА", STAMP);
            say("Посёлок узнаёт, кто вызвал полицию на старика.");
            mine = "flooded"; matvey = false;
            addTrust(-25);
        } else {
            say("Через месяц выясняется: это был дед Матвей, и шахта цела только благодаря ему. Но он уже не верит, что ты на стороне людей.");
            mine = "alive"; matvey = false;
        }
        pause();
        say("Утро. У ворот шахты собираются мужики в старых касках.");
        say("Нужна работа. Других работ нет.", H);
        say("Рядом — электростанция, которой нужен уголь.");
        String k2;
        if (mine.equals("alive")) {
            k2 = choose("Что делать с шахтой?",
                    new Opt("Открыть угольный разрез и возить уголь на электростанцию", 1_800_000, "open"),
                    new Opt("Сдать оборудование на металлолом", -1_500_000, "scrap"));
        } else {
            say("Шахта затоплена. Остаётся только металлолом.", REDK);
            k2 = choose("Что делать?", new Opt("Сдать оборудование на металлолом", -1_200_000, "scrap"));
        }
        mineOpen = k2.equals("open");
        if (mineOpen) {
            advance(25);
            income.put("Уголь", 700_000L);
            jobs += 700;
            say("Самосвалы с углём идут на электростанцию. Первые зарплаты.");
            addPop(320, "шахтёры с семьями");
            addTrust(10);
        } else {
            advance(10);
            say("Подъёмные машины режут автогеном. Мужики молча расходятся.");
            addPop(-40, "уехали искать работу");
            addTrust(-10);
        }
        pause();
    }

    void ch5() {
        chapter = "5. ВОДОПРОВОД";
        focus("water");
        say("Часть 5 · Вода", H);
        say("Открываешь кран. Кран сухой.");
        say("ВОДЫ НЕТ · С 2014", STAMP);
        if (matvey) say("12 лет воду вёдрами носил.", QUOTE);
        say("В январе здесь <b>−41°</b>. Старая труба лежала на 2 метрах — а земля промерзает глубже. Ковш на трёх метрах звякает о железо: старый трубопровод.");
        say("Насос, провода, трансформатор насосной — " + col(C_RED, "тоже сдали на металлолом") + ".");
        water = choose("Как провести воду?",
                new Opt("Пластиковая труба с утеплителем на 3 метрах + новый насос", 800_000, "deep"),
                new Opt("Положить трубу по-старому, на 2 метрах (дешевле)", 400_000, "shallow"),
                new Opt("Наладить подвоз воды водовозкой", 100_000, "truck"));
        if (water.equals("deep")) {
            advance(20);
            say("Из крана с хрипом идёт ржавая вода… потом — чистая.");
            addTrust(10);
        } else if (water.equals("shallow")) {
            advance(12);
            say("Вода есть. Пока тепло.");
        } else {
            advance(5);
            income.put("Водовозка", -120_000L);
            say("По дворам ездит бочка. Очередь с вёдрами.");
            addTrust(-5);
        }
        pause();
        int good = 0;
        if (heat.equals("coal") || heat.equals("elec")) good++;
        if (light.equals("trafo")) good++;
        if (water.equals("deep") || water.equals("shallow")) good++;
        if (road >= 1) good++;
        say("В область уходит отчёт: тепло, свет, дорога, вода.");
        long grant;
        if (good >= 3) { grant = 12_000_000; say("Губернатор впечатлён. Программа поддержки моногородов:"); }
        else if (good >= 2) { grant = 7_000_000; say("В области говорят «неплохо». Выделяют часть денег:"); }
        else { grant = 3_000_000; say("В области недовольны, но дают минимум:"); }
        budget += grant;
        say("+" + money(grant), BIG);
        addPop(400 + good * 120L, "узнали, что в Яру снова можно жить");
        pause();
    }

    void ch6() {
        chapter = "6. СВЯЗЬ";
        focus("shops");
        say("Часть 6 · Магазины и связь", H);
        say("Новые жители задают два вопроса:");
        say("«Где связь?» «Где магазины?»", GOLDK);
        say("Ближайшая вышка — у трассы, в 60 км.");
        comm = choose("Связь:",
                new Opt("Построить свою вышку и протянуть оптику вдоль трассы", 900_000, "tower"),
                new Opt("Спутниковый интернет в ДК (дёшево, но слабо)", 300_000, "sat"),
                new Opt("Обойдутся", 0, "none"));
        if (comm.equals("tower")) {
            advance(15);
            say("На телефонах появляются полоски сети. В чатах — фото с подписью «я дома».");
            addTrust(5);
        } else if (comm.equals("sat")) {
            advance(5);
            income.put("Спутник", -50_000L);
            say("В ДК стоит очередь к единственному Wi-Fi.");
        } else {
            say("Связи нет. Молодёжь крутит пальцем у виска.");
            addTrust(-5);
        }
        say("Сети магазинов отвечают одинаково:");
        say("2,6 %", BIGR);
        say("Жителей — 2,6 % от нужного. Не приведём.", QUOTE);
        shops = choose("Магазины:",
                new Opt("Отремонтировать первые этажи и сдать за 1 ₽ — пекарня, аптека, парикмахерская", 2_000_000, "own"),
                new Opt("Доплачивать сети, чтобы зашла (субсидия каждый месяц)", 300_000, "subsidy"),
                new Opt("Пусть ездят в райцентр", 0, "none"));
        if (shops.equals("own")) {
            advance(20);
            jobs += 150;
            say("Пахнет хлебом. Над дверью — «ПЕКАРНЯ». Рядом — «АПТЕКА».");
            addTrust(10);
        } else if (shops.equals("subsidy")) {
            advance(15);
            income.put("Субсидия сети", -200_000L);
            jobs += 80;
            say("Открылся сетевой магазин. Полки полные, ценник — городской.");
        } else {
            advance(3);
            say("За хлебом — 60 км. Кто-то собирает чемоданы.");
            addPop(-80, "уехали из-за бытовухи");
        }
        pause();
    }

    void ch7() {
        chapter = "7. КУРОРТ";
        focus("lift");
        say("Часть 7 · Курорт", H);
        say("За посёлком — гора. Снег лежит <b>7 месяцев в году</b>. Лучшие склоны в области — и ни одного подъёмника.");
        say("Новый подъёмник стоит " + col(C_RED, "60 000 000 ₽") + ". У нас — " + col(C_GOLD, money(budget)) + ".");
        java.util.List<Opt> opts = new java.util.ArrayList<>();
        if (mine.equals("alive")) {
            say("Захар, механик шахты, чешет затылок:");
            say("Поставим от шахтной лебёдки. 1957 года, но она вечная.", QUOTE);
            long cost = matvey ? 1_200_000 : 2_200_000;
            if (matvey) say("Матвей за тебя поручился — Захар работает почти даром.", DIMK);
            opts.add(new Opt("Подъёмник на шахтной лебёдке 1957 года", cost, "winch"));
        }
        opts.add(new Opt("Купить б/у подъёмник из Европы", 7_000_000, "used"));
        opts.add(new Opt("Забыть про курорт", 0, "none"));
        lift = choose("Подъёмник:", opts.toArray(new Opt[0]), null);
        if (lift.equals("none")) {
            say("Гора остаётся просто горой.");
            pause();
            return;
        }
        advance(20);
        say("Тросы натянуты. Кресла поползли вверх.");
        say("Но склоны надо ровнять. " + col(C_GOLD, "Нужен ратрак") + " — машина, которая ровняет снег.");
        String k2 = choose("Ратрак:",
                new Opt("Купить новый ратрак", 3_000_000, "new"),
                new Opt("Сдать старое железо шахты на лом (+1,5 млн) и купить б/у ратрак (−1,2 млн)", -300_000, "scrap"));
        ratrak = true;
        if (k2.equals("scrap")) say("Старый ратрак ревёт и дымит. Ездит — и ладно.");
        advance(10);
        say("Остаётся гостиница «Яр». Стены облупились, окна заколочены.");
        String k3 = choose("Гостиница:",
                new Opt("Покрасить стены, вставить окна, открыть 40 номеров", 500_000, "hotel"),
                new Opt("Туристы пусть ездят на день", 0, "none"));
        hotel = k3.equals("hotel");
        advance(10);
        double base = lift.equals("winch") ? 1_600_000 : 2_400_000;
        double roadMult = road == 0 ? 0.4 : road == 1 ? 0.8 : 1.0;
        double hotelMult = k3.equals("hotel") ? 1.25 : 0.8;
        long rev = Math.round(base * roadMult * hotelMult / 10000) * 10000;
        income.put("Курорт", rev);
        jobs += 900;
        say("За первый месяц курорт приносит " + col(C_GREEN, money(rev)) + ".");
        if (road == 0) say("Туристы жалуются на дорогу — доезжают не все.", DIMK);
        addPop(250, "инструкторы, повара, горничные");
        pause();
    }

    void ch8() {
        chapter = "8. ЖИЛЬЁ";
        focus("renov");
        say("Часть 8 · Жильё", H);
        say("Курорт заметили в Москве. Приходит федеральный грант:");
        budget += 20_000_000;
        say("+20 000 000 ₽", BIG);
        say("Хватит, чтобы отремонтировать <b>300 квартир</b> — целый квартал.");
        say("Но туристическая фирма предлагает: «Отдайте квартиры под апартаменты. Будете получать каждый месяц».");
        housingChoice = choose("ТВОЙ ВЫБОР — кому отдать квартиры?",
                new Opt("А. ЖИТЕЛЯМ — семьи, которые хотят вернуться", 15_000_000, "people"),
                new Opt("Б. ТУРИСТАМ — апартаменты и доход каждый месяц", 15_000_000, "tourists"));
        advance(30);
        if (housingChoice.equals("people")) {
            say("В подъездах снова пахнет краской и пирогами. Ключи — на руках.");
            addPop(1900, "семьи въехали в квартиры");
            housing = pop + 1500;
            addTrust(15);
        } else {
            say("Апартаменты заполняются лыжниками. Местные смотрят на это молча.");
            income.put("Апартаменты", 1_000_000L);
            addPop(400, "персонал апартаментов");
            housing = pop + 600;
            addTrust(-10);
        }
        pause();
    }

    // ───────────────────────── финальная фаза ─────────────────────────

    static final long FACTORY_COST = 3_500_000, FACTORY_JOBS = 7_500, FACTORY_INCOME = 600_000;
    static final long BLOCK_COST = 3_000_000, BLOCK_HOMES = 7_500, AD_COST = 800_000;
    static final String[] FACTORY_NAMES = {
            "Углеобогатительная фабрика", "Лесопильный завод", "Завод стройматериалов",
            "Цех ремонта горной техники", "Тепличный комбинат", "Завод минеральной воды",
            "Дата-центр на угольном электричестве", "Мебельная фабрика", "Завод металлоконструкций",
            "Логистический хаб", "Швейная фабрика", "Завод угольных брикетов"};

    double quality() {
        double q = 0;
        q += heat.equals("coal") ? .10 : heat.equals("elec") ? .08 : -.10;
        q += road == 0 ? -.10 : road == 1 ? .05 : .10;
        q += light.equals("trafo") ? .06 : .02;
        q += water.equals("deep") ? .08 : water.equals("shallow") ? .06 : -.06;
        q += comm.equals("tower") ? .06 : comm.equals("sat") ? .02 : -.06;
        q += shops.equals("own") ? .07 : shops.equals("subsidy") ? .04 : -.08;
        q += !lift.equals("none") ? .05 : 0;
        q += (trust - 50) / 500.0;
        return q;
    }

    int infraScore() {
        boolean[] pts = {
                heat.equals("coal"), road >= 1, road == 2, light.equals("trafo"), mineOpen,
                water.equals("deep") || water.equals("shallow"), comm.equals("tower"),
                shops.equals("own") || shops.equals("subsidy"), !lift.equals("none"),
                "people".equals(housingChoice), trust >= 70};
        int s = 1;
        for (boolean b : pts) if (b) s++;
        return s;
    }

    long support() { return jobs + (long) Math.floor(pop * 0.3); }

    // ───────────────────────── рост города ─────────────────────────

    int needSchools() { return pop / 15000; }
    int needHosp() { return pop / 40000; }
    double serviceFactor() {
        if (act < 2) return 1;
        int ds = Math.max(0, needSchools() - schools), dh = Math.max(0, needHosp() - hospitals);
        return Math.max(.3, 1 - .12 * ds - .18 * dh);
    }
    int factoryCap() { return infraScore() + capBonus + (act >= 2 ? pop / 12000 : 0); }
    long factoryCost() { return act >= 2 ? 6_000_000L + (factories / 5) * 1_000_000L : FACTORY_COST; }
    long microCost() { return ("muni".equals(north) ? 12_000_000L : 9_000_000L) + micro * 600_000L; }
    static final int MAX_BLOCKS = 20;

    void growthTick(int days) {
        long cap = Math.min(housing, support());
        double smogF = 1 - Math.min(.5, smog / 250.0);
        double rate = Math.max(.08, Math.min(.8, .2 + 1.2 * quality() + .1 * ads)) * serviceFactor() * smogF * days / 30.0;
        if (cap > pop) {
            long gain = (long) Math.floor((cap - pop) * rate);
            if (act >= 2) gain = Math.min(gain, (long) Math.floor((pop * .03 + 2000) * (1 + .3 * ads + a3boost) * serviceFactor() * smogF * days / 30.0));
            addPop(gain, "переехали в город");
        } else if (cap < pop) addPop(-(long) Math.floor((pop - cap) * .25 * days / 30.0), housing < support() ? "негде жить" : "нет работы");
        ads = 0;
    }

    void freezeEvent() {
        if ("shallow".equals(water) && !frozen && day >= 320) {
            frozen = true;
            clear();
            say("Заморозки", H);
            say("Октябрь. Ударило −30°. Труба на двух метрах промерзает.");
            say("ПОЛ-ГОРОДА БЕЗ ВОДЫ", STAMP);
            if (can(2_500_000)) {
                budget -= 2_500_000;
                say("Срочный ремонт: " + col(C_RED, money(2_500_000)) + ".");
            } else {
                say("Денег на ремонт нет. Люди уезжают.");
                addPop(-(long) Math.floor(pop * .12), "уехали без воды");
            }
            addTrust(-10);
            pause();
        }
    }

    String[] capsArr() {
        java.util.ArrayList<String> c = new java.util.ArrayList<>();
        String w = "<br><font color='#e8ecf4'><big><b>";
        c.add("🏠 Жильё" + w + fmt(housing) + "</b></big></font>");
        c.add("🏭 Работа" + w + fmt(support()) + "</b></big></font>");
        c.add("Заводы" + w + factories + "/" + factoryCap() + "</b></big></font>");
        if (act >= 2) {
            int ns = needSchools(), nh = needHosp();
            c.add((schools < ns ? "<font color='#ff9a8f'>" : "<font>") + "🏫 Школы</font>" + w + schools + "/" + ns + "</b></big></font>");
            c.add((hospitals < nh ? "<font color='#ff9a8f'>" : "<font>") + "🏥 Больн.</font>" + w + hospitals + "/" + nh + "</b></big></font>");
        }
        return c.toArray(new String[0]);
    }

    Opt[] buildMenu() {
        java.util.ArrayList<Opt> o = new java.util.ArrayList<>();
        boolean a2 = act >= 2;
        String fname = FACTORY_NAMES[factories % FACTORY_NAMES.length];
        o.add(factories < factoryCap() ? new Opt("🏭 Завод", fname, factoryCost(), "factory") : new Opt("🏭 Завод", "инвесторам не хватает инфраструктуры", 1_000_000_000_000L, "factory"));
        o.add(a2 && blocks >= MAX_BLOCKS ? new Opt("🏠 Квартал", "старых домов не осталось", 1_000_000_000_000L, "block")
                : new Opt("🏠 Квартал", "+" + fmt(BLOCK_HOMES) + " жилья" + (a2 ? " · осталось " + (MAX_BLOCKS - blocks) : ""), BLOCK_COST, "block"));
        if (a2 && north != null) o.add(new Opt("🏙 Микрорайон", "+25 000 жилья", microCost(), "micro"));
        if (a2) {
            o.add(new Opt("🏫 Школа", "нужна на 15 000 жителей", 2_000_000, "school"));
            o.add(new Opt("🏥 Больница", "нужна на 40 000", 4_500_000, "hosp"));
            o.add(new Opt("🌳 Парк", "доверие ↑, смог ↓", 1_500_000, "park"));
            o.add(new Opt("🛍 Торговый центр", "+2 500 работы, доход", 3_500_000, "mall"));
        }
        o.add(new Opt("📣 Реклама", "быстрее переезжают", a2 ? 1_200_000 : AD_COST, "ad"));
        o.add(new Opt(deadline < 0 ? "Завершить месяц" : "Завершить месяц →", 0, "end", 2));
        if (deadline < 0) o.add(new Opt("⏩ 3 месяца", 0, "end3", 2));
        return o.toArray(new Opt[0]);
    }

    void doBuild(String k) {
        switch (k) {
            case "factory":
                factories++; jobs += FACTORY_JOBS;
                income.put("Заводы", income.getOrDefault("Заводы", 0L) + FACTORY_INCOME);
                if (act >= 2) smog = Math.min(100, smog + (filters ? 1.5 : 4));
                say("Строится: <b>" + FACTORY_NAMES[(factories - 1) % FACTORY_NAMES.length] + "</b>", GREENK, 120); break;
            case "block": blocks++; housing += BLOCK_HOMES; say("Пятиэтажки в лесах. Скоро — новые жильцы.", GREENK, 120); break;
            case "micro": micro++; housing += 25000; say("Микрорайон «Северный»: краны, котлованы, три новые высотки.", GREENK, 120); break;
            case "school": schools++; jobs += 120; say("Новая школа: 1 100 мест, бассейн и кружок робототехники.", GREENK, 120); break;
            case "hosp": hospitals++; jobs += 600; say("Больница открыта: приёмный покой, роддом, вертолётная площадка.", GREENK, 120); break;
            case "park": parks++; trust = Math.min(100, trust + 3); smog = Math.max(0, smog - 5);
                say(parks == 1 ? "Парк с колесом обозрения — над городом видно гору и копёр." : "Ещё один парк: аллеи, фонари, каток.", GREENK, 120); break;
            case "mall": malls++; jobs += 2500; income.put("Торговые центры", income.getOrDefault("Торговые центры", 0L) + 400_000L);
                say("Торговый центр: кино, фудкорт, очередь на открытии.", GREENK, 120); break;
            case "ad": ads++; say("Ролик про Угольный Яр набирает миллионы просмотров.", GREENK, 120); break;
        }
    }

    static final long[][] MS_BONUS = {{10000, 4_000_000}, {25000, 6_000_000}, {75000, 10_000_000}, {100000, 15_000_000}, {150000, 20_000_000},
            {250000, 30_000_000}, {500000, 50_000_000}, {1000000, 100_000_000}};

    boolean skip3 = false;
    void monthTurn() {
        save();
        hud();
        say(deadline < 0 ? "Год " + year + " · месяц " + (((month - 1) % 12) + 1) : "Месяц " + month, H);
        if (act >= 2) {
            int ns = needSchools(), nh = needHosp();
            if (schools < ns || hospitals < nh)
                say("⚠ Не хватает " + (schools < ns ? "школ: " + (ns - schools) : "") + (schools < ns && hospitals < nh ? ", " : "") + (hospitals < nh ? "больниц: " + (nh - hospitals) : "") + " — люди переезжают медленнее.", DIMK, 150);
            if (smog > 40) say("🌫 Смог над городом: " + Math.round(smog) + ". Рост замедляется, доверие падает.", DIMK, 150);
        }
        while (true) {
            String k = choose("Что строим?", buildMenu(), capsArr(), true);
            if (k.equals("end") || k.equals("end3")) { skip3 = k.equals("end3"); break; }
            doBuild(k);
        }
        int days = skip3 ? 90 : Math.min(30, daysLeft());
        if (skip3) { month += 2; skip3 = false; }
        advance(days);
        growthTick(days);
        if (act >= 2 && smog > 40) trust = Math.max(0, trust - 2);
        if (act >= 2) smog = Math.max(0, smog - parks * .3);
        for (long[] m : MS_BONUS) {
            if (pop >= m[0] && !done.contains("ms" + m[0])) {
                done.add("ms" + m[0]); budget += m[1];
                final String fs = "🎉 " + fmt(m[0]);
                post(() -> city.addFloat(fs, GOLD));
                say("🎉 " + fmt(m[0]) + " жителей! Область добавляет " + money(m[1]) + ".", GOLDK);
            }
        }
        freezeEvent();
        if (deadline < 0) year = month / 12 + 1;
        sleep(500);
        clear();
    }

    void ch9() {
        chapter = "9. РАБОТА";
        focus("town");
        if (done.contains("ch9init")) { monthLoop1(); return; }
        done.add("ch9init");
        say("Часть 9 · Работа", H);
        jobs += (long) Math.floor(pop * .5);
        housing = Math.max(housing, pop);
        say("ПРОБЛЕМА №1", BIGR);
        say("Дома есть, а работы нет. Люди хотят переехать, но им нужны работа и квартира. Теперь ты сам решаешь, что строить каждый месяц.");
        say("🏭 <b>Завод</b> (" + money(FACTORY_COST) + ") — работа для ~" + fmt(FACTORY_JOBS) + " жителей с семьями и +" + money(FACTORY_INCOME) + "/мес.");
        say("🏠 <b>Жилой квартал</b> (" + money(BLOCK_COST) + ") — квартиры для " + fmt(BLOCK_HOMES) + " жителей.");
        say("📣 <b>Реклама</b> (" + money(AD_COST) + ") — в этом месяце люди переезжают быстрее.");
        say("Население растёт к меньшему из двух чисел — «жильё» и «работа». Инвесторы готовы зайти максимум на <b>" + infraScore() + "</b> заводов.", DIMK);
        pause("К делу →");
        monthLoop1();
    }
    void monthLoop1() {
        chapter = "9. РАБОТА";
        while (daysLeft() > 3) { month++; monthTurn(); }
    }

    // ───────────────────────── финал части I ─────────────────────────

    boolean finale() {
        chapter = "ФИНАЛ";
        day = DEADLINE;
        clear();
        say("Последний день", H);
        say("Три ночи без сна. Ночь 1. Ночь 2. Ночь 3.");
        say("В ДК «Шахтёр» сидит комиссия из области и считает жителей.");
        sleep(800);
        int shown = pop;
        if (pop >= GOAL - 500 && pop < GOAL && trust >= 60) {
            say("Комиссия насчитала " + fmt(pop) + ". Не хватает " + fmt(GOAL - pop) + ".");
            say("Без пяти полночь. К ДК подъезжают машины, автобусы, такси. Жители всю ночь обзванивали родню: «Приезжай. Здесь снова живут».");
            if (matvey) say("Последним заходит сын деда Матвея — двенадцать лет на северах.");
            shown = pop = GOAL;
        }
        hud();
        say(fmt(shown), shown < GOAL ? BIGR : BIG);
        if (shown >= GOAL) {
            say("ЦЕЛЬ ДОСТИГНУТА", STAMP);
            say("Угольный Яр остаётся на карте.");
            if (matvey) say("Ну вот. Не зря держал.", QUOTE);
        } else if (shown >= 30000) {
            say("ЦЕЛЬ НЕ ДОСТИГНУТА — НО ГОРОД ЖИВ", GOLDK);
            say("До плана далеко, но расселять город на " + fmt(shown) + " человек никто не решится. Срок продлевают.");
        } else if (shown >= 5000) {
            say("ЦЕЛЬ НЕ ДОСТИГНУТА", REDK);
            say("Город решено «оптимизировать». Но люди не уезжают. Огни в окнах горят — назло комиссии.");
        } else {
            say("ПОСЁЛОК РАССЕЛЁН", STAMP);
            say("Последний автобус уходит к трассе. Окна гаснут одно за другим.");
        }
        return summary(shown, shown >= 5000);
    }

    boolean summary(int shown, boolean canContinue) {
        say("<font color='" + C_DIM + "'>Итоги:</font><br>Жителей: <b>" + fmt(shown) + "</b> / " + fmt(goal)
                + "<br>Бюджет: " + money(budget) + "<br>Доверие: " + trust + "/100<br>Заводов: " + factories
                + ", кварталов: " + blocks + "<br>Матвей: " + (matvey ? "встретил у люка" : "так и не поговорили"));
        if (canContinue) {
            String k = choose("История продолжается", new Opt("▶ Часть II · «Большой Яр»", 0, "cont", 1), new Opt("Начать заново ↻", 0, "new"));
            return k.equals("cont");
        }
        say("Спасибо, что были с нами!", H);
        pause("Сыграть ещё раз ↻");
        return false;
    }

    void bankrupt() {
        clear();
        say("Банкротство", H);
        say("Деньги на счету посёлка закончились. Котельная встаёт, свет отключают за долги.", REDK);
        say("Область вводит внешнее управление. Твоя история здесь закончилась.");
        summary(pop, false);
    }

    // ───────────────────────── ЧАСТЬ II ─────────────────────────

    final java.util.Random rng = new java.util.Random();
    void trustAdd(int n) { trust = Math.max(0, Math.min(100, trust + n)); }

    boolean randomEvent() {
        if (rng.nextDouble() > .55) return false;
        java.util.ArrayList<String> pool = new java.util.ArrayList<>();
        String[] all = {"blizzard", "investor", "mineAccident", "blogger", "festival", "coalPrice", "frost", "rink"};
        for (String id : all) {
            if (lastEv.getOrDefault(id, -5) == month - 1) continue;
            if ((id.equals("mineAccident")) && !(mineOpen && !"closed".equals(mineFate) && !"it".equals(mineFate))) continue;
            if (id.equals("coalPrice") && !(mineOpen && !"closed".equals(mineFate))) continue;
            pool.add(id);
        }
        if (pool.isEmpty()) return false;
        String id = pool.get(rng.nextInt(pool.size()));
        lastEv.put(id, month);
        clear();
        switch (id) {
            case "blizzard":
                say("Метель", H); say("Трое суток метёт так, что не видно соседнего дома. Трассу заносит.");
                if (road == 2 || rail != null) say("Асфальт и железная дорога выручают: город не отрезан.");
                else { budget -= 1_500_000; say("Расчистка трассы: " + col(C_RED, money(1_500_000)) + "."); }
                break;
            case "investor": {
                say("Инвестор", H); say("Из Новосибирска приезжает инвестор: хочет вложиться в Яр, но просит налоговые льготы.");
                String k = choose("Что ответишь?", new Opt("Подписать соглашение", -6_000_000, "yes"), new Opt("Отказать — льготы дорого обойдутся", 0, "no"));
                if (k.equals("yes")) { income.put("Льготы инвестору", income.getOrDefault("Льготы инвестору", 0L) - 150_000L); capBonus++; say("Деньги пришли. Инвестор обещает ещё один завод (лимит заводов +1)."); }
                else say("Инвестор уезжает. В городе считают, что правильно.");
                break; }
            case "mineAccident": {
                say("Авария на шахте", H); say("Ночью в лаве обвалилась кровля. Люди успели выйти, но крепь надо менять.");
                String k = choose("Что делать?", new Opt("Остановить добычу и перекрепить всё", 2_500_000, "fix"), new Opt("Работать дальше, починить по ходу", 0, "risk"));
                if (k.equals("fix")) { say("Две недели простоя. Шахтёры уважают решение."); trustAdd(5); }
                else if (rng.nextDouble() < .5) { say("Через месяц обвал повторился. Пострадали двое. Город в шоке.", REDK); trustAdd(-15); addPop(-(long) (pop * .01), "уехали после аварии"); }
                else say("Обошлось. Но шахтёры ворчат.");
                break; }
            case "blogger":
                say("Видео про Яр", H); say("Блогер-миллионник снял ролик «Город, который отказался умирать». 8 миллионов просмотров.");
                ads += 2; say("В этом месяце люди переезжают заметно быстрее.", GREENK); break;
            case "festival": {
                say("Фестиваль «Огни Яра»", H); say("Жители предлагают устроить фестиваль: салют над копром, ярмарка, концерт в ДК.");
                String k = choose("Устраиваем?", new Opt("Да, город заслужил праздник", 1_000_000, "yes"), new Opt("Не сейчас", 0, "no"));
                if (k.equals("yes")) { trustAdd(8); post(() -> city.addFloat("🎆 Огни Яра", GOLD)); say("Над шахтой салют. Весь город на улицах."); }
                break; }
            case "coalPrice":
                say("Уголь дорожает", H); say("На бирже скачок цен на уголь. Разрез работает в три смены.");
                budget += 3_000_000; say("+" + money(3_000_000) + " в бюджет.", GREENK); break;
            case "frost":
                say("Мороз −52°", H); say("Рекордный мороз. Котельная работает на пределе.");
                if ("coal".equals(heat) || energy != null) say("Тепло держится. В соседних городах лопнули трубы — у нас нет.");
                else { budget -= 1_500_000; say("Аварийный ремонт теплосетей: " + col(C_RED, money(1_500_000)) + "."); }
                break;
            case "rink": {
                say("Просьба жителей", H); say("Школьники собрали 2 000 подписей: просят залить большой каток у ДК.");
                String k = choose("Заливаем?", new Opt("Конечно", 600_000, "yes"), new Opt("Денег нет", 0, "no"));
                if (k.equals("yes")) { trustAdd(5); say("Каток с гирляндами — главное место зимних вечеров."); } else { trustAdd(-3); say("Дети расстроены."); }
                break; }
        }
        pause();
        return true;
    }

    boolean storyEvent() {
        String[] ids = {"rail", "energy", "health", "ecology", "airport", "north", "coal", "cup", "tram", "museum"};
        int[] at = {1, 3, 5, 7, 9, 11, 14, 16, 18, 21};
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            if (done.contains("a2_" + id) || month < at[i]) continue;
            if (id.equals("coal") && !mineOpen) continue;
            if (id.equals("cup") && (lift == null || lift.equals("none"))) continue;
            if (id.equals("tram") && north == null) continue;
            done.add("a2_" + id);
            clear();
            runStory(id);
            pause();
            return true;
        }
        return false;
    }

    void runStory(String id) {
        String k;
        switch (id) {
            case "rail":
                chapter = "II·1 ЖЕЛЕЗНАЯ ДОРОГА"; focus("rail");
                say("Железная дорога", H);
                say("Заводам тесно: всё везут фурами по 60 км трассы. До Транссиба — та же дистанция, и насыпь старой ветки ещё цела.");
                say("60 КМ ПУТЕЙ", BIG);
                k = choose("Ветка до Транссиба:", new Opt("Электрифицированная двухпутка: электрички до областного центра", 18_000_000, "electric"),
                        new Opt("Однопутка на тепловозах — дешевле, грузы пойдут", 9_000_000, "diesel"), new Opt("Обойдёмся фурами", 0, "none"));
                if (!k.equals("none")) {
                    rail = k; advance(k.equals("electric") ? 40 : 25);
                    income.put("Железная дорога", k.equals("electric") ? 2_000_000L : 1_000_000L); capBonus += k.equals("electric") ? 2 : 1;
                    say(k.equals("electric") ? "По насыпи идёт первая электричка «Яр — Новосибирск». На перроне — оркестр." : "Тепловоз с вагонами угля медленно ползёт к Транссибу.");
                    addPop(k.equals("electric") ? 3000 : 1200, "приехали по новой ветке");
                } else say("Фуры продолжают стоять в пробке на въезде.");
                break;
            case "energy":
                chapter = "II·2 ЭНЕРГИЯ"; focus("energy");
                say("Энергия", H);
                say("Заводам не хватает мощности. Подстанция гудит на пределе, вечером мигает свет.");
                k = choose("Какую станцию строим?", new Opt("ТЭЦ на угле Яра: дёшево и много, но дым", 15_000_000, "tec"),
                        new Opt("ГЭС на реке Яр: чисто, но дорого и долго", 28_000_000, "hydro"), new Opt("Ветряки на сопках: чисто, но мощности меньше", 10_000_000, "wind"));
                energy = k; advance(k.equals("hydro") ? 60 : 35);
                if (k.equals("tec")) { capBonus += 3; smog = Math.min(100, smog + 25); say("Две градирни поднимаются над городом. Пар видно с трассы."); }
                if (k.equals("hydro")) { capBonus += 3; trustAdd(5); say("Плотина перекрыла реку. Огни ГЭС отражаются в воде."); }
                if (k.equals("wind")) { capBonus += 1; say("На сопках крутятся двадцать белых ветряков."); }
                break;
            case "health":
                chapter = "II·3 ЛЮДИ"; focus("uni");
                say("Учиться и лечиться", H);
                say("Молодые уезжают учиться и не возвращаются. А в больнице одна скорая на весь город.");
                k = choose("Что первым?", new Opt("Горный институт: студенты и инженеры для заводов", 12_000_000, "uni"), new Opt("Большой медицинский центр с роддомом", 10_000_000, "med"));
                advance(40);
                if (k.equals("uni")) { uni = true; jobs += 3000; capBonus += 1; say("В сентябре в Горный институт пришли 2 000 первокурсников."); addPop(5000, "студенты и преподаватели"); }
                else { med = true; hospitals += 2; trustAdd(10); say("В новом роддоме за первый месяц родились 140 детей."); addPop(2500, "семьи с детьми"); }
                break;
            case "ecology":
                chapter = "II·4 ЭКОЛОГИЯ"; focus("town");
                say("Экология", H);
                if (smog < 20) { say("Учёные из института замерили воздух: Яр — один из самых чистых промышленных городов Сибири."); trustAdd(5); ads++; break; }
                say("Над городом серая пелена. Смог — " + Math.round(smog) + ". Мамы с колясками выходят на митинг.");
                k = choose("Что делать?", new Opt("Фильтры на все трубы и новые заводы", 8_000_000, "filters"), new Opt("Обещать разобраться позже", 0, "ignore"));
                if (k.equals("filters")) { filters = true; smog = Math.round(smog * .4); trustAdd(8); say("Трубы оделись в фильтры. Через месяц снег снова белый."); }
                else { trustAdd(-20); say("Митинг повторяется каждую субботу.", REDK); }
                break;
            case "airport":
                chapter = "II·5 НЕБО"; focus("airport");
                say("Аэропорт", H);
                say("В 20 км — заброшенный военный аэродром. Полоса 2,5 км, бетон ещё советский.");
                k = choose("Что с аэродромом?", new Opt("Аэропорт: рейсы в Москву и туристы на курорт", 20_000_000, "full"),
                        new Opt("Вертолётная площадка для санавиации", 4_000_000, "heli"), new Opt("Пусть зарастает", 0, "none"));
                if (!k.equals("none")) {
                    airport = k; advance(k.equals("full") ? 45 : 15);
                    income.put("Аэропорт", k.equals("full") ? (lift != null && !lift.equals("none") ? 2_500_000L : 1_500_000L) : 200_000L);
                    say(k.equals("full") ? "Первый самолёт заходит на посадку над огнями Яра. В салоне аплодируют." : "Над городом стрекочет санитарный вертолёт.");
                    if (k.equals("full")) addPop(2000, "прилетели и остались");
                }
                break;
            case "north":
                chapter = "II·6 СЕВЕРНЫЙ"; focus("north");
                say("Микрорайон «Северный»", H);
                say("Старых пятиэтажек больше нет. На месте отвалов шахты — ровное поле на 100 000 человек.");
                k = choose("Кто строит?", new Opt("Город: школы, дворы без машин, парк", 15_000_000, "muni"), new Opt("Застройщик: быстро и бесплатно, но человейники", 0, "dev"));
                north = k; micro++; housing += 25000;
                if (k.equals("muni")) { trustAdd(5); say("Первые высотки «Северного» — с детскими площадками во дворах."); }
                else { trustAdd(-5); say("Застройщик ставит 25-этажки впритык. Зато быстро."); }
                say("Теперь в меню строительства есть 🏙 Микрорайон: +25 000 жилья.", DIMK);
                break;
            case "coal":
                chapter = "II·7 КРИЗИС"; focus("mine");
                say("Уголь никому не нужен", H);
                say("Цены на уголь рухнули вдвое. Разрез работает в убыток. Шахтёры ждут, что скажет мэр.");
                if (matvey) say("Шахту я держал не ради угля. Ради людей. Решай, как людям лучше.", QUOTE);
                k = choose("Будущее шахты:", new Opt("Перепрофилировать: дата-центр «Яр-IT» на дешёвой энергии", 12_000_000, "it"),
                        new Opt("Субсидировать добычу из бюджета", 0, "subsidy"), new Opt("Закрыть разрез", 0, "closed"));
                mineFate = k;
                if (k.equals("it")) { income.put("Уголь", 0L); income.put("Яр-IT", 2_500_000L); capBonus += 1; say("В старом здании шахты гудят серверы. Шахтёры учатся на операторов."); addPop(1500, "айтишники"); }
                if (k.equals("subsidy")) { income.put("Уголь", -1_000_000L); say("Разрез работает. Бюджет теряет миллион в месяц."); }
                if (k.equals("closed")) { income.put("Уголь", 0L); jobs = Math.max(0, jobs - 3000); trustAdd(-15); say("Самосвалы встали. Второй раз за двенадцать лет.", REDK); }
                break;
            case "cup":
                chapter = "II·8 КУБОК"; focus("lift");
                say("Кубок России", H);
                say("Федерация горных лыж предлагает провести этап Кубка России на склонах Яра. Нужна новая трасса и освещение.");
                k = choose("Берём?", new Opt("Да: трасса с подсветкой и трибуны", 10_000_000, "yes"), new Opt("Рано", 0, "no"));
                if (k.equals("yes")) { cup = true; income.put("Курорт", income.getOrDefault("Курорт", 0L) + 1_500_000L); ads += 2; say("Трансляцию смотрят 3 миллиона человек. Трасса светится над городом."); }
                break;
            case "tram":
                chapter = "II·9 ТРАМВАЙ"; focus("town");
                say("Трамвай до «Северного»", H);
                say("Утром на выезде из «Северного» пробка на час. Люди просят трамвай.");
                k = choose("Трамвай:", new Opt("Строим линию через весь город", 14_000_000, "yes"), new Opt("Пустим больше автобусов", 2_000_000, "bus"));
                if (k.equals("yes")) { tram = true; trustAdd(8); say("Красно-белый трамвай звенит по проспекту Шахтёров."); }
                else say("Автобусы ходят чаще. Пробки чуть меньше.");
                break;
            case "museum":
                chapter = "II·10 ПАМЯТЬ"; focus("mine");
                say("Копёр", H);
                say("Застройщик предлагает снести старый копёр шахты «Северная» — на его месте поместится ещё квартал.");
                if (matvey) say("Дед Матвей приходит в мэрию сам. Молча кладёт на стол «Журнал вентилятора». Двенадцать лет записей.");
                k = choose("Копёр:", new Opt("Музей шахты и смотровая площадка с подсветкой", 2_500_000, "museum"), new Opt("Снести, построить квартал", -3_000_000, "demolish"));
                if (k.equals("museum")) { museum = true; trustAdd(12); income.put("Музей", 300_000L); say("Копёр подсвечен прожекторами. На входе в музей — журнал вентилятора под стеклом."); if (matvey) say("Ну вот. Теперь и на пенсию можно.", QUOTE); }
                else { demolished = true; trustAdd(-20); housing += 5000; say("Копёр режут автогеном. Старики стоят у забора и не уходят.", REDK); }
                break;
        }
    }

    boolean act2() {
        if (!done.contains("a2_started")) {
            act = 2; done.add("a2_started"); chapter = "ЧАСТЬ II"; month = 0;
            goal = 150000; deadline = day + 730;
            schools = Math.max(schools, 2); hospitals = Math.max(hospitals, 1);
            housing = Math.max(housing, pop);
            clear(); post(() -> city.focus("wide", 9000));
            say("Часть II · Большой Яр", H);
            say("Через неделю после комиссии звонит губернатор.");
            say(fmt(pop) + " человек за год. Такого в области не было. Хочу, чтобы Яр стал вторым городом области. Даю два года.", QUOTE);
            say("150 000", BIG);
            say("Цель — 150 000 жителей за 730 дней. Область выделяет стартовый фонд.");
            budget += 30_000_000;
            say("+" + money(30_000_000) + " на счёт", GREENK);
            say("Новое в меню: 🏫 школы, 🏥 больницы, 🌳 парки, 🛍 торговые центры. Без школ и больниц город растёт медленнее. Заводы дают смог — следи за ним.", DIMK);
            pause("Вперёд →");
            save();
        }
        while (daysLeft() > 3) {
            month++;
            if (!storyEvent()) randomEvent();
            if (daysLeft() <= 3) break;
            chapter = "II · СТРОЙКА";
            monthTurn();
        }
        return true;
    }

    boolean finale2() {
        chapter = "ФИНАЛ II"; clear();
        post(() -> city.focus("wide", 12000));
        say("Два года спустя", H);
        say("full".equals(airport) ? "Губернатор прилетает в Яр — теперь сюда можно прилететь." : "Губернатор приезжает в Яр по трассе.");
        say("С балкона мэрии видно весь город: от копра до огней «Северного».");
        sleep(800);
        say(fmt(pop), pop < goal ? BIGR : BIG);
        if (pop >= 150000) { say("ВТОРОЙ ГОРОД ОБЛАСТИ", STAMP); say("Указ подписан: Угольный Яр получает статус города областного значения."); }
        else if (pop >= 90000) { say("ПОЧТИ", GOLDK); say("До плана не дотянули, но губернатор доволен: такого роста нет нигде в Сибири."); }
        else { say("ПЛАН НЕ ВЫПОЛНЕН", REDK); say("В области недовольны. Но город живёт — и будет жить."); }
        if (museum && matvey) say("Вечером дед Матвей поднимается на смотровую площадку копра. Внизу — сто тысяч огней.");
        say("<font color='" + C_DIM + "'>Итоги части II:</font><br>Жителей: <b>" + fmt(pop) + "</b><br>Заводов: " + factories + ", микрорайонов: " + micro
                + "<br>Школ: " + schools + ", больниц: " + hospitals + ", парков: " + parks + "<br>Смог: " + Math.round(smog) + " · Доверие: " + trust);
        String k = choose("Дальше:", new Opt("▶ Свободная игра: строй город без срока", 0, "free", 1), new Opt("Начать заново ↻", 0, "new"));
        return !k.equals("new");
    }

    // ═══════════════════ ЧАСТЬ III · «МИЛЛИОННИК» ═══════════════════
    volatile String a3metro, a3bridge, a3tower, a3games, a3matvey;
    volatile boolean a3vsm;
    volatile int a3gamesDay;
    double a3boost;

    long P(double mult, long base) { return Math.round(Math.max(base, monthly() * mult) / 1_000_000.0) * 1_000_000L; }
    static final Opt LATER = new Opt("⏳ Отложить — вернёмся через 3 месяца", 0, "later");

    static final String[] A3_IDS = {"metro", "bridge", "tower", "games", "vsm", "matvey"};
    static final int[] A3_POP = {180000, 240000, 300000, 400000, 550000, 750000};

    boolean act3Story() {
        for (int i = 0; i < A3_IDS.length; i++) {
            String id = A3_IDS[i];
            if (done.contains("a3_" + id) || pop < A3_POP[i]) continue;
            Integer w = lastEv.get("a3wait_" + id);
            if (w != null && w > month) continue;
            done.add("a3_" + id);
            clear();
            String r;
            switch (id) {
                case "metro": r = a3Metro(); break;
                case "bridge": r = a3Bridge(); break;
                case "tower": r = a3Tower(); break;
                case "games": r = a3Games(); break;
                case "vsm": r = a3Vsm(); break;
                default: r = a3Matvey(); break;
            }
            if ("later".equals(r)) { done.remove("a3_" + id); lastEv.put("a3wait_" + id, month + 3); say("Хорошо. Вернёмся к этому через три месяца — копите бюджет.", DIMK); }
            pause(); chapter = "ГОРОД"; save();
            return true;
        }
        return false;
    }

    String a3Metro() {
        chapter = "III·1 МЕТРО"; focus("metro");
        say("Часть III · Метро", H);
        say("Утром проспект Шахтёров стоит в пробке от копра до вокзала. Трамваи битком, автобусы не справляются.");
        say("Метростроевцы из Новосибирска готовы зайти: грунт плотный, а у города есть то, чего нет ни у кого, — шахтёры.");
        say("6 СТАНЦИЙ", BIG);
        String k = choose("Транспорт для большого города:",
                new Opt("🚇 Метро: первая линия, 6 станций — копали шахтёры", P(2.5, 120_000_000L), "full"),
                new Opt("🚊 Скоростной трамвай по выделенке (ЛРТ)", P(1, 40_000_000L), "lrt"),
                new Opt("🚌 Ещё автобусов", 8_000_000L, "bus"), LATER);
        if (k.equals("later")) return k;
        a3metro = k;
        if (k.equals("full")) { advance(60); a3boost += .2; capBonus += 3; jobs += 8000; addTrust(10);
            say("Станция «Шахтёрская» украшена старыми касками и лампами. Первый поезд ведёт сын деда Матвея.");
            if (matvey) say("Под землёй опять наши. Только теперь — с поездами.", QUOTE); }
        else if (k.equals("lrt")) { advance(35); a3boost += .15; capBonus += 1; jobs += 3000; say("Синие вагоны идут по выделенке — 12 минут от вокзала до горы."); }
        else { advance(10); addTrust(-6); say("Автобусов стало больше. Пробок — тоже."); }
        return k;
    }

    String a3Bridge() {
        chapter = "III·2 ЛЕВЫЙ БЕРЕГ"; focus("river");
        say("Левый берег", H);
        say("Город упёрся в реку. На правом берегу места больше нет, а левый — пустой: сосны, песок, старые дачи.");
        say("Мост откроет место для целого нового района.");
        say("1 200 М", BIG);
        String k = choose("Как перейти реку?",
                new Opt("🌉 Вантовый мост с пилоном 180 м — символ города", P(3, 150_000_000L), "cable"),
                new Opt("⛴ Наплавной мост и паром", P(.7, 25_000_000L), "pont"),
                new Opt("Пока строим вверх, а не вширь", 0, "none"), LATER);
        if (k.equals("later")) return k;
        if (!k.equals("none")) {
            a3bridge = k; advance(k.equals("cable") ? 75 : 30);
            housing += k.equals("cable") ? 80000 : 20000; a3boost += k.equals("cable") ? .12 : .04;
            say(k.equals("cable") ? "Пилон светится над рекой, ванты как струны. На открытии — 40 тысяч человек и салют." : "Понтоны покачиваются, летом ходит паром. Левый берег заселяется.");
            addPop(k.equals("cable") ? 15000 : 4000, "переехали на левый берег");
        } else { say("Цены на квартиры ползут вверх."); addTrust(-4); }
        return k;
    }

    String a3Tower() {
        chapter = "III·3 ТЕЛЕБАШНЯ"; focus("tvtower");
        say("Башня над Яром", H);
        say("Старая вышка связи не тянет: 5G, телевидение, аварийная связь для шахт.");
        say("Архитекторы предлагают башню с вращающимся рестораном — чтобы с неё был виден весь город, гора и река.");
        String k = choose("Какую башню ставим?",
                new Opt("🗼 Телебашня 330 м с рестораном и смотровой", P(1.5, 90_000_000L), "full"),
                new Opt("📡 Простая вышка 5G", 20_000_000L, "5g"), LATER);
        if (k.equals("later")) return k;
        a3tower = k; advance(k.equals("full") ? 50 : 15);
        if (k.equals("full")) { income.put("Телебашня", Math.round(Math.max(3_000_000, monthly() * .03))); addTrust(8);
            say("Ресторан делает оборот за 40 минут. В ясную ночь видно огни посёлков за 80 км."); }
        else { jobs += 1500; say("Связь отличная. Туристы фотографируют гору, а не вышку."); }
        return k;
    }

    String a3Games() {
        chapter = "III·4 УНИВЕРСИАДА"; focus("stadium");
        say("Универсиада", H);
        say("Министерство спорта ищет город для зимней Универсиады. Нужны стадион, трассы, аэропорт и гостиницы.");
        double chance = Math.min(.95, (cup ? .25 : 0) + ("full".equals(airport) ? .25 : airport != null ? .1 : 0) + ("full".equals(a3metro) ? .15 : 0) + .25);
        say("Шансы заявки: <b>" + Math.round(chance * 100) + "%</b> (кубок мира, аэропорт и метро помогают).", DIMK);
        String k = choose("Участвуем?",
                new Opt("🏟 Подать заявку: стадион на 40 000 и деревня спортсменов", P(3, 250_000_000L), "bid"),
                new Opt("🏅 Провести всероссийские игры — надёжно", P(1, 60_000_000L), "nat"),
                new Opt("Не участвуем", 0, "none"),
                new Opt("⏳ Отложить — следующий отбор через 3 месяца", 0, "later"));
        if (k.equals("later")) return k;
        if (k.equals("none")) { say("Универсиаду проводят в Красноярске."); return k; }
        advance(k.equals("bid") ? 60 : 30);
        a3gamesDay = day; a3games = "built";
        if (k.equals("bid")) {
            say("Москва. Голосование. Конверт открывают медленно…");
            if (Math.random() < chance) {
                a3games = "won"; say("УГОЛЬНЫЙ ЯР", STAMP);
                say("Яр принимает Универсиаду! Спонсоры, телевидение, 60 стран.");
                budget += P(3, 120_000_000L); a3boost += .15; income.put("Универсиада и туризм", Math.round(monthly() * .05));
                addPop(30000, "переехали работать на Универсиаду");
            } else { say("Проиграли Казани в два голоса. Стадион остаётся городу.", REDK); addTrust(-5); a3boost += .05; }
        } else { a3boost += .08; say("Всероссийские игры прошли без единой накладки. Стадион полон каждые выходные."); addPop(6000, "болельщики остались жить"); }
        return k;
    }

    String a3Vsm() {
        chapter = "III·5 МАГИСТРАЛЬ"; focus("vsm");
        say("Скоростная магистраль", H);
        say("РЖД проектирует высокоскоростную магистраль на восток. Трасса может пройти через Яр — или в 200 км южнее.");
        say("2 ЧАСА ДО НОВОСИБИРСКА", BIG);
        String k = choose("Бьёмся за магистраль?",
                new Opt("🚄 Да: доля города в строительстве и вокзал ВСМ", P(3, 300_000_000L), "yes"),
                new Opt("Пусть идёт южнее", 0, "no"),
                new Opt("⏳ Отложить — РЖД ждёт ответа 3 месяца", 0, "later"));
        if (k.equals("later")) return k;
        if (k.equals("yes")) {
            a3vsm = true; if (rail == null) rail = "electric";
            advance(90); a3boost += .2; income.put("ВСМ", Math.round(monthly() * .04)); capBonus += 3;
            say("Белый поезд проходит мост за 9 секунд. Утром — в Новосибирске, вечером — дома.");
            addPop(20000, "приехали по ВСМ");
        } else say("Магистраль ушла южнее. Яр остался в стороне от главного пути.");
        return k;
    }

    String a3Matvey() {
        chapter = "III·6 ДЕВЯНОСТО"; post(() -> city.focus("monument", 7000));
        say("Девяносто лет", H);
        if (matvey) {
            say("Деду Матвею — девяносто. Он всё так же каждое утро спускается в шахтёрский музей — проверить насосы, «на всякий случай».");
            say("Когда я двенадцать лет один качал воду, думал: дурак старый. А оно вон как вышло.", QUOTE);
        } else say("Старейшему шахтёру Яра — девяносто. Говорят, он двенадцать лет в одиночку держал насосы в закрытой шахте.");
        say("Горожане собрали подписи: хотят отметить его так, чтобы помнили дети.");
        String k = choose("Как отметим?",
                new Opt("🗿 Памятник «Шахтёр, который не ушёл» перед ДК", 30_000_000L, "monument"),
                new Opt("🪧 Назвать главную площадь его именем", 0, "square"));
        a3matvey = k; addTrust(12);
        advance(k.equals("monument") ? 30 : 5);
        say(k.equals("monument") ? "Бронзовый шахтёр поднимает лампу над площадью. Лампа горит всегда." : "На табличке: «Площадь Матвея». Он ворчит, что не заслужил, — и приходит туда каждый вечер.");
        return k;
    }

    boolean act3() {
        if (!done.contains("free")) {
            done.add("free"); act = 3; deadline = -1; year = 1; month = 0;
            goal = pop < 250000 ? 250000 : pop < 500000 ? 500000 : 1000000;
            clear();
            say("Свободная игра", H);
            say("Срока больше нет. Цели: 250 000 → 500 000 → миллион и дальше. За каждую — награда области.");
            say("Впереди — Часть III: метро, мост на левый берег, телебашня, Универсиада, скоростная магистраль. Главы откроются, когда город вырастет.", GOLDK);
            say("Игра сохраняется сама каждый месяц.", DIMK);
            pause("Строить →");
        }
        while (true) {
            month++;
            chapter = "ГОРОД";
            if (!act3Story()) randomEvent();
            monthTurn();
            if (pop >= goal) {
                clear();
                say(goal >= 2000000 ? "Новая высота" : goal >= 1000000 ? "Миллионник" : goal >= 500000 ? "Большой город" : "Крупный город", H);
                say(fmt(goal), BIG);
                say(goal >= 1000000 ? "ГОРОД-МИЛЛИОННИК" : "ЦЕЛЬ ДОСТИГНУТА", STAMP);
                if (goal >= 2000000) { say("Агломерация растёт: Яр — один из крупнейших городов Сибири."); goal += 1000000; }
                else if (goal >= 1000000) { say("Миллион. Двенадцать лет назад здесь горело 312 окон."); if (matvey) say("Я ж говорил — вернутся.", QUOTE); goal = 2000000; }
                else goal = goal == 250000 ? 500000 : 1000000;
                say("Следующая цель: " + fmt(goal) + ".");
                pause();
            }
        }
    }

    // ───────────────────────── сохранение ─────────────────────────

    void save() {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("day", day); o.put("pop", pop); o.put("trust", trust); o.put("factories", factories); o.put("blocks", blocks); o.put("ads", ads);
            o.put("budget", budget); o.put("housing", housing); o.put("jobs", jobs); o.put("road", road);
            o.put("act", act); o.put("goal", goal); o.put("deadline", deadline); o.put("step", step); o.put("month", month); o.put("year", year);
            o.put("schools", schools); o.put("hospitals", hospitals); o.put("parks", parks); o.put("malls", malls); o.put("micro", micro);
            o.put("smog", smog); o.put("capBonus", capBonus);
            o.put("a3boost", a3boost); o.put("a3gamesDay", a3gamesDay); o.put("a3vsm", a3vsm);
            if (a3metro != null) o.put("a3metro", a3metro); if (a3bridge != null) o.put("a3bridge", a3bridge); if (a3tower != null) o.put("a3tower", a3tower);
            if (a3games != null) o.put("a3games", a3games); if (a3matvey != null) o.put("a3matvey", a3matvey);
            String[] sk = {"chapter", "heat", "light", "water", "comm", "shops", "lift", "housingChoice", "mine", "rail", "energy", "airport", "mineFate", "north"};
            String[] sv = {chapter, heat, light, water, comm, shops, lift, housingChoice, mine, rail, energy, airport, mineFate, north};
            for (int i = 0; i < sk.length; i++) if (sv[i] != null) o.put(sk[i], sv[i]);
            String[] bk = {"matvey", "mineOpen", "frozen", "roadSet", "ratrak", "hotel", "ventLit", "matveyVis", "uni", "med", "tram", "museum", "demolished", "cup", "filters"};
            boolean[] bv = {matvey, mineOpen, frozen, roadSet, ratrak, hotel, ventLit, matveyVis, uni, med, tram, museum, demolished, cup, filters};
            for (int i = 0; i < bk.length; i++) o.put(bk[i], bv[i]);
            org.json.JSONObject inc = new org.json.JSONObject(); for (Map.Entry<String, Long> e : income.entrySet()) inc.put(e.getKey(), e.getValue()); o.put("income", inc);
            o.put("done", new org.json.JSONArray(done));
            org.json.JSONObject le = new org.json.JSONObject(); for (Map.Entry<String, Integer> e : lastEv.entrySet()) le.put(e.getKey(), e.getValue()); o.put("lastEv", le);
            getSharedPreferences("yar", MODE_PRIVATE).edit().putString("save", o.toString()).apply();
        } catch (Exception ignored) { }
    }

    boolean load() {
        try {
            String s = getSharedPreferences("yar", MODE_PRIVATE).getString("save", null);
            if (s == null) return false;
            org.json.JSONObject o = new org.json.JSONObject(s);
            day = o.getInt("day"); pop = o.getInt("pop"); trust = o.getInt("trust"); factories = o.getInt("factories"); blocks = o.getInt("blocks"); ads = o.getInt("ads");
            budget = o.getDouble("budget"); housing = o.getLong("housing"); jobs = o.getLong("jobs"); road = o.getInt("road");
            act = o.getInt("act"); goal = o.getInt("goal"); deadline = o.getInt("deadline"); step = o.getInt("step"); month = o.getInt("month"); year = o.getInt("year");
            schools = o.getInt("schools"); hospitals = o.getInt("hospitals"); parks = o.getInt("parks"); malls = o.getInt("malls"); micro = o.getInt("micro");
            smog = o.getDouble("smog"); capBonus = o.getInt("capBonus");
            a3boost = o.optDouble("a3boost", 0); a3gamesDay = o.optInt("a3gamesDay", 0); a3vsm = o.optBoolean("a3vsm");
            a3metro = o.optString("a3metro", null); a3bridge = o.optString("a3bridge", null); a3tower = o.optString("a3tower", null);
            a3games = o.optString("a3games", null); a3matvey = o.optString("a3matvey", null);
            chapter = o.optString("chapter", "ПРОЛОГ"); heat = o.optString("heat", null); light = o.optString("light", null); water = o.optString("water", null);
            comm = o.optString("comm", null); shops = o.optString("shops", null); lift = o.optString("lift", "none"); housingChoice = o.optString("housingChoice", null);
            mine = o.optString("mine", null); rail = o.optString("rail", null); energy = o.optString("energy", null); airport = o.optString("airport", null);
            mineFate = o.optString("mineFate", null); north = o.optString("north", null);
            matvey = o.optBoolean("matvey"); mineOpen = o.optBoolean("mineOpen"); frozen = o.optBoolean("frozen"); roadSet = o.optBoolean("roadSet");
            ratrak = o.optBoolean("ratrak"); hotel = o.optBoolean("hotel"); ventLit = o.optBoolean("ventLit"); matveyVis = o.optBoolean("matveyVis");
            uni = o.optBoolean("uni"); med = o.optBoolean("med"); tram = o.optBoolean("tram"); museum = o.optBoolean("museum");
            demolished = o.optBoolean("demolished"); cup = o.optBoolean("cup"); filters = o.optBoolean("filters");
            income.clear(); org.json.JSONObject inc = o.getJSONObject("income");
            for (java.util.Iterator<String> it = inc.keys(); it.hasNext(); ) { String key = it.next(); income.put(key, inc.getLong(key)); }
            done.clear(); org.json.JSONArray d = o.getJSONArray("done"); for (int i = 0; i < d.length(); i++) done.add(d.getString(i));
            lastEv.clear(); org.json.JSONObject le = o.getJSONObject("lastEv");
            for (java.util.Iterator<String> it = le.keys(); it.hasNext(); ) { String key = it.next(); lastEv.put(key, le.getInt(key)); }
            return step > 0;
        } catch (Exception e) { return false; }
    }
    void clearSave() { getSharedPreferences("yar", MODE_PRIVATE).edit().remove("save").apply(); }

    boolean runStep(int s) {
        switch (s) {
            case 0: prologue(); return true;
            case 1: ch1(); return true;
            case 2: ch2(); return true;
            case 3: ch3(); return true;
            case 4: ch4(); return true;
            case 5: ch5(); return true;
            case 6: ch6(); return true;
            case 7: ch7(); return true;
            case 8: ch8(); return true;
            case 9: ch9(); return true;
            case 10: return finale();
            case 11: return act2();
            case 12: return finale2();
            case 13: return act3();
        }
        return false;
    }

    void gameLoop() {
        try {
            boolean resume = !autoplay && load();
            while (true) {
                if (resume) {
                    resume = false;
                    post(() -> city.reset());
                    hud(); clear();
                    say("Угольный Яр", H);
                    say("Сохранённая игра: <b>" + chapter + "</b>, " + fmt(pop) + " жителей, " + money(budget) + ".");
                    String k = choose("Продолжить?", new Opt("▶ Продолжить", 0, "go", 1), new Opt("Новая игра ↻", 0, "new"));
                    clear();
                    if (k.equals("new")) { clearSave(); newGame(); }
                } else newGame();
                if (a3test) { a3test = false; setupA3Test(); }
                try {
                    while (step <= 13) {
                        save();
                        if (!runStep(step)) break;
                        step++;
                    }
                } catch (Bankrupt e) {
                    bankrupt();
                }
                clearSave();
            }
        } catch (Quit ignored) {
            // активити закрыта
        }
    }
}
