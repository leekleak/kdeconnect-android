package org.kde.kdeconnect.helpers

import android.util.SparseIntArray
import android.view.KeyEvent

val SPECIAL_KEY_MAP = SparseIntArray().apply {
    var i = 0
    put(KeyEvent.KEYCODE_DEL, ++i) // 1
    put(KeyEvent.KEYCODE_TAB, ++i) // 2
    put(KeyEvent.KEYCODE_ENTER, 12)
    ++i // 3 is not used, return is 12 instead
    put(KeyEvent.KEYCODE_DPAD_LEFT, ++i) // 4
    put(KeyEvent.KEYCODE_DPAD_UP, ++i) // 5
    put(KeyEvent.KEYCODE_DPAD_RIGHT, ++i) // 6
    put(KeyEvent.KEYCODE_DPAD_DOWN, ++i) // 7
    put(KeyEvent.KEYCODE_PAGE_UP, ++i) // 8
    put(KeyEvent.KEYCODE_PAGE_DOWN, ++i) // 9
    put(KeyEvent.KEYCODE_MOVE_HOME, ++i) // 10
    put(KeyEvent.KEYCODE_MOVE_END, ++i) // 11
    put(KeyEvent.KEYCODE_NUMPAD_ENTER, ++i) // 12
    put(KeyEvent.KEYCODE_FORWARD_DEL, ++i) // 13
    put(KeyEvent.KEYCODE_ESCAPE, ++i) // 14
    put(KeyEvent.KEYCODE_SYSRQ, ++i) // 15
    put(KeyEvent.KEYCODE_SCROLL_LOCK, ++i) // 16
    ++i // 17
    ++i // 18
    ++i // 19
    ++i // 20
    put(KeyEvent.KEYCODE_F1, ++i) // 21
    put(KeyEvent.KEYCODE_F2, ++i) // 22
    put(KeyEvent.KEYCODE_F3, ++i) // 23
    put(KeyEvent.KEYCODE_F4, ++i) // 24
    put(KeyEvent.KEYCODE_F5, ++i) // 25
    put(KeyEvent.KEYCODE_F6, ++i) // 26
    put(KeyEvent.KEYCODE_F7, ++i) // 27
    put(KeyEvent.KEYCODE_F8, ++i) // 28
    put(KeyEvent.KEYCODE_F9, ++i) // 29
    put(KeyEvent.KEYCODE_F10, ++i) // 30
    put(KeyEvent.KEYCODE_F11, ++i) // 31
    put(KeyEvent.KEYCODE_F12, ++i) // 32
}