package com.activaplan.app.presentation

import androidx.compose.runtime.Composable

/**
 * Mengembalikan fungsi yang menampilkan dialog izin lokasi sistem. [onResult] dipanggil setelah pengguna menjawab;
 * status terbaru dibaca lewat `LocationTracker.permission()`.
 */
@Composable
expect fun rememberLocationPermissionRequester(onResult: () -> Unit): () -> Unit
