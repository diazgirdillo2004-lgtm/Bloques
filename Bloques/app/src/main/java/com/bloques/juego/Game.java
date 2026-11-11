package com.bloques.juego;

import java.util.Random;

/**
 * Lógica pura del juego (sin dependencias de Android).
 * Sigue las reglas de la guía moderna:
 *  - Tablero 10x20 visible + 20 filas ocultas encima
 *  - Rotación SRS con wall kicks (tablas oficiales para JLSTZ e I)
 *  - Generador "bolsa de 7"
 *  - Hold (1 vez por pieza), cola de siguientes, pieza fantasma
 *  - Lock delay de 0,5 s con reinicio por movimiento (máx. 15)
 *  - T-Spin y T-Spin Mini (regla de 3 esquinas), Back-to-Back x1.5, combos,
 *    limpieza total (perfect clear)
 *  - Caída suave 1 pto/celda, caída fuerte 2 ptos/celda
 *  - Nivel sube cada 10 líneas, gravedad según la fórmula de la guía
 */
public final class Game {

    public static final int W = 10;
    public static final int H = 40;
    public static final int VIS = 20;
    public static final int TOP = H - VIS; // primera fila visible

    public static final int I = 0, O = 1, T = 2, S = 3, Z = 4, J = 5, L = 6;

    public static final int READY = 0, PLAYING = 1, PAUSED = 2, OVER = 3;

    private static final float LOCK_DELAY = 500f;
    private static final int MAX_RESETS = 15;

    private static final String[][] SHAPES = {
            {"....", "####", "....", "...."}, // I
            {"##", "##"},                       // O
            {".#.", "###", "..."},              // T
            {".##", "##.", "..."},              // S
            {"##.", ".##", "..."},              // Z
            {"#..", "###", "..."},              // J
            {"..#", "###", "..."}               // L
    };
    static final int[] SIZE = {4, 2, 3, 3, 3, 3, 3};
    /** CELLS[tipo][rotación][celda] = {x, y} dentro de su caja (y hacia abajo). */
    static final int[][][][] CELLS = new int[7][4][4][2];

    static {
        for (int t = 0; t < 7; t++) {
            int n = SIZE[t], k = 0;
            for (int y = 0; y < n; y++)
                for (int x = 0; x < n; x++)
                    if (SHAPES[t][y].charAt(x) == '#') {
                        CELLS[t][0][k][0] = x;
                        CELLS[t][0][k][1] = y;
                        k++;
                    }
            for (int r = 1; r < 4; r++)
                for (int c = 0; c < 4; c++) {
                    int x = CELLS[t][r - 1][c][0], y = CELLS[t][r - 1][c][1];
                    CELLS[t][r][c][0] = n - 1 - y; // giro horario
                    CELLS[t][r][c][1] = x;
                }
        }
    }

    // Tablas SRS (y hacia ARRIBA, como en la guía). Orden: 0R, R0, R2, 2R, 2L, L2, L0, 0L
    private static final int[][][] KICK_JLSTZ = {
            {{0, 0}, {-1, 0}, {-1, 1}, {0, -2}, {-1, -2}},
            {{0, 0}, {1, 0}, {1, -1}, {0, 2}, {1, 2}},
            {{0, 0}, {1, 0}, {1, -1}, {0, 2}, {1, 2}},
            {{0, 0}, {-1, 0}, {-1, 1}, {0, -2}, {-1, -2}},
            {{0, 0}, {1, 0}, {1, 1}, {0, -2}, {1, -2}},
            {{0, 0}, {-1, 0}, {-1, -1}, {0, 2}, {-1, 2}},
            {{0, 0}, {-1, 0}, {-1, -1}, {0, 2}, {-1, 2}},
            {{0, 0}, {1, 0}, {1, 1}, {0, -2}, {1, -2}}
    };
    private static final int[][][] KICK_I = {
            {{0, 0}, {-2, 0}, {1, 0}, {-2, -1}, {1, 2}},
            {{0, 0}, {2, 0}, {-1, 0}, {2, 1}, {-1, -2}},
            {{0, 0}, {-1, 0}, {2, 0}, {-1, 2}, {2, -1}},
            {{0, 0}, {1, 0}, {-2, 0}, {1, -2}, {-2, 1}},
            {{0, 0}, {2, 0}, {-1, 0}, {2, 1}, {-1, -2}},
            {{0, 0}, {-2, 0}, {1, 0}, {-2, -1}, {1, 2}},
            {{0, 0}, {1, 0}, {-2, 0}, {1, -2}, {-2, 1}},
            {{0, 0}, {-1, 0}, {2, 0}, {-1, 2}, {2, -1}}
    };

    private static int kickIndex(int from, int to) {
        switch (from * 4 + to) {
            case 1: return 0;   // 0->R
            case 4: return 1;   // R->0
            case 6: return 2;   // R->2
            case 9: return 3;   // 2->R
            case 11: return 4;  // 2->L
            case 14: return 5;  // L->2
            case 12: return 6;  // L->0
            default: return 7;  // 0->L
        }
    }

    // ---------------- Estado ----------------
    public final int[][] board = new int[H][W]; // 0 vacío, 1..7 = tipo+1
    public int cur, rot, px, py;
    public int hold = -1;
    public boolean holdUsed;
    private final int[] queue = new int[14];
    private int qLen;
    private final Random rng = new Random();

    public int state = READY;
    public long score;
    public int lines, level = 1;
    private int combo = -1;
    private boolean b2b;

    private float gravAcc, lockTimer;
    private int resets, lowestY;
    private boolean softDrop, lastRotate;
    private int lastKick;

    // Texto emergente ("TETRIS", "T-SPIN DOBLE"...)
    public String popup = "", popup2 = "";
    public float popupTime;

    // ---------------- Ciclo de juego ----------------
    public void start() {
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) board[y][x] = 0;
        hold = -1;
        holdUsed = false;
        qLen = 0;
        score = 0;
        lines = 0;
        level = 1;
        combo = -1;
        b2b = false;
        softDrop = false;
        popupTime = 0;
        fillQueue();
        state = PLAYING;
        spawn(popQueue());
    }

    private void fillQueue() {
        while (qLen <= 7) {
            int[] bag = {0, 1, 2, 3, 4, 5, 6};
            for (int i = 6; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int tmp = bag[i]; bag[i] = bag[j]; bag[j] = tmp;
            }
            for (int i = 0; i < 7; i++) queue[qLen++] = bag[i];
        }
    }

    private int popQueue() {
        int t = queue[0];
        System.arraycopy(queue, 1, queue, 0, qLen - 1);
        qLen--;
        fillQueue();
        return t;
    }

    public int peekNext(int i) {
        return queue[i];
    }

    private void spawn(int type) {
        cur = type;
        rot = 0;
        px = type == O ? 4 : 3;
        py = TOP - 2;
        gravAcc = 0;
        lockTimer = 0;
        resets = 0;
        lastRotate = false;
        if (!fits(cur, rot, px, py)) { // block out
            state = OVER;
            return;
        }
        if (fits(cur, rot, px, py + 1)) py++; // la pieza aparece y baja una fila de inmediato
        lowestY = py;
    }

    public void setPaused(boolean p) {
        if (p && state == PLAYING) state = PAUSED;
        else if (!p && state == PAUSED) state = PLAYING;
    }

    public void update(float dt) {
        if (state != PLAYING) return;
        if (popupTime > 0) popupTime -= dt;

        float g = gravityMs();
        if (softDrop) g = Math.max(1f, Math.min(g, g / 20f));
        gravAcc += dt;
        while (gravAcc >= g) {
            gravAcc -= g;
            if (fits(cur, rot, px, py + 1)) {
                py++;
                lastRotate = false;
                if (softDrop) score += 1;
                onMovedDown();
            } else {
                gravAcc = 0;
                break;
            }
        }

        if (!fits(cur, rot, px, py + 1)) {
            lockTimer += dt;
            if (lockTimer >= LOCK_DELAY) lockPiece();
        } else {
            lockTimer = 0;
        }
    }

    /** Milisegundos por fila según la fórmula de la guía. */
    private float gravityMs() {
        int lv = Math.min(level, 20);
        double sec = Math.pow(0.8 - (lv - 1) * 0.007, lv - 1);
        return (float) (sec * 1000.0);
    }

    private void onMovedDown() {
        if (py > lowestY) {
            lowestY = py;
            resets = 0;
            lockTimer = 0;
        }
    }

    private void afterManip() {
        if (!fits(cur, rot, px, py + 1)) {
            if (resets < MAX_RESETS) {
                resets++;
                lockTimer = 0;
            } else {
                lockPiece();
            }
        }
    }

    // ---------------- Acciones del jugador ----------------
    public boolean move(int dx) {
        if (state != PLAYING) return false;
        if (fits(cur, rot, px + dx, py)) {
            px += dx;
            lastRotate = false;
            afterManip();
            return true;
        }
        return false;
    }

    public void setSoftDrop(boolean on) {
        softDrop = on;
    }

    public boolean rotate(int dir) {
        if (state != PLAYING || cur == O) return false;
        int to = (rot + dir + 4) & 3;
        int[][] kicks = (cur == I ? KICK_I : KICK_JLSTZ)[kickIndex(rot, to)];
        for (int k = 0; k < 5; k++) {
            int nx = px + kicks[k][0];
            int ny = py - kicks[k][1];
            if (fits(cur, to, nx, ny)) {
                px = nx;
                py = ny;
                rot = to;
                lastRotate = true;
                lastKick = k;
                onMovedDown();
                afterManip();
                return true;
            }
        }
        return false;
    }

    public void hardDrop() {
        if (state != PLAYING) return;
        int d = 0;
        while (fits(cur, rot, px, py + 1)) {
            py++;
            d++;
        }
        if (d > 0) lastRotate = false;
        score += 2L * d;
        lockPiece();
    }

    public void doHold() {
        if (state != PLAYING || holdUsed) return;
        int t = cur;
        if (hold < 0) {
            hold = t;
            spawn(popQueue());
        } else {
            int h = hold;
            hold = t;
            spawn(h);
        }
        holdUsed = true;
    }

    public int ghostY() {
        int y = py;
        while (fits(cur, rot, px, y + 1)) y++;
        return y;
    }

    // ---------------- Fijado y puntuación ----------------
    private void lockPiece() {
        int tspin = 0; // 0 nada, 1 mini, 2 completo
        if (cur == T && lastRotate) tspin = detectTSpin();

        boolean allHidden = true;
        int[][] cells = CELLS[cur][rot];
        for (int i = 0; i < 4; i++) {
            int x = px + cells[i][0], y = py + cells[i][1];
            board[y][x] = cur + 1;
            if (y >= TOP) allHidden = false;
        }

        int cleared = clearLines();
        scoreClear(cleared, tspin);

        if (allHidden) { // lock out
            state = OVER;
            return;
        }
        holdUsed = false;
        spawn(popQueue());
    }

    private int detectTSpin() {
        int cx = px + 1, cy = py + 1;
        boolean tl = occ(cx - 1, cy - 1), tr = occ(cx + 1, cy - 1);
        boolean bl = occ(cx - 1, cy + 1), br = occ(cx + 1, cy + 1);
        int n = (tl ? 1 : 0) + (tr ? 1 : 0) + (bl ? 1 : 0) + (br ? 1 : 0);
        if (n < 3) return 0;
        boolean front;
        switch (rot) {
            case 0: front = tl && tr; break;
            case 1: front = tr && br; break;
            case 2: front = bl && br; break;
            default: front = tl && bl; break;
        }
        return (front || lastKick == 4) ? 2 : 1;
    }

    private boolean occ(int x, int y) {
        if (x < 0 || x >= W || y < 0 || y >= H) return true;
        return board[y][x] != 0;
    }

    private int clearLines() {
        int n = 0;
        for (int y = H - 1; y >= 0; y--) {
            boolean full = true;
            for (int x = 0; x < W; x++)
                if (board[y][x] == 0) { full = false; break; }
            if (full) {
                n++;
                for (int yy = y; yy > 0; yy--) System.arraycopy(board[yy - 1], 0, board[yy], 0, W);
                for (int x = 0; x < W; x++) board[0][x] = 0;
                y++; // revisar la misma fila otra vez
            }
        }
        return n;
    }

    private void scoreClear(int n, int tspin) {
        int base = 0;
        String name = "";
        if (tspin == 2) {
            int[] t = {400, 800, 1200, 1600};
            base = t[Math.min(n, 3)];
            String[] s = {"T-SPIN", "T-SPIN SIMPLE", "T-SPIN DOBLE", "T-SPIN TRIPLE"};
            name = s[Math.min(n, 3)];
        } else if (tspin == 1) {
            int[] t = {100, 200, 400};
            base = t[Math.min(n, 2)];
            String[] s = {"T-SPIN MINI", "T-SPIN MINI SIMPLE", "T-SPIN MINI DOBLE"};
            name = s[Math.min(n, 2)];
        } else if (n > 0) {
            int[] t = {0, 100, 300, 500, 800};
            base = t[n];
            String[] s = {"", "", "DOBLE", "TRIPLE", "TETRIS"};
            name = s[n];
        }

        boolean difficult = n > 0 && (n == 4 || tspin > 0);
        boolean b2bBonus = false;
        if (n > 0) {
            if (difficult) {
                if (b2b) b2bBonus = true;
                b2b = true;
            } else {
                b2b = false;
            }
        }
        long pts = (long) base * level;
        if (b2bBonus) pts = pts * 3 / 2;

        String extra = "";
        if (n > 0) {
            combo++;
            if (combo > 0) {
                pts += 50L * combo * level;
                extra = "COMBO " + combo;
            }
        } else {
            combo = -1;
        }

        if (n > 0 && isBoardEmpty()) {
            int[] pc = {0, 800, 1200, 1800, 2000};
            int b = (n == 4 && b2bBonus) ? 3200 : pc[n];
            pts += (long) b * level;
            extra = "¡LIMPIEZA TOTAL!";
        }

        score += pts;
        if (n > 0) {
            lines += n;
            level = 1 + lines / 10;
        }

        if (name.length() > 0 || extra.length() > 0) {
            popup = (b2bBonus ? "B2B " : "") + name;
            popup2 = extra;
            popupTime = 1200f;
        }
    }

    private boolean isBoardEmpty() {
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                if (board[y][x] != 0) return false;
        return true;
    }

    public boolean fits(int type, int r, int x0, int y0) {
        int[][] cells = CELLS[type][r];
        for (int i = 0; i < 4; i++) {
            int x = x0 + cells[i][0], y = y0 + cells[i][1];
            if (x < 0 || x >= W || y < 0 || y >= H) return false;
            if (board[y][x] != 0) return false;
        }
        return true;
    }
}
