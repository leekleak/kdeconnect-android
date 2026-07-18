package org.kde.kdeconnect.helpers

import android.util.SparseIntArray
import android.view.KeyEvent

// Values 2 to and 17-20 are unused
val SPECIAL_KEY_MAP = mapOf(
    1 to KeyEvent.KEYCODE_DEL,
    2 to KeyEvent.KEYCODE_TAB,

    4 to KeyEvent.KEYCODE_DPAD_LEFT,
    5 to KeyEvent.KEYCODE_DPAD_UP,
    6 to KeyEvent.KEYCODE_DPAD_RIGHT,
    7 to KeyEvent.KEYCODE_DPAD_DOWN,
    8 to KeyEvent.KEYCODE_PAGE_UP,
    9 to KeyEvent.KEYCODE_PAGE_DOWN,
    10 to KeyEvent.KEYCODE_MOVE_HOME,
    11 to KeyEvent.KEYCODE_MOVE_END,
    12 to KeyEvent.KEYCODE_NUMPAD_ENTER,
    13 to KeyEvent.KEYCODE_FORWARD_DEL,
    14 to KeyEvent.KEYCODE_ESCAPE,
    15 to KeyEvent.KEYCODE_SYSRQ,
    16 to KeyEvent.KEYCODE_SCROLL_LOCK,

    21 to KeyEvent.KEYCODE_F1,
    22 to KeyEvent.KEYCODE_F2,
    23 to KeyEvent.KEYCODE_F3,
    24 to KeyEvent.KEYCODE_F4,
    25 to KeyEvent.KEYCODE_F5,
    26 to KeyEvent.KEYCODE_F6,
    27 to KeyEvent.KEYCODE_F7,
    28 to KeyEvent.KEYCODE_F8,
    29 to KeyEvent.KEYCODE_F9,
    30 to KeyEvent.KEYCODE_F10,
    31 to KeyEvent.KEYCODE_F11,
    32 to KeyEvent.KEYCODE_F12
)

val SPECIAL_KEY_ENCODING_MAP = SPECIAL_KEY_MAP.map { (k, v) -> v to k }.toMap()