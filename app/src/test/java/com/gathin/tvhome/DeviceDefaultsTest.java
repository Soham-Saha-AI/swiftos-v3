package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class DeviceDefaultsTest {
    @Test public void newGenericTvDoesNotAssumeKolkataOrStartWeather() {
        for (String model : new String[]{"BRAVIA 4K", "Chromecast", "Google TV", "MiTV-AXSO2", "", null}) {
            assertFalse(DeviceDefaults.showWeather(model, false, true));
        }
    }
    @Test public void ownerWeatherIsPreservedButAnExplicitChoiceAlwaysWins() {
        assertTrue(DeviceDefaults.showWeather("MiTV-AXSO1", false, false));
        assertFalse(DeviceDefaults.showWeather("MiTV-AXSO1", true, false));
        assertTrue(DeviceDefaults.showWeather("BRAVIA 4K", true, true));
    }
    @Test public void heldVolumePowerVoiceAndMediaStayWithAndroid() {
        for (int key : new int[]{3,24,25,26,84,85,86,87,88,89,90,91,126,127,164,219,231}) {
            assertTrue("system key " + key, DeviceDefaults.systemManagedKey(key));
        }
    }
    @Test public void dpadOkAndBackRemainAvailableToTheLaunchersOwnDialogs() {
        for (int key : new int[]{4,19,20,21,22,23,66,111,160}) {
            assertFalse("app key " + key, DeviceDefaults.systemManagedKey(key));
        }
    }
}
