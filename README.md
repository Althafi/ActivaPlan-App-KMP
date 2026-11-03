# ActivaPlan

ActivaPlan adalah aplikasi personal training mandiri berbasis **Kotlin Multiplatform (KMP)** dengan UI **Compose Multiplatform**, untuk Android dan iOS dari satu codebase. Pengguna memilih latihan per otot dan level, menyusun rutinitas, menjadwalkannya, lalu menjalankan sesi latihan dengan timer, istirahat, dan riwayat. ActivaPlan juga menyediakan sesi jalan dan lari (kardio), rencana mingguan, dan program bertahap 4 pekan.

ActivaPlan bersifat **offline sepenuhnya**: semua data tersimpan di perangkat. Tidak ada akun, login, server, atau pelacak. Aplikasi bahkan tidak mendeklarasikan izin `INTERNET`, sehingga tidak ada fitur yang butuh koneksi.

---

## Daftar Isi

- [Fitur](#fitur)
- [Arsitektur](#arsitektur)
- [Struktur Proyek](#struktur-proyek)
- [Tech Stack](#tech-stack)
- [Data Bawaan](#data-bawaan)
- [Data Pengguna](#data-pengguna)
- [Latihan Kekuatan](#latihan-kekuatan)
- [Rutinitas dan Jadwal](#rutinitas-dan-jadwal)
- [Beranda](#beranda)
- [Sesi Latihan](#sesi-latihan)
- [Kardio, GPS, dan Detak Jantung](#kardio-gps-dan-detak-jantung)
- [Rencana Mingguan dan Program](#rencana-mingguan-dan-program)
- [Pengingat](#pengingat)
- [Splash, Perkenalan, dan Onboarding](#splash-perkenalan-dan-onboarding)
- [Bahasa dan Tema](#bahasa-dan-tema)
- [Alur Navigasi](#alur-navigasi)
- [Setup dan Menjalankan](#setup-dan-menjalankan)
- [Tes](#tes)
- [Keterbatasan yang Diketahui](#keterbatasan-yang-diketahui)

---

## Fitur

| Fitur | Keterangan |
|---|---|
| Beranda | Kartu latihan hari ini, streak pekan ini, kemajuan program aktif, dan rekomendasi template |
| Jelajah | 70 latihan kekuatan dalam tiga mode, difilter per mode, kategori otot, level, dan kata kunci; switch ke 10 sesi kardio |
| Detail latihan | Gambar langkah atau diagram board, set/rep/istirahat, langkah, tips, kesalahan umum, catatan keselamatan, Turun/Naik level |
| Rutinitas | Buat dan ubah rutinitas (kekuatan, kardio, atau campuran), estimasi menit, template, rencana mingguan, dan program |
| Jadwal | Pemilih pekan, jadwal per hari, tambah/ubah lewat bottom sheet, deteksi bentrok, pengingat per jadwal |
| Sesi latihan | Cek keselamatan, ring hitung mundur atau hitung repetisi, istirahat, jeda, sheet Gagal, detail gerakan, layar Selesai beranimasi |
| Kardio | Sesi jalan dan lari per segmen dengan zona detak jantung, isyarat bunyi/getar, dan pelacakan jarak/pace lewat GPS (opsional) |
| Riwayat | Sesi dikelompokkan per bulan, dengan status Selesai, Sebagian, atau Dihentikan |
| Setelan | Tema, bahasa, pengingat dan izin notifikasi, waktu default, GPS dan usia, simpan/hapus riwayat, hapus semua data, ulangi pengantar, atribusi dan lisensi |
| Awal pakai | Splash beranimasi, 3 layar Perkenalan, dan Onboarding (level, alat yang dimiliki, pengingat) |

Bottom nav punya lima tab: **Beranda, Jelajah, Rutinitas, Jadwal, Setelan**. Riwayat dibuka dari Beranda, layar Selesai, atau Setelan.

Daftar di tiap layar mengikuti perubahan data secara otomatis lewat `Flow`, tanpa perlu menarik untuk memuat ulang. Aksi yang berhasil atau gagal diberi umpan balik lewat snackbar, dengan tombol **Coba lagi** saat penyimpanan gagal.

---

## Arsitektur

**Clean Architecture** dengan **MVVM** di lapisan presentasi.

```
Screen (Compose)  ──►  ViewModel (StateFlow)  ──►  UseCase  ──►  Repository  ──►  JSON bawaan | DataStore
```

- **Aliran data satu arah.** Layar mengirim aksi ke ViewModel, ViewModel menghasilkan `UiState` baru (`Loading`, `Ready`, `Error`) dan peristiwa sekali pakai (snackbar, keluar layar).
- **KMP-first.** Logika bisnis, ViewModel, dan UI berada di `commonMain`. Kode platform hanya untuk pengingat, izin, GPS, isyarat bunyi/getar, layar tetap menyala, DataStore factory, bahasa sistem, dan tampilan sistem (status bar).
- **Antarmuka di domain, implementasi di platform.** Contohnya `ReminderScheduler`, `NotificationPermission`, `LocationTracker`, dan `CuePlayer`, disediakan lewat `platformModule()` per platform.
- **Aturan sebagai fungsi murni.** State machine sesi (`SessionState`), deteksi bentrok jadwal (`ScheduleConflicts`), pemilihan jadwal hari ini (`GetTodayWorkoutUseCase`), hitungan streak, rekomendasi template, zona detak jantung, dan jarak GPS tidak menyentuh penyimpanan atau platform, sehingga mudah diuji.
- **Waktu bisa disuntikkan.** `LocalClock` membuat semua logika yang bergantung pada tanggal dan jam dapat diuji dengan waktu palsu.
- **Reaktif.** Repository mengekspos `Flow`, sehingga Beranda, Jadwal, dan Riwayat ikut berubah ketika data berubah. Beranda juga berdetak tiap menit supaya pergantian hari ikut diperbarui.
- **Injeksi dependensi** dengan Koin.

---

## Struktur Proyek

```
ActivaPlan/
├── androidApp/                  # Host Android (MainActivity, Application, manifest, penandatanganan rilis)
├── iosApp/                      # Host iOS (SwiftUI)
├── composeApp/
│   └── src/
│       ├── commonMain/          # Kode bersama + composeResources (data JSON, gambar, font)
│       ├── androidMain/         # Aktual Android (pengingat, GPS, isyarat, izin, DataStore)
│       ├── iosMain/             # Aktual iOS (pengingat, GPS, isyarat, izin, DataStore)
│       ├── commonTest/          # Tes domain, use case, dan repository
│       └── androidHostTest/     # Tes JVM yang membaca file JSON bawaan
└── gradle/libs.versions.toml
```

Isi `commonMain/kotlin/com/activaplan/app/`:

```
├── App.kt                  # Root composable: tema, bahasa, splash, gerbang onboarding, navigasi
├── data/
│   ├── local/              # UserDataStore (DataStore + JSON), DataStoreFactory
│   └── repository/         # ExerciseRepository, CardioRepository, WeeklyPlanRepository, UserDataRepository
├── domain/
│   ├── model/              # Exercise, Routine, ScheduleEntry, SessionLog, Settings, Cardio, WeeklyPlan, Program, ...
│   ├── usecase/            # Beranda, Jelajah, Rutinitas, Jadwal, Sesi, Riwayat, Setelan, Rencana
│   ├── session/            # SessionState: state machine sesi latihan
│   ├── schedule/           # ScheduleConflicts: deteksi bentrok jadwal
│   ├── reminder/           # Reminder, ReminderSynchronizer, izin notifikasi
│   ├── cardio/             # HeartRateZones, PaceGuide
│   ├── tracking/           # Model dan penghitung jarak/pace GPS
│   ├── cue/                # CuePlayer: isyarat bunyi dan getar
│   ├── i18n/               # StringKey, Strings, kamus nama latihan
│   ├── time/               # LocalClock, format jam
│   └── validation/         # Validasi data bawaan dan data pengguna
├── presentation/
│   ├── home, explore, detail, routines, schedule, session, history, settings, onboarding, splash
│   └── navigation/         # ActivaNavHost, rute, TopLevelDestination
├── designsystem/           # Tema, warna terang/gelap, tipografi, komponen (tombol, kartu, sheet, dialog, badge, ...)
└── di/                     # AppModule (Koin)
```

---

## Tech Stack

| Bagian | Teknologi |
|---|---|
| Bahasa | Kotlin 2.4.21 (Multiplatform) |
| UI | Compose Multiplatform 1.12.2, Material 3 1.9.0, font Inter |
| Navigasi | Navigation Compose Multiplatform 2.10 |
| DI | Koin 4.2.2 |
| Async | kotlinx-coroutines 1.11 + StateFlow |
| Penyimpanan | DataStore 1.2 (Preferences) + kotlinx-serialization-json 1.12 |
| Tanggal | kotlinx-datetime 0.8 |
| Pengingat Android | AlarmManager (alarm tepat waktu) + penerima boot |
| Pengingat iOS | UNUserNotificationCenter (`UNCalendarNotificationTrigger`) |
| Lokasi Android | Layanan foreground bertipe lokasi |
| Lokasi iOS | CoreLocation (`CLLocationManager`, aktivitas kebugaran) |
| Splash Android | androidx.core:core-splashscreen 1.0.1 |
| Build | AGP 9.4.1, Gradle version catalog, minSdk 26, targetSdk 36, compileSdk 37; iOS 16+ |

Izin Android yang dideklarasikan: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED` (pengingat); `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` (GPS saat kardio); `WAKE_LOCK` dan `VIBRATE` (layar tetap menyala dan isyarat getar). Tidak ada izin `INTERNET`.

---

## Data Bawaan

Katalog latihan dibundel sebagai JSON di `composeApp/src/commonMain/composeResources/files` dan dibaca saat dibutuhkan. Semuanya divalidasi oleh tes (referensi progresi dan regresi, ID unik, terjemahan lengkap).

| File | Isi |
|---|---|
| `exercises_general.json` | 39 latihan tanpa alat + 10 template harian |
| `exercises_pushup_board.json` | 31 latihan Push-up Board dan Board + Tali + 7 template harian |
| `cardio_workouts.json` | 10 sesi kardio (jalan, jalan-lari, easy run, fartlek, tempo, interval, tanjakan, dan lainnya) |
| `weekly_plans.json` | 12 rencana mingguan |
| `programs.json` | 3 program 4 pekan (Mudah, Sedang, Sulit) |
| `i18n_exercises.json` | Terjemahan English untuk nama, langkah, tips, dan kesalahan umum latihan |

Teks latihan ditulis sendiri untuk ActivaPlan. Ada 70 gambar langkah buatan sendiri (`composeResources/drawable/<id>.webp`) yang dirujuk lewat field `images` tiap latihan; latihan tanpa gambar menampilkan diagram board atau ikon. Font Inter berlisensi SIL OFL 1.1.

---

## Data Pengguna

Tidak memakai database. Data pengguna disimpan di **DataStore Preferences** sebagai JSON, satu kunci per koleksi:

| Kunci | Isi |
|---|---|
| `routines` | Rutinitas buatan pengguna (nama, daftar latihan, set, rep atau durasi, istirahat) |
| `schedule` | Entri jadwal (hari, jam, rutinitas, pengingat) |
| `session_logs` | Riwayat sesi (tanggal, rutinitas, status, set selesai, jarak GPS) |
| `settings` | Tema, bahasa, pengingat, waktu default, level awal, alat, GPS, usia, program aktif, status onboarding |

Hal yang perlu diketahui:

- **Tahan data rusak.** Bila JSON suatu kunci tidak terbaca, aplikasi tetap terbuka dengan nilai kosong atau default, dan teks aslinya disalin ke kunci `<nama>_corrupt` sebelum ditimpa supaya masih bisa dipulihkan.
- **Validasi terhadap katalog.** Entri jadwal atau rutinitas yang merujuk latihan yang sudah tidak ada diabaikan, bukan membuat aplikasi gagal.
- **Kegagalan tulis dilaporkan.** Error penyimpanan dilempar ke ViewModel sehingga UI dapat menampilkan "Gagal menyimpan" dengan tombol Coba lagi.
- **Riwayat opsional.** Bila "Simpan riwayat sesi" dimatikan, log tidak ditulis. Streak tetap menghitung sesi yang baru selesai.
- **Hapus semua data** mengosongkan rutinitas, jadwal, dan riwayat, tanpa menyentuh katalog bawaan.
- **Tidak ada cadangan.** `allowBackup` dimatikan dan belum ada ekspor/impor, jadi menghapus aplikasi berarti menghapus datanya.

---

## Latihan Kekuatan

Latihan dibagi dalam tiga **mode** sesuai alat yang dimiliki pengguna:

| Mode | Keterangan |
|---|---|
| Tanpa Alat | Latihan berat badan |
| Push-up Board | Latihan dengan board berzona warna (biru, merah, kuning, hijau) yang menentukan otot sasaran |
| Board + Tali | Latihan board ditambah tali; sesi diawali cek keselamatan |

Setiap latihan punya **kategori** (Dada, Perut, Lengan, Bahu, Punggung, Kaki, Seluruh tubuh), **level** (Mudah, Sedang, Sulit), nilai awal set/rep atau durasi/istirahat, langkah, tips, kesalahan umum, serta tautan **progresi** (Naik level) dan **regresi** (Turun level).

**Jelajah** menyaring berdasarkan mode, kategori, level, dan kata kunci. Mode awal mengikuti alat yang dipilih di Onboarding. Dari **Detail**, pengguna bisa pindah ke latihan yang lebih mudah atau lebih sulit, atau menambahkannya ke rutinitas yang ada atau yang baru.

---

## Rutinitas dan Jadwal

### Rutinitas

Rutinitas adalah daftar latihan berurutan. Batasnya:

| Aturan | Nilai |
|---|---|
| Jumlah item | 1 sampai 12 |
| Nama | 1 sampai 40 karakter |
| Set | 1 sampai 6 |
| Repetisi | 1 sampai 50 |
| Durasi (latihan waktu) | 5 sampai 180 detik, kelipatan 5 |
| Istirahat | 15 sampai 180 detik |

Satu rutinitas boleh campuran kekuatan dan kardio. **Estimasi menit** dihitung dari set, repetisi atau durasi, dan istirahat. Tab **Rutinitasku** memuat rutinitas pengguna, tab **Template** memuat template harian, rencana mingguan, dan program. Template disalin ke rutinitas milik pengguna sebelum diubah, sehingga bawaannya tidak pernah terubah.

### Jadwal

Jadwal menghubungkan rutinitas ke hari dan jam. Satu entri bisa mencakup beberapa hari sekaligus.

- **Bentrok.** Dua entri di hari yang sama bentrok bila jam mulainya sama, atau selisihnya lebih kecil dari estimasi durasi rutinitas yang lebih awal. Pengguna memilih **Ganti** atau **Tambah** (tetap simpan keduanya).
- **Pengingat per jadwal.** Tiap entri punya saklar pengingat sendiri.
- **Waktu default** dari Setelan hanya mengisi nilai awal saat membuat jadwal baru.

---

## Beranda

Beranda menampilkan satu kartu **latihan hari ini** dengan aturan berikut (`GetTodayWorkoutUseCase`):

- **Jam hanya pengingat.** Jadwal hari ini tetap bisa dimulai sepanjang hari sampai selesai. Jam yang sudah lewat hanya memberi label "Sudah lewat", dan tombol mulai tidak dikunci. Kunci baru berlaku saat hari berganti.
- **Pemilihan.** Bila ada beberapa jadwal hari ini, yang tampil adalah yang berikutnya (jamnya belum lewat) di antara yang belum selesai, atau yang terakhir bila semuanya sudah lewat jamnya.
- **Selesai = ada log COMPLETED hari ini** untuk rutinitas itu. Sesi Sebagian atau Dihentikan tidak dihitung. Rutinitas yang dijadwalkan dua kali sehari butuh dua sesi selesai.
- **Setelah semua jadwal hari ini selesai**, kartu menunjuk jadwal hari terdekat berikutnya (paling jauh hari yang sama pekan depan). Tombolnya **nonaktif** dengan teks "Tersedia hari Selasa" sampai harinya tiba, dan muncul tombol **Lihat Riwayat**.
- **Tanpa jadwal hari ini**, kartu kosong mengajak pengguna menambah jadwal.

Bagian lain di Beranda:

- **Streak.** Tujuh lingkaran Senin sampai Minggu (centang hijau bila selesai, garis oranye untuk hari ini). Streak adalah jumlah hari terjadwal berturut-turut yang selesai, dihitung mundur dari hari ini dan boleh melewati batas pekan. Hari tanpa jadwal tidak memutus, hari terjadwal yang terlewat memutus, dan hari ini yang belum selesai tidak memutus.
- **Program aktif.** Nama program, pekan ke berapa, penanda kemajuan, dan ajakan lanjut ke pekan berikutnya setelah tujuh hari.
- **Rekomendasi template.** Level yang sama atau satu tingkat di atas rutinitas terakhir yang selesai (atau level awal dari Onboarding bila belum ada sesi). Template yang butuh alat yang tidak dimiliki pengguna disembunyikan.

---

## Sesi Latihan

Sesi dijalankan oleh `SessionState`, state machine murni tanpa jam. Waktu masuk lewat selisih waktu nyata, sehingga hasilnya sama setelah rotasi layar atau kembali dari background.

```
Cek keselamatan (hanya bila ada latihan tali)
        │
        ▼
   Latihan ──► Istirahat ──► Latihan ──► ... ──► Selesai
        │
        └── Lewati ──► Sheet Gagal (Coba lagi | Turun level | Lewati latihan)
```

- **Latihan repetisi** memakai ring yang diketuk untuk menghitung repetisi. **Latihan durasi** memakai ring hitung mundur.
- **Selesai Set** menghitung set selesai berapa pun repetisi yang sudah diketuk.
- **Istirahat** bisa ditambah 15 detik atau dilewati. Kartu "Berikutnya" menunjukkan set atau latihan yang menyusul.
- **Jeda** menghentikan timer. Tombol tutup membuka dialog "Hentikan latihan?".
- **Gagal.** Menekan Lewati membuka sheet: ulangi set, turun ke latihan regresi untuk sisa set (rutinitas aslinya tidak berubah), atau lewati latihan itu.
- **Detail gerakan.** Mengetuk kartu petunjuk (atau kartu "Berikutnya" saat istirahat) membuka sheet berisi gambar, set/rep/istirahat, langkah, tips, dan kesalahan umum. Timer dijeda selama sheet terbuka dan dilanjutkan saat ditutup, kecuali sesi memang sedang dijeda.
- **Layar tetap menyala** selama sesi berjalan.
- **Selesai.** Layar "Latihan Selesai!" beranimasi: lingkaran sukses membesar dengan pantulan, centang tergambar bertahap, cincin memancar, konfeti menyebar, lalu judul, statistik, dan tombol muncul bergantian. Animasi dilewati bila pengguna mengurangi gerak di pengaturan sistem. Layar ini menampilkan jumlah latihan, set, menit, jarak dan pace (bila GPS), serta streak.
- **Status log:** `COMPLETED` (semua set selesai), `PARTIAL` (sebagian set), `ABANDONED` (dihentikan). Layar Selesai hanya muncul bila sesi berakhir wajar dan ada set yang selesai. Log disimpan sebelum layar ditutup; bila gagal, pengguna dapat mengulang dan sesi tidak ditutup.

---

## Kardio, GPS, dan Detak Jantung

Sesi kardio adalah rangkaian **segmen** (pemanasan, kerja, pemulihan, pendinginan, stabil) dengan gerakan (jalan, lari santai, lari, lari cepat) dan **usaha 1 sampai 10 (RPE)**.

- **Per segmen.** Kardio dibuka menjadi satu langkah per segmen, sehingga jeda, dialog Hentikan, log, dan streak yang sama dipakai. Waktu habis berpindah sendiri ke segmen berikutnya (tanpa sheet Gagal); "Segmen berikutnya" mempercepat.
- **Isyarat bunyi dan getar.** Tiap pergantian segmen dan saat sesi selesai, supaya pelari tidak perlu menatap layar. Android memakai getar dan nada; iOS memakai getar dan nada sistem (mengikuti sakelar senyap).
- **Zona detak jantung.** Dari usia di Setelan, detak maksimal dihitung dengan rumus Tanaka (208 − 0,7 × usia). Tiap RPE dipetakan ke persen detak maksimal dan zona 1 sampai 5, ditampilkan sebagai perkiraan bpm. Bila usia belum diisi, zona tampil dalam persen. Aplikasi **tidak membaca sensor detak jantung**; RPE dan uji bicara tetap yang utama.
- **Pelacakan GPS (opsional).** Dinyalakan di Setelan dan hanya aktif bila izin lokasi diberikan. Jarak dan pace dihitung di perangkat dan tidak dikirim ke mana pun. Android memakai layanan foreground bertipe lokasi dengan notifikasi; iOS memakai CoreLocation. Saat GPS aktif, layar sesi menampilkan jarak dan pace saat ini, dan layar Selesai menampilkan jarak total dan rata-rata pace.
- **Pace acuan.** Rentang umum per gerakan (jalan 9:00 sampai 12:00 per km, lari santai 7:00 sampai 10:00, lari 6:00 sampai 7:30, lari cepat 5:00 sampai 6:30) sebagai panduan, bukan target pribadi.

---

## Rencana Mingguan dan Program

Dua tingkat template di atas template harian:

- **Rencana mingguan (12).** Tujuh hari yang sekali ketuk membuat beberapa rutinitas sekaligus dan mengisi Jadwal. Tersedia di tiga tingkat (Mudah, Sedang, Sulit) dengan pilihan campuran kekuatan dan kardio, kekuatan murni, ringkas 3 hari ("Hemat Waktu"), serta fokus kardio, inti, atau lari. Sebelum diterapkan, pengguna melihat isi tiap hari; konflik dengan jadwal yang ada ditangani dengan pilihan Ganti atau Tambah.
- **Program bertahap (3).** Empat pekan berurutan per level. Menerapkan satu pekan mencatat pekan aktif dan tanggal mulainya. Beranda menampilkan kemajuannya dan, setelah tujuh hari, mengajak lanjut ke pekan berikutnya.

Aturan penyusunan yang dijaga validator saat tes: otot yang sama tidak dilatih dua hari berturut-turut, lari keras (interval, tempo, tanjakan) tidak jatuh sehari sebelum hari kaki dan paling banyak dua sesi per pekan, dan minimal ada satu hari istirahat.

---

## Pengingat

Pengingat adalah notifikasi lokal mingguan: tiap entri jadwal yang pengingatnya menyala berbunyi pada hari dan jam jadwalnya. `ReminderSynchronizer` menyamakan jadwal notifikasi dengan data setiap kali jadwal, setelan pengingat, atau bahasa berubah.

| Platform | Mekanisme |
|---|---|
| Android | Satu alarm tepat waktu (`AlarmManager`) per pengingat yang dipasang ulang setiap berbunyi. Penerima boot memasang ulang alarm setelah perangkat dihidupkan ulang. |
| iOS | `UNCalendarNotificationTrigger`. Karena iOS membatasi notifikasi tertunda hingga 64, hanya pengingat terdekat yang dijadwalkan dan dibangun ulang saat jadwal berubah. |

- Mematikan "Pengingat latihan" di Setelan membatalkan semua alarm; menyalakannya menjadwal ulang.
- Izin notifikasi punya tiga status (Diizinkan, Ditolak, Belum diatur). Bila ditolak, Jadwal menampilkan banner yang membuka pengaturan sistem, dan status dibaca ulang saat pengguna kembali ke aplikasi.
- Teks notifikasi mengikuti bahasa pilihan pengguna.

---

## Splash, Perkenalan, dan Onboarding

1. **Splash** beranimasi dengan logo vektor, tampil setiap aplikasi dibuka (di Android didahului splash sistem statis).
2. **3 layar Perkenalan** (pager geser) dengan ilustrasi vektor beranimasi, hanya saat pertama kali. Bisa dilewati, dan dapat dilihat lagi dari Setelan.
3. **Onboarding** satu layar, hanya saat pertama kali: level awal, alat yang dimiliki (minimal satu), dan pengingat beserta jamnya. Pilihan ini menyaring rekomendasi di Beranda dan menentukan mode awal Jelajah.

Pengguna lama langsung ke Beranda setelah splash. Mengulang pengantar dari Setelan tidak mengubah setelan apa pun.

---

## Bahasa dan Tema

- **Bahasa:** Indonesia dan English. "Ikuti sistem" memakai bahasa perangkat; bahasa lain di luar Indonesia memakai English. Berlaku langsung tanpa restart untuk teks UI, nama dan langkah latihan, template, dan notifikasi.
- **Tema:** Terang, Gelap, atau Ikuti sistem, juga berlaku langsung. Semua layar punya versi terang dan gelap.
- **Teks UI** didefinisikan di `StringKey`, sebuah enum yang wajib memuat kedua bahasa sekaligus, sehingga terjemahan yang hilang menjadi error kompilasi. Parameter ditulis `{0}`, `{1}`, dan seterusnya.

---

## Alur Navigasi

```
Splash ──► Perkenalan 1-3 ──► Onboarding   (hanya pertama kali)
   │
   ▼
Beranda ─────┬── Jelajah ──► Detail latihan | Detail kardio
(5 tab)      ├── Rutinitas ──► Builder | Pratinjau template | Pratinjau rencana | Pratinjau program
             ├── Jadwal ──► Sheet tambah/ubah
             └── Setelan ──► Riwayat | Ulangi pengantar

Beranda / Rutinitas ──► Sesi latihan ──► Latihan Selesai ──► Riwayat
```

---

## Setup dan Menjalankan

### Prasyarat

- Android Studio dengan plugin Kotlin Multiplatform
- JDK 17+
- Xcode di macOS untuk target iOS

Tidak ada kunci API, akun layanan, atau `local.properties` khusus selain lokasi Android SDK. Cukup clone lalu build.

```bash
# Android: build debug
./gradlew :androidApp:assembleDebug

# iOS: buka proyek di Xcode lalu Run (framework ComposeApp dibangun oleh Gradle)
open iosApp/iosApp.xcodeproj
```

Pratinjau: tiap layar punya `@Preview` terang dan gelap, dan `designsystem/ComponentGallery.kt` memuat semua komponen.

### Build rilis Android

Salin `keystore.properties.example` menjadi `keystore.properties` (diabaikan git) lalu isi keystore Anda. Penandatanganan rilis dibaca dari file itu. R8 untuk rilis saat ini nonaktif.

---

## Tes

```bash
./gradlew :composeApp:testAndroidHostTest
```

Tes JVM mencakup:

- model dan serialisasi, validasi data bawaan, dan pembacaan semua file JSON (termasuk kelengkapan terjemahan);
- penyimpanan pengguna dan repository, termasuk data rusak;
- state machine sesi, use case sesi, dan perhitungan kardio, GPS, serta zona detak jantung;
- jadwal hari ini (jam sebagai pengingat, penguncian setelah selesai, rolling ke hari berikutnya), streak, rekomendasi template, dan estimasi menit;
- deteksi bentrok jadwal, penerapan rencana mingguan dan program, serta pengingat;
- kamus string dan i18n.

Yang belum bisa diuji otomatis dan perlu dicek manual di perangkat: tampilan dan gerak animasi (splash, ilustrasi Perkenalan, layar Selesai), perilaku sheet saat digulir, notifikasi yang benar-benar berbunyi, alur izin notifikasi dan lokasi, serta pelacakan GPS di lapangan.

---

## Keterbatasan yang Diketahui

- Data hanya ada di satu perangkat. Belum ada sinkronisasi maupun ekspor/impor, dan cadangan otomatis sistem dimatikan.
- Aplikasi tidak membaca sensor detak jantung. Zona dan pace hanyalah panduan umum, bukan nasihat medis.
- Status "selesai hari ini" dibaca dari riwayat. Bila "Simpan riwayat sesi" dimatikan, log tidak ditulis, sehingga tombol mulai tidak terkunci setelah latihan dan Lihat Riwayat tidak muncul.
- Riwayat belum bisa diubah atau dihapus per sesi; hanya hapus semua riwayat dari Setelan.
- Animasi dan gerak diuji secara visual saja; kinerja di perangkat kelas bawah perlu diukur dengan build rilis.
- Waktu notifikasi bergantung pada kebijakan baterai sistem; di Android, izin alarm tepat waktu dapat dicabut pengguna.
- Pengujian utama dilakukan di Android; perilaku iOS (pengingat, GPS, isyarat) perlu diverifikasi di perangkat.
