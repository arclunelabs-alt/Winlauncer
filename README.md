<div align="center">

# Win11 Launcher

**Launcher Android bertema Windows 11, rasa desktop.**

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)
[![Build APK](https://github.com/arclunelabs-alt/Winlauncer/actions/workflows/main.yml/badge.svg)](https://github.com/arclunelabs-alt/Winlauncer/actions)
[![Release](https://img.shields.io/github/v/release/arclunelabs-alt/Winlauncer)](https://github.com/arclunelabs-alt/Winlauncer/releases)

</div>

Win11 Launcher mengubah tampilan HP Android menjadi lingkungan desktop bergaya Windows 11: taskbar, menu Start, jendela yang bisa digeser dan di-snap, desktop virtual, dan aplikasi mini bawaan. Ditulis dengan Kotlin murni di atas framework View Android, tanpa AndroidX dan tanpa layout XML.

> Proyek hobi dan tidak berafiliasi dengan Microsoft. "Windows" adalah merek dagang Microsoft.

## Unduh

Ambil APK terbaru di halaman **[Releases](https://github.com/arclunelabs-alt/Winlauncer/releases)**.

> APK yang dirilis adalah build *debug* (belum untuk Play Store). Kalau pembaruan gagal terpasang di atas versi lama, hapus dulu versi lama lalu pasang yang baru.

## Instalasi

1. Unduh `Win11Launcher-vX.Y.apk` dari Releases.
2. Buka file-nya. Kalau diminta, izinkan **Instal aplikasi tidak dikenal** untuk browser/pengelola file yang kamu pakai.
3. Kalau muncul peringatan Play Protect, pilih **Tetap instal**.
4. Tekan tombol Home, pilih **Win11 Launcher**, lalu **Selalu** (jadikan peluncur utama).
5. Putar HP ke landscape.

Butuh Android 8.0 (API 26) atau lebih baru.

## Fitur

**Tampilan Windows 11**
- Taskbar (ikon di tengah atau di kiri), menu Start, pusat notifikasi, Quick Settings, dan panel Widget.
- Efek kaca Mica/acrylic dari wallpaper yang diburamkan, animasi halus, dan ikon bergaya Fluent.
- Enam wallpaper bawaan atau foto dari galeri, mode gelap/terang, warna aksen, dan skala UI yang bisa diatur.

**Jendela dan multitasking**
- Jendela mengambang: geser, ubah ukuran, minimalkan, maksimalkan (ketuk dua kali title bar).
- Snap ke kiri/kanan/layar penuh, Tampilan Tugas, dan hingga 4 **desktop virtual**.

**Aplikasi bawaan**
- File Explorer, Browser mini (tab dan address bar), Foto (grid, zoom, slideshow), Media Player (musik dan video), Paint, Pengelola Tugas, Kalkulator, Notepad, dan Jam.

**Sistem**
- Layar kunci dengan jam besar dan PIN opsional (disimpan sebagai hash), animasi boot, dan suara sistem.
- Riwayat clipboard, tangkapan layar, banner notifikasi, dan mode Fokus.
- Widget cuaca dan berita (Open-Meteo dan BBC Indonesia), cuaca juga tampil di taskbar.
- Pencarian menyeluruh: aplikasi, pengaturan, kontak, dan file.
- Folder dan grup di Start, ikon desktop yang bisa digeser.

## Pintasan keyboard dan mouse

| Pintasan | Fungsi |
| --- | --- |
| Win | Buka/tutup Start |
| Win + D | Tampilkan desktop |
| Win + E | File Explorer |
| Win + I | Pengaturan |
| Win + L | Kunci layar |
| Win + S | Pencarian |
| Win + Shift + S / Print Screen | Tangkapan layar |
| Win + V | Riwayat clipboard |
| Win + A / N / W | Quick Settings / Notifikasi / Widget |
| Win + B | Browser |
| Win + Tab | Tampilan Tugas |
| Win + ← → ↑ ↓ | Snap, maksimalkan, atau minimalkan jendela |
| Ctrl + Win + D | Desktop virtual baru |
| Ctrl + Win + ← / → | Pindah desktop virtual |
| Ctrl + Win + F4 | Tutup desktop virtual |
| Alt + Tab | Ganti jendela |
| Alt + F4 | Tutup jendela |
| Ctrl + Shift + Esc | Pengelola Tugas |
| Esc | Tutup panel |
| Klik kanan mouse | Sama dengan tekan lama (menu konteks) |

Sebagian HP mencegat kombinasi Win dan Alt+Tab di tingkat sistem, jadi tidak semuanya tentu bekerja.

## Izin

Izin diminta saat fitur terkait dipakai.

| Izin | Dipakai untuk |
| --- | --- |
| Akses semua file | File Explorer, Foto, Media Player, pencarian file |
| Kontak | Pencarian kontak |
| Akses notifikasi (diaktifkan manual) | Pusat notifikasi dan banner |
| Internet | Browser, cuaca, berita |
| Tulis pengaturan sistem | Kecerahan di Quick Settings |

## Catatan dan keterbatasan

- Layar kunci launcher adalah fitur tampilan, **bukan pengganti** kunci layar Android. Aplikasi lain tetap bisa dibuka lewat recents atau panel notifikasi.
- Lupa PIN? Tekan **Lupa PIN?** di layar PIN, lalu verifikasi dengan kunci layar Android.
- Android 10 ke atas hanya mengizinkan membaca clipboard saat launcher tampil di layar.
- Tangkapan layar mungkin tidak menyertakan isi Browser (WebView).

## Build dari sumber

Butuh JDK 17 dan Gradle 8.9 (atau gunakan wrapper).

```bash
git clone https://github.com/arclunelabs-alt/Winlauncer.git
cd Winlauncer
./gradlew assembleDebug
# hasil: app/build/outputs/apk/debug/app-debug.apk
```

Build juga berjalan otomatis di GitHub Actions pada tiap push (workflow **Build APK**, hasilnya ada di bagian Artifacts).

## Struktur proyek

```
app/src/main/java/com/arclune/winlauncher/
  MainActivity.kt    Aktivitas utama, panel, pintasan, layar kunci
  Taskbar.kt         Taskbar dan baki sistem
  StartMenu.kt       Menu Start, pencarian, folder
  Windows.kt         Sistem jendela, snap, desktop virtual
  Menus.kt           Menu konteks dan Tampilan Tugas
  Panels.kt          Quick Settings, notifikasi, widget
  DesktopView.kt     Ikon desktop
  AppRepo.kt         Daftar aplikasi, pin, folder
  Acrylic.kt         Efek kaca/blur
  LockScreen.kt      Layar kunci, PIN, animasi boot
  Info.kt, Search.kt Cuaca, berita, pencarian
  FileExplorer.kt, WebBrowser.kt, Photos.kt, Media.kt,
  PaintApp.kt, TaskManager.kt, MiniApps.kt    Aplikasi bawaan
```

## Rencana

- Snap Layouts, widget desktop, dan Sticky Notes
- Menyimpan posisi jendela antar sesi
- Pengurangan pemakaian RAM saat banyak jendela terbuka

Ide dan laporan bug sangat dipersilakan lewat **Issues**.

## Lisensi

Belum ditentukan. Tambahkan file `LICENSE` (misalnya MIT) agar orang lain tahu cara memakai kodenya.
