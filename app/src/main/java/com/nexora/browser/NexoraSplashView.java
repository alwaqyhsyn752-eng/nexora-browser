package com.nexora.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class NexoraSplashView extends View {

    public interface OnAnimationEnd { void onFinished(); }
    private OnAnimationEnd callback;
    private boolean finished = false;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random(42);
    private long startTime = -1, lastFrame = -1;

    private static class Particle { float x, y, vx, vy, sz, alpha; int color; }
    private static class Star { float x, y, alpha, twinkle; }
    private static class Wave { float cx, cy, r, maxR, alpha; int color; }
    private static class Lightning { float x1, y1, x2, y2, life, maxLife; }

    private final List<Particle> particles = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();
    private final List<Wave> waves = new ArrayList<>();
    private final List<Lightning> lightnings = new ArrayList<>();

    private float centerX, centerY, earthRadius;
    private float earthRotation, cameraScale = 1f, flashAlpha, earthAlpha, gridAlpha,
            starsAlpha, logoAlpha, logoScale, titleAlpha, browserAlpha, taglineAlpha;
    private int titleChars, browserChars;

    private final String TITLE = "NEXORA";
    private final String BROWSER = "BROWSER";
    private final String TAGLINE = "EXPLORE BEYOND LIMITS";

    public NexoraSplashView(Context c) { super(c); }
    public NexoraSplashView(Context c, AttributeSet a) { super(c, a); }
    public NexoraSplashView(Context c, AttributeSet a, int s) { super(c, a, s); }
    public void setOnAnimationEnd(OnAnimationEnd cb) { this.callback = cb; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w <= 0 || h <= 0) return;
        centerX = w / 2f;
        centerY = h * 0.40f;
        earthRadius = Math.min(w, h) * 0.20f;
        stars.clear();
        for (int i = 0; i < 120; i++) {
            Star s = new Star();
            s.x = rnd.nextFloat() * w;
            s.y = rnd.nextFloat() * h;
            s.alpha = 0.3f + rnd.nextFloat() * 0.7f;
            s.twinkle = rnd.nextFloat() * 6.28f;
            stars.add(s);
        }
    }

    private float getTime() {
        return startTime < 0 ? 0 : (System.currentTimeMillis() - startTime) / 1000f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.currentTimeMillis();
        if (startTime < 0) { startTime = now; lastFrame = now; }
        float dt = (now - lastFrame) / 1000f;
        if (dt > 0.1f) dt = 0.1f;
        lastFrame = now;
        float time = getTime();
        updateState(dt, time);

        canvas.drawColor(Color.BLACK);
        drawStars(canvas);
        if (time < 3.7f) drawEarth(canvas);
        drawParticles(canvas, dt);
        drawWaves(canvas, dt);
        drawLightnings(canvas, dt, time);
        if (time >= 3.0f && time < 3.8f) drawFlash(canvas);
        if (time >= 3.5f) { drawLogo(canvas); drawText(canvas); }

        if (time < 6.0f) postInvalidateOnAnimation();
        else if (!finished) {
            finished = true;
            if (callback != null) callback.onFinished();
        }
    }

    private void updateState(float dt, float time) {
        earthRotation += dt * 0.8f;
        earthAlpha = clamp((time - 0.3f) / 0.8f, 0, 1);
        gridAlpha = clamp((time - 0.8f) / 0.7f, 0, 1);
        starsAlpha = clamp(time / 0.5f, 0, 1);
        if (time > 1.5f && time < 3.5f) cameraScale = 1f + ((time - 1.5f) / 2f) * 0.8f;
        else if (time >= 3.5f) cameraScale = 1.8f;
        if (time > 3.0f && time < 3.8f) flashAlpha = Math.max(0f, 1f - Math.abs(time - 3.4f) / 0.4f);
        else flashAlpha = 0f;
        if (time > 3.5f) {
            logoAlpha = clamp((time - 3.5f) / 0.5f, 0, 1);
            logoScale = 0.6f + clamp((time - 3.5f) / 0.5f, 0, 1) * 0.4f;
        }
        if (time > 4.0f) {
            titleAlpha = clamp((time - 4.0f) / 0.3f, 0, 1);
            titleChars = Math.min((int) ((time - 4.0f) * 8), TITLE.length());
        }
        if (time > 4.7f) {
            browserAlpha = clamp((time - 4.7f) / 0.3f, 0, 1);
            browserChars = Math.min((int) ((time - 4.7f) * 10), BROWSER.length());
        }
        if (time > 5.2f) taglineAlpha = clamp((time - 5.2f) / 0.5f, 0, 1);
    }

    private void drawStars(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        for (Star s : stars) {
            s.twinkle += 0.03f;
            p.setColor(Color.WHITE);
            p.setAlpha((int) (s.alpha * (0.6f + 0.4f * (float) Math.sin(s.twinkle)) * starsAlpha * 255));
            c.drawCircle(s.x, s.y, 1.5f, p);
        }
    }

    private void drawEarth(Canvas c) {
        if (earthAlpha <= 0 || earthRadius <= 0) return;
        c.save();
        c.translate(centerX, centerY);
        c.scale(cameraScale, cameraScale);
        float r = earthRadius;

        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(0, 0, r * 1.8f,
                new int[]{0x882AE0FF, 0x448A2BE2, 0x00000000},
                new float[]{0.4f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        p.setAlpha((int) (earthAlpha * 255));
        c.drawCircle(0, 0, r * 1.8f, p);
        p.setShader(null);

        p.setShader(new RadialGradient(-r * 0.3f, -r * 0.3f, r * 1.5f,
                new int[]{0xFF1E90FF, 0xFF8A2BE2, 0xFF050014},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(0, 0, r, p);
        p.setShader(null);

        if (gridAlpha > 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.2f);
            p.setColor(0xFF00E5FF);
            p.setAlpha((int) (gridAlpha * earthAlpha * 200));
            for (int i = -3; i <= 3; i++) {
                float angle = i * 22f + (earthRotation * 60f) % 360f;
                float rx = r * Math.abs((float) Math.cos(Math.toRadians(angle))) + 1;
                c.drawOval(-rx, -r, rx, r, p);
            }
            p.setColor(0xFF8A2BE2);
            p.setAlpha((int) (gridAlpha * earthAlpha * 180));
            for (int i = -2; i <= 2; i++) {
                float y = (i / 2.5f) * r;
                float rx = (float) Math.sqrt(Math.max(0, r * r - y * y));
                c.drawOval(-rx, y - r * 0.05f, rx, y + r * 0.05f, p);
            }
        }

        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(-r * 0.4f, -r * 0.5f, r * 0.8f,
                new int[]{0x88FFFFFF, 0x00000000},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        p.setAlpha((int) (earthAlpha * 255));
        c.drawCircle(0, 0, r * 0.9f, p);
        p.setShader(null);
        c.restore();
    }

    private void drawParticles(Canvas c, float dt) {
        if (earthAlpha > 0 && particles.size() < 80 && rnd.nextFloat() < dt * 30) {
            Particle pt = new Particle();
            float ang = rnd.nextFloat() * 6.28f;
            float dist = earthRadius * (1.2f + rnd.nextFloat() * 0.8f);
            pt.x = centerX + (float) Math.cos(ang) * dist;
            pt.y = centerY + (float) Math.sin(ang) * dist;
            pt.vx = (rnd.nextFloat() - 0.5f) * 20f;
            pt.vy = (rnd.nextFloat() - 0.5f) * 20f;
            pt.sz = 1f + rnd.nextFloat() * 2.5f;
            pt.alpha = 0.6f + rnd.nextFloat() * 0.4f;
            pt.color = rnd.nextBoolean() ? 0xFF00E5FF : 0xFF8A2BE2;
            particles.add(pt);
        }
        p.setStyle(Paint.Style.FILL);
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle pt = it.next();
            pt.x += pt.vx * dt; pt.y += pt.vy * dt;
            pt.vx *= 0.98f; pt.vy *= 0.98f;
            pt.alpha -= dt * 0.5f;
            if (pt.alpha <= 0) { it.remove(); continue; }
            p.setColor(pt.color);
            p.setAlpha((int) (pt.alpha * 255));
            c.drawCircle(pt.x, pt.y, pt.sz, p);
            p.setAlpha((int) (pt.alpha * 60));
            c.drawCircle(pt.x, pt.y, pt.sz * 2.5f, p);
        }
    }

    private void drawWaves(Canvas c, float dt) {
        if (earthAlpha > 0.5f && rnd.nextFloat() < dt * 2) {
            Wave w = new Wave();
            w.cx = centerX; w.cy = centerY;
            w.r = earthRadius * 0.5f;
            w.maxR = earthRadius * 3f;
            w.alpha = 0.8f;
            w.color = rnd.nextBoolean() ? 0xFF00E5FF : 0xFF8A2BE2;
            waves.add(w);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        Iterator<Wave> it = waves.iterator();
        while (it.hasNext()) {
            Wave w = it.next();
            w.r += (w.maxR - w.r) * dt * 1.2f;
            w.alpha -= dt * 0.4f;
            if (w.alpha <= 0 || w.r > w.maxR) { it.remove(); continue; }
            p.setColor(w.color);
            p.setAlpha((int) (w.alpha * earthAlpha * 255));
            c.drawCircle(w.cx, w.cy, w.r, p);
            p.setAlpha((int) (w.alpha * earthAlpha * 60));
            c.drawCircle(w.cx, w.cy, w.r * 1.05f, p);
        }
    }

    private void drawLightnings(Canvas c, float dt, float time) {
        if (time > 1.5f && time < 3.5f && rnd.nextFloat() < dt * 3) {
            Lightning l = new Lightning();
            float ang = rnd.nextFloat() * 6.28f;
            float dist = earthRadius * cameraScale * (0.8f + rnd.nextFloat() * 0.6f);
            l.x1 = centerX + (float) Math.cos(ang) * dist;
            l.y1 = centerY + (float) Math.sin(ang) * dist;
            float ang2 = ang + (rnd.nextFloat() - 0.5f) * 1.5f;
            float dist2 = earthRadius * cameraScale * (1.5f + rnd.nextFloat() * 1.5f);
            l.x2 = centerX + (float) Math.cos(ang2) * dist2;
            l.y2 = centerY + (float) Math.sin(ang2) * dist2;
            l.maxLife = 0.3f; l.life = l.maxLife;
            lightnings.add(l);
        }
        p.setStyle(Paint.Style.STROKE);
        Iterator<Lightning> it = lightnings.iterator();
        while (it.hasNext()) {
            Lightning l = it.next();
            l.life -= dt;
            if (l.life <= 0) { it.remove(); continue; }
            float a = l.life / l.maxLife;
            Path path = new Path();
            path.moveTo(l.x1, l.y1);
            for (int i = 1; i < 5; i++) {
                float f = i / 5f;
                path.lineTo(l.x1 + (l.x2 - l.x1) * f + (rnd.nextFloat() - 0.5f) * 15f,
                            l.y1 + (l.y2 - l.y1) * f + (rnd.nextFloat() - 0.5f) * 15f);
            }
            path.lineTo(l.x2, l.y2);
            p.setStrokeWidth(2f);
            p.setColor(0xFF00E5FF);
            p.setAlpha((int) (a * 255));
            c.drawPath(path, p);
            p.setStrokeWidth(1f);
            p.setColor(Color.WHITE);
            p.setAlpha((int) (a * 180));
            c.drawPath(path, p);
        }
    }

    private void drawFlash(Canvas c) {
        if (flashAlpha <= 0) return;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        p.setAlpha((int) (flashAlpha * 255));
        c.drawRect(0, 0, getWidth(), getHeight(), p);
    }

    private void drawLogo(Canvas c) {
        if (logoAlpha <= 0 || earthRadius <= 0) return;
        c.save();
        c.translate(centerX, centerY - 40);
        c.scale(logoScale, logoScale);
        float r = earthRadius * 1.2f;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(0, 0, r * 1.5f,
                new int[]{0x88FF00E5, 0x448A2BE2, 0x00000000},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        p.setAlpha((int) (logoAlpha * 255));
        c.drawCircle(0, 0, r * 1.5f, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4f);
        p.setColor(0xFF00E5FF);
        c.drawCircle(0, 0, r * 0.8f, p);
        p.setStrokeWidth(2f);
        p.setColor(0xFF8A2BE2);
        c.drawCircle(0, 0, r, p);
        p.setStrokeWidth(8f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFF00E5FF);
        Path npath = new Path();
        float nr = r * 0.5f;
        npath.moveTo(-nr, nr);
        npath.lineTo(-nr, -nr);
        npath.lineTo(nr, nr);
        npath.lineTo(nr, -nr);
        c.drawPath(npath, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        c.restore();
    }

    private void drawText(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        if (titleAlpha > 0 && titleChars > 0) {
            String s = TITLE.substring(0, titleChars);
            p.setTextSize(earthRadius * 1.1f);
            p.setColor(0xFF00E5FF);
            p.setAlpha((int) (titleAlpha * 60));
            c.drawText(s, centerX, centerY + earthRadius * 2.2f, p);
            p.setColor(Color.WHITE);
            p.setAlpha((int) (titleAlpha * 255));
            c.drawText(s, centerX, centerY + earthRadius * 2.2f, p);
        }
        if (browserAlpha > 0 && browserChars > 0) {
            p.setTextSize(earthRadius * 0.45f);
            p.setColor(0xFF00E5FF);
            p.setAlpha((int) (browserAlpha * 255));
            c.drawText(BROWSER.substring(0, browserChars), centerX, centerY + earthRadius * 2.75f, p);
        }
        if (taglineAlpha > 0) {
            p.setTextSize(earthRadius * 0.28f);
            p.setColor(0xFF8A2BE2);
            p.setAlpha((int) (taglineAlpha * 255));
            c.drawText(TAGLINE, centerX, centerY + earthRadius * 3.2f, p);
        }
    }

    private float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }
}
