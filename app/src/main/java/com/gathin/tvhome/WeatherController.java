package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.net.ssl.HttpsURLConnection;

/** Foreground-only weather refresh. This never installs a service, listener, or remote-key filter. */
final class WeatherController implements AutoCloseable {
    private static final String TAG = "TVHomeWeather";
    private static final long RETRY_MS = 5 * 60_000L;
    private static final int TIMEOUT_MS = 5_000;
    private static final int MAX_BYTES = 32 * 1024;

    private final SharedPreferences cache;
    private final String cacheKey;
    private final String city;
    private final URL endpoint;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "HomeWeather");
        thread.setDaemon(true);
        return thread;
    });
    private final Runnable refresh = this::refreshIfDue;
    private Consumer<WeatherReading> render;
    private WeatherReading reading;
    private boolean active;
    private boolean cacheLoaded;
    private boolean inFlight;
    private long nextAttemptElapsed;
    private volatile boolean closed;
    private volatile HttpsURLConnection connection;

    /** Call lifecycle methods on the main thread. Location is fixed, never detected or tracked. */
    WeatherController(Context context, String city, double latitude, double longitude,
            Consumer<WeatherReading> render) {
        if (city == null || city.trim().isEmpty() || city.trim().length() > 120
                || !Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180
                || render == null) throw new IllegalArgumentException("Invalid weather setup");
        this.city = city.trim();
        this.render = render;
        cache = context.getApplicationContext().getSharedPreferences("home_weather_v1", Context.MODE_PRIVATE);
        cacheKey = this.city + "|" + Double.toString(latitude) + "|" + Double.toString(longitude);
        try {
            endpoint = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + latitude
                    + "&longitude=" + longitude
                    + "&current=temperature_2m,weather_code,is_day"
                    + "&temperature_unit=celsius&timeformat=unixtime&timezone=auto");
        } catch (IOException impossible) {
            throw new IllegalArgumentException("Invalid weather endpoint", impossible);
        }
    }

    void start() {
        if (closed) return;
        active = true;
        if (!cacheLoaded) {
            cacheLoaded = true;
            reading = readCache();
            if (reading != null) {
                Log.i(TAG, "Weather cache restored");
                long age = Math.max(0, System.currentTimeMillis() - reading.fetchedAtMs);
                if (age < WeatherReading.FRESH_MS) {
                    nextAttemptElapsed = SystemClock.elapsedRealtime() + WeatherReading.FRESH_MS - age;
                }
            }
        }
        refreshIfDue();
    }

    void stop() {
        active = false;
        handler.removeCallbacks(refresh);
        // An already-started bounded request may finish and warm the cache, without updating hidden UI.
    }

    private void refreshIfDue() {
        handler.removeCallbacks(refresh);
        if (closed || !active) return;
        renderCurrent();
        if (inFlight) return;
        long elapsed = SystemClock.elapsedRealtime();
        if (elapsed < nextAttemptElapsed) {
            schedule(nextAttemptElapsed - elapsed);
            return;
        }
        inFlight = true;
        // A quick Home/app/Home trip keeps the same cooldown, including after a failed request.
        nextAttemptElapsed = elapsed + RETRY_MS;
        executor.execute(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            WeatherReading result = null;
            try {
                result = fetch();
                if (!closed) writeCache(result);
                Log.i(TAG, "Weather refreshed");
            } catch (Exception failure) {
                if (!closed) Log.w(TAG, "Weather unavailable (" + failure.getClass().getSimpleName() + ")");
            }
            final WeatherReading fetched = result;
            if (!closed) handler.post(() -> finished(fetched));
        });
    }

    private void finished(WeatherReading fetched) {
        if (closed) return;
        inFlight = false;
        if (fetched != null) reading = fetched;
        nextAttemptElapsed = SystemClock.elapsedRealtime()
                + (fetched == null ? RETRY_MS : WeatherReading.FRESH_MS);
        if (active) {
            renderCurrent();
            schedule(fetched == null ? RETRY_MS : WeatherReading.FRESH_MS);
        }
    }

    private void renderCurrent() {
        if (render != null && active && !closed) {
            render.accept(reading != null && reading.isUsable(System.currentTimeMillis()) ? reading : null);
        }
    }

    private void schedule(long delay) {
        if (!active || closed) return;
        handler.removeCallbacks(refresh);
        // Expire a cached value at its six-hour boundary even if a retry would otherwise run later.
        if (reading != null) {
            long expires = Math.min(reading.observedAtMs, reading.fetchedAtMs) + WeatherReading.USABLE_MS;
            long remaining = expires - System.currentTimeMillis();
            if (remaining >= 0) delay = Math.min(delay, remaining + 1);
        }
        handler.postDelayed(refresh, Math.max(1_000L, delay));
    }

    private WeatherReading fetch() throws Exception {
        if (closed) throw new IOException("Closed");
        HttpsURLConnection request = (HttpsURLConnection) endpoint.openConnection();
        connection = request;
        try {
            if (closed) throw new IOException("Closed");
            request.setConnectTimeout(TIMEOUT_MS);
            request.setReadTimeout(TIMEOUT_MS);
            request.setInstanceFollowRedirects(false);
            request.setRequestProperty("Accept", "application/json");
            request.setRequestProperty("User-Agent", "CleanYourTV-HomeWeather/1");
            if (request.getResponseCode() != HttpsURLConnection.HTTP_OK) {
                throw new IOException("Weather HTTP failure");
            }
            if (request.getContentLengthLong() > MAX_BYTES) throw new IOException("Response too large");
            long deadline = SystemClock.elapsedRealtime() + 15_000L;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
            try (InputStream input = request.getInputStream()) {
                byte[] chunk = new byte[1024];
                int length;
                while ((length = input.read(chunk)) != -1) {
                    if (closed || SystemClock.elapsedRealtime() > deadline) throw new IOException("Request expired");
                    if (bytes.size() + length > MAX_BYTES) throw new IOException("Response too large");
                    bytes.write(chunk, 0, length);
                }
            }
            JSONObject current = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name())).getJSONObject("current");
            double code = current.getDouble("weather_code");
            double day = current.getDouble("is_day");
            double seconds = current.getDouble("time");
            if (!Double.isFinite(code) || code != Math.rint(code) || code < 0 || code > 999
                    || (day != 0 && day != 1) || !Double.isFinite(seconds)
                    || seconds != Math.rint(seconds) || seconds <= 0 || seconds > Long.MAX_VALUE / 1000L) {
                throw new IOException("Invalid weather fields");
            }
            long now = System.currentTimeMillis();
            WeatherReading result = WeatherReading.create(city, current.getDouble("temperature_2m"),
                    (int) code, day == 1, (long) seconds * 1000L, now, now);
            if (result == null) throw new IOException("Invalid weather reading");
            return result;
        } finally {
            request.disconnect();
            if (connection == request) connection = null;
        }
    }

    private WeatherReading readCache() {
        try {
            if (!cacheKey.equals(cache.getString("location", ""))) return null;
            return WeatherReading.create(city, Double.parseDouble(cache.getString("temperature", "NaN")),
                    cache.getInt("code", -1), cache.getBoolean("day", true),
                    cache.getLong("observed", 0), cache.getLong("fetched", 0), System.currentTimeMillis());
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private void writeCache(WeatherReading value) {
        cache.edit().putString("location", cacheKey).putString("temperature", Double.toString(value.tempC))
                .putInt("code", value.weatherCode).putBoolean("day", value.isDay)
                .putLong("observed", value.observedAtMs).putLong("fetched", value.fetchedAtMs).apply();
    }

    @Override
    public void close() {
        closed = true;
        active = false;
        render = null;
        handler.removeCallbacksAndMessages(null);
        HttpsURLConnection request = connection;
        if (request != null) request.disconnect();
        executor.shutdownNow();
    }
}
