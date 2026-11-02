package com.activaplan.app.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.activaplan.app.tracking.IosLocationHub

@Composable
actual fun rememberLocationPermissionRequester(onResult: () -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    return remember { { IosLocationHub.requestAuthorization { currentOnResult() } } }
}
