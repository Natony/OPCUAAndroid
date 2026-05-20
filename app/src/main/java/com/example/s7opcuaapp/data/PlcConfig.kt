package com.example.s7opcuaapp.data

object PlcConfig {
    const val BOOL_NODE_START = 13
    const val BOOL_COUNT = 15
    const val INT_NODE_START = 28
    const val INT_COUNT = 36

    const val POSITION_INT_OFFSET = 15

    val BOOL_NODE_RANGE: IntRange = BOOL_NODE_START until (BOOL_NODE_START + BOOL_COUNT)
    val INT_NODE_RANGE: IntRange = INT_NODE_START until (INT_NODE_START + INT_COUNT)
}
