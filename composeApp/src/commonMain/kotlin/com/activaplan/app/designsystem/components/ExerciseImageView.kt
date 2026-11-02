package com.activaplan.app.designsystem.components

import activaplan.composeapp.generated.resources.Res
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.activaplan.app.domain.model.ExerciseImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Gambar langkah latihan dari `composeResources/drawable/[ExerciseImage.file]`.
 * [placeholder] tampil selama memuat, bila [image] null, atau bila file tidak ada/gagal didekode.
 */
@Composable
fun ExerciseImageView(
    image: ExerciseImage?,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit,
) {
    val bitmap by produceState<ImageBitmap?>(null, image?.file) {
        value = image?.let { ExerciseImageCache.load(it.file) }
    }
    val loaded = bitmap
    if (image == null || loaded == null) {
        placeholder()
    } else {
        Image(
            loaded,
            contentDescription = image.alt,
            modifier = modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    }
}

/**
 * Cache LRU kecil untuk gambar yang sudah didekode, supaya menggulir daftar atau kembali ke layar tidak
 * membaca dan mendekode ulang file yang sama. Dibaca dan ditulis dari thread UI saja; dekode di thread latar.
 */
private object ExerciseImageCache {
    private const val MAX_ENTRIES = 24
    private val entries = LinkedHashMap<String, ImageBitmap>()

    suspend fun load(file: String): ImageBitmap? {
        entries.remove(file)?.let { hit ->
            entries[file] = hit
            return hit
        }
        val decoded = withContext(Dispatchers.Default) {
            runCatching { Res.readBytes("drawable/$file").decodeToImageBitmap() }.getOrNull()
        } ?: return null
        entries[file] = decoded
        if (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
        return decoded
    }
}
