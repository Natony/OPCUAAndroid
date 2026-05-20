package com.example.s7opcuaapp.data.model

import com.example.s7opcuaapp.data.PlcConfig

/**
 * Model chứa dữ liệu PLC (mã booleans và ints).
 */
data class PlcData(
    val bools: List<Boolean>,
    val ints: List<Int>
) {
    companion object {
        fun empty(): PlcData {
            return PlcData(
                bools = List(PlcConfig.BOOL_COUNT) { false },
                ints = List(PlcConfig.INT_COUNT) { 0 }
            )
        }
    }
}
