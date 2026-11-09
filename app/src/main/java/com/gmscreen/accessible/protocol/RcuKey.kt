package com.gmscreen.accessible.protocol

object RcuKey {
    // Navigation (Carefully mapped: Down = 1 [Next Ch], Up = 2 [Prev Ch])
    const val DOWN = 1
    const val UP = 2
    const val LEFT = 3
    const val RIGHT = 4
    const val OK = 5

    // Aliases for channel next/prev
    const val CH_NEXT = DOWN
    const val CH_PREV = UP

    // Menus
    const val MENU = 6
    const val EXIT = 7

    // Colors
    const val RED = 8
    const val GREEN = 9
    const val YELLOW = 10
    const val BLUE = 11

    // Numeric Pad
    const val KEY_0 = 12
    const val KEY_1 = 13
    const val KEY_2 = 14
    const val KEY_3 = 15
    const val KEY_4 = 16
    const val KEY_5 = 17
    const val KEY_6 = 18
    const val KEY_7 = 19
    const val KEY_8 = 20
    const val KEY_9 = 21

    // Sound & Volume
    const val MUTE = 23
    const val VOL_UP = 35
    const val VOL_DOWN = 36

    // Channels
    const val CH_UP = 69
    const val CH_DOWN = 70
    const val TV_RADIO = 22

    // Advanced & Controls
    const val POWER = 1041
    const val INFO = 57
    const val EPG = 32
    const val AUDIO = 54
    const val SUBTITLE = 31
    const val RECALL = 29
}
