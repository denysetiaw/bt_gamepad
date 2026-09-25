# BT Gamepad – HP Android jadi gamepad Bluetooth untuk laptop

Aplikasi ini membuat HP tampil ke laptop sebagai **gamepad Bluetooth HID asli**, sama seperti
controller fisik. Laptop **tidak perlu** aplikasi server, driver, atau program tambahan.

## Fitur
| Kontrol | Keterangan |
|---|---|
| 2 analog stick | Analog penuh 360°, dead zone, multi-touch |
| D-Pad 8 arah | Bisa digeser untuk arah diagonal |
| A / B / X / Y | Tombol utama |
| LB / RB | Tombol bahu |
| LT / RT | Trigger (dikirim sebagai sumbu analog Rx/Ry sekaligus tombol digital) |
| L3 / R3 | Klik stick |
| Select / Start / Home | Tombol menu |
| Mode Tilt | Miringkan HP seperti setir → stick kiri kiri/kanan (untuk game balap) |
| Getar | HP bergetar setiap kali tombol ditekan |

Semua tombol bisa ditekan bersamaan (multi-touch), report dikirim hingga ~125 kali/detik.

## Syarat
- HP **Android 9 (Pie) atau lebih baru** yang mendukung profil Bluetooth *HID Device*.
  Hampir semua HP modern mendukungnya, tapi beberapa merk/ROM mematikannya. Kalau statusnya
  "HP ini tidak mendukung Bluetooth HID Device", HP tersebut memang tidak bisa dipakai.
- Laptop dengan Bluetooth (Windows 10/11, Linux, atau macOS).

## Build & install
1. Buka folder `BtGamepad` di **Android Studio** (Ladybug atau lebih baru).
2. Tunggu Gradle sync selesai (akan mengunduh Gradle 8.9 & Android Gradle Plugin 8.7.3).
3. Colokkan HP (USB debugging aktif) → tekan **Run ▶**.
   Atau: **Build > Build APK(s)**, lalu salin file `app/build/outputs/apk/debug/app-debug.apk` ke HP.

## Cara menghubungkan ke laptop
1. Buka aplikasi di HP, izinkan akses Bluetooth ("Perangkat sekitar").
2. **Penting:** kalau HP & laptop *pernah* di-pair sebelumnya, hapus pairing tersebut di
   **kedua sisi** dulu. Pairing lama tidak memuat info "gamepad" sehingga laptop tidak mengenalinya.
3. Tekan **Mode Pairing** di aplikasi (HP terlihat selama 2 menit).
4. Di laptop:
   - **Windows:** Settings > Bluetooth & devices > Add device > Bluetooth → pilih nama HP.
   - **Linux:** `bluetoothctl` → `scan on` → `pair <MAC>` → `connect <MAC>` (atau lewat menu Bluetooth).
   - **macOS:** System Settings > Bluetooth → Connect.
5. Status di aplikasi berubah menjadi 🟢 **Terhubung**. Selanjutnya cukup tekan
   **Hubungkan** → pilih laptop untuk menyambung ulang.

## Cek apakah gamepad terbaca
- Windows: tekan Win+R → `joy.cpl` → pilih "BT Gamepad" → **Properties**, gerakkan stick & tekan tombol.
- Semua OS: buka https://hardwaretester.com/gamepad di browser.

## Catatan soal game di Windows (XInput vs DirectInput)
Gamepad ini adalah gamepad HID standar (**DirectInput**), sama seperti banyak controller generik.
- Game lama/emulator & game yang mendukung DirectInput → langsung jalan.
- Game modern yang hanya mengenal controller Xbox (**XInput**):
  - Jalankan game lewat **Steam** dan aktifkan *Steam Input* (Settings > Controller) – Steam
    otomatis memetakan gamepad ini menjadi controller Xbox; atau
  - Pakai **x360ce** / **HidHide + ViGEm** untuk mengemulasikan controller Xbox.

## Pemetaan tombol (HID)
| Tombol HID | Kontrol |  | Tombol HID | Kontrol |
|---|---|---|---|---|
| 1 | A | | 9 | L3 |
| 2 | B | | 10 | R3 |
| 3 | X | | 11 | Home |
| 4 | Y | | 12 | LT (digital) |
| 5 | LB | | 13 | RT (digital) |
| 6 | RB | | Hat | D-Pad |
| 7 | Select | | X / Y | Stick kiri |
| 8 | Start | | Z / Rz | Stick kanan |
| | | | Rx / Ry | LT / RT (analog) |

## Struktur kode
```
app/src/main/java/com/btgamepad/
├── GamepadActivity.kt      – layar gamepad, izin, pairing, mode tilt
├── hid/HidDescriptor.kt    – HID report descriptor (identitas "gamepad" ke laptop)
├── hid/GamepadState.kt     – kondisi tombol/stick → report 9 byte
├── hid/HidGamepad.kt       – BluetoothHidDevice: registrasi, koneksi, kirim report
└── ui/                     – PadButton, JoystickView, DPadView, Haptics
app/src/main/res/layout/activity_gamepad.xml – tata letak kontrol (landscape)
```

## Keterbatasan
- **Getaran dari game (rumble/force feedback)** tidak didukung: laptop tidak mengirim perintah
  rumble ke gamepad HID generik. Getaran di HP hanya umpan balik saat tombol disentuh.
- Latensi Bluetooth HP biasanya sedikit lebih tinggi dari controller fisik (±10–30 ms).
- Aplikasi harus tetap terbuka di layar agar koneksi aktif; layar dijaga tetap menyala otomatis.
