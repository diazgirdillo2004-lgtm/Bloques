package com.bloques.juego;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/**
 * Vista única que dibuja todo con Canvas (sin imágenes ni librerías)
 * y maneja los botones táctiles con multitáctil.
 */
public final class GameView extends View {

    private static final float DAS = 170f; // retardo antes de autorepetir
    private static final float ARR = 45f;  // velocidad de autorepetición

    private static final int B_HOLD = 0, B_HARD = 1, B_CCW = 2, B_CW = 3,
            B_LEFT = 4, B_SOFT = 5, B_RIGHT = 6, NB = 7;

    private static final int[] COLORS = {
            0xFF2EE6E6, // I
            0xFFF2D22E, // O
            0xFFB04CF0, // T
            0xFF4CD964, // S
            0xFFF0453A, // Z
            0xFF3A6BF0, // J
            0xFFF09A2E  // L
    };
    private final int[] light = new int[7];
    private final int[] dark = new int[7];

    private static final int C_BG = 0xFF10131A;
    private static final int C_BOARD = 0xFF181D28;
    private static final int C_GRID = 0xFF222937;
    private static final int C_PANEL = 0xFF1A2030;
    private static final int C_BTN = 0xFF242C3D;
    private static final int C_BTN_ON = 0xFF3B4863;
    private static final int C_TEXT = 0xFFE8ECF4;
    private static final int C_MUTED = 0xFF8A93A8;

    private final Game game = new Game();
    private final SharedPreferences prefs;
    private long record;
    private boolean recordSaved;

    private final Paint fill = new Paint();
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();

    // Layout
    private float cell, bx, by, headerH;
    private float holdX, nextX, panelW;
    private final RectF[] btn = new RectF[NB];
    private final RectF pauseRect = new RectF();
    private final RectF ovBtn1 = new RectF(), ovBtn2 = new RectF();

    // Entrada
    private final int[] pointerBtn = new int[16];
    private final boolean[] pressed = new boolean[NB];
    private int dasDir;
    private float dasTimer, arrTimer;

    private long lastFrame;

    public GameView(Context ctx, SharedPreferences prefs) {
        super(ctx);
        this.prefs = prefs;
        record = prefs.getLong("record", 0);
        for (int i = 0; i < 16; i++) pointerBtn[i] = -1;
        for (int i = 0; i < NB; i++) btn[i] = new RectF();
        for (int i = 0; i < 7; i++) {
            light[i] = blend(COLORS[i], 0xFFFFFFFF, 0.35f);
            dark[i] = blend(COLORS[i], 0xFF000000, 0.35f);
        }
        stroke.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        setKeepScreenOn(true);
    }

    private static int blend(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // ---------------- Ciclo de vida ----------------
    public void pauseGame() {
        if (game.state == Game.PLAYING) {
            game.setPaused(true);
            releaseAll();
            invalidate();
        }
    }

    /** @return true si consumió el botón Atrás. */
    public boolean handleBack() {
        if (game.state == Game.PLAYING) {
            pauseGame();
            return true;
        }
        return false;
    }

    private void resume() {
        game.setPaused(false);
        lastFrame = SystemClock.uptimeMillis();
        invalidate();
    }

    private void newGame() {
        releaseAll();
        recordSaved = false;
        game.start();
        lastFrame = SystemClock.uptimeMillis();
        invalidate();
    }

    // ---------------- Layout ----------------
    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float ctrlH = h * 0.27f;
        headerH = Math.max(h * 0.055f, 36f);
        float pad = w * 0.02f;
        float availH = h - ctrlH - headerH - pad * 2;
        cell = (float) Math.floor(Math.min(w / 19.4f, availH / Game.VIS));
        float boardW = cell * Game.W;
        bx = (w - boardW) / 2f;
        by = headerH + pad;
        panelW = cell * 4.2f;
        holdX = bx - panelW - cell * 0.35f;
        nextX = bx + boardW + cell * 0.35f;

        float hs = headerH * 0.8f;
        pauseRect.set(w - hs - pad, (headerH - hs) / 2f, w - pad, (headerH + hs) / 2f);

        // Controles: 4 columnas x 2 filas, el giro horario ocupa dos filas
        float top = h - ctrlH + pad;
        float g = w * 0.025f;
        float cw = (w - g * 5) / 4f;
        float rh = (h - top - g * 2) / 2f;
        for (int c = 0; c < 4; c++) {
            float x0 = g + c * (cw + g);
            if (c < 3) {
                btn[c == 0 ? B_HOLD : c == 1 ? B_HARD : B_CCW].set(x0, top, x0 + cw, top + rh);
                btn[c == 0 ? B_LEFT : c == 1 ? B_SOFT : B_RIGHT].set(x0, top + rh + g, x0 + cw, top + rh * 2 + g);
            } else {
                btn[B_CW].set(x0, top, x0 + cw, top + rh * 2 + g);
            }
        }

        float ow1 = w * 0.6f, oh1 = Math.max(cell * 1.8f, 44f);
        float cy = by + cell * Game.VIS * 0.62f;
        ovBtn1.set((w - ow1) / 2f, cy, (w + ow1) / 2f, cy + oh1);
        ovBtn2.set((w - ow1) / 2f, cy + oh1 * 1.35f, (w + ow1) / 2f, cy + oh1 * 2.35f);
    }

    // ---------------- Dibujo ----------------
    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(now - lastFrame, 50);
        lastFrame = now;

        if (game.state == Game.PLAYING) {
            handleDas(dt);
            game.update(dt);
        }
        if (game.state == Game.OVER && !recordSaved) {
            recordSaved = true;
            releaseAll();
            if (game.score > record) {
                record = game.score;
                prefs.edit().putLong("record", record).apply();
            }
        }

        c.drawColor(C_BG);
        drawHeader(c);
        drawBoard(c);
        drawSidePanels(c);
        drawControls(c);
        drawOverlay(c);

        if (game.state == Game.PLAYING) postInvalidateOnAnimation();
    }

    private void drawHeader(Canvas c) {
        text.setColor(C_MUTED);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(headerH * 0.42f);
        c.drawText("BLOQUES", getWidth() * 0.03f, headerH * 0.65f, text);
        text.setTextAlign(Paint.Align.CENTER);

        if (game.state == Game.PLAYING) {
            fill.setColor(C_MUTED);
            float w = pauseRect.width(), x = pauseRect.left, y = pauseRect.top;
            c.drawRect(x + w * 0.28f, y + w * 0.22f, x + w * 0.42f, y + w * 0.78f, fill);
            c.drawRect(x + w * 0.58f, y + w * 0.22f, x + w * 0.72f, y + w * 0.78f, fill);
        }
    }

    private void drawBoard(Canvas c) {
        float bw = cell * Game.W, bh = cell * Game.VIS;
        fill.setColor(C_BOARD);
        c.drawRect(bx, by, bx + bw, by + bh, fill);
        fill.setColor(C_GRID);
        for (int x = 1; x < Game.W; x++) c.drawRect(bx + x * cell, by, bx + x * cell + 1, by + bh, fill);
        for (int y = 1; y < Game.VIS; y++) c.drawRect(bx, by + y * cell, bx + bw, by + y * cell + 1, fill);

        for (int y = Game.TOP; y < Game.H; y++)
            for (int x = 0; x < Game.W; x++) {
                int v = game.board[y][x];
                if (v != 0) block(c, bx + x * cell, by + (y - Game.TOP) * cell, cell, v - 1);
            }

        if (game.state == Game.PLAYING || game.state == Game.PAUSED) {
            int[][] cells = Game.CELLS[game.cur][game.rot];
            int gy = game.ghostY();
            stroke.setColor(COLORS[game.cur]);
            stroke.setStrokeWidth(Math.max(2f, cell * 0.08f));
            for (int i = 0; i < 4; i++) {
                int x = game.px + cells[i][0], y = gy + cells[i][1];
                if (y < Game.TOP) continue;
                float sx = bx + x * cell, sy = by + (y - Game.TOP) * cell;
                float in = cell * 0.12f;
                c.drawRect(sx + in, sy + in, sx + cell - in, sy + cell - in, stroke);
            }
            for (int i = 0; i < 4; i++) {
                int x = game.px + cells[i][0], y = game.py + cells[i][1];
                if (y < Game.TOP) continue;
                block(c, bx + x * cell, by + (y - Game.TOP) * cell, cell, game.cur);
            }
        }

        stroke.setColor(0xFF39445C);
        stroke.setStrokeWidth(2f);
        c.drawRect(bx - 1, by - 1, bx + bw + 1, by + bh + 1, stroke);

        if (game.state == Game.PLAYING && game.popupTime > 0) {
            int a = (int) (255 * Math.min(1f, game.popupTime / 400f));
            text.setTextSize(cell * 0.85f);
            float ty = by + bh * 0.3f;
            text.setColor((a << 24) | 0xFFFFFF);
            if (game.popup.length() > 0) {
                fitText(game.popup, bw * 0.95f, cell * 0.85f);
                c.drawText(game.popup, bx + bw / 2f, ty, text);
            }
            if (game.popup2.length() > 0) {
                text.setTextSize(cell * 0.65f);
                text.setColor((a << 24) | 0xF2D22E);
                c.drawText(game.popup2, bx + bw / 2f, ty + cell * 1.0f, text);
            }
        }
    }

    private void block(Canvas c, float x, float y, float s, int t) {
        fill.setColor(dark[t]);
        c.drawRect(x + 1, y + 1, x + s - 1, y + s - 1, fill);
        float e = Math.max(2f, s * 0.12f);
        fill.setColor(COLORS[t]);
        c.drawRect(x + e, y + e, x + s - e, y + s - e, fill);
        fill.setColor(light[t]);
        c.drawRect(x + 1, y + 1, x + s - 1, y + e, fill);
    }

    private void miniPiece(Canvas c, int t, float cx, float cy, float s, boolean dim) {
        int[][] cells = Game.CELLS[t][0];
        int minX = 9, maxX = -9, minY = 9, maxY = -9;
        for (int i = 0; i < 4; i++) {
            minX = Math.min(minX, cells[i][0]); maxX = Math.max(maxX, cells[i][0]);
            minY = Math.min(minY, cells[i][1]); maxY = Math.max(maxY, cells[i][1]);
        }
        float ox = cx - (maxX - minX + 1) * s / 2f, oy = cy - (maxY - minY + 1) * s / 2f;
        for (int i = 0; i < 4; i++) {
            float x = ox + (cells[i][0] - minX) * s, y = oy + (cells[i][1] - minY) * s;
            if (dim) {
                fill.setColor(0xFF4A5268);
                c.drawRect(x + 1, y + 1, x + s - 1, y + s - 1, fill);
            } else {
                block(c, x, y, s, t);
            }
        }
    }

    private void drawSidePanels(Canvas c) {
        float lbl = cell * 0.5f;
        float box = cell * 3.2f;
        float s = cell * 0.62f;

        // HOLD
        panelLabel(c, "RESERVA", holdX, by, lbl);
        float hy = by + lbl * 1.4f;
        fill.setColor(C_PANEL);
        c.drawRect(holdX, hy, holdX + panelW, hy + box, fill);
        if (game.hold >= 0) miniPiece(c, game.hold, holdX + panelW / 2f, hy + box / 2f, s, game.holdUsed);

        // Estadísticas
        float sy = hy + box + cell * 1.0f;
        sy = stat(c, "PUNTOS", String.valueOf(game.score), sy);
        sy = stat(c, "NIVEL", String.valueOf(game.level), sy);
        sy = stat(c, "LÍNEAS", String.valueOf(game.lines), sy);
        stat(c, "RÉCORD", String.valueOf(Math.max(record, game.score)), sy);

        // NEXT
        panelLabel(c, "SIGUE", nextX, by, lbl);
        float ny = by + lbl * 1.4f;
        int n = 4;
        float slot = cell * 2.4f;
        fill.setColor(C_PANEL);
        c.drawRect(nextX, ny, nextX + panelW, ny + box * 0.9f + slot * (n - 1) + cell * 0.3f, fill);
        if (game.state != Game.READY) {
            miniPiece(c, game.peekNext(0), nextX + panelW / 2f, ny + box * 0.45f, s, false);
            for (int i = 1; i < n; i++)
                miniPiece(c, game.peekNext(i), nextX + panelW / 2f,
                        ny + box * 0.9f + slot * (i - 0.5f), s * 0.8f, false);
        }
    }

    private void panelLabel(Canvas c, String s, float x, float y, float size) {
        text.setColor(C_MUTED);
        text.setTextSize(size);
        c.drawText(s, x + panelW / 2f, y + size, text);
    }

    private float stat(Canvas c, String label, String value, float y) {
        text.setColor(C_MUTED);
        text.setTextSize(cell * 0.45f);
        c.drawText(label, holdX + panelW / 2f, y, text);
        text.setColor(C_TEXT);
        fitText(value, panelW, cell * 0.75f);
        c.drawText(value, holdX + panelW / 2f, y + cell * 0.85f, text);
        return y + cell * 2.0f;
    }

    private void fitText(String s, float maxW, float size) {
        text.setTextSize(size);
        float w = text.measureText(s);
        if (w > maxW) text.setTextSize(size * maxW / w);
    }

    private void drawControls(Canvas c) {
        for (int i = 0; i < NB; i++) {
            RectF r = btn[i];
            fill.setColor(pressed[i] ? C_BTN_ON : C_BTN);
            c.drawRect(r, fill);
            float cx = r.centerX(), cy = r.centerY();
            float u = Math.min(r.width(), btn[B_LEFT].height()) * 0.22f;
            fill.setColor(C_TEXT);
            stroke.setColor(C_TEXT);
            switch (i) {
                case B_LEFT: tri(c, cx + u * 0.6f, cy - u, cx + u * 0.6f, cy + u, cx - u * 0.8f, cy); break;
                case B_RIGHT: tri(c, cx - u * 0.6f, cy - u, cx - u * 0.6f, cy + u, cx + u * 0.8f, cy); break;
                case B_SOFT: tri(c, cx - u, cy - u * 0.6f, cx + u, cy - u * 0.6f, cx, cy + u * 0.8f); break;
                case B_HARD:
                    tri(c, cx - u, cy - u * 0.9f, cx + u, cy - u * 0.9f, cx, cy + u * 0.4f);
                    c.drawRect(cx - u, cy + u * 0.6f, cx + u, cy + u * 0.9f, fill);
                    break;
                case B_CW: rotIcon(c, cx, cy, u * 1.1f, true); break;
                case B_CCW: rotIcon(c, cx, cy, u, false); break;
                case B_HOLD:
                    fitText("RESERVA", r.width() * 0.8f, r.height() * 0.28f);
                    text.setColor(C_TEXT);
                    c.drawText("RESERVA", cx, cy + text.getTextSize() * 0.35f, text);
                    break;
            }
        }
    }

    private void tri(Canvas c, float x1, float y1, float x2, float y2, float x3, float y3) {
        path.reset();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.close();
        fill.setAntiAlias(true);
        c.drawPath(path, fill);
        fill.setAntiAlias(false);
    }

    private void rotIcon(Canvas c, float cx, float cy, float r, boolean cw) {
        stroke.setStrokeWidth(Math.max(3f, r * 0.28f));
        rf.set(cx - r, cy - r, cx + r, cy + r);
        float start = cw ? 120f : 60f, sweep = cw ? 270f : -270f;
        c.drawArc(rf, start, sweep, false, stroke);
        double th = Math.toRadians(start + sweep);
        float ex = cx + (float) Math.cos(th) * r, ey = cy + (float) Math.sin(th) * r;
        float tx = (float) (cw ? -Math.sin(th) : Math.sin(th));
        float ty = (float) (cw ? Math.cos(th) : -Math.cos(th));
        float nx = (float) Math.cos(th), ny = (float) Math.sin(th);
        float h = r * 0.75f;
        tri(c, ex + tx * h * 0.7f, ey + ty * h * 0.7f,
                ex - tx * h * 0.2f + nx * h * 0.6f, ey - ty * h * 0.2f + ny * h * 0.6f,
                ex - tx * h * 0.2f - nx * h * 0.6f, ey - ty * h * 0.2f - ny * h * 0.6f);
    }

    private void drawOverlay(Canvas c) {
        if (game.state == Game.PLAYING) return;
        fill.setColor(0xCC0B0E14);
        c.drawRect(0, 0, getWidth(), getHeight(), fill);
        float cx = getWidth() / 2f;
        float y = by + cell * Game.VIS * 0.3f;
        text.setColor(C_TEXT);
        String title, b1, b2 = null;
        if (game.state == Game.READY) {
            title = "BLOQUES";
            b1 = "JUGAR";
        } else if (game.state == Game.PAUSED) {
            title = "PAUSA";
            b1 = "CONTINUAR";
            b2 = "REINICIAR";
        } else {
            title = "FIN DEL JUEGO";
            b1 = "JUGAR DE NUEVO";
        }
        fitText(title, getWidth() * 0.85f, cell * 1.6f);
        c.drawText(title, cx, y, text);

        text.setColor(C_MUTED);
        text.setTextSize(cell * 0.7f);
        if (game.state == Game.OVER) {
            c.drawText("Puntos: " + game.score, cx, y + cell * 1.6f, text);
            c.drawText((game.score >= record && game.score > 0 ? "¡Nuevo récord!" : "Récord: " + record),
                    cx, y + cell * 2.6f, text);
        } else if (game.state == Game.READY) {
            c.drawText("Récord: " + record, cx, y + cell * 1.6f, text);
        }

        overlayButton(c, ovBtn1, b1, 0xFF3A6BF0);
        if (b2 != null) overlayButton(c, ovBtn2, b2, C_BTN_ON);
    }

    private void overlayButton(Canvas c, RectF r, String s, int col) {
        fill.setColor(col);
        c.drawRect(r, fill);
        text.setColor(C_TEXT);
        fitText(s, r.width() * 0.85f, r.height() * 0.42f);
        c.drawText(s, r.centerX(), r.centerY() + text.getTextSize() * 0.35f, text);
    }

    // ---------------- Entrada ----------------
    private void handleDas(float dt) {
        if (dasDir == 0) return;
        dasTimer += dt;
        if (dasTimer >= DAS) {
            arrTimer += dt;
            while (arrTimer >= ARR) {
                arrTimer -= ARR;
                if (!game.move(dasDir)) {
                    arrTimer = 0;
                    break;
                }
            }
        }
    }

    private int hit(float x, float y) {
        for (int i = 0; i < NB; i++) if (btn[i].contains(x, y)) return i;
        return -1;
    }

    private static boolean isSlideBtn(int b) {
        return b == B_LEFT || b == B_SOFT || b == B_RIGHT;
    }

    private void press(int b) {
        if (b < 0) return;
        pressed[b] = true;
        switch (b) {
            case B_HOLD: game.doHold(); break;
            case B_HARD: game.hardDrop(); break;
            case B_CCW: game.rotate(-1); break;
            case B_CW: game.rotate(1); break;
            case B_SOFT: game.setSoftDrop(true); break;
            case B_LEFT:
            case B_RIGHT:
                dasDir = b == B_LEFT ? -1 : 1;
                dasTimer = 0;
                arrTimer = 0;
                game.move(dasDir);
                break;
        }
    }

    private void release(int b) {
        if (b < 0) return;
        pressed[b] = false;
        if (b == B_SOFT) game.setSoftDrop(false);
        if (b == B_LEFT || b == B_RIGHT) {
            int d = b == B_LEFT ? -1 : 1;
            if (dasDir == d) {
                int other = b == B_LEFT ? B_RIGHT : B_LEFT;
                if (pressed[other]) {
                    dasDir = -d;
                    dasTimer = 0;
                    arrTimer = 0;
                } else {
                    dasDir = 0;
                }
            }
        }
    }

    private void releaseAll() {
        for (int i = 0; i < 16; i++) pointerBtn[i] = -1;
        for (int i = 0; i < NB; i++) pressed[i] = false;
        dasDir = 0;
        game.setSoftDrop(false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();

        if (game.state != Game.PLAYING) {
            if (action == MotionEvent.ACTION_DOWN) {
                float x = e.getX(), y = e.getY();
                if (ovBtn1.contains(x, y)) {
                    if (game.state == Game.PAUSED) resume();
                    else newGame();
                } else if (game.state == Game.PAUSED && ovBtn2.contains(x, y)) {
                    newGame();
                }
            }
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex();
                int id = e.getPointerId(i);
                float x = e.getX(i), y = e.getY(i);
                if (pauseRect.contains(x, y) || y < headerH) {
                    pauseGame();
                    return true;
                }
                if (id < 16) {
                    int b = hit(x, y);
                    pointerBtn[id] = b;
                    press(b);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int id = e.getPointerId(i);
                    if (id >= 16) continue;
                    int old = pointerBtn[id];
                    int nb = hit(e.getX(i), e.getY(i));
                    if (nb != old && isSlideBtn(old) && isSlideBtn(nb)) {
                        release(old);
                        pointerBtn[id] = nb;
                        press(nb);
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int id = e.getPointerId(e.getActionIndex());
                if (id < 16) {
                    release(pointerBtn[id]);
                    pointerBtn[id] = -1;
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                break;
        }
        return true;
    }
}
