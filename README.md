# Absen Relawan SPPG Ciparay 2

Aplikasi Android (WebView) untuk mengakses portal SIPGN BGN: `https://sipgn.bgn.go.id/public`

- Package: `id.sppg.ciparay2.absen`
- minSdk 24 (Android 7.0) — targetSdk 34
- Bahasa: Kotlin, Gradle Kotlin DSL

## Fitur

| Fitur | Keterangan |
|---|---|
| Splash screen | Logo + warna hijau, muncul ±0,5 detik saat aplikasi dibuka |
| Tombol back | Kembali ke halaman sebelumnya; tekan 2x di halaman awal untuk keluar |
| Halaman offline | Muncul otomatis saat tidak ada internet, ada tombol "Coba lagi" |
| Pull-to-refresh | Tarik layar ke bawah untuk muat ulang |
| Izin lokasi | Diminta saat portal meminta geolocation (absen geotag) |
| Izin kamera | Diminta saat portal membuka kamera (selfie absen) |
| Upload file | Pilih dari galeri atau ambil foto langsung dari kamera |
| Link eksternal | Link ke domain lain, `tel:`, `mailto:`, WhatsApp dibuka di aplikasi terkait |

## Cara build APK

### Opsi A — Android Studio (paling mudah)

1. Install [Android Studio](https://developer.android.com/studio).
2. **File → Open** → pilih folder `AbsenRelawanSPPG`.
3. Tunggu Gradle sync selesai (otomatis download SDK yang dibutuhkan).
4. **Build → Build Bundle(s)/APK(s) → Build APK(s)**.
5. APK ada di `app/build/outputs/apk/debug/app-debug.apk`.

### Opsi B — GitHub Actions (tanpa install apa-apa)

1. Buat repository baru di GitHub, upload seluruh folder ini.
2. Buka tab **Actions** → jalankan workflow **Build APK** (atau langsung otomatis setelah push ke `main`).
3. Setelah selesai, download APK dari bagian **Artifacts**.

### Install ke HP

Kirim file APK ke HP, buka, lalu izinkan **Install dari sumber tidak dikenal** saat diminta.

## Build versi release (untuk Play Store)

APK debug tidak bisa diupload ke Play Store. Untuk release:

1. Buat keystore:
   ```
   keytool -genkey -v -keystore absen.jks -keyalg RSA -keysize 2048 -validity 10000 -alias absen
   ```
2. Di Android Studio: **Build → Generate Signed Bundle/APK → Android App Bundle (.aab)**, pilih keystore tadi.
3. Upload `.aab` ke Google Play Console (biaya pendaftaran developer USD 25, sekali bayar).

**Simpan keystore dan passwordnya.** Kalau hilang, aplikasi tidak bisa diupdate lagi di Play Store.

## Yang perlu diganti sebelum publikasi

| Item | File | Sekarang |
|---|---|---|
| Nama aplikasi | `app/src/main/res/values/strings.xml` → `app_name` | Absen Relawan SPPG Ciparay 2 |
| URL awal | `app/src/main/res/values/strings.xml` → `start_url` | https://sipgn.bgn.go.id/public |
| Package name | `app/build.gradle.kts` → `applicationId` + `namespace`, dan folder `java/id/sppg/ciparay2/absen` | id.sppg.ciparay2.absen |
| Warna tema | `app/src/main/res/values/colors.xml` → `brand` | #1B7F4F |
| Icon | `app/src/main/res/drawable/ic_launcher_foreground.xml` | vector sederhana (checklist) |

Icon sebaiknya diganti pakai **Image Asset Studio** di Android Studio (klik kanan folder `res` → New → Image Asset) supaya semua ukuran ter-generate rapi.

## Catatan penting

- **Domain dibatasi.** Navigasi di dalam aplikasi hanya diizinkan untuk host `sipgn.bgn.go.id`. Kalau portal melakukan login lewat domain lain (misalnya SSO), tambahkan host tersebut di `MainActivity.kt` pada fungsi `shouldOverrideUrlLoading`.
- **Play Store.** `sipgn.bgn.go.id` adalah portal milik Badan Gizi Nasional. Aplikasi yang hanya membungkus website pihak lain biasanya ditolak Play Store (kebijakan Spam & Minimum Functionality / Impersonation) tanpa izin tertulis dari pemilik situs. Untuk dibagikan internal ke relawan lewat file APK, tidak ada masalah.
- **Izin kamera** diminta saat pertama kali portal memanggil kamera. Kalau opsi "ambil foto" belum muncul di dialog upload, izinkan kamera dulu lewat Settings → Apps → Absen Relawan → Permissions.
