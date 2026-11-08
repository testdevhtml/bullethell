package com.bulletstorm.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/** Rendu (Canvas accéléré) + gestion du tactile. Résolution virtuelle : 720 de large. */
public class GameView extends View {

    private static final float PI = (float) Math.PI;
    private static final int[] BG = {0xFF070B1E, 0xFF140822, 0xFF071A1A, 0xFF1E0A10, 0xFF10102A};

    private final Game game;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Random srnd = new Random(7);

    private float scale = 1f;
    private long last = 0;

    // étoiles de fond
    private final float[] sx = new float[80], sy = new float[80], ss = new float[80];

    // joystick flottant
    private int joyId = -1, pendId = -1, pendBtn = -1;
    private float joyOx, joyOy, joyKx, joyKy;
    private static final float JOY_R = 85f;

    public GameView(Context ctx, Game game) {
        super(ctx);
        this.game = game;
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        setFocusable(true);
        for (int i = 0; i < sx.length; i++) {
            sx[i] = srnd.nextFloat() * 720f; sy[i] = srnd.nextFloat() * 1600f; ss[i] = 30f + srnd.nextFloat() * 200f;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        scale = w / 720f;
        game.resize(720f, h / scale);
    }

    // =====================================================================
    //  Tactile
    // =====================================================================
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        int idx = e.getActionIndex();
        switch (a) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int id = e.getPointerId(idx);
                float x = e.getX(idx) / scale, y = e.getY(idx) / scale;
                Game.Button b = game.hit(x, y);
                if (b != null) {
                    if (game.screen == Game.PLAY) game.press(b.id);
                    else { pendId = id; pendBtn = b.id; }
                } else if (game.screen == Game.PLAY && joyId == -1) {
                    joyId = id; joyOx = x; joyOy = y; joyKx = x; joyKy = y;
                    game.setJoystick(0, 0);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    if (e.getPointerId(i) != joyId) continue;
                    float x = e.getX(i) / scale, y = e.getY(i) / scale;
                    float dx = x - joyOx, dy = y - joyOy;
                    float m = (float) Math.sqrt(dx * dx + dy * dy);
                    if (m > JOY_R) { // la base suit le doigt : pas besoin de recentrer
                        joyOx += dx / m * (m - JOY_R); joyOy += dy / m * (m - JOY_R);
                        dx = x - joyOx; dy = y - joyOy; m = JOY_R;
                    }
                    joyKx = joyOx + dx; joyKy = joyOy + dy;
                    float nx = dx / JOY_R, ny = dy / JOY_R;
                    if (m / JOY_R < 0.12f) { nx = 0; ny = 0; }
                    game.setJoystick(nx, ny);
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int id = e.getPointerId(idx);
                float x = e.getX(idx) / scale, y = e.getY(idx) / scale;
                if (id == joyId) { joyId = -1; game.setJoystick(0, 0); }
                if (id == pendId) {
                    Game.Button b = game.hit(x, y);
                    int want = pendBtn;
                    pendId = -1; pendBtn = -1;
                    if (b != null && b.id == want) game.press(want);
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                joyId = -1; pendId = -1; pendBtn = -1;
                game.setJoystick(0, 0);
                break;
            default:
        }
        return true;
    }

    // =====================================================================
    //  Dessin
    // =====================================================================
    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : (now - last) / 1e9f;
        last = now;
        game.update(dt);
        if (game.screen != Game.PLAY && joyId != -1) { joyId = -1; game.setJoystick(0, 0); }

        final float W = game.W, H = game.H;
        int stageIdx = Math.max(0, game.stage - 1) % 5;
        boolean inRun = game.screen == Game.PLAY || game.screen == Game.LEVELUP || game.screen == Game.PAUSE
                || game.screen == Game.OVER || game.screen == Game.CLEAR;

        c.drawColor(inRun ? BG[stageIdx] : BG[0]);
        c.save();
        c.scale(scale, scale);
        if (game.shake > 0 && game.screen == Game.PLAY) {
            float s = game.shake * 14f;
            c.translate((srnd.nextFloat() - 0.5f) * s, (srnd.nextFloat() - 0.5f) * s);
        }

        drawStars(c, dt, W, H);

        if (inRun) {
            drawWorld(c, W, H);
            drawHud(c, W, H);
            if (game.flash > 0) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(withAlpha(game.invul > 0 && game.hp < game.maxHp ? 0xFFFF5252 : 0xFFFFFFFF, (int) (Math.min(1f, game.flash) * 90)));
                c.drawRect(0, 0, W, H, p);
            }
            if (game.screen == Game.PLAY) drawJoystick(c);
        }

        switch (game.screen) {
            case Game.MENU: drawMenu(c, W, H); break;
            case Game.SHOP: drawShopHeader(c, W, H); break;
            case Game.LEVELUP: dim(c, W, H, 170); text(c, "NIVEAU SUPÉRIEUR !", W / 2, H * 0.19f, 52, 0xFFFFEA00, Paint.Align.CENTER); text(c, "Choisis une amélioration", W / 2, H * 0.19f + 44, 26, 0xFFB0BEC5, Paint.Align.CENTER); break;
            case Game.PAUSE: dim(c, W, H, 170); text(c, "PAUSE", W / 2, H * 0.30f, 76, 0xFFFFFFFF, Paint.Align.CENTER); break;
            case Game.OVER: drawOver(c, W, H); break;
            case Game.CLEAR: drawClear(c, W, H); break;
            default:
        }
        drawButtons(c);
        c.restore();
        postInvalidateOnAnimation();
    }

    // ---------- fond ----------
    private void drawStars(Canvas c, float dt, float W, float H) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < sx.length; i++) {
            sy[i] += ss[i] * dt;
            if (sy[i] > H) { sy[i] = -4; sx[i] = srnd.nextFloat() * W; }
            p.setColor(withAlpha(0xFFFFFFFF, (int) (40 + ss[i] * 0.6f)));
            c.drawCircle(sx[i], sy[i], 1f + ss[i] / 110f, p);
        }
    }

    // ---------- monde ----------
    private void drawWorld(Canvas c, float W, float H) {
        Game g = game;
        // gemmes
        for (Game.Gem m : g.gems) {
            if (m.kind == 0) { glow(c, m.x, m.y, 7, 0xFF40C4FF); }
            else { glow(c, m.x, m.y, 8, 0xFFFFD740); }
        }
        // faisceau du laser joueur
        for (Game.WeaponInst w : g.weapons) {
            if (w.type == Game.W_LASER && w.on) {
                float width = 14 + 2 * w.level;
                float ex = g.px + (float) Math.cos(w.ang) * 2500f, ey = g.py + (float) Math.sin(w.ang) * 2500f;
                p.setStyle(Paint.Style.STROKE);
                p.setColor(withAlpha(0xFFFF2D95, 90)); p.setStrokeWidth(width * 1.8f); c.drawLine(g.px, g.py, ex, ey, p);
                p.setColor(0xFFFF2D95); p.setStrokeWidth(width); c.drawLine(g.px, g.py, ex, ey, p);
                p.setColor(0xFFFFFFFF); p.setStrokeWidth(width * 0.4f); c.drawLine(g.px, g.py, ex, ey, p);
            }
        }
        // faisceaux ennemis
        for (Game.Beam b : g.beams) {
            float ex = b.x + (float) Math.cos(b.ang) * 2500f, ey = b.y + (float) Math.sin(b.ang) * 2500f;
            p.setStyle(Paint.Style.STROKE);
            if (b.warn > 0) {
                int al = 80 + (int) (80 * Math.abs(Math.sin(g.time * 25)));
                p.setColor(withAlpha(b.col, al)); p.setStrokeWidth(3); c.drawLine(b.x, b.y, ex, ey, p);
            } else {
                p.setColor(withAlpha(b.col, 110)); p.setStrokeWidth(b.width * 1.5f); c.drawLine(b.x, b.y, ex, ey, p);
                p.setColor(b.col); p.setStrokeWidth(b.width); c.drawLine(b.x, b.y, ex, ey, p);
                p.setColor(0xFFFFFFFF); p.setStrokeWidth(b.width * 0.4f); c.drawLine(b.x, b.y, ex, ey, p);
            }
        }
        // ennemis
        for (Game.Enemy e : g.enemies) if (!e.dead) drawEnemy(c, e);
        // orbes
        for (float[] o : g.orbs) { glow(c, o[0], o[1], 16, 0xFFB388FF); }
        // joueur
        drawPlayer(c);
        // tirs joueur
        for (Game.PBullet b : g.pbullets) {
            if (b.kind == 1) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(withAlpha(0xFFFF9100, 80)); c.drawCircle(b.x, b.y, 18, p);
                p.setColor(0xFFFF9100); c.drawCircle(b.x, b.y, 9, p);
                p.setColor(((int) (g.time * 20)) % 2 == 0 ? 0xFFFF1744 : 0xFFFFFFFF); c.drawCircle(b.x, b.y, 4, p);
            } else {
                p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(withAlpha(b.col, 110)); p.setStrokeWidth(b.r * 2.6f);
                c.drawLine(b.x, b.y, b.x - b.vx * 0.025f, b.y - b.vy * 0.025f, p);
                p.setColor(0xFFFFFFFF); p.setStrokeWidth(b.r * 0.9f);
                c.drawLine(b.x, b.y, b.x - b.vx * 0.018f, b.y - b.vy * 0.018f, p);
                p.setStrokeCap(Paint.Cap.BUTT);
            }
        }
        // balles ennemies (au-dessus de tout pour la lisibilité)
        p.setStyle(Paint.Style.FILL);
        for (Game.Bullet b : g.ebullets) {
            p.setColor(withAlpha(b.col, 70)); c.drawCircle(b.x, b.y, b.r * 1.9f, p);
            p.setColor(b.col); c.drawCircle(b.x, b.y, b.r, p);
            p.setColor(0xFFFFFFFF); c.drawCircle(b.x, b.y, b.r * 0.5f, p);
        }
        // hitbox du joueur (point visible)
        p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFFFFF); c.drawCircle(g.px, g.py, 4f, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0xFFFF1744); c.drawCircle(g.px, g.py, Game.PR, p);

        // effets : tirs sniper / foudre
        for (Game.Fx f : g.fxs) {
            float k = f.life / f.max;
            p.setStyle(Paint.Style.STROKE);
            if (f.type == 0) {
                p.setColor(withAlpha(f.col, (int) (110 * k))); p.setStrokeWidth(f.width * 2.2f * k); c.drawLine(f.x1, f.y1, f.x2, f.y2, p);
                p.setColor(withAlpha(0xFFFFFFFF, (int) (255 * k))); p.setStrokeWidth(f.width * k); c.drawLine(f.x1, f.y1, f.x2, f.y2, p);
            } else {
                drawLightning(c, f, k);
            }
        }
        // anneaux (explosions, bombe)
        for (Game.Ring r : g.rings) {
            float k = r.t / r.dur, rr = r.maxR * (1f - (1f - k) * (1f - k));
            p.setStyle(Paint.Style.FILL); p.setColor(withAlpha(r.col, (int) (50 * (1 - k)))); c.drawCircle(r.x, r.y, rr, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(7 * (1 - k) + 1); p.setColor(withAlpha(r.col, (int) (220 * (1 - k)))); c.drawCircle(r.x, r.y, rr, p);
        }
        // particules
        p.setStyle(Paint.Style.FILL);
        for (Game.Part q : g.parts) {
            float k = q.life / q.max;
            p.setColor(withAlpha(q.col, (int) (255 * k))); c.drawCircle(q.x, q.y, q.size * (0.4f + k), p);
        }
    }

    private void drawLightning(Canvas c, Game.Fx f, float k) {
        int segs = 7;
        float dx = f.x2 - f.x1, dy = f.y2 - f.y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;
        float nx = -dy / len, ny = dx / len;
        Random r = new Random(f.seed * 31L + (long) (game.time * 40));
        path.reset();
        path.moveTo(f.x1, f.y1);
        for (int i = 1; i < segs; i++) {
            float t = i / (float) segs, off = (r.nextFloat() - 0.5f) * 40f;
            path.lineTo(f.x1 + dx * t + nx * off, f.y1 + dy * t + ny * off);
        }
        path.lineTo(f.x2, f.y2);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(withAlpha(f.col, (int) (120 * k))); p.setStrokeWidth(10); c.drawPath(path, p);
        p.setColor(withAlpha(0xFFFFFFFF, (int) (255 * k))); p.setStrokeWidth(3.5f); c.drawPath(path, p);
    }

    private void drawPlayer(Canvas c) {
        Game g = game;
        if (g.invul > 0 && g.invul < 50 && ((int) (g.time * 14)) % 2 == 0) return; // clignote
        float x = g.px, y = g.py;
        // flamme du réacteur
        float fl = 10 + 6 * (float) Math.abs(Math.sin(g.time * 40));
        path.reset(); path.moveTo(x - 7, y + 14); path.lineTo(x, y + 14 + fl + 6); path.lineTo(x + 7, y + 14); path.close();
        p.setStyle(Paint.Style.FILL); p.setColor(0xFFFF9100); c.drawPath(path, p);
        // coque
        path.reset();
        path.moveTo(x, y - 28); path.lineTo(x + 20, y + 18); path.lineTo(x, y + 8); path.lineTo(x - 20, y + 18); path.close();
        p.setColor(withAlpha(0xFF00E5FF, 70)); c.drawCircle(x, y, 34, p);
        p.setColor(0xFF00E5FF); c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f); p.setColor(0xFFFFFFFF); c.drawPath(path, p);
    }

    private void drawEnemy(Canvas c, Game.Enemy e) {
        boolean fl = e.flash > 0;
        int col = fl ? 0xFFFFFFFF : e.col;
        float x = e.x, y = e.y, r = e.r;
        if (e.boss) { drawBoss(c, e, fl); return; }
        p.setStyle(Paint.Style.FILL);
        p.setColor(withAlpha(e.col, 45)); c.drawCircle(x, y, r * 1.6f, p);
        path.reset();
        switch (e.type) {
            case 0:
                path.moveTo(x, y + r); path.lineTo(x - r * 0.95f, y - r * 0.7f); path.lineTo(x, y - r * 0.3f); path.lineTo(x + r * 0.95f, y - r * 0.7f); path.close();
                break;
            case 1:
                path.moveTo(x, y + r * 1.2f); path.lineTo(x + r * 0.85f, y); path.lineTo(x, y - r); path.lineTo(x - r * 0.85f, y); path.close();
                break;
            case 2:
                poly(path, x, y, r, 6, e.t * 2f);
                break;
            default:
                path.moveTo(x - r, y - r * 0.5f); path.lineTo(x + r, y - r * 0.5f); path.lineTo(x + r * 0.6f, y + r * 0.8f); path.lineTo(x - r * 0.6f, y + r * 0.8f); path.close();
        }
        p.setColor(col); c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f); p.setColor(0xFFFFFFFF); c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL); p.setColor(0xFF10131F); c.drawCircle(x, y, r * 0.25f, p);
        // barre de vie des gros ennemis
        if (e.type >= 2 && e.hp < e.maxHp) hpBar(c, x - r, y - r - 12, r * 2, 6, e.hp / e.maxHp, e.col);
    }

    private void drawBoss(Canvas c, Game.Enemy e, boolean fl) {
        float x = e.x, y = e.y, r = e.r, t = game.time;
        int col = fl ? 0xFFFFFFFF : e.col;
        p.setStyle(Paint.Style.FILL);
        p.setColor(withAlpha(e.col, 35)); c.drawCircle(x, y, r * 1.9f, p);
        p.setColor(withAlpha(e.col, 50)); c.drawCircle(x, y, r * 1.4f, p);
        path.reset();
        switch (e.bossType) {
            case 0: poly(path, x, y, r, 8, t * 0.7f); break;
            case 1: poly(path, x, y, r * 1.1f, 4, PI / 4f); break;
            case 2: poly(path, x, y, r, 6, -t * 0.5f); break;
            case 3: poly(path, x, y, r * 1.15f, 3, PI / 2f); break;
            default: star(path, x, y, r * 1.2f, r * 0.62f, 5, t * 0.8f);
        }
        p.setColor(0xFF10131F); c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(6); p.setColor(col); c.drawPath(path, p);
        // noyau
        float pulse = 1f + 0.12f * (float) Math.sin(t * 6);
        p.setStyle(Paint.Style.FILL);
        p.setColor(withAlpha(col, 160)); c.drawCircle(x, y, r * 0.5f * pulse, p);
        p.setColor(0xFFFFFFFF); c.drawCircle(x, y, r * 0.22f * pulse, p);
        if (e.bossType == 1) { // canon du tank
            p.setColor(col); c.drawRect(x - 10, y, x + 10, y + r * 1.25f, p);
        }
    }

    // ---------- HUD ----------
    private void drawHud(Canvas c, float W, float H) {
        Game g = game;
        // barre de progression d'étage / PV du boss
        float bx = 36, bw = W - 72, by = 72;
        p.setStyle(Paint.Style.FILL); p.setColor(0x55FFFFFF); c.drawRoundRect(bx, by, bx + bw, by + 14, 7, 7, p);
        if (g.boss != null && !g.boss.dead) {
            p.setColor(Game.BCOL[g.boss.bossType]);
            c.drawRoundRect(bx, by, bx + bw * Math.max(0, g.boss.hp / g.boss.maxHp), by + 14, 7, 7, p);
            text(c, Game.BNAME[g.boss.bossType], W / 2, by - 8, 22, 0xFFFFFFFF, Paint.Align.CENTER);
        } else {
            p.setColor(0xFF00E5FF);
            float prog = g.bossSpawned ? 1f : g.stageT / g.stageLen;
            c.drawRoundRect(bx, by, bx + bw * prog, by + 14, 7, 7, p);
            text(c, "ÉTAGE " + g.stage, W / 2, by - 8, 22, 0xFFFFFFFF, Paint.Align.CENTER);
        }
        // PV
        if (g.maxHp <= 8) {
            for (int i = 0; i < g.maxHp; i++) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(i < g.hp ? 0xFFFF1744 : 0x44FF1744);
                c.drawCircle(52 + i * 34, 128, 12, p);
                p.setColor(0xFFFFFFFF); c.drawCircle(52 + i * 34 - 4, 124, 3, p);
            }
        } else {
            p.setStyle(Paint.Style.FILL); p.setColor(0xFFFF1744); c.drawCircle(52, 128, 12, p);
            text(c, g.hp + " / " + g.maxHp, 74, 137, 28, 0xFFFFFFFF, Paint.Align.LEFT);
        }
        // or de la partie
        p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFD740); c.drawCircle(W - 205, 127, 11, p);
        text(c, String.valueOf(g.runGold), W - 188, 137, 28, 0xFFFFFFFF, Paint.Align.LEFT);
        // XP
        p.setColor(0x55FFFFFF); c.drawRoundRect(bx, 164, bx + bw, 182, 9, 9, p);
        p.setColor(0xFF69F0AE); c.drawRoundRect(bx, 164, bx + bw * Math.min(1f, g.xp / (float) g.xpNeed), 182, 9, 9, p);
        text(c, "NIV " + g.level, W / 2, 180, 17, 0xFF0A0F24, Paint.Align.CENTER);
        // armes équipées
        for (int i = 0; i < g.weapons.size(); i++) {
            Game.WeaponInst w = g.weapons.get(i);
            float x = 24 + i * 62, y = H - 84;
            p.setStyle(Paint.Style.FILL); p.setColor(0xAA0A0F24); c.drawRoundRect(x, y, x + 52, y + 52, 10, 10, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(Game.WCOL[w.type]); c.drawRoundRect(x, y, x + 52, y + 52, 10, 10, p);
            text(c, String.valueOf(w.level), x + 26, y + 36, 28, Game.WCOL[w.type], Paint.Align.CENTER);
        }
        // alerte boss
        if (g.warnT > 0 && g.screen == Game.PLAY) {
            int al = (int) (60 + 60 * Math.sin(g.time * 12));
            p.setStyle(Paint.Style.FILL); p.setColor(withAlpha(0xFFFF1744, Math.max(0, al))); c.drawRect(0, 0, W, H, p);
            text(c, "ALERTE BOSS", W / 2, H * 0.42f, 68, 0xFFFFFFFF, Paint.Align.CENTER);
            text(c, Game.BNAME[(g.stage - 1) % 5], W / 2, H * 0.42f + 62, 36, Game.BCOL[(g.stage - 1) % 5], Paint.Align.CENTER);
        }
    }

    private void drawJoystick(Canvas c) {
        if (joyId == -1) return;
        p.setStyle(Paint.Style.FILL); p.setColor(0x22FFFFFF); c.drawCircle(joyOx, joyOy, JOY_R, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(0x66FFFFFF); c.drawCircle(joyOx, joyOy, JOY_R, p);
        p.setStyle(Paint.Style.FILL); p.setColor(0x99FFFFFF); c.drawCircle(joyKx, joyKy, 34, p);
    }

    // ---------- écrans ----------
    private void drawMenu(Canvas c, float W, float H) {
        float t = game.time;
        // décor : anneaux et balles en orbite
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3);
        for (int i = 0; i < 3; i++) {
            p.setColor(withAlpha(0xFF00E5FF, 60 - i * 15));
            c.drawCircle(W / 2, H * 0.30f, 150 + i * 55, p);
        }
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 12; i++) {
            float a = t * 0.8f + i * PI / 6f, rr = 205 + (i % 3) * 55;
            glow(c, W / 2 + (float) Math.cos(a) * rr, H * 0.30f + (float) Math.sin(a) * rr, 7, i % 2 == 0 ? 0xFFFF2D95 : 0xFFFFEA00);
        }
        text(c, "BULLET", W / 2, H * 0.27f, 104, 0xFF00E5FF, Paint.Align.CENTER);
        text(c, "STORM", W / 2, H * 0.27f + 100, 104, 0xFFFF2D95, Paint.Align.CENTER);
        text(c, "Or : " + game.gold + "     Record : étage " + game.bestStage, W / 2, H * 0.50f - 40, 30, 0xFFFFD740, Paint.Align.CENTER);
        text(c, "Glisse pour te déplacer - le tir est automatique", W / 2, H - 50, 24, 0xFF90A4AE, Paint.Align.CENTER);
    }

    private void drawShopHeader(Canvas c, float W, float H) {
        text(c, "BOUTIQUE", W / 2, 90, 60, 0xFFFFEA00, Paint.Align.CENTER);
        text(c, "Or : " + game.gold, W / 2, 140, 34, 0xFFFFFFFF, Paint.Align.CENTER);
    }

    private void drawOver(Canvas c, float W, float H) {
        dim(c, W, H, 190);
        text(c, "DÉFAITE", W / 2, H * 0.22f, 84, 0xFFFF1744, Paint.Align.CENTER);
        text(c, "Étage atteint : " + game.stage, W / 2, H * 0.22f + 70, 36, 0xFFFFFFFF, Paint.Align.CENTER);
        text(c, "Niveau " + game.level + "  -  " + game.kills + " ennemis détruits", W / 2, H * 0.22f + 118, 30, 0xFFB0BEC5, Paint.Align.CENTER);
        text(c, "+" + game.lastReward + " or gagné", W / 2, H * 0.22f + 175, 40, 0xFFFFD740, Paint.Align.CENTER);
        text(c, "Record : étage " + game.bestStage, W / 2, H * 0.22f + 220, 26, 0xFF90A4AE, Paint.Align.CENTER);
    }

    private void drawClear(Canvas c, float W, float H) {
        dim(c, W, H, 170);
        text(c, "BOSS VAINCU !", W / 2, H * 0.28f, 70, 0xFF69F0AE, Paint.Align.CENTER);
        text(c, "Étage " + game.stage + " terminé", W / 2, H * 0.28f + 62, 36, 0xFFFFFFFF, Paint.Align.CENTER);
        text(c, "Bonus : +" + game.bossBonus + " or", W / 2, H * 0.28f + 118, 34, 0xFFFFD740, Paint.Align.CENTER);
        text(c, "+2 PV  -  bombes rechargées", W / 2, H * 0.28f + 166, 28, 0xFFB0BEC5, Paint.Align.CENTER);
        text(c, "Prochain boss : " + Game.BNAME[game.stage % 5], W / 2, H * 0.28f + 220, 28, Game.BCOL[game.stage % 5], Paint.Align.CENTER);
    }

    private void drawButtons(Canvas c) {
        for (Game.Button b : game.buttons) {
            if (game.screen == Game.PLAY) {
                if (b.id == Game.B_PAUSE) {
                    p.setStyle(Paint.Style.FILL); p.setColor(0x66000000); c.drawRoundRect(b.x, b.y, b.x + b.w, b.y + b.h, 14, 14, p);
                    p.setColor(0xFFFFFFFF);
                    c.drawRect(b.x + 18, b.y + 15, b.x + 28, b.y + b.h - 15, p);
                    c.drawRect(b.x + b.w - 28, b.y + 15, b.x + b.w - 18, b.y + b.h - 15, p);
                } else if (b.id == Game.B_BOMB) {
                    float cx = b.x + b.w / 2, cy = b.y + b.h / 2;
                    boolean ok = game.bombs > 0;
                    p.setStyle(Paint.Style.FILL); p.setColor(ok ? 0x55FF9100 : 0x33888888); c.drawCircle(cx, cy, 58, p);
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5); p.setColor(ok ? 0xFFFF9100 : 0xFF777777); c.drawCircle(cx, cy, 58, p);
                    text(c, "BOMBE", cx, cy - 2, 24, ok ? 0xFFFFFFFF : 0xFF999999, Paint.Align.CENTER);
                    text(c, "x" + game.bombs, cx, cy + 32, 30, ok ? 0xFFFFEA00 : 0xFF999999, Paint.Align.CENTER);
                }
                continue;
            }
            p.setStyle(Paint.Style.FILL); p.setColor(0xCC0E1430); c.drawRoundRect(b.x, b.y, b.x + b.w, b.y + b.h, 22, 22, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4); p.setColor(b.color); c.drawRoundRect(b.x, b.y, b.x + b.w, b.y + b.h, 22, 22, p);
            boolean hasSub = b.sub != null && b.sub.length() > 0;
            float cx = b.x + b.w / 2;
            float ts = b.title.length() > 22 ? 28 : 38;
            text(c, b.title, cx, hasSub ? b.y + b.h * 0.45f : b.y + b.h * 0.62f, ts, 0xFFFFFFFF, Paint.Align.CENTER);
            if (hasSub) text(c, b.sub, cx, b.y + b.h * 0.80f, 23, b.color, Paint.Align.CENTER);
        }
    }

    // ---------- utilitaires de dessin ----------
    private void dim(Canvas c, float W, float H, int a) {
        p.setStyle(Paint.Style.FILL); p.setColor(withAlpha(0xFF000000, a)); c.drawRect(0, 0, W, H, p);
    }

    private void text(Canvas c, String s, float x, float y, float size, int col, Paint.Align al) {
        p.setStyle(Paint.Style.FILL); p.setTextSize(size); p.setTextAlign(al); p.setColor(col);
        c.drawText(s, x, y, p);
    }

    private void glow(Canvas c, float x, float y, float r, int col) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(withAlpha(col, 70)); c.drawCircle(x, y, r * 1.9f, p);
        p.setColor(col); c.drawCircle(x, y, r, p);
        p.setColor(0xFFFFFFFF); c.drawCircle(x, y, r * 0.45f, p);
    }

    private void hpBar(Canvas c, float x, float y, float w, float h, float frac, int col) {
        p.setStyle(Paint.Style.FILL); p.setColor(0x88000000); c.drawRect(x, y, x + w, y + h, p);
        p.setColor(col); c.drawRect(x, y, x + w * Math.max(0, frac), y + h, p);
    }

    private static void poly(Path path, float cx, float cy, float r, int sides, float rot) {
        for (int i = 0; i < sides; i++) {
            float a = rot + i * 2f * PI / sides;
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
    }

    private static void star(Path path, float cx, float cy, float ro, float ri, int pts, float rot) {
        for (int i = 0; i < pts * 2; i++) {
            float a = rot + i * PI / pts, r = (i % 2 == 0) ? ro : ri;
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
    }

    private static int withAlpha(int col, int a) {
        if (a < 0) a = 0; if (a > 255) a = 255;
        return (col & 0x00FFFFFF) | (a << 24);
    }
}
