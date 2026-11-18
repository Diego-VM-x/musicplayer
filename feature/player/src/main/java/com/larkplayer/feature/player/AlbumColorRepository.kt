package com.larkplayer.feature.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts a pastel accent from the current album artwork — this is what gives
 * Lark its per-song coloured player / mini-player.
 */
@Singleton
class AlbumColorRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache = object : LruCache<String, Int>(32) {}

    private val _accent = MutableStateFlow(DEFAULT)
    val accent: StateFlow<Int> = _accent.asStateFlow()

    private var current: String? = null

    suspend fun load(artworkUri: String?) {
        if (artworkUri.isNullOrBlank() || artworkUri == current) return
        current = artworkUri
        val cached = cache.get(artworkUri)
        if (cached != null) {
            _accent.value = cached
            return
        }
        val color = withContext(Dispatchers.IO) { extract(artworkUri) }
        cache.put(artworkUri, color)
        _accent.value = color
    }

    private suspend fun extract(uri: String): Int = runCatching {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false)
            .size(160)
            .build()
        val result = context.imageLoader.execute(request)
        val bitmap: Bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return DEFAULT
        val palette = Palette.from(bitmap)
            .maximumColorCount(20)
            .clearFilters()
            .generate()
        val swatch = palette.swatches
            .filter { it.population > 0 }
            .maxByOrNull { it.hsl[1] * 2.2f + it.hsl[2] }
        swatch?.rgb?.let(::pastel) ?: DEFAULT
    }.getOrDefault(DEFAULT)

    /** Keeps enough saturation for a Lark-like tint but ensures legibility on dark. */
    private fun pastel(rgb: Int): Int {
        val hsl = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            android.graphics.Color.red(rgb),
            android.graphics.Color.green(rgb),
            android.graphics.Color.blue(rgb),
            hsl,
        )
        hsl[1] = hsl[1].coerceIn(0.38f, 0.72f)
        hsl[2] = hsl[2].coerceIn(0.62f, 0.86f)
        return android.graphics.Color.HSVToColor(hsl)
    }

    companion object {
        /** Soft lavender used by Lark before artwork is parsed. */
        const val DEFAULT = 0xFFB08CFF.toInt()
    }
}
