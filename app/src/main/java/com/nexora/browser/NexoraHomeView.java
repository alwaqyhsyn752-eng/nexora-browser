package com.nexora.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NexoraHomeView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random(7);
    private long lastFrame = -1;
    private float rotation = 0f;
    private float centerX, centerY, radius;
    private static class Star { float x, y, a, tw; }
    private final List<Star> stars = new ArrayList<>();

    public NexoraHomeView(Context c) { super(c); }
    public NexoraHomeView(Context c, AttributeSet a) { super(c, a); }
    public NexoraHomeView(Context c, AttributeSet a, int s) { super(c, a, s); }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w <= 0 || h <= 0) return;
        centerX = w / 2f;
        centerY = h * 0.45f;
        radius = Math.min(w, h) * 0.20f;
        stars.clear();
        for (int i = 0; i < 80; i++) {
            Star s = new Star();
            s.x = rnd.nextFloat() * w;
            s.y = rnd.nextFloat() * h;
            s.a = 0.2f + rnd.nextFloat() * 0.6f;
            s.tw = rnd.nextFloat() * 6.28f;
            stars.add(s);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = System.currentTimeMillis();
        if (lastFrame < 0) lastFrame = now;
        float dt = (now - lastFrame) / 1000f;
        if (dt > 0.1f) dt = 0.1f;
        lastFrame = now;
        rotation += dt * 0.3f;
        c.drawColor(0xFF050014);
        p.setStyle(Paint.Style.FILL);
        for (Star s : stars) {
            s.tw += 0.02f;
            p.setColor(Color.WHITE);
            p.setAlpha((int) (s.a * (0.5f + 0.5f * (float) Math.sin(s.tw)) * 255));
            c.drawCircle(s.x, s.y, 1.2f, p);
        }
        if (radius <= 0) { postInvalidateOnAnimation(); return; }
        p.setShader(new RadialGradient(centerX, centerY, radius * 2f,
                new int[]{0x3333CCFF, 0x118A2BE2, 0x00000000},
                new float[]{0.3f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(centerX, centerY, radius * 2f, p);
        p.setShader(null);
        p.setShader(new RadialGradient(centerX - radius * 0.3f, centerY - radius * 0.3f, radius * 1.4f,
                new int[]{0xFF1E90FF, 0xFF6A3BE2, 0xFF0A0020},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(centerX, centerY, radius, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f);
        p.setColor(0xFF00E5FF);
        p.setAlpha(180);
        for (int i = -3; i <= 3; i++) {
            float angle = i * 22f + (rotation * 180f) % 360f;
            float rx = radius * Math.abs((float) Math.cos(Math.toRadians(angle))) + 1;
            c.drawOval(centerX - rx, centerY - radius, centerX + rx, centerY + radius, p);
        }
        p.setColor(0xFF8A2BE2);
        for (int i = -2; i <= 2; i++) {
            float y = centerY + (i / 2.5f) * radius;
            float rx = (float) Math.sqrt(Math.max(0, radius * radius - (y - centerY) * (y - centerY)));
            c.drawOval(centerX - rx, y - radius * 0.05f, centerX + rx, y + radius * 0.05f, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(centerX - radius * 0.4f, centerY - radius * 0.5f, radius * 0.8f,
                new int[]{0x66FFFFFF, 0x00000000},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(centerX, centerY, radius * 0.9f, p);
        p.setShader(null);
        postInvalidateOnAnimation();
    }
}
