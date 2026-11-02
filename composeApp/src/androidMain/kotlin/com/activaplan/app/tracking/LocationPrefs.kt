package com.activaplan.app.tracking

import android.content.Context

/** Android tidak membedakan "belum ditanya" dan "ditolak", jadi pertanyaan izin lokasi dicatat sendiri. */
internal object LocationPrefs {
    private const val FILE = "activaplan_location"
    private const val KEY_ASKED = "permission_asked"

    fun wasAsked(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
    }
}
