package com.example.s7opcuaapp.ui.screen.control

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.s7opcuaapp.R

@Composable
fun rememberPositionCount(): Int {
    val context = LocalContext.current
    return remember(context) {
        var n = 0
        while (context.resources.getIdentifier(
                "ic_pos${n + 1}_state0", "drawable", context.packageName
            ) != 0
        ) {
            n++
        }
        n
    }
}

@Composable
fun rememberPositionIcon(posNum: Int, state: Int): Int {
    val context = LocalContext.current
    return remember(posNum, state) {
        val id = context.resources.getIdentifier(
            "ic_pos${posNum}_state${state}", "drawable", context.packageName
        )
        if (id != 0) id else R.drawable.ic_pos1_state0
    }
}
