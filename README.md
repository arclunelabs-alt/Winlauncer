# Win11 Launcher untuk Android (Landscape) — v1.3

Launcher bertema Windows 11 untuk HP Android (mode landscape, rasa desktop).

## Baru di v1.3
- **Lupa PIN?** di layar kunci: verifikasi dengan kunci layar Android lalu PIN launcher dihapus.
- **Tangkapan layar** (Win+Shift+S, Print Screen, menu tray/desktop) ke Pictures/Win11Launcher.
- **Hapus semua** notifikasi di panel notifikasi.
- Ketuk dua kali title bar jendela untuk maksimalkan/pulihkan.

## Baru di v1.2
- **Aplikasi bawaan:** Browser mini (tab, address bar), Pengelola Tugas, Paint, Foto (grid, zoom, slideshow), Media Player (musik & video), plus File Explorer, Kalkulator, Notepad, Jam.
- **Layar kunci** dengan jam besar, PIN opsional (disimpan sebagai hash), dan **animasi boot**; **suara sistem** (startup, buka kunci, notifikasi).
- **Desktop virtual** (sampai 4) di Tampilan Tugas; pindahkan jendela antar-desktop.
- **Riwayat clipboard** (Win+V) dengan sematan.
- **Widget cuaca & berita** (Open-Meteo + BBC Indonesia), cuaca juga tampil di taskbar.
- **Pencarian lengkap:** aplikasi, pengaturan, kontak, dan file.
- **Folder & grup di Start**, geser urutan ikon.
- **Keyboard & mouse:** Win (Start), Win+D/E/I/L/S/V/A/N/W/B/Tab, Win+panah (snap), Ctrl+Win+D/←/→/F4 (desktop virtual), Alt+Tab, Alt+F4, Ctrl+Shift+Esc; klik kanan mouse; tooltip.
- **Banner notifikasi** dan mode Fokus.

## Build
Lewat GitHub Actions (lihat `.github/workflows/build.yml`) atau `./gradlew assembleDebug`.

## Izin yang mungkin diminta
Akses semua file (File Explorer, Foto, Media, pencarian file), kontak (pencarian), akses notifikasi (banner & pusat notifikasi), internet (browser, cuaca, berita).
