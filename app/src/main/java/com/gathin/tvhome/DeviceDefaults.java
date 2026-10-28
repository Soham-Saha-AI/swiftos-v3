package com.gathin.tvhome;

/** Conservative cross-device defaults. No vendor key is guessed or globally intercepted. */
final class DeviceDefaults {
    private DeviceDefaults() {}

    static boolean showWeather(String model, boolean hasChoice, boolean savedEnabled) {
        if (hasChoice) return savedEnabled;
        // Preserve compatibility defaults without assuming a location for all devices.
        return model != null && "MiTV-AXSO1".equalsIgnoreCase(model.trim());
    }

    /** Android owns these keys, including their repeat/hold behavior. Numeric codes are stable SDK constants. */
    static boolean systemManagedKey(int code) {
        switch (code) {
            case 3:   // HOME
            case 24:  // VOLUME_UP
            case 25:  // VOLUME_DOWN
            case 26:  // POWER
            case 84:  // SEARCH / voice search on older remotes
            case 85:  // MEDIA_PLAY_PAUSE
            case 86:  // MEDIA_STOP
            case 87:  // MEDIA_NEXT
            case 88:  // MEDIA_PREVIOUS
            case 89:  // MEDIA_REWIND
            case 90:  // MEDIA_FAST_FORWARD
            case 91:  // MUTE
            case 126: // MEDIA_PLAY
            case 127: // MEDIA_PAUSE
            case 164: // VOLUME_MUTE
            case 219: // ASSIST
            case 231: // VOICE_ASSIST
                return true;
            default:
                return false;
        }
    }
}
