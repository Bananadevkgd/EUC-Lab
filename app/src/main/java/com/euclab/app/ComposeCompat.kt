package com.euclab.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent as activitySetContent
import androidx.compose.runtime.Composable

/** Small compatibility shim so MainActivity stays focused on the prototype UI. */
fun ComponentActivity.setContent(content: @Composable () -> Unit) {
    this.activitySetContent(content)
}
