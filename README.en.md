<!-- Language: English -->
<div align="center">
  <img src="banner.png" width="760" alt="WinNext — Windows Emulator"/>
</div>

# 🪟 WinNext — Run Windows programs and games on Android

**WinNext** is a Windows 11-inspired desktop environment for Android that runs compatible Windows programs through a real compatibility engine (Wine/Proton).

Designed and developed by **Kazem Rafiei** — Afghanistan, Ghazni province, Jaghori district
طراحی و توسعه: کاظم رفیعی

Other languages: [دری (README.md)](README.md) · [فارسی (README.fa.md)](README.fa.md)

---

## ⚙️ Execution engine (real status)

WinNext is derived from the open-source Winlator Bionic project and ships a **real execution engine**:

| Component | Role |
|---|---|
| **Proton 9.0 (Wine)** | Windows compatibility layer (downloaded at build time) |
| **Box64 / FEXCore** | x86/x86_64 → ARM translation |
| **DXVK / VKD3D / Vegas** | DirectX → Vulkan |
| **Turnip / Zink / VirGL** | Graphics drivers |

**Honest limitations:**
- Running every Windows program cannot be guaranteed; compatibility depends on the device CPU (arm64), GPU, Android version and the program itself.
- Desktop Microsoft Office may not run or may be unstable. Official alternatives: Microsoft 365 Android or web apps.
- The Windows 11 look does not imply actual Microsoft Windows; WinNext is not a Microsoft product.

## 🚀 Quick start — no technical setup

1. Open the app; the WinNext desktop appears.
2. On first run, the default container **"WinNext Default"** is created automatically (progress is shown).
3. Tap the blue **"WinNext Apps"** icon — the real Windows environment starts directly.
4. Launch installed programs from the environment's Start Menu.

- The default container is general-purpose; you never have to create a container manually.
- Files, installed programs and settings survive exits, phone restarts and app updates.
- The blue icon and the Containers list Play button share **one execution service**; repeated taps never create duplicate containers or sessions.
- Advanced technical settings (Wine, graphics driver, DX wrapper) remain for power users but are not required.

## 🖥️ Desktop and personalization

- Desktop, taskbar and Start Menu in a Windows 11-inspired style.
- **Personalization** screen: wallpaper from built-in images or your gallery, light/dark desktop theme, interface accent color.
- Wallpapers are applied to the **real Windows environment** (Wine desktop) and persist across restarts.
- File Manager ("This PC") browsing Android storage and the environment's Drive C.

## 📦 Installing Windows programs

- Install `.exe` / `.msi` files inside the running environment; they are never handed to Android directly.
- Shortcuts appear in the environment's real Start Menu and in the Shortcuts screen.

## 🌍 Languages

- Default: **Dari, Afghanistan (fa-AF)** — right-to-left interface.
- Also: Persian, Pashto, English, German, Swiss German, French, Italian.
- Change in **Settings → App language**; the choice is persisted.

## 🔒 Optional app lock

- Optional PIN (4–8 digits) for the WinNext app itself, stored salted+hashed (SHA-256), never in plain text; repeated attempts are rate-limited.
- Independent of any Windows-side account; toggling it never deletes container data.

## 🛠️ Troubleshooting

| Problem | Solution |
|---|---|
| "Engine not installed" | Tap "Start setup" and grant file access. |
| Default environment creation failed | Check free storage and retry. |
| Environment damaged | WinNext repairs it with a backup; if unrecoverable, a clear message is shown. |
| Program does not start | Check engine logs in the engine settings. |

## 📲 Requirements

Android 8.0+ (arm64-v8a), Vulkan; 4 GB RAM recommended, plus storage for the engine and programs.

## 🐞 Reporting issues

Report problems at the [GitHub issue tracker](https://github.com/kazemwafa2/WinNext/issues).

## 📄 License and credits

- Built on [Winlator](https://github.com/brunodev85/winlator) (brunodev85), Winlator Bionic (MishaMixXx) and Star-Compose.
- Wine/Proton (LGPL 2.1), Box64 (MIT), FEX-Emu (MIT), Mesa/Turnip (MIT) — external authors' names and licenses are preserved.
- Project license: [LICENSE](LICENSE). Bundled wallpapers are original artwork created for WinNext.
