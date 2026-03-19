package com.termux.x11.input;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Parses a JSON gesture configuration string into a flat action map.
 *
 * Key format: "<type>.<subtype>.<fingerCount>"
 * Examples: "tap.single.2", "swipe.up.4", "pinch.in.3"
 *
 * Tap-release keys: "tap-release.<before>-<after>" where before/after are finger counts.
 * Example: "tap-release.2-1" means "had 2 fingers, lifted 1 (1 remains)".
 * Example: "tap-release.1-0" means "had 1 finger, lifted it (0 remain)" — fires on fast re-tap.
 *
 * JSON top-level keys map to prefixes:
 *   taps        -> tap
 *   swipes      -> swipe
 *   pinch       -> pinch
 *   rotate      -> rotate
 *   tap-release -> tap-release
 */
public class GestureConfig {

    // Default config matching the reference YAML specification.
    // An empty string in preferences means "use this default".
    public static final String DEFAULT_CONFIG =
        "{\n" +
        "  \"taps\": {\n" +
        "    \"single\": {\"2\":\"mouse-right-click\",\"3\":\"mouse-middle-click\",\"4\":\"tab-new\",\"5\":\"others-toggle-show-desktop\"},\n" +
        "    \"double\": {\"2\":\"tab-new\",\"3\":\"media-play/pause\",\"4\":\"tab-open-last\",\"5\":\"window-close\"},\n" +
        "    \"triple\": {\"2\":\"tab-close\"}\n" +
        "  },\n" +
        "  \"swipes\": {\n" +
        "    \"up\":    {\"4\":\"tab-prev\",\"5\":\"window-maximise\"},\n" +
        "    \"down\":  {\"4\":\"tab-next\",\"5\":\"window-minimise\"},\n" +
        "    \"left\":  {\"3\":\"tab-prev\",\"4\":\"window-prev\",\"5\":\"window-tile-left\"},\n" +
        "    \"right\": {\"3\":\"tab-next\",\"4\":\"window-next\",\"5\":\"window-tile-right\"}\n" +
        "  },\n" +
        "  \"pinch\": {\n" +
        "    \"in\":  {\"2\":\"pinch-in\",\"3\":\"close-applications-show-all\",\"4\":\"window-minimise\",\"5\":\"exit-fullscreen\"},\n" +
        "    \"out\": {\"2\":\"pinch-out\",\"3\":\"open-applications-show-all\",\"4\":\"window-maximise\",\"5\":\"fullscreen\"}\n" +
        "  },\n" +
        "  \"rotate\": {\"left\":{\"2\":\"rotate-left\"},\"right\":{\"2\":\"rotate-right\"}},\n" +
        "  \"tap-release\": {\"2-1\":\"volume-down\",\"1-0\":\"volume-up\",\"3-2\":\"volume-mute\"}\n" +
        "}";

    private final Map<String, String> mActions;

    /**
     * @param json JSON config string. Null or empty uses DEFAULT_CONFIG. Falls back to DEFAULT_CONFIG on parse error.
     */
    public GestureConfig(String json) {
        Map<String, String> parsed = null;
        if (json != null && !json.isEmpty()) {
            try {
                parsed = parse(json);
            } catch (JSONException e) {
                android.util.Log.w("GestureConfig", "Invalid gesture config JSON, using default: " + e.getMessage());
            }
        }
        if (parsed == null) {
            try {
                parsed = parse(DEFAULT_CONFIG);
            } catch (JSONException e) {
                parsed = new HashMap<>(); // should never happen
            }
        }
        mActions = Collections.unmodifiableMap(parsed);
    }

    /** Returns the action string for the given gesture key, or null if not configured. */
    public String getAction(String key) {
        return mActions.get(key);
    }

    private static Map<String, String> parse(String json) throws JSONException {
        Map<String, String> out = new HashMap<>();
        JSONObject root = new JSONObject(json);
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String topKey = keys.next();
            // Map top-level JSON keys to flat prefixes
            String prefix;
            switch (topKey) {
                case "taps":        prefix = "tap"; break;
                case "swipes":      prefix = "swipe"; break;
                case "pinch":       prefix = "pinch"; break;
                case "rotate":      prefix = "rotate"; break;
                case "tap-release": prefix = "tap-release"; break;
                default:            prefix = topKey; break;
            }
            flattenObject(out, prefix, root.getJSONObject(topKey));
        }
        return out;
    }

    /**
     * Recursively flattens a JSON object into the map.
     * Nested objects recurse with "prefix.key"; string values are stored directly.
     */
    private static void flattenObject(Map<String, String> out, String prefix, JSONObject obj) throws JSONException {
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = obj.get(key);
            if (value instanceof JSONObject) {
                flattenObject(out, prefix + "." + key, (JSONObject) value);
            } else {
                out.put(prefix + "." + key, value.toString());
            }
        }
    }
}
