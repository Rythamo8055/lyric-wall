# Privacy Policy for LYRIC WALL

**Effective Date:** September 24, 2026  
**Last Updated:** September 24, 2026

Welcome to **LYRIC WALL** ("we," "our," or "us"). We are committed to protecting your privacy and ensuring transparency regarding how our mobile application operates.

This Privacy Policy describes how LYRIC WALL handles information when you install and use our Android app.

---

### 1. Information Handled by the Application

LYRIC WALL is designed as a standalone, privacy-centric personalization app. We do not collect, harvest, store, sell, or transmit any personally identifiable information (PII).

#### A. Location Information (Approximate and Precise)
* **Permissions:** `android.permission.ACCESS_COARSE_LOCATION` and `android.permission.ACCESS_FINE_LOCATION`
* **Purpose:** If granted by the user, location coordinates are queried locally on your device to determine your current city, calculate local astronomical lunar terminators (moon phase angle), and request ambient weather conditions (temperature, rain, cloud cover).
* **Handling:** Location data is processed ephemerally in-memory and transmitted strictly over secure HTTPS to open weather data providers. We do not log, record, or track your physical location or location history. If you decline location access, the app continues to operate using fallback coordinates or manually selected cities.

#### B. Battery Status
* **Purpose:** The app registers an Android system receiver (`Intent.ACTION_BATTERY_CHANGED`) solely to display the battery percentage gauge on your retro wallpaper HUD.
* **Handling:** Battery data is processed strictly in local device memory and is never recorded or exported off the device.

#### C. Screen Unlock and Productivity Counter
* **Purpose:** The app counts screen unlock events locally to provide an optional daily unlock counter on your retro HUD.
* **Handling:** This counter is saved exclusively inside your device's private local storage (`SharedPreferences`) and automatically resets at midnight. It never leaves your device.

---

### 2. Third-Party Services and APIs

To provide dynamic weather information, LYRIC WALL interacts with:
* **Open-Meteo API**: Queries current weather forecasts using coordinates. No user accounts, advertising IDs, or personal identifiers are passed to Open-Meteo.

LYRIC WALL contains:
* **NO** advertising SDKs or networks.
* **NO** user tracking or analytics SDKs (e.g. Firebase Analytics, AppsFlyer, etc.).
* **NO** background location tracking when the app/wallpaper is not active.

---

### 3. Data Retention & Deletion

We do not maintain any user databases, cloud accounts, or remote servers. All app settings and counters reside exclusively within local app sandbox storage on your device. Clearing app storage or uninstalling the app permanently removes all associated data immediately.

---

### 4. Children’s Privacy

LYRIC WALL does not target children under the age of 13, nor does it knowingly collect any personal information from children. The content of the app is rated PEGI 3 / Everyone.

---

### 5. Contact Information

If you have questions, feedback, or inquiries regarding this Privacy Policy, please contact:

* **App:** LYRIC WALL
* **Contact Email:** support@lyricwall.app *(replace with your email)*
