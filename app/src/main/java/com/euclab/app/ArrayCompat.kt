package com.euclab.app

/** Small compatibility helper for the Kotlin version used by the Android build. */
fun <T> Array<out T>.chunked(size: Int): List<List<T>> = this.asList().chunked(size)
