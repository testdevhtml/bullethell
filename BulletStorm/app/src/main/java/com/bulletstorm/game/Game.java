package com.bulletstorm.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Moteur du jeu (Java pur, aucune dépendance Android) : logique, entités, armes, boss, progression.
 * Le rendu et le tactile sont gérés par GameView.
 */
public class Game {

    public interface Storage {
        int getInt(String key, int def);
        void putInt(String key, int value);
    }

    // ---------- écrans ----------
    public static final int MENU = 0, PLAY = 1, LEVELUP = 2, PAUSE = 3, OVER = 4, SHOP = 5, CLEAR = 6;

    // ---------- armes ----------
    public static final int W_BLASTER = 0, W_LASER = 1, W_TNT = 2, W_SNIPER = 3, W_ORBS = 4, W_TESLA = 5;
    public static final int NW = 6, MAXW = 8;
    public static final String[] WNAME = {"BLASTER", "LASER", "LANCE-TNT", "SNIPER", "ORBES PLASMA", "TESLA"};
    public static final String[] WDESC = {
            "Tir en éventail auto-visé", "Rayon continu perforant", "Obus explosif de zone",
            "Tir perforant dévastateur", "Orbes qui bloquent les balles", "Foudre en chaîne"};
    public static final String[] WUPD = {
            "Plus de projectiles, plus de dégâts", "Rayon plus long et plus puissant", "Explosion plus grande, obus multiples",
            "Dégâts massifs et critiques", "Plus d'orbes, plus rapides", "Plus de rebonds, plus de dégâts"};
    public static final int[] WCOST = {0, 300, 500, 800, 1200, 1600};
    public static final int[] WCOL = {0xFF00E5FF, 0xFFFF2D95, 0xFFFF9100, 0xFFFFFFFF, 0xFFB388FF, 0xFFFFEA00};

    // ---------- bonus passifs ----------
    public static final int P_DMG = 0, P_RATE = 1, P_SPEED = 2, P_HP = 3, P_MAGNET = 4;
    public static final int NP = 5, MAXP = 6;
    public static final String[] PNAME = {"DÉGÂTS", "CADENCE", "VITESSE", "CŒUR MAX", "AIMANT"};
    public static final String[] PDESC = {"+15% de dégâts", "+12% de cadence de tir", "Déplacement plus rapide",
            "+1 PV max et +1 PV", "Portée de ramassage accrue"};

    // ---------- ennemis / boss ----------
    public static final int[] ECOL = {0xFFFF5252, 0xFFFF9100, 0xFFB388FF, 0xFFC6FF00};
    public static final String[] BNAME = {"GARDIEN SPIRALE", "TANK GATLING", "REINE DE LA RUCHE", "PRISME LASER", "NOYAU OMÉGA"};
    public static final int[] BCOL = {0xFF00E5FF, 0xFFFF9100, 0xFFB2FF59, 0xFFFF2D95, 0xFFFF1744};

    // ---------- boutons ----------
    public static final int B_PLAY = 1, B_SHOP = 2, B_WEAPON = 3, B_BACK = 4, B_PAUSE = 5, B_BOMB = 6,
            B_RESUME = 7, B_MENU = 8, B_NEXT = 9, B_AGAIN = 10, B_BUYW = 100, B_PERM = 200, B_CARD = 300;

    static final float PI = (float) Math.PI, TAU = PI * 2f;
    static final float PR = 8f; // rayon de la hitbox du joueur

    // ---------- classes d'entités ----------
    public static class Button {
        public int id;
        public String title = "", sub = "";
        public float x, y, w, h;
        public int color = 0xFF00E5FF;
        public boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    public static class WeaponInst {
        public int type, level = 1;
        public float cd, t, ang = -PI / 2f;
        public boolean on;
        WeaponInst(int ty) { type = ty; }
    }

    public static class Enemy {
        public int id, type, ph, state, col, bossType;
        public boolean boss, dead;
        public float x, y, vx, vy, r, hp, maxHp, t, flash, orbCd, shootT, baseX, ang, ang2, ty;
        public float[] tm = new float[6];
    }

    public static class Bullet {
        public float x, y, vx, vy, r;
        public int col;
        public boolean dead;
    }

    public static class PBullet {
        public float x, y, vx, vy, r, dmg, life, tx, ty, rad;
        public int pierce, col, kind, nHit;
        public boolean dead;
        int[] hit = new int[8];
        boolean has(int id) { for (int i = 0; i < nHit; i++) if (hit[i] == id) return true; return false; }
        void add(int id) { if (nHit < hit.length) hit[nHit++] = id; }
    }

    public static class Gem {
        public float x, y, vx;
        public int kind, val;
        public boolean mag, dead;
    }

    public static class Part {
        public float x, y, vx, vy, life, max, size;
        public int col;
    }

    public static class Fx {
        public float x1, y1, x2, y2, life, max, width;
        public int col, type, seed;
    }

    public static class Ring {
        public float x, y, maxR, t, dur;
        public int col;
    }

    public static class Beam {
        public float x, y, ang, warn, fire, width, rot;
        public Enemy owner;
        public int col;
    }

    // ---------- état ----------
    public float W = 720f, H = 1280f;
    public final Random rnd = new Random();
    private final Storage store;
    public int screen = MENU;
    public final List<Button> buttons = new ArrayList<>();
    public float time;

    // sauvegarde
    public int gold, unlockMask = 1, bestStage, permHp, permDmg, startWeapon;

    // joueur
    public float px, py, invul, joyX, joyY, shake, flash;
    public int hp, maxHp, bombs;
    public int level, xp, xpNeed, pendingLevels, kills, runGold, lastReward;
    public final List<WeaponInst> weapons = new ArrayList<>();
    public final int[] passives = new int[NP];
    public int[] choices = new int[3];

    // niveau
    public int stage;
    public float stageT, stageLen, warnT, spawnT, clearT;
    public boolean bossSpawned, bossDead, magnetAll;
    public Enemy boss;
    private int idc;
    public int bossBonus;

    public final List<Enemy> enemies = new ArrayList<>();
    public final List<Bullet> ebullets = new ArrayList<>();
    public final List<PBullet> pbullets = new ArrayList<>();
    public final List<Gem> gems = new ArrayList<>();
    public final List<Part> parts = new ArrayList<>();
    public final List<Fx> fxs = new ArrayList<>();
    public final List<Ring> rings = new ArrayList<>();
    public final List<Beam> beams = new ArrayList<>();
    public final List<float[]> orbs = new ArrayList<>(); // x, y, niveau

    public Game(Storage s) {
        store = s;
        gold = s.getInt("gold", 0);
        unlockMask = s.getInt("unlock", 1) | 1;
        bestStage = s.getInt("best", 0);
        permHp = s.getInt("phr", 0);
        permDmg = s.getInt("pdm", 0);
        startWeapon = s.getInt("sw", 0);
        if (!isUnlocked(startWeapon)) startWeapon = 0;
        px = W / 2; py = H * 0.78f;
        layoutButtons();
    }

    private void save() {
        store.putInt("gold", gold);
        store.putInt("unlock", unlockMask);
        store.putInt("best", bestStage);
        store.putInt("phr", permHp);
        store.putInt("pdm", permDmg);
        store.putInt("sw", startWeapon);
    }

    public boolean isUnlocked(int w) { return (unlockMask & (1 << w)) != 0; }

    public void resize(float w, float h) {
        W = w; H = h;
        if (screen != PLAY) { px = W / 2; py = H * 0.78f; }
        layoutButtons();
    }

    // =====================================================================
    //  Entrées / boutons
    // =====================================================================
    public void setJoystick(float x, float y) { joyX = x; joyY = y; }

    public Button hit(float x, float y) {
        for (int i = buttons.size() - 1; i >= 0; i--) if (buttons.get(i).contains(x, y)) return buttons.get(i);
        return null;
    }

    private void btn(int id, String title, String sub, float x, float y, float w, float h, int col) {
        Button b = new Button();
        b.id = id; b.title = title; b.sub = sub; b.x = x; b.y = y; b.w = w; b.h = h; b.color = col;
        buttons.add(b);
    }

    public void layoutButtons() {
        buttons.clear();
        float bw = 470, bh = 108, x = (W - bw) / 2f;
        switch (screen) {
            case MENU: {
                float y = H * 0.50f;
                btn(B_PLAY, "JOUER", "", x, y, bw, bh, 0xFF00E5FF);
                btn(B_WEAPON, "ARME : " + WNAME[startWeapon], "toucher pour changer", x, y + 130, bw, bh, WCOL[startWeapon]);
                btn(B_SHOP, "BOUTIQUE", "Or : " + gold, x, y + 260, bw, bh, 0xFFFFEA00);
                break;
            }
            case PLAY:
                btn(B_PAUSE, "II", "", W - 92, 96, 60, 60, 0xFFFFFFFF);
                btn(B_BOMB, "BOMBE", "", W - 150, H - 190, 120, 120, 0xFFFF9100);
                break;
            case LEVELUP:
                for (int i = 0; i < 3; i++) {
                    int c = choices[i];
                    String t, s;
                    int col;
                    if (c < NW) {
                        WeaponInst w = findWeapon(c);
                        if (w == null) { t = "NOUVELLE ARME : " + WNAME[c]; s = WDESC[c]; }
                        else { t = WNAME[c] + "  NV." + w.level + " > " + (w.level + 1); s = WUPD[c]; }
                        col = WCOL[c];
                    } else if (c < 20) {
                        int p = c - 10;
                        t = PNAME[p] + "  " + passives[p] + " > " + (passives[p] + 1); s = PDESC[p]; col = 0xFF69F0AE;
                    } else { t = "RÉPARATION"; s = "+2 PV"; col = 0xFFFF5252; }
                    btn(B_CARD + i, t, s, 50, H * 0.27f + i * 215, W - 100, 190, col);
                }
                break;
            case PAUSE:
                btn(B_RESUME, "REPRENDRE", "", x, H * 0.42f, bw, bh, 0xFF00E5FF);
                btn(B_MENU, "QUITTER LA PARTIE", "", x, H * 0.42f + 130, bw, bh, 0xFFFF5252);
                break;
            case OVER:
                btn(B_AGAIN, "REJOUER", "", x, H * 0.56f, bw, bh, 0xFF00E5FF);
                btn(B_SHOP, "BOUTIQUE", "", x, H * 0.56f + 130, bw, bh, 0xFFFFEA00);
                btn(B_MENU, "MENU", "", x, H * 0.56f + 260, bw, bh, 0xFFFFFFFF);
                break;
            case CLEAR:
                btn(B_NEXT, "ÉTAGE SUIVANT", "", x, H * 0.66f, bw, bh, 0xFF00E5FF);
                break;
            case SHOP: {
                float y = 170;
                for (int i = 0; i < NW; i++) {
                    String sub = (isUnlocked(i) ? "DÉBLOQUÉE" : WCOST[i] + " OR") + "  -  " + WDESC[i];
                    btn(B_BUYW + i, WNAME[i], sub, 40, y + i * 118, W - 80, 106, WCOL[i]);
                }
                float y2 = y + NW * 118;
                btn(B_PERM, "CŒUR DE DÉPART +1  (" + permHp + "/3)", permHp >= 3 ? "MAX" : permCostHp() + " OR", 40, y2, W - 80, 106, 0xFFFF5252);
                btn(B_PERM + 1, "DÉGÂTS +10%  (" + permDmg + "/5)", permDmg >= 5 ? "MAX" : permCostDmg() + " OR", 40, y2 + 118, W - 80, 106, 0xFF69F0AE);
                btn(B_BACK, "RETOUR", "", x, Math.max(y2 + 250, H - 130), bw, 90, 0xFFFFFFFF);
                break;
            }
            default:
        }
    }

    private int permCostHp() { return 500 * (permHp + 1); }
    private int permCostDmg() { return 300 * (permDmg + 1); }

    private void setScreen(int s) { screen = s; joyX = joyY = 0; layoutButtons(); }

    public void press(int id) {
        if (id >= B_CARD && id < B_CARD + 3) { applyCard(choices[id - B_CARD]); setScreen(PLAY); return; }
        if (id >= B_BUYW && id < B_BUYW + NW) {
            int w = id - B_BUYW;
            if (!isUnlocked(w) && gold >= WCOST[w]) { gold -= WCOST[w]; unlockMask |= 1 << w; save(); layoutButtons(); }
            return;
        }
        if (id == B_PERM) {
            if (permHp < 3 && gold >= permCostHp()) { gold -= permCostHp(); permHp++; save(); layoutButtons(); }
            return;
        }
        if (id == B_PERM + 1) {
            if (permDmg < 5 && gold >= permCostDmg()) { gold -= permCostDmg(); permDmg++; save(); layoutButtons(); }
            return;
        }
        switch (id) {
            case B_PLAY: case B_AGAIN: startRun(); break;
            case B_SHOP: setScreen(SHOP); break;
            case B_BACK: case B_MENU:
                if (screen == PAUSE) endRunEarly();
                setScreen(MENU);
                break;
            case B_WEAPON: {
                int w = startWeapon;
                for (int i = 1; i <= NW; i++) { int n = (w + i) % NW; if (isUnlocked(n)) { startWeapon = n; break; } }
                save(); layoutButtons();
                break;
            }
            case B_PAUSE: setScreen(PAUSE); break;
            case B_RESUME: setScreen(PLAY); break;
            case B_BOMB: useBomb(); break;
            case B_NEXT: nextStage(); break;
            default:
        }
    }

    /** Touche retour Android. Renvoie false pour quitter l'appli. */
    public boolean back() {
        switch (screen) {
            case PLAY: setScreen(PAUSE); return true;
            case PAUSE: setScreen(PLAY); return true;
            case LEVELUP: return true;
            case MENU: return false;
            default: setScreen(MENU); return true;
        }
    }

    public void pauseIfPlaying() { if (screen == PLAY) setScreen(PAUSE); }

    // =====================================================================
    //  Démarrage / fin de partie
    // =====================================================================
    public void startRun() {
        stage = 1; level = 1; xp = 0; xpNeed = xpFor(1); pendingLevels = 0; kills = 0; runGold = 0;
        Arrays.fill(passives, 0);
        maxHp = 5 + permHp; hp = maxHp; bombs = 2; invul = 0;
        px = W / 2; py = H * 0.78f;
        weapons.clear();
        weapons.add(new WeaponInst(startWeapon));
        beginStage();
        setScreen(PLAY);
    }

    private void beginStage() {
        clearAllEntities();
        stageT = 0; stageLen = 38f + 4f * Math.min(stage, 6); spawnT = 1f; warnT = 0;
        bossSpawned = false; bossDead = false; boss = null; magnetAll = false;
        shake = 0; flash = 0;
    }

    private void clearAllEntities() {
        enemies.clear(); ebullets.clear(); pbullets.clear(); gems.clear();
        parts.clear(); fxs.clear(); rings.clear(); beams.clear(); orbs.clear();
        for (WeaponInst w : weapons) { w.on = false; w.cd = 0; w.t = 0; }
    }

    private void nextStage() {
        stage++;
        hp = Math.min(maxHp, hp + 2);
        bombs = 2;
        beginStage();
        invul = 1.5f;
        setScreen(PLAY);
    }

    private void gameOver() {
        lastReward = runGold + kills / 2 + (stage - 1) * 60;
        gold += lastReward;
        if (stage > bestStage) bestStage = stage;
        save();
        setScreen(OVER);
    }

    private void endRunEarly() {
        int r = runGold + kills / 2 + (stage - 1) * 60;
        gold += r;
        if (stage > bestStage) bestStage = stage;
        save();
    }

    private void enterClear() {
        runGold += bossBonus;
        setScreen(CLEAR);
    }

    private int xpFor(int l) { return 5 + 3 * l; }

    // =====================================================================
    //  Level-up
    // =====================================================================
    private WeaponInst findWeapon(int type) {
        for (WeaponInst w : weapons) if (w.type == type) return w;
        return null;
    }

    private void openLevelUp() {
        List<Integer> pool = new ArrayList<>();
        for (WeaponInst w : weapons) if (w.level < MAXW) pool.add(w.type);
        if (weapons.size() < 3)
            for (int t = 0; t < NW; t++) if (isUnlocked(t) && findWeapon(t) == null) pool.add(t);
        for (int p = 0; p < NP; p++) if (passives[p] < MAXP) pool.add(10 + p);
        pool.add(20);
        Collections.shuffle(pool, rnd);
        for (int i = 0; i < 3; i++) choices[i] = pool.get(i % pool.size());
        // éviter les doublons quand la liste est courte
        if (pool.size() >= 3) for (int i = 0; i < 3; i++) choices[i] = pool.get(i);
        setScreen(LEVELUP);
    }

    private void applyCard(int c) {
        if (c < NW) {
            WeaponInst w = findWeapon(c);
            if (w == null) weapons.add(new WeaponInst(c)); else w.level++;
        } else if (c < 20) {
            int p = c - 10;
            passives[p]++;
            if (p == P_HP) { maxHp++; hp = Math.min(maxHp, hp + 1); }
        } else {
            hp = Math.min(maxHp, hp + 2);
        }
    }

    private void addXp(int v) {
        xp += v;
        while (xp >= xpNeed) { xp -= xpNeed; level++; xpNeed = xpFor(level); pendingLevels++; }
    }

    // =====================================================================
    //  Boucle principale
    // =====================================================================
    public void update(float dt) {
        if (dt > 0.033f) dt = 0.033f;
        time += dt;
        if (screen != PLAY) return;
        if (invul > 0) invul -= dt;
        shake = Math.max(0, shake - dt * 2.2f);
        flash = Math.max(0, flash - dt * 2f);
        movePlayer(dt);

        if (bossDead) {
            clearT -= dt;
            updateGems(dt); updateParts(dt); updateFxLists(dt);
            if (clearT <= 0) enterClear();
            return;
        }

        if (!bossSpawned) {
            stageT += dt;
            spawnWave(dt);
            if (stageT >= stageLen) { bossSpawned = true; warnT = 3.2f; }
        } else if (warnT > 0) {
            warnT -= dt;
            if (warnT <= 0) spawnBoss();
        }

        updateEnemies(dt);
        updateBeams(dt);
        updateWeapons(dt);
        updatePBullets(dt);
        updateEBullets(dt);
        updateGems(dt);
        updateParts(dt);
        updateFxLists(dt);

        if (hp <= 0) { gameOver(); return; }
        if (pendingLevels > 0) { pendingLevels--; openLevelUp(); }
    }

    private void movePlayer(float dt) {
        float sp = 330f + 28f * passives[P_SPEED];
        float jx = joyX, jy = joyY;
        float m = (float) Math.sqrt(jx * jx + jy * jy);
        if (m > 1f) { jx /= m; jy /= m; }
        px = clamp(px + jx * sp * dt, 22, W - 22);
        py = clamp(py + jy * sp * dt, 210, H - 60);
    }

    // =====================================================================
    //  Apparition des ennemis
    // =====================================================================
    private void spawnWave(float dt) {
        spawnT -= dt;
        if (spawnT > 0) return;
        float prog = stageT / stageLen;
        spawnT = Math.max(0.5f, 1.6f - 0.07f * stage - 0.5f * prog) * (0.75f + 0.5f * rnd.nextFloat());
        if (enemies.size() >= 22) return;
        int types = Math.min(4, stage + 1);
        int type = rnd.nextFloat() < 0.45f ? 0 : rnd.nextInt(types);
        if (type == 0 && rnd.nextFloat() < 0.35f) {
            int n = 3 + rnd.nextInt(3);
            float x0 = 80 + rnd.nextFloat() * (W - 160 - (n - 1) * 90);
            for (int i = 0; i < n; i++) spawnEnemy(0, x0 + i * 90, -40);
        } else {
            spawnEnemy(type, 80 + rnd.nextFloat() * (W - 160), -40);
        }
    }

    private void spawnEnemy(int type, float x, float y) {
        Enemy e = new Enemy();
        e.id = ++idc; e.type = type; e.x = x; e.y = y; e.baseX = x; e.col = ECOL[type];
        float hm = 1f + 0.3f * (stage - 1);
        switch (type) {
            case 0: e.hp = 16 * hm; e.r = 20; e.vy = 110; e.shootT = 0.8f + rnd.nextFloat() * 1.5f; e.ang = rnd.nextFloat() * 6f; break;
            case 1: e.hp = 30 * hm; e.r = 22; e.ty = H * 0.15f + rnd.nextFloat() * H * 0.2f; break;
            case 2: e.hp = 65 * hm; e.r = 28; e.ty = H * 0.12f + rnd.nextFloat() * H * 0.15f; e.shootT = 0.5f; break;
            default: e.hp = 48 * hm; e.r = 26; e.ty = H * 0.10f + rnd.nextFloat() * H * 0.15f; e.shootT = 1f; break;
        }
        e.maxHp = e.hp;
        enemies.add(e);
    }

    private float bulletMul() { return Math.min(1.5f, 1f + 0.04f * (stage - 1)); }

    private void updateEnemies(float dt) {
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            if (e.dead) continue;
            e.t += dt; e.flash -= dt; e.orbCd -= dt;
            if (e.boss) updateBoss(e, dt); else updateMinion(e, dt);
            if (!e.dead && invul <= 0) {
                float rr = e.r + PR;
                if (dist2(e.x, e.y, px, py) < rr * rr) hitPlayer();
            }
        }
        enemies.removeIf(e -> e.dead || (!e.boss && (e.y > H + 90 || e.y < -250 || e.x < -140 || e.x > W + 140)));
    }

    private void updateMinion(Enemy e, float dt) {
        float bs = bulletMul();
        switch (e.type) {
            case 0:
                e.y += e.vy * dt;
                e.x = e.baseX + sin(e.t * 2f + e.ang) * 60f;
                e.shootT -= dt;
                if (e.shootT <= 0 && e.y > 0 && e.y < H * 0.7f) {
                    e.shootT = Math.max(1.0f, 2.2f - 0.12f * stage) + rnd.nextFloat() * 0.6f;
                    bullet(e.x, e.y, ang(e.x, e.y, px, py), 220 * bs, 6, e.col);
                }
                break;
            case 1:
                if (e.state == 0) { e.y += 170 * dt; if (e.y >= e.ty) { e.state = 1; e.t = 0; } }
                else if (e.state == 1) {
                    if (e.t > 0.7f) { float a = ang(e.x, e.y, px, py); e.vx = cos(a) * 480; e.vy = sin(a) * 480; e.state = 2; }
                } else { e.x += e.vx * dt; e.y += e.vy * dt; }
                break;
            case 2:
                if (e.state == 0) { e.y += 100 * dt; if (e.y >= e.ty) { e.state = 1; e.t = 0; } }
                else if (e.state == 1) {
                    e.shootT -= dt;
                    if (e.shootT <= 0) {
                        e.shootT = 0.13f; e.ang += 0.45f;
                        bullet(e.x, e.y, e.ang, 165 * bs, 6, e.col);
                        bullet(e.x, e.y, e.ang + PI, 165 * bs, 6, e.col);
                    }
                    if (e.t > 7f) e.state = 2;
                } else e.y += 130 * dt;
                break;
            default:
                if (e.state == 0) { e.y += 130 * dt; if (e.y >= e.ty) { e.state = 1; e.t = 0; e.vx = (rnd.nextBoolean() ? 1 : -1) * 90; } }
                else if (e.state == 1) {
                    e.x += e.vx * dt;
                    if (e.x < 60) { e.x = 60; e.vx = Math.abs(e.vx); }
                    if (e.x > W - 60) { e.x = W - 60; e.vx = -Math.abs(e.vx); }
                    e.shootT -= dt;
                    if (e.shootT <= 0) { e.shootT = 1.5f; fanAt(e.x, e.y, 5, 0.24f, 235 * bs, e.col); }
                    if (e.t > 12f) e.state = 2;
                } else e.y += 140 * dt;
        }
    }

    // =====================================================================
    //  Boss
    // =====================================================================
    private void spawnBoss() {
        Enemy b = new Enemy();
        b.id = ++idc; b.boss = true; b.bossType = (stage - 1) % 5;
        b.x = W / 2; b.y = -130; b.r = 62; b.col = BCOL[b.bossType];
        b.maxHp = b.hp = 1500f + 650f * (stage - 1);
        enemies.add(b);
        boss = b;
    }

    private void updateBoss(Enemy b, float dt) {
        if (b.state == 0) {
            b.y += 140 * dt;
            if (b.y >= H * 0.17f) { b.y = H * 0.17f; b.state = 1; b.t = 0; }
            return;
        }
        float frac = b.hp / b.maxHp;
        int ph = frac > 0.66f ? 0 : frac > 0.33f ? 1 : 2;
        if (ph != b.ph) {
            b.ph = ph;
            clearBullets(); beams.clear();
            flash = 0.6f; shake = 0.6f;
            burst(b.x, b.y, 40, b.col, 320);
            Arrays.fill(b.tm, 0f);
            b.tm[5] = -1.2f; // courte pause entre les phases
        }
        // déplacement
        float amp = b.bossType == 1 ? W * 0.32f : W * 0.26f;
        float spd = b.bossType == 1 ? 0.5f : 0.6f;
        b.x = W / 2 + sin(b.t * spd) * amp;
        b.y = H * 0.17f + sin(b.t * 0.9f) * 18f;

        if (b.tm[5] < 0) { b.tm[5] += dt; return; }
        float bs = bulletMul();

        switch (b.bossType) {
            case 0: { // GARDIEN SPIRALE
                float rate = ph == 0 ? 0.09f : ph == 1 ? 0.08f : 0.07f;
                int arms = ph == 0 ? 2 : ph == 1 ? 3 : 4;
                float dir = (ph == 1 && ((int) (b.t / 3f)) % 2 == 1) ? -0.33f : 0.33f;
                b.tm[0] += dt;
                while (b.tm[0] >= rate) {
                    b.tm[0] -= rate; b.ang += dir;
                    for (int k = 0; k < arms; k++) bullet(b.x, b.y, b.ang + k * TAU / arms, 185 * bs, 6, b.col);
                }
                b.tm[1] += dt;
                if (b.tm[1] >= (ph == 0 ? 2.6f : ph == 1 ? 2.2f : 1.8f)) { b.tm[1] = 0; ring(b, 18 + ph * 5, 160 * bs, rnd.nextFloat() * TAU, 0xFFFFFFFF); }
                if (ph == 2) { b.tm[2] += dt; if (b.tm[2] >= 1.6f) { b.tm[2] = 0; fanAt(b.x, b.y, 5, 0.22f, 260 * bs, 0xFFFFEA00); } }
                break;
            }
            case 1: { // TANK GATLING
                int n = ph == 0 ? 5 : ph == 1 ? 7 : 9;
                b.tm[0] += dt;
                if (b.tm[0] >= (ph == 0 ? 1.7f : ph == 1 ? 1.4f : 1.2f) && b.tm[1] <= 0) { b.tm[0] = 0; b.tm[1] = 3; b.tm[2] = 0; }
                if (b.tm[1] > 0) {
                    b.tm[2] -= dt;
                    if (b.tm[2] <= 0) { b.tm[2] = 0.17f; b.tm[1] -= 1; fanAt(b.x, b.y, n, 0.2f, 300 * bs, b.col); }
                }
                b.tm[3] += dt;
                if (b.tm[3] >= (ph == 0 ? 5.5f : ph == 1 ? 4.5f : 3.5f)) { b.tm[3] = 0; curtain(bs, 72, 0xFFFFFFFF); }
                if (ph == 2) { b.tm[4] += dt; if (b.tm[4] >= 2.4f) { b.tm[4] = 0; ring(b, 16, 150 * bs, 0, 0xFFFFEA00); } }
                break;
            }
            case 2: { // REINE DE LA RUCHE
                b.tm[0] += dt;
                if (b.tm[0] >= (ph == 0 ? 1.5f : ph == 1 ? 1.3f : 1.1f)) { b.tm[0] = 0; b.ang += 0.2f; ring(b, 14 + ph * 2, 150 * bs, b.ang, b.col); }
                b.tm[1] += dt;
                if (b.tm[1] >= 4.2f - ph * 0.5f) {
                    b.tm[1] = 0;
                    int n = 2 + ph;
                    if (enemies.size() < 20) for (int i = 0; i < n; i++) spawnEnemy(0, b.x + (i - (n - 1) / 2f) * 70, b.y + 50);
                }
                b.tm[2] += dt;
                if (b.tm[2] >= 0.45f) {
                    b.tm[2] = 0;
                    for (int i = 0; i < 8; i++) bullet(b.x, b.y, i * TAU / 8 + b.t * 0.5f, (110 + 60 * sin(b.t * 2 + i)) * bs, 6, 0xFFFFEA00);
                }
                if (ph >= 1) {
                    b.tm[3] += dt;
                    if (b.tm[3] >= 0.14f) { b.tm[3] = 0; b.ang2 -= 0.31f; bullet(b.x, b.y, b.ang2, 170 * bs, 6, 0xFFFFFFFF); bullet(b.x, b.y, b.ang2 + PI, 170 * bs, 6, 0xFFFFFFFF); }
                }
                break;
            }
            case 3: { // PRISME LASER
                b.tm[0] += dt;
                if (b.tm[0] >= (ph == 0 ? 4.2f : ph == 1 ? 3.8f : 3.4f)) {
                    b.tm[0] = 0;
                    int n = 2 + ph;
                    float a = ang(b.x, b.y, px, py);
                    for (int i = 0; i < n; i++) addBeam(b, a + (i - (n - 1) / 2f) * 0.55f, 1.1f, 0.7f, 44, 0);
                    if (ph == 2) { float dir = rnd.nextBoolean() ? 1 : -1; addBeam(b, dir > 0 ? 0.3f : PI - 0.3f, 1.0f, 2.2f, 40, dir * 1.1f); }
                }
                b.tm[1] += dt;
                if (b.tm[1] >= 1.6f - 0.1f * ph) { b.tm[1] = 0; ring(b, 12 + ph * 3, 150 * bs, rnd.nextFloat() * TAU, b.col); }
                b.tm[2] += dt;
                if (b.tm[2] >= (ph == 0 ? 7f : 5.5f)) { b.tm[2] = 0; curtain(bs, 70, b.col); }
                break;
            }
            default: { // NOYAU OMÉGA : enchaîne tous les patterns
                int atk = ((int) (b.t / 7f)) % 4;
                float sp = 1f + 0.15f * ph;
                if (atk == 0 || atk == 3) {
                    b.tm[0] += dt;
                    float rate = 0.085f / sp;
                    while (b.tm[0] >= rate) {
                        b.tm[0] -= rate;
                        b.ang += (atk == 0 ? 0.3f : -0.3f);
                        int arms = atk == 0 ? 3 : 2;
                        for (int k = 0; k < arms; k++) bullet(b.x, b.y, b.ang + k * TAU / arms, 185 * bs, 6, atk == 0 ? b.col : 0xFFFFFFFF);
                        if (atk == 3) bullet(b.x, b.y, -b.ang * 1.7f + 1f, 150 * bs, 6, 0xFF00E5FF);
                    }
                }
                if (atk == 1) {
                    b.tm[1] += dt;
                    if (b.tm[1] >= 0.9f / sp) { b.tm[1] = 0; fanAt(b.x, b.y, 7, 0.2f, 290 * bs, 0xFFFFEA00); }
                    b.tm[2] += dt;
                    if (b.tm[2] >= 2.4f) { b.tm[2] = 0; ring(b, 22, 150 * bs, rnd.nextFloat() * TAU, 0xFFFFFFFF); }
                }
                if (atk == 2) {
                    b.tm[3] += dt;
                    if (b.tm[3] >= 2.6f / sp) { b.tm[3] = 0; curtain(bs, 70, 0xFFFFFFFF); }
                    b.tm[4] += dt;
                    if (b.tm[4] >= 4f) {
                        b.tm[4] = 0;
                        float a = ang(b.x, b.y, px, py);
                        addBeam(b, a - 0.35f, 1.1f, 0.7f, 42, 0);
                        addBeam(b, a + 0.35f, 1.1f, 0.7f, 42, 0);
                    }
                }
                break;
            }
        }
    }

    private void addBeam(Enemy owner, float a, float warn, float fire, float width, float rot) {
        Beam bm = new Beam();
        bm.owner = owner; bm.x = owner.x; bm.y = owner.y; bm.ang = a;
        bm.warn = warn; bm.fire = fire; bm.width = width; bm.rot = rot; bm.col = owner.col;
        beams.add(bm);
    }

    private void updateBeams(float dt) {
        for (Beam bm : beams) {
            if (bm.owner != null && !bm.owner.dead) { bm.x = bm.owner.x; bm.y = bm.owner.y; }
            if (bm.warn > 0) { bm.warn -= dt; continue; }
            bm.fire -= dt;
            bm.ang += bm.rot * dt;
            if (invul <= 0) {
                float ex = bm.x + cos(bm.ang) * 2500f, ey = bm.y + sin(bm.ang) * 2500f;
                if (segDist(bm.x, bm.y, ex, ey, px, py) < bm.width * 0.5f + 4f) hitPlayer();
            }
        }
        beams.removeIf(bm -> bm.fire <= 0 || (bm.owner != null && bm.owner.dead));
    }

    private void bossDefeated() {
        bossDead = true;
        clearT = 2.2f;
        bossBonus = 40 + 25 * stage;
        invul = 99f;
        flash = 1f; shake = 1f;
        clearBullets(); beams.clear();
        for (Enemy e : enemies) {
            if (e.dead) continue;
            e.dead = true;
            burst(e.x, e.y, e.boss ? 80 : 10, e.col, 380);
        }
        magnetAll = true;
        for (int i = 0; i < 4; i++) rings.add(ring(boss.x, boss.y, 160 + i * 90, 0.5f + i * 0.15f, boss.col));
    }

    // =====================================================================
    //  Armes du joueur
    // =====================================================================
    private float dmgMul() { return 1f + 0.15f * passives[P_DMG] + 0.1f * permDmg; }
    private float rateMul() { return 1f + 0.12f * passives[P_RATE]; }

    private void updateWeapons(float dt) {
        float dm = dmgMul(), rm = rateMul();
        orbs.clear();
        for (WeaponInst w : weapons) {
            switch (w.type) {
                case W_BLASTER: {
                    w.cd -= dt * rm;
                    if (w.cd <= 0) {
                        Enemy t = nearest(px, py);
                        if (t != null) { w.cd = Math.max(0.13f, 0.30f - 0.015f * w.level); fireBlaster(w, t, dm); } else w.cd = 0;
                    }
                    break;
                }
                case W_LASER: {
                    if (w.on) {
                        w.t -= dt;
                        Enemy t = nearest(px, py);
                        float target = t != null ? ang(px, py, t.x, t.y) : -PI / 2f;
                        w.ang = lerpAngle(w.ang, target, Math.min(1f, 9f * dt));
                        w.cd -= dt;
                        if (w.cd <= 0) { w.cd = 0.08f; laserTick(w, dm); }
                        if (w.t <= 0) { w.on = false; w.t = Math.max(0.6f, 1.7f - 0.1f * w.level) / rm; }
                    } else {
                        w.t -= dt;
                        if (w.t <= 0) {
                            if (nearest(px, py) != null) { w.on = true; w.t = 0.9f + 0.12f * w.level; w.cd = 0; w.ang = -PI / 2f; }
                            else w.t = 0;
                        }
                    }
                    break;
                }
                case W_TNT: {
                    w.cd -= dt * rm;
                    if (w.cd <= 0) {
                        Enemy t = nearest(px, py);
                        if (t != null) {
                            w.cd = Math.max(0.7f, 1.6f - 0.1f * w.level);
                            fireTnt(w, t, dm);
                            if (w.level >= 5) { Enemy t2 = randomEnemy(); fireTnt(w, t2 != null ? t2 : t, dm); }
                        } else w.cd = 0;
                    }
                    break;
                }
                case W_SNIPER: {
                    w.cd -= dt * rm;
                    if (w.cd <= 0) {
                        Enemy t = strongest();
                        if (t != null) { w.cd = Math.max(0.8f, 2.0f - 0.12f * w.level); fireSniper(w, t, dm); } else w.cd = 0;
                    }
                    break;
                }
                case W_ORBS: {
                    w.ang += dt * (3.2f + 0.8f * (rm - 1f));
                    int n = 2 + w.level / 3;
                    float rad = 95 + 4 * w.level;
                    for (int i = 0; i < n; i++) {
                        float a = w.ang + i * TAU / n;
                        orbs.add(new float[]{px + cos(a) * rad, py + sin(a) * rad, w.level});
                    }
                    break;
                }
                default: { // TESLA
                    w.cd -= dt * rm;
                    if (w.cd <= 0) {
                        Enemy t = nearest(px, py);
                        if (t != null) { w.cd = Math.max(0.45f, 1.2f - 0.07f * w.level); fireTesla(w, t, dm); } else w.cd = 0;
                    }
                }
            }
        }
        orbCollisions(dm);
    }

    private void fireBlaster(WeaponInst w, Enemy t, float dm) {
        int n = 1 + w.level / 2;
        float base = ang(px, py - 12, t.x, t.y);
        for (int i = 0; i < n; i++) {
            float a = base + (i - (n - 1) / 2f) * 0.11f;
            PBullet b = new PBullet();
            b.x = px; b.y = py - 12; b.vx = cos(a) * 820; b.vy = sin(a) * 820;
            b.r = 6; b.dmg = (7 + 1.6f * w.level) * dm; b.pierce = w.level >= 6 ? 1 : 0;
            b.col = WCOL[W_BLASTER]; b.life = 1.6f;
            pbullets.add(b);
        }
    }

    private void laserTick(WeaponInst w, float dm) {
        float ex = px + cos(w.ang) * 2500f, ey = py + sin(w.ang) * 2500f;
        float width = 14 + 2 * w.level;
        float dmg = (55 + 25 * w.level) * 0.08f * dm;
        for (Enemy e : enemies) {
            if (e.dead || e.y < -20) continue;
            if (segDist(px, py, ex, ey, e.x, e.y) < e.r + width * 0.5f) damageEnemy(e, dmg);
        }
    }

    private void fireTnt(WeaponInst w, Enemy t, float dm) {
        float a = ang(px, py, t.x, t.y);
        PBullet b = new PBullet();
        b.x = px; b.y = py - 10; b.vx = cos(a) * 430; b.vy = sin(a) * 430;
        b.r = 9; b.kind = 1; b.tx = t.x; b.ty = t.y; b.life = 3f;
        b.dmg = (45 + 16 * w.level) * dm; b.rad = 85 + 9 * w.level; b.col = WCOL[W_TNT];
        pbullets.add(b);
    }

    private void explode(float x, float y, float dmg, float rad) {
        Ring r = new Ring();
        r.x = x; r.y = y; r.maxR = rad; r.dur = 0.4f; r.col = WCOL[W_TNT];
        rings.add(r);
        shake = Math.min(1f, shake + 0.15f);
        burst(x, y, 22, 0xFFFF9100, 330);
        burst(x, y, 10, 0xFFFFEA00, 200);
        for (Bullet b : ebullets) if (!b.dead && dist2(b.x, b.y, x, y) < rad * rad) b.dead = true;
        for (Enemy e : enemies) {
            if (e.dead || e.y < -30) continue;
            float rr = rad + e.r;
            if (dist2(e.x, e.y, x, y) < rr * rr) damageEnemy(e, dmg);
        }
    }

    private void fireSniper(WeaponInst w, Enemy t, float dm) {
        float a = ang(px, py, t.x, t.y);
        float ex = px + cos(a) * 2500f, ey = py + sin(a) * 2500f;
        boolean crit = rnd.nextFloat() < Math.min(0.5f, 0.05f * w.level);
        float dmg = (75 + 36 * w.level) * dm * (crit ? 2f : 1f);
        for (Enemy e : enemies) {
            if (e.dead || e.y < -30) continue;
            if (segDist(px, py, ex, ey, e.x, e.y) < e.r + 10) { damageEnemy(e, dmg); burst(e.x, e.y, 6, 0xFFFFFFFF, 220); }
        }
        Fx f = new Fx();
        f.x1 = px; f.y1 = py; f.x2 = ex; f.y2 = ey; f.life = f.max = 0.3f; f.col = crit ? 0xFFFFEA00 : 0xFFFFFFFF; f.type = 0; f.width = crit ? 9 : 6;
        fxs.add(f);
        shake = Math.min(1f, shake + 0.08f);
    }

    private void fireTesla(WeaponInst w, Enemy first, float dm) {
        int jumps = 2 + w.level / 2;
        float dmg = (16 + 7 * w.level) * dm;
        List<Enemy> chain = new ArrayList<>();
        Enemy cur = first;
        float fx = px, fy = py;
        for (int j = 0; j < jumps && cur != null; j++) {
            chain.add(cur);
            Fx f = new Fx();
            f.x1 = fx; f.y1 = fy; f.x2 = cur.x; f.y2 = cur.y; f.life = f.max = 0.22f; f.col = WCOL[W_TESLA]; f.type = 1; f.width = 4; f.seed = rnd.nextInt(1000);
            fxs.add(f);
            damageEnemy(cur, dmg);
            fx = cur.x; fy = cur.y;
            Enemy next = null; float bd = 230f * 230f;
            for (Enemy e : enemies) {
                if (e.dead || e.y < -30 || chain.contains(e)) continue;
                float d = dist2(e.x, e.y, fx, fy);
                if (d < bd) { bd = d; next = e; }
            }
            cur = next;
        }
    }

    private void orbCollisions(float dm) {
        for (float[] o : orbs) {
            float dmg = (12 + 4 * o[2]) * dm;
            for (Enemy e : enemies) {
                if (e.dead || e.y < -30 || e.orbCd > 0) continue;
                float rr = e.r + 16;
                if (dist2(e.x, e.y, o[0], o[1]) < rr * rr) { e.orbCd = 0.25f; damageEnemy(e, dmg); }
            }
            for (Bullet b : ebullets) {
                if (b.dead) continue;
                float rr = 16 + b.r;
                if (dist2(b.x, b.y, o[0], o[1]) < rr * rr) { b.dead = true; burst(b.x, b.y, 2, b.col, 90); }
            }
        }
    }

    private void updatePBullets(float dt) {
        for (PBullet b : pbullets) {
            b.x += b.vx * dt; b.y += b.vy * dt; b.life -= dt;
            if (b.life <= 0 || b.x < -50 || b.x > W + 50 || b.y < -50 || b.y > H + 50) {
                if (b.kind == 1 && b.life <= 0) explode(b.x, b.y, b.dmg, b.rad);
                b.dead = true;
                continue;
            }
            if (b.kind == 1 && dist2(b.x, b.y, b.tx, b.ty) < 16f * 16f) {
                explode(b.x, b.y, b.dmg, b.rad); b.dead = true; continue;
            }
            for (Enemy e : enemies) {
                if (e.dead || e.y < -30) continue;
                float rr = e.r + b.r;
                if (dist2(e.x, e.y, b.x, b.y) < rr * rr) {
                    if (b.kind == 1) { explode(b.x, b.y, b.dmg, b.rad); b.dead = true; break; }
                    if (b.has(e.id)) continue;
                    damageEnemy(e, b.dmg);
                    b.add(e.id);
                    burst(b.x, b.y, 2, b.col, 120);
                    if (b.pierce <= 0) { b.dead = true; break; }
                    b.pierce--;
                }
            }
        }
        pbullets.removeIf(b -> b.dead);
    }

    private void updateEBullets(float dt) {
        for (Bullet b : ebullets) {
            if (b.dead) continue;
            b.x += b.vx * dt; b.y += b.vy * dt;
            if (b.x < -60 || b.x > W + 60 || b.y < -60 || b.y > H + 60) { b.dead = true; continue; }
            if (invul <= 0) {
                float rr = b.r + PR;
                if (dist2(b.x, b.y, px, py) < rr * rr) { b.dead = true; hitPlayer(); }
            }
        }
        ebullets.removeIf(b -> b.dead);
    }

    // =====================================================================
    //  Dégâts, morts, drops
    // =====================================================================
    private void damageEnemy(Enemy e, float d) {
        if (e.dead) return;
        if (e.boss && e.state == 0) return; // invulnérable pendant l'entrée
        e.hp -= d;
        e.flash = 0.07f;
        if (e.hp <= 0) killEnemy(e);
    }

    private void killEnemy(Enemy e) {
        if (e.dead) return;
        e.dead = true;
        kills++;
        burst(e.x, e.y, e.boss ? 60 : 12, e.col, e.boss ? 380 : 230);
        if (e.boss) {
            for (int i = 0; i < 14; i++) dropGem(e.x, e.y, 0, 3);
            for (int i = 0; i < 10; i++) dropGem(e.x, e.y, 1, 2 + stage / 2);
            bossDefeated();
        } else {
            dropGem(e.x, e.y, 0, e.type == 0 ? 1 : e.type == 1 ? 2 : 3);
            if (rnd.nextFloat() < 0.25f) dropGem(e.x, e.y, 1, 1 + stage / 2);
        }
    }

    private void dropGem(float x, float y, int kind, int val) {
        Gem g = new Gem();
        g.x = x + (rnd.nextFloat() - 0.5f) * 30; g.y = y + (rnd.nextFloat() - 0.5f) * 30;
        g.vx = (rnd.nextFloat() - 0.5f) * 140; g.kind = kind; g.val = val;
        gems.add(g);
    }

    private void updateGems(float dt) {
        float mr = 120f + 50f * passives[P_MAGNET];
        for (Gem g : gems) {
            float d2 = dist2(g.x, g.y, px, py);
            if (magnetAll || d2 < mr * mr) g.mag = true;
            if (g.mag) {
                float a = ang(g.x, g.y, px, py);
                g.x += cos(a) * 640 * dt; g.y += sin(a) * 640 * dt;
            } else { g.y += 70 * dt; g.x += g.vx * dt; g.vx *= 0.98f; }
            if (d2 < 28f * 28f) {
                g.dead = true;
                if (g.kind == 0) addXp(g.val); else runGold += g.val;
            } else if (g.y > H + 30) g.dead = true;
        }
        gems.removeIf(g -> g.dead);
    }

    private void hitPlayer() {
        if (invul > 0 || bossDead) return;
        hp--;
        invul = 1.6f;
        shake = 0.6f; flash = 0.5f;
        burst(px, py, 20, 0xFFFF5252, 260);
        for (Bullet b : ebullets) if (dist2(b.x, b.y, px, py) < 150f * 150f) b.dead = true;
    }

    private void useBomb() {
        if (screen != PLAY || bombs <= 0 || bossDead) return;
        bombs--;
        invul = Math.max(invul, 1.5f);
        flash = 0.8f; shake = 0.8f;
        clearBullets();
        Ring r = new Ring();
        r.x = px; r.y = py; r.maxR = Math.max(W, H); r.dur = 0.6f; r.col = 0xFFFF9100;
        rings.add(r);
        for (Enemy e : enemies) if (!e.dead && e.y > -30) damageEnemy(e, e.boss ? 120 : 150);
    }

    /** Efface toutes les balles ennemies (conversion en particules). */
    private void clearBullets() {
        int n = 0;
        for (Bullet b : ebullets) { if ((n++ % 4) == 0) burst(b.x, b.y, 1, b.col, 80); }
        ebullets.clear();
    }

    // =====================================================================
    //  Effets
    // =====================================================================
    private void updateParts(float dt) {
        for (Part p : parts) { p.x += p.vx * dt; p.y += p.vy * dt; p.vx *= 0.97f; p.vy *= 0.97f; p.life -= dt; }
        parts.removeIf(p -> p.life <= 0);
    }

    private void updateFxLists(float dt) {
        for (Fx f : fxs) f.life -= dt;
        fxs.removeIf(f -> f.life <= 0);
        for (Ring r : rings) r.t += dt;
        rings.removeIf(r -> r.t >= r.dur);
    }

    private void burst(float x, float y, int n, int col, float spd) {
        for (int i = 0; i < n; i++) {
            if (parts.size() > 350) return;
            Part p = new Part();
            float a = rnd.nextFloat() * TAU, s = spd * (0.3f + rnd.nextFloat() * 0.7f);
            p.x = x; p.y = y; p.vx = cos(a) * s; p.vy = sin(a) * s;
            p.life = p.max = 0.3f + rnd.nextFloat() * 0.5f;
            p.size = 2 + rnd.nextFloat() * 3; p.col = col;
            parts.add(p);
        }
    }

    private Ring ring(float x, float y, float maxR, float dur, int col) {
        Ring r = new Ring();
        r.x = x; r.y = y; r.maxR = maxR; r.dur = dur; r.col = col;
        return r;
    }

    // =====================================================================
    //  Génération de balles ennemies
    // =====================================================================
    private void bullet(float x, float y, float a, float speed, float r, int col) {
        bulletV(x, y, cos(a) * speed, sin(a) * speed, r, col);
    }

    private void bulletV(float x, float y, float vx, float vy, float r, int col) {
        if (ebullets.size() > 1400) return;
        Bullet b = new Bullet();
        b.x = x; b.y = y; b.vx = vx; b.vy = vy; b.r = r; b.col = col;
        ebullets.add(b);
    }

    private void fanAt(float x, float y, int n, float spread, float speed, int col) {
        float a = ang(x, y, px, py);
        for (int i = 0; i < n; i++) bullet(x, y, a + (i - (n - 1) / 2f) * spread, speed, 7, col);
    }

    private void ring(Enemy b, int n, float speed, float off, int col) {
        for (int i = 0; i < n; i++) bullet(b.x, b.y, off + i * TAU / n, speed, 7, col);
    }

    /** Rideau de balles tombant du haut de l'écran, avec une brèche près du joueur. */
    private void curtain(float bs, float gapHalf, int col) {
        float gx = clamp(px + (rnd.nextFloat() - 0.5f) * 200f, 120, W - 120);
        for (float x = 25; x < W; x += 50) {
            if (Math.abs(x - gx) < gapHalf) continue;
            bulletV(x, -10, 0, 230 * bs, 8, col);
        }
    }

    // =====================================================================
    //  Utilitaires
    // =====================================================================
    private Enemy nearest(float x, float y) {
        Enemy best = null; float bd = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            if (e.dead || e.y < -10) continue;
            float d = dist2(e.x, e.y, x, y);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private Enemy strongest() {
        Enemy best = null; float bh = -1;
        for (Enemy e : enemies) {
            if (e.dead || e.y < 0) continue;
            if (e.hp > bh) { bh = e.hp; best = e; }
        }
        return best;
    }

    private Enemy randomEnemy() {
        List<Enemy> l = new ArrayList<>();
        for (Enemy e : enemies) if (!e.dead && e.y > 0) l.add(e);
        return l.isEmpty() ? null : l.get(rnd.nextInt(l.size()));
    }

    static float ang(float x1, float y1, float x2, float y2) { return (float) Math.atan2(y2 - y1, x2 - x1); }
    static float cos(float a) { return (float) Math.cos(a); }
    static float sin(float a) { return (float) Math.sin(a); }
    static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
    static float dist2(float x1, float y1, float x2, float y2) { float dx = x1 - x2, dy = y1 - y2; return dx * dx + dy * dy; }

    static float lerpAngle(float a, float b, float t) {
        float d = b - a;
        while (d > PI) d -= TAU;
        while (d < -PI) d += TAU;
        return a + d * t;
    }

    /** Distance entre le point (cx,cy) et le segment (x1,y1)-(x2,y2). */
    static float segDist(float x1, float y1, float x2, float y2, float cx, float cy) {
        float dx = x2 - x1, dy = y2 - y1;
        float l2 = dx * dx + dy * dy;
        float t = l2 == 0 ? 0 : ((cx - x1) * dx + (cy - y1) * dy) / l2;
        t = clamp(t, 0f, 1f);
        float qx = x1 + t * dx, qy = y1 + t * dy;
        return (float) Math.sqrt(dist2(cx, cy, qx, qy));
    }
}
