package com.activaplan.app.presentation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.activaplan.app.tracking.LocationPrefs

@Composable
actual fun rememberLocationPermissionRequester(onResult: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        LocationPrefs.markAsked(context)
        currentOnResult()
    }
    return remember(launcher) {
        { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    }
}
