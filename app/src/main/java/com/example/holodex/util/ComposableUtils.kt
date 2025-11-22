package com.example.holodex.util

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun findActivity(): ComponentActivity {
    val context = LocalContext.current
    return remember(context) {
        var a = context
        while (a is ContextWrapper) {
            if (a is ComponentActivity) {
                return@remember a
            }
            a = a.baseContext
        }
        // This should not happen in a normal app setup
        error("Could not find activity context")
    }
}