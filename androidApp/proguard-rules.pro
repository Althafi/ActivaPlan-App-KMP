# Aturan R8 untuk build rilis. Pustaka (kotlinx-serialization, Koin, Compose) membawa aturan konsumennya sendiri;
# berkas ini hanya menambah penjagaan untuk model aplikasi yang diserialisasi ke JSON.

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# Pola resmi kotlinx-serialization: pertahankan Companion dan serializer() pada kelas @Serializable milik aplikasi.
-keepclassmembers class com.activaplan.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.activaplan.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ditemukan lewat nama di AndroidManifest; manifest merge sudah menjaganya, ini sekadar sabuk pengaman.
-keep class com.activaplan.app.tracking.TrackingService
-keep class com.activaplan.app.reminder.ReminderAlarmReceiver
-keep class com.activaplan.app.reminder.ReminderRestoreReceiver
