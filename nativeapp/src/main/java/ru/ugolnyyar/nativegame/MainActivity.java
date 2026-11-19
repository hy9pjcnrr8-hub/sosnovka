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
    private Thread gameThread;
    /** Скрытый режим для автотеста: игра сама выбирает варианты. */
    volatile boolean autoplay = false;

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

    Button makeButton(String text, String costLine, int costColor, boolean enabled, int style) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setGravity(style == 0 ? (Gravity.START | Gravity.CENTER_VERTICAL) : Gravity.CENTER);
        b.setPadding(dp(13), dp(10), dp(13), dp(10));
        b.setStateListAnimator(null);
        String full = escape(text);
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
        sleep(wait);
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
        if (autoplay) { sleep(2200); clear(); return; }
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
        final String text, key; final long cost; final int style;
        Opt(String text, long cost, String key) { this(text, cost, key, 0); }
        Opt(String text, long cost, String key, int style) { this.text = text; this.cost = cost; this.key = key; this.style = style; }
    }

    String choose(String title, Opt[] opts, String[] caps) {
        hud();
        answers.clear();
        final long budgetNow = (long) budget;
        post(() -> {
            ctl.removeAllViews();
            if (caps != null) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                for (int i = 0; i < caps.length; i++) {
                    TextView c = new TextView(this);
                    c.setText(html(caps[i]));
                    c.setTextColor(DIM);
                    c.setTextSize(12);
                    c.setPadding(dp(9), dp(7), dp(9), dp(7));
                    c.setBackground(box(PANEL, LINE, 10, 1));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                    if (i > 0) lp.leftMargin = dp(8);
                    row.addView(c, lp);
                }
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                rlp.bottomMargin = dp(8);
                ctl.addView(row, rlp);
            }
            TextView q = new TextView(this);
            q.setText("▶ " + title);
            q.setTextColor(TEXT);
            q.setTextSize(15);
            q.setTypeface(Typeface.DEFAULT_BOLD);
            q.setPadding(0, 0, 0, dp(8));
            ctl.addView(q);
            for (Opt o : opts) {
                boolean afford = o.cost <= 0 || budgetNow >= o.cost;
                String costLine = null;
                int costColor = GOLD;
                if (o.cost > 0 && o.cost < 100_000_000_000L) {
                    costLine = money(o.cost) + (afford ? "" : " · не хватает денег");
                    costColor = afford ? GOLD : RED;
                } else if (o.cost < 0) {
                    costLine = "+" + money(-o.cost);
                    costColor = GREEN;
                }
                Button b = makeButton(o.text, costLine, costColor, afford, o.style);
                b.setOnClickListener(v -> { ctl.removeAllViews(); answers.offer(o); });
                ctl.addView(b);
            }
        });
        Opt o;
        if (autoplay) {
            sleep(2200);
            Opt pick = null;
            if (title.startsWith("Что строим")) {
                boolean wantFactory = support() <= housing;
                for (Opt x : opts) if (x.key.equals(wantFactory ? "factory" : "block") && (x.cost <= 0 || budget >= x.cost)) pick = x;
                if (pick == null) for (Opt x : opts) if (x.key.equals(wantFactory ? "block" : "factory") && (x.cost <= 0 || budget >= x.cost)) pick = x;
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

    void newGame() {
        day = 0; budget = 12_000_000; pop = 312; trust = 50; chapter = "ПРОЛОГ";
        housing = 0; jobs = 0; factories = 0; blocks = 0; ads = 0;
        income.clear();
        heat = light = water = comm = shops = housingChoice = mine = null;
        lift = "none"; road = 0;
        matvey = mineOpen = frozen = ms10 = ms25 = false;
        roadSet = ratrak = hotel = ventLit = matveyVis = false;
        post(() -> city.reset());
    }

    int daysLeft() { return Math.max(0, DEADLINE - day); }
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
        String l1 = col(C_RED, date + " · " + daysLeft() + " ДН.") + "   " + col(C_GOLD, chapter) + "   " + col(C_BLUE, "♥ " + trust);
        String l2 = col(C_GREEN, "👥 " + fmt(pop) + " / " + fmt(GOAL)) + "   " + col(C_GOLD, money(budget)) + " " + col(inc >= 0 ? C_GREEN : C_RED, (inc >= 0 ? "+" : "") + fmt(inc) + "/мес");
        String l3 = "";
        float frac = Math.min(1f, pop / (float) GOAL);
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

    void growthTick(int days) {
        long cap = Math.min(housing, support());
        double rate = Math.max(.08, Math.min(.8, .2 + 1.2 * quality() + .1 * ads)) * days / 30.0;
        if (cap > pop) addPop((long) Math.floor((cap - pop) * rate), "переехали в город");
        else if (cap < pop) addPop(-(long) Math.floor((pop - cap) * .25 * days / 30.0), housing < support() ? "негде жить" : "нет работы");
        ads = 0;
    }

    void freezeEvent() {
        if (water.equals("shallow") && !frozen && day >= 320) {
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

    void ch9() {
        chapter = "9. РАБОТА";
        focus("town");
        say("Часть 9 · Работа", H);
        jobs += (long) Math.floor(pop * .5);
        housing = Math.max(housing, pop);
        say("ПРОБЛЕМА №1", BIGR);
        say("Дома есть, а работы нет. Люди хотят переехать, но им нужны работа и квартира. Теперь ты сам решаешь, что строить каждый месяц.");
        say("🏭 <b>Завод</b> (" + money(FACTORY_COST) + ") — работа для ~" + fmt(FACTORY_JOBS) + " жителей с семьями и +" + money(FACTORY_INCOME) + "/мес.");
        say("🏠 <b>Жилой квартал</b> (" + money(BLOCK_COST) + ") — квартиры для " + fmt(BLOCK_HOMES) + " жителей.");
        say("📣 <b>Реклама</b> (" + money(AD_COST) + ") — в этом месяце люди переезжают быстрее.");
        say("Население растёт к меньшему из двух чисел — «жильё» и «работа». Инвесторы готовы зайти максимум на <b>" + infraScore() + "</b> заводов: всё, что ты построил раньше, сейчас окупается.", DIMK);
        pause("К делу →");

        int month = 0;
        while (daysLeft() > 3) {
            month++;
            say("Месяц " + month, H);
            while (true) {
                int fmax = infraScore();
                String[] caps = {
                        "🏠 Жильё на<br><font color='#e8ecf4'><big><b>" + fmt(housing) + "</b></big></font>",
                        "🏭 Работа на<br><font color='#e8ecf4'><big><b>" + fmt(support()) + "</b></big></font>",
                        "Заводы<br><font color='#e8ecf4'><big><b>" + factories + "/" + fmax + "</b></big></font>"};
                Opt fopt = factories < fmax
                        ? new Opt("🏭 Завод: " + FACTORY_NAMES[factories % FACTORY_NAMES.length], FACTORY_COST, "factory")
                        : new Opt("🏭 Завод — инвесторы не идут: не хватает инфраструктуры", 1_000_000_000_000L, "factory");
                String k = choose("Что строим?", new Opt[]{
                        fopt,
                        new Opt("🏠 Жилой квартал (ремонт старых пятиэтажек)", BLOCK_COST, "block"),
                        new Opt("📣 Рекламная кампания «Вернись в Яр»", AD_COST, "ad"),
                        new Opt("Завершить месяц →", 0, "end", 2)}, caps);
                if (k.equals("factory")) {
                    factories++;
                    jobs += FACTORY_JOBS;
                    income.put("Заводы", income.getOrDefault("Заводы", 0L) + FACTORY_INCOME);
                    say("Строится: <b>" + FACTORY_NAMES[(factories - 1) % FACTORY_NAMES.length] + "</b>", GREENK);
                } else if (k.equals("block")) {
                    blocks++;
                    housing += BLOCK_HOMES;
                    say("Пятиэтажки в лесах. Скоро — новые жильцы.", GREENK);
                } else if (k.equals("ad")) {
                    ads++;
                    say("Ролик про Угольный Яр набирает миллионы просмотров.", GREENK);
                } else break;
            }
            int days = Math.min(30, daysLeft());
            advance(days);
            growthTick(days);
            if (pop >= 10000 && !ms10) {
                ms10 = true; budget += 4_000_000;
                say("🎉 10 000 жителей! Область добавляет " + money(4_000_000) + ".", GOLDK);
            }
            if (pop >= 25000 && !ms25) {
                ms25 = true; budget += 6_000_000;
                say("🎉 25 000 жителей! Область добавляет " + money(6_000_000) + ".", GOLDK);
            }
            freezeEvent();
            sleep(500);
            clear();
        }
    }

    // ───────────────────────── финал ─────────────────────────

    void finale() {
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
        summary(shown);
    }

    void summary(int shown) {
        say("<font color='" + C_DIM + "'>Итоги:</font><br>Жителей: <b>" + fmt(shown) + "</b> / " + fmt(GOAL)
                + "<br>Бюджет: " + money(budget) + "<br>Доверие: " + trust + "/100<br>Заводов: " + factories
                + ", кварталов: " + blocks + "<br>Матвей: " + (matvey ? "встретил у люка" : "так и не поговорили"));
        say("Спасибо, что были с нами!", H);
        pause("Сыграть ещё раз ↻");
    }

    void bankrupt() {
        clear();
        say("Банкротство", H);
        say("Деньги на счету посёлка закончились. Котельная встаёт, свет отключают за долги.", REDK);
        say("Область вводит внешнее управление. Твоя история здесь закончилась.");
        summary(pop);
    }

    void gameLoop() {
        try {
            while (true) {
                newGame();
                try {
                    prologue();
                    ch1(); ch2(); ch3(); ch4(); ch5(); ch6(); ch7(); ch8(); ch9();
                    finale();
                } catch (Bankrupt e) {
                    bankrupt();
                }
            }
        } catch (Quit ignored) {
            // активити закрыта
        }
    }
}
