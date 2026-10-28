package com.gathin.tvhome;

/** Small, validated weather value shared by the cache and the Home widget. */
final class WeatherReading {
    static final long FRESH_MS = 30 * 60_000L;
    static final long USABLE_MS = 6 * 60 * 60_000L;
    private static final long FUTURE_TOLERANCE_MS = 30 * 60_000L;

    final String city;
    final double tempC;
    final int weatherCode;
    final boolean isDay;
    final long observedAtMs;
    final long fetchedAtMs;

    private WeatherReading(String city, double tempC, int weatherCode, boolean isDay,
            long observedAtMs, long fetchedAtMs) {
        this.city = city;
        this.tempC = tempC;
        this.weatherCode = weatherCode;
        this.isDay = isDay;
        this.observedAtMs = observedAtMs;
        this.fetchedAtMs = fetchedAtMs;
    }

    /** Null means invalid or too old; a missing value must never become invented weather. */
    static WeatherReading create(String city, double tempC, int weatherCode, boolean isDay,
            long observedAtMs, long fetchedAtMs, long now) {
        if (city == null || city.trim().isEmpty() || city.trim().length() > 120
                || !Double.isFinite(tempC) || tempC < -100 || tempC > 70
                || weatherCode < 0 || weatherCode > 999) return null;
        WeatherReading reading = new WeatherReading(city.trim(), tempC, weatherCode, isDay,
                observedAtMs, fetchedAtMs);
        return reading.isUsable(now) ? reading : null;
    }

    boolean isFresh(long now) {
        return isUsable(now) && now - fetchedAtMs < FRESH_MS;
    }

    boolean isStale(long now) {
        return !isFresh(now);
    }

    boolean isUsable(long now) {
        return now > 0 && observedAtMs > 0 && fetchedAtMs > 0
                && observedAtMs - now <= FUTURE_TOLERANCE_MS
                && fetchedAtMs - now <= FUTURE_TOLERANCE_MS
                && now - observedAtMs <= USABLE_MS
                && now - fetchedAtMs <= USABLE_MS;
    }

    String formattedTemperature() {
        return Long.toString(Math.round(tempC)) + "\u00b0C";
    }

    String condition() {
        switch (weatherCode) {
            case 0: return isDay ? "Clear" : "Clear night";
            case 1: return "Mainly clear";
            case 2: return "Partly cloudy";
            case 3: return "Cloudy";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 56: case 57: return "Freezing drizzle";
            case 61: case 63: case 65: return "Rain";
            case 66: case 67: return "Freezing rain";
            case 71: case 73: case 75: case 77: return "Snow";
            case 80: case 81: case 82: return "Showers";
            case 85: case 86: return "Snow showers";
            case 95: case 96: case 97: case 99: return "Thunderstorm";
            default: return "Weather";
        }
    }

    /** Stable icon names; unknown future provider codes use a neutral cloud. */
    String iconKind() {
        switch (weatherCode) {
            case 0: case 1: return "clear";
            case 2: return "partly";
            case 45: case 48: return "fog";
            case 51: case 53: case 55: case 56: case 57:
            case 61: case 63: case 65: case 66: case 67:
            case 80: case 81: case 82: return "rain";
            case 71: case 73: case 75: case 77: case 85: case 86: return "snow";
            case 95: case 96: case 97: case 99: return "storm";
            default: return "cloud";
        }
    }
}
