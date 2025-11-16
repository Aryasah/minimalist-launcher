# 🎯 Minimalist Launcher (Android · Jetpack Compose)

A **distraction-free**, **super-clean**, and beautifully minimal Android launcher designed for focus, calmness, and everyday usability.

Built entirely using **Kotlin + Jetpack Compose**, with modern architecture and thoughtful interactions.

---

## 🌟 Features

### 🧘 **Focus Mode**

* Beautiful **breathing animation** (Lottie)
* Smooth **progress ring**
* **Timer presets** with animated expand/collapse
* Optional **calm background music (BGM)**
* **DND toggle** with permission flow
* Clean minimal layout with subtle depth and motion

### 🎨 **Personalization**

* **Dynamic font loading** (bundle fonts, SAF-import, or package fonts)
* **Global font scaling** with live preview + apply/reset
* **Monochrome icon theme** with optional icon packs
  (Lawnicons, Arcticons, etc.)
* **Adaptive dark theme**

### 📱 **Home Screen**

* Minimal vertical list of your chosen apps
* **Radial floating menu** for quick actions:

  * Focus mode
  * Edit home apps
  * App drawer
  * Settings
* Smooth gesture handling + clean UX

### 🔦 **Shake to Toggle Flashlight**

* Motorola-style shake detection
* Uses accelerometer + torch APIs
* Optimized for low power (SENSOR_DELAY_NORMAL)
* Torch state monitored using **CameraManager.TorchCallback**

---

## 🎥 Demo Video
https://github.com/user-attachments/assets/a880e104-c2fd-43f8-af8f-26f28631bd5d

**👉 *Short demo showcasing Focus Mode, font scaling, radial menu, and home experience.***

---

## 🧩 Tech Stack

| Area         | Tools                                          |
| ------------ | ---------------------------------------------- |
| UI           | Jetpack Compose, Material 3                    |
| Architecture | ViewModel, Coroutines                          |
| Storage      | DataStore Preferences                          |
| Animation    | Lottie Compose, Compose animations             |
| Sensors      | Accelerometer (shake detection)                |
| System       | Camera2 API, Notification Policy Manager (DND) |
| Icons        | Custom vector + dynamic monochrome processing  |

---

## ⚙️ Installation (Developer Setup)

1. Clone the repository

   ```sh
   git clone https://github.com/Aryasah/minimalist-launcher
   ```

2. Open in **Android Studio Flamingo+**

3. Run on device — **no special permissions** except:

   * Camera (for flashlight toggle)
   * DND access (only if user enables DND mode in Focus Screen)

---

## 🛠 Customization & Extensibility

This launcher is designed to be easy to extend:

* Add more **Focus presets**
* Add themes and wallpapers
* Add **widgets support**
* Add **gestures** (double-tap, swipe actions)
* Add **AI-powered summaries or suggestions** (future upgrade)

---

## ❤️ Made With

```
Made with love, caffeine, and Jetpack Compose —
by Arya Sah
```

---

## 📬 Connect With Me

If you liked this project, feel free to star ⭐ it and connect with me!

---
