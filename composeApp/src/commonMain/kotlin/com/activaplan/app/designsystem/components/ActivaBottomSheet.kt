package com.activaplan.app.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.activaplan.app.designsystem.ActivaDimens
import com.activaplan.app.designsystem.ActivaPlanTheme

/** Drag handle Material 3 (4 + 2 x 22) ditambah sedikit celah di bawah status bar. */
private val SheetChrome = 64.dp

/** Batas bawah tinggi isi, agar layar yang sangat pendek (mis. lanskap) tetap menampilkan sesuatu. */
private val MinContentHeight = 160.dp

/**
 * Bottom sheet modal: permukaan `surface`, sudut atas 24, terbuka penuh sesuai isi.
 *
 * Tinggi isi dibatasi sampai sheet berhenti di bawah status bar. Tanpa batas ini sheet yang isinya setinggi layar
 * menempel ke tepi atas, dan Material 3 mengurangi padding insets atas sesuai posisi sheet; setiap geseran kecil
 * mengubah tinggi sheet, lalu posisinya, lalu padding lagi, sehingga sheet bergetar saat isinya digulir. Isi yang
 * lebih tinggi dari batas harus menggulir sendiri (lihat sheet Jadwal dan "Tambah ke Rutinitas").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivaBottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = ActivaPlanTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        contentColor = c.text,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.border) },
    ) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = maxSheetContentHeight())
                .padding(horizontal = ActivaDimens.screenPadding)
                .padding(bottom = 24.dp),
            content = content,
        )
    }
}

/** Tinggi layar dikurangi insets sistem dan [SheetChrome]; tanpa batas (Unspecified) selama ukuran jendela belum diketahui. */
@Composable
private fun maxSheetContentHeight(): Dp {
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    if (windowHeightPx <= 0) return Dp.Unspecified
    val insets = WindowInsets.safeDrawing
    val reservedPx = insets.getTop(density) + insets.getBottom(density)
    return with(density) { (windowHeightPx - reservedPx).toDp() - SheetChrome }.coerceAtLeast(MinContentHeight)
}
