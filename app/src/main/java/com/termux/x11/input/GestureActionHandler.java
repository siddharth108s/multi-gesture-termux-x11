package com.termux.x11.input;

import static android.view.KeyEvent.KEYCODE_ALT_LEFT;
import static android.view.KeyEvent.KEYCODE_CTRL_LEFT;
import static android.view.KeyEvent.KEYCODE_D;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static android.view.KeyEvent.KEYCODE_EQUALS;
import static android.view.KeyEvent.KEYCODE_F11;
import static android.view.KeyEvent.KEYCODE_F4;
import static android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
import static android.view.KeyEvent.KEYCODE_META_LEFT;
import static android.view.KeyEvent.KEYCODE_MINUS;
import static android.view.KeyEvent.KEYCODE_SHIFT_LEFT;
import static android.view.KeyEvent.KEYCODE_T;
import static android.view.KeyEvent.KEYCODE_TAB;
import static android.view.KeyEvent.KEYCODE_W;
import static android.view.KeyEvent.KEYCODE_Y;
import static android.view.KeyEvent.KEYCODE_Z;

import android.content.Context;
import android.media.AudioManager;
import android.view.KeyEvent;

import com.termux.x11.LorieView;

/**
 * Executes gesture actions by name.
 *
 * X11 key sequence actions are sent via LorieView.sendKeyEvent().
 * System actions (volume, media) use AudioManager.
 * Mouse actions ("mouse-*") must be handled by the caller before reaching this class.
 */
public class GestureActionHandler {
    private final AudioManager mAudioManager;
    private final LorieView mLorieView;

    public GestureActionHandler(Context context, LorieView lorieView) {
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mLorieView = lorieView;
    }

    /** Execute the named action. Silently ignores unknown actions. */
    public void execute(String action) {
        switch (action) {
            // Tab management
            case "tab-new":       sendKeys(KEYCODE_CTRL_LEFT, KEYCODE_T); break;
            case "tab-close":     sendKeys(KEYCODE_CTRL_LEFT, KEYCODE_W); break;
            case "tab-prev":      sendKeys(KEYCODE_CTRL_LEFT, KEYCODE_SHIFT_LEFT, KEYCODE_TAB); break;
            case "tab-next":      sendKeys(KEYCODE_CTRL_LEFT, KEYCODE_TAB); break;
            case "tab-open-last": sendKeys(KEYCODE_CTRL_LEFT, KEYCODE_SHIFT_LEFT, KEYCODE_T); break;
            // Window management
            case "window-close":       sendKeys(KEYCODE_ALT_LEFT, KEYCODE_F4); break;
            case "window-prev":        sendKeys(KEYCODE_ALT_LEFT, KEYCODE_SHIFT_LEFT, KEYCODE_TAB); break;
            case "window-next":        sendKeys(KEYCODE_ALT_LEFT, KEYCODE_TAB); break;
            case "window-maximise":    sendKeys(KEYCODE_META_LEFT, KEYCODE_DPAD_UP); break;
            case "window-minimise":    sendKeys(KEYCODE_META_LEFT, KEYCODE_DPAD_DOWN); break;
            case "window-tile-left":   sendKeys(KEYCODE_META_LEFT, KEYCODE_DPAD_LEFT); break;
            case "window-tile-right":  sendKeys(KEYCODE_META_LEFT, KEYCODE_DPAD_RIGHT); break;
            // Desktop / application
            case "others-toggle-show-desktop":     sendKeys(KEYCODE_META_LEFT, KEYCODE_D); break;
            case "close-applications-show-all":    sendKeys(KEYCODE_CTRL_LEFT, KEYCODE_ALT_LEFT, KEYCODE_TAB); break;
            case "open-applications-show-all":     sendKey(KEYCODE_META_LEFT); break;
            // Fullscreen
            case "fullscreen":
            case "exit-fullscreen": sendKey(KEYCODE_F11); break;
            // Zoom (pinch → Ctrl +/-)
            case "pinch-in":    sendKeys(KEYCODE_CTRL_LEFT, KEYCODE_MINUS); break;
            case "pinch-out":   sendKeys(KEYCODE_CTRL_LEFT, KEYCODE_EQUALS); break;
            // Undo/redo (rotate → Ctrl Z/Y)
            case "rotate-left":  sendKeys(KEYCODE_CTRL_LEFT, KEYCODE_Z); break;
            case "rotate-right": sendKeys(KEYCODE_CTRL_LEFT, KEYCODE_Y); break;
            // Media
            case "media-play/pause": dispatchMediaKey(KEYCODE_MEDIA_PLAY_PAUSE); break;
            // System volume
            case "volume-up":   adjustVolume(AudioManager.ADJUST_RAISE); break;
            case "volume-down": adjustVolume(AudioManager.ADJUST_LOWER); break;
            case "volume-mute": adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE); break;
        }
    }

    /**
     * Sends a modifier+key sequence to the X11 server.
     * All keycodes except the last are treated as modifiers (held for the duration).
     * The last keycode is the main key (pressed then released).
     * Modifiers are released in reverse order after the main key.
     *
     * Example: sendKeys(CTRL_LEFT, SHIFT_LEFT, T)
     *   → CTRL down, SHIFT down, T down, T up, SHIFT up, CTRL up
     */
    private void sendKeys(int... keycodes) {
        int modifierCount = keycodes.length - 1;
        // Press modifiers
        for (int i = 0; i < modifierCount; i++)
            mLorieView.sendKeyEvent(0, keycodes[i], true, 0);
        // Press + release main key
        int mainKey = keycodes[modifierCount];
        mLorieView.sendKeyEvent(0, mainKey, true, 0);
        mLorieView.sendKeyEvent(0, mainKey, false, 0);
        // Release modifiers in reverse
        for (int i = modifierCount - 1; i >= 0; i--)
            mLorieView.sendKeyEvent(0, keycodes[i], false, 0);
    }

    /** Sends a single key press and release with no modifiers. */
    private void sendKey(int keycode) {
        mLorieView.sendKeyEvent(0, keycode, true, 0);
        mLorieView.sendKeyEvent(0, keycode, false, 0);
    }

    private void dispatchMediaKey(int keycode) {
        mAudioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keycode));
        mAudioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keycode));
    }

    private void adjustVolume(int direction) {
        mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
    }
}
