// Copyright 2013 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.termux.x11.input;

import android.content.Context;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Message;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import java.lang.ref.WeakReference;

/**
 * Detects multi-finger tap, double-tap, triple-tap, and long-press events.
 * The stock Android gesture detectors only handle single-finger gestures.
 *
 * Tap counting: after the first lift, a timer waits for getDoubleTapTimeout().
 * A second tap within that window increments the count. A third increments again.
 * When the timer fires, the appropriate callback (onTap/onDoubleTap/onTripleTap) is called.
 *
 * Multi-tap sequence state (mTapCount, mPendingPointerCount) is NOT reset on ACTION_DOWN —
 * only reset when the timeout fires or when finger count changes between taps.
 */
public class TapGestureDetector {

    public interface OnTapListener {
        void onTap(int pointerCount, float x, float y);
        void onDoubleTap(int pointerCount, float x, float y);
        void onTripleTap(int pointerCount, float x, float y);
        void onLongPress(int pointerCount, float x, float y);
    }

    private static final int MSG_LONGPRESS = 0;
    private static final int MSG_TAP_TIMEOUT = 1;

    private final OnTapListener mListener;
    private final Handler mHandler;

    private final SparseArray<PointF> mInitialPositions = new SparseArray<>();
    private final int mTouchSlopSquare;

    /** The maximum number of fingers seen in the current gesture. */
    private int mPointerCount;
    /** Coordinates of the first finger down in the current gesture. */
    private PointF mInitialPoint;
    /** True if movement or long-press has cancelled the current tap. */
    private boolean mTapCancelled;

    // Multi-tap state: counts consecutive taps before the timeout fires.
    private int mTapCount = 0;
    private int mPendingPointerCount = 0;
    private float mPendingX, mPendingY;

    /** @noinspection NullableProblems */
    @SuppressWarnings("deprecation")
    private static class EventHandler extends Handler {
        private final WeakReference<TapGestureDetector> mDetector;

        EventHandler(TapGestureDetector detector) {
            mDetector = new WeakReference<>(detector);
        }

        @Override
        public void handleMessage(Message message) {
            TapGestureDetector d = mDetector.get();
            if (d == null) return;
            if (message.what == MSG_LONGPRESS) {
                d.mTapCancelled = true;
                d.mListener.onLongPress(d.mPointerCount, d.mInitialPoint.x, d.mInitialPoint.y);
                d.mInitialPoint = null;
            } else if (message.what == MSG_TAP_TIMEOUT) {
                d.firePendingTap();
            }
        }
    }

    public TapGestureDetector(Context context, OnTapListener listener) {
        mListener = listener;
        mHandler = new EventHandler(this);
        int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mTouchSlopSquare = touchSlop * touchSlop;
    }

    /**
     * Cancel an in-progress tap sequence. Called externally when another gesture
     * (e.g. tap-release) wins the gesture conflict.
     */
    public void cancelTapSequence() {
        mHandler.removeMessages(MSG_TAP_TIMEOUT);
        mTapCount = 0;
        mPendingPointerCount = 0;
    }

    public void onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Starting a new gesture — reset in-gesture state but keep multi-tap sequence.
                // If the pending tap has a different finger count, fire it now before continuing.
                // We don't know the new tap's finger count yet, so defer comparison to ACTION_UP.
                resetGesture();
                trackDownEvent(event);
                mHandler.sendEmptyMessageDelayed(MSG_LONGPRESS, ViewConfiguration.getLongPressTimeout());
                mPointerCount = 1;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                trackDownEvent(event);
                mPointerCount = Math.max(mPointerCount, event.getPointerCount());
                break;

            case MotionEvent.ACTION_MOVE:
                if (!mTapCancelled && trackMoveEvent(event)) {
                    cancelLongPress();
                    mTapCancelled = true;
                }
                break;

            case MotionEvent.ACTION_UP:
                cancelLongPress();
                if (!mTapCancelled) {
                    // If the pending tap had a different finger count, fire it first
                    if (mTapCount > 0 && mPendingPointerCount != mPointerCount) {
                        mHandler.removeMessages(MSG_TAP_TIMEOUT);
                        firePendingTap();
                    }
                    mTapCount++;
                    mPendingPointerCount = mPointerCount;
                    mPendingX = mInitialPoint != null ? mInitialPoint.x : 0;
                    mPendingY = mInitialPoint != null ? mInitialPoint.y : 0;
                    mHandler.removeMessages(MSG_TAP_TIMEOUT);
                    mHandler.sendEmptyMessageDelayed(MSG_TAP_TIMEOUT, ViewConfiguration.getDoubleTapTimeout());
                }
                mInitialPoint = null;
                break;

            case MotionEvent.ACTION_POINTER_UP:
                cancelLongPress();
                trackUpEvent(event);
                break;

            case MotionEvent.ACTION_CANCEL:
                cancelLongPress();
                mHandler.removeMessages(MSG_TAP_TIMEOUT);
                mTapCount = 0;
                break;

            default:
                break;
        }
    }

    /** Fires the appropriate tap callback based on mTapCount, then resets multi-tap state. */
    private void firePendingTap() {
        int count = mTapCount;
        int fingers = mPendingPointerCount;
        float x = mPendingX;
        float y = mPendingY;
        mTapCount = 0;
        mPendingPointerCount = 0;
        if (count == 1)      mListener.onTap(fingers, x, y);
        else if (count == 2) mListener.onDoubleTap(fingers, x, y);
        else                 mListener.onTripleTap(fingers, x, y);
    }

    private void trackDownEvent(MotionEvent event) {
        int pointerIndex = event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN
                ? event.getActionIndex() : 0;
        int pointerId = event.getPointerId(pointerIndex);
        PointF pos = new PointF(event.getX(pointerIndex), event.getY(pointerIndex));
        mInitialPositions.put(pointerId, pos);
        if (mInitialPoint == null) mInitialPoint = pos;
    }

    private void trackUpEvent(MotionEvent event) {
        int pointerIndex = event.getActionMasked() == MotionEvent.ACTION_POINTER_UP
                ? event.getActionIndex() : 0;
        mInitialPositions.remove(event.getPointerId(pointerIndex));
    }

    private boolean trackMoveEvent(MotionEvent event) {
        for (int i = 0; i < event.getPointerCount(); i++) {
            PointF down = mInitialPositions.get(event.getPointerId(i));
            if (down == null) {
                mInitialPositions.put(event.getPointerId(i), new PointF(event.getX(i), event.getY(i)));
                continue;
            }
            float dx = event.getX(i) - down.x;
            float dy = event.getY(i) - down.y;
            if (dx * dx + dy * dy > mTouchSlopSquare) return true;
        }
        return false;
    }

    /** Resets only the current in-gesture state (not the multi-tap sequence). */
    private void resetGesture() {
        cancelLongPress();
        mPointerCount = 0;
        mInitialPositions.clear();
        mTapCancelled = false;
    }

    private void cancelLongPress() {
        mHandler.removeMessages(MSG_LONGPRESS);
    }
}
