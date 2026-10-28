package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.*;

public class WeatherReadingTest {
    private static final long NOW = 1_792_713_600_000L;

    private static WeatherReading reading(double temperature, int code, boolean day,
            long observed, long fetched) {
        return WeatherReading.create("Kolkata", temperature, code, day, observed, fetched, NOW);
    }

    @Test public void cacheHasSeparateFreshAndUsableLifetimes() {
        WeatherReading fresh = reading(28.2, 2, true, NOW - 15 * 60_000L, NOW);
        assertNotNull(fresh);
        assertTrue(fresh.isFresh(NOW + WeatherReading.FRESH_MS - 1));
        assertFalse(fresh.isFresh(NOW + WeatherReading.FRESH_MS));
        assertTrue(fresh.isStale(NOW + WeatherReading.FRESH_MS));
        assertTrue(fresh.isUsable(NOW + WeatherReading.USABLE_MS - 15 * 60_000L));
        assertFalse(fresh.isUsable(NOW + WeatherReading.USABLE_MS - 15 * 60_000L + 1));
    }

    @Test public void oldProviderDataCannotBeRevivedByANewFetchTime() {
        assertNull(reading(28, 0, true, NOW - WeatherReading.USABLE_MS - 1, NOW));
        assertNull(reading(28, 0, true, NOW, NOW - WeatherReading.USABLE_MS - 1));
    }

    @Test public void clockErrorsAndMissingTimestampsAreRejected() {
        assertNull(reading(28, 0, true, 0, NOW));
        assertNull(reading(28, 0, true, NOW, 0));
        assertNull(reading(28, 0, true, NOW + 30 * 60_000L + 1, NOW));
        assertNull(reading(28, 0, true, NOW, NOW + 30 * 60_000L + 1));
        assertNotNull(reading(28, 0, true, NOW + 15 * 60_000L, NOW));
    }

    @Test public void extremeOrNonFiniteTemperaturesNeverReachTheWidget() {
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, -100.1, 70.1}) {
            assertNull(reading(invalid, 0, true, NOW, NOW));
        }
        assertNotNull(reading(-100, 0, true, NOW, NOW));
        assertNotNull(reading(70, 0, true, NOW, NOW));
    }

    @Test public void roundingUsesCelsiusAndHasNoNegativeZero() {
        assertEquals("28\u00b0C", reading(28.2, 0, true, NOW, NOW).formattedTemperature());
        assertEquals("29\u00b0C", reading(28.5, 0, true, NOW, NOW).formattedTemperature());
        assertEquals("0\u00b0C", reading(-0.4, 0, true, NOW, NOW).formattedTemperature());
        assertEquals("-6\u00b0C", reading(-5.6, 0, true, NOW, NOW).formattedTemperature());
    }

    @Test public void allDocumentedWeatherFamiliesHaveAnIcon() {
        int[][] codes = {{0, 1}, {2}, {3}, {45, 48}, {51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82},
                {71, 73, 75, 77, 85, 86}, {95, 96, 97, 99}};
        String[] icons = {"clear", "partly", "cloud", "fog", "rain", "snow", "storm"};
        for (int family = 0; family < codes.length; family++) {
            for (int code : codes[family]) {
                WeatherReading value = reading(28, code, true, NOW, NOW);
                assertEquals("Code " + code, icons[family], value.iconKind());
                assertFalse("Code " + code, value.condition().isEmpty());
            }
        }
    }

    @Test public void nightIsNotPresentedAsSunshine() {
        assertEquals("Clear night", reading(24, 0, false, NOW, NOW).condition());
        assertEquals("Clear", reading(29, 0, true, NOW, NOW).condition());
    }

    @Test public void unknownCodesStayNeutralAndMalformedCodesAreRejected() {
        WeatherReading unknown = reading(28, 999, true, NOW, NOW);
        assertEquals("cloud", unknown.iconKind());
        assertEquals("Weather", unknown.condition());
        assertNull(reading(28, -1, true, NOW, NOW));
        assertNull(reading(28, 1000, true, NOW, NOW));
    }

    @Test public void locationIsRequiredAndStoredWithoutIncidentalWhitespace() {
        assertNull(WeatherReading.create(null, 28, 0, true, NOW, NOW, NOW));
        assertNull(WeatherReading.create("   ", 28, 0, true, NOW, NOW, NOW));
        WeatherReading value = WeatherReading.create(" Kolkata ", 28, 0, true, NOW, NOW, NOW);
        assertNotNull(value);
        assertEquals("Kolkata", value.city);
    }
}
