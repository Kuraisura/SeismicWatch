# 🌍 SeismicWatch

**Hazard & Emergency Safety App** · `Android`

Comprehensive Philippine disaster preparedness app with earthquake tracking, family location sharing, SOS, and offline mesh relay.

## Screenshots

### Authentication gate — users sign in with email/phone, password, Google, or Phone OTP before accessing any hazard data. This is the first screen every user sees when opening the app for the first time or after being logged out.

![Authentication gate — users sign in with email/phone, password, Google, or Phone OTP before accessing any hazard data. This is the first screen every user sees when opening the app for the first time or after being logged out.](screenshots/1.jpg)

### Command center — the home dashboard summarizes threat levels at a glance: how many active alerts, earthquakes, and signal warnings are happening right now. The typhoon card shows the most urgent active storm so users know immediately whether they need to act.

![Command center — the home dashboard summarizes threat levels at a glance: how many active alerts, earthquakes, and signal warnings are happening right now. The typhoon card shows the most urgent active storm so users know immediately whether they need to act.](screenshots/2.jpg)

### Situational awareness map — plots every recent earthquake across the Philippines as color-coded markers so users can visually assess whether nearby areas were affected. The magnitude legend helps non-technical users understand risk at a glance.

![Situational awareness map — plots every recent earthquake across the Philippines as color-coded markers so users can visually assess whether nearby areas were affected. The magnitude legend helps non-technical users understand risk at a glance.](screenshots/3.jpg)

### Typhoon tracking — shows the projected path and intensity of an approaching cyclone so users can estimate whether their area will be hit and when to prepare or evacuate.

![Typhoon tracking — shows the projected path and intensity of an approaching cyclone so users can estimate whether their area will be hit and when to prepare or evacuate.](screenshots/4.jpg)

### Post-quake assessment — after an earthquake is detected, this screen tells the user the exact magnitude, epicenter, and most importantly whether THEY are in the danger zone ("You Are Safe" banner), reducing panic.

![Post-quake assessment — after an earthquake is detected, this screen tells the user the exact magnitude, epicenter, and most importantly whether THEY are in the danger zone ("You Are Safe" banner), reducing panic.](screenshots/5.jpg)

### Earthquake archive — filters and lists all seismic events by severity so users can review past quakes in their region for research, insurance, or personal awareness.

![Earthquake archive — filters and lists all seismic events by severity so users can review past quakes in their region for research, insurance, or personal awareness.](screenshots/6.jpg)

### Typhoon archive — tracks active and past storms with signal level filters so users can check historical cyclone data for their province.

![Typhoon archive — tracks active and past storms with signal level filters so users can check historical cyclone data for their province.](screenshots/7.jpg)

### Emergency contacts — quick-access list of trusted people with one-tap call, SMS, and locate buttons. During a disaster, users don't have time to scroll through their phone contacts.

![Emergency contacts — quick-access list of trusted people with one-tap call, SMS, and locate buttons. During a disaster, users don't have time to scroll through their phone contacts.](screenshots/8.jpg)

### Live location sharing — lets users share their real-time GPS position with family members inside a "circle," similar to Life360. Critical during evacuations when families get separated.

![Live location sharing — lets users share their real-time GPS position with family members inside a "circle," similar to Life360. Critical during evacuations when families get separated.](screenshots/9.jpg)

### Automated alert rules — configures automatic SMS and phone calls to emergency contacts when the user is detected in a danger zone. Removes the need to manually alert family during a panic.

![Automated alert rules — configures automatic SMS and phone calls to emergency contacts when the user is detected in a danger zone. Removes the need to manually alert family during a panic.](screenshots/10.jpg)

### Notification preferences — lets users choose which hazard types (earthquake, typhoon, flood) trigger push notifications, so they only get warned about threats that matter to them.

![Notification preferences — lets users choose which hazard types (earthquake, typhoon, flood) trigger push notifications, so they only get warned about threats that matter to them.](screenshots/11.jpg)

### Profile & thresholds — stores personal details used for location matching and lets users set the minimum earthquake magnitude that triggers an alert (avoiding noise from micro-quakes).

![Profile & thresholds — stores personal details used for location matching and lets users set the minimum earthquake magnitude that triggers an alert (avoiding noise from micro-quakes).](screenshots/12.jpg)

### Safety tools configuration — enables hardware features like flashlight strobe for signaling rescue teams, SMS fallback when mobile data is down, mesh relay for off-grid communication, and panic button behavior.

![Safety tools configuration — enables hardware features like flashlight strobe for signaling rescue teams, SMS fallback when mobile data is down, mesh relay for off-grid communication, and panic button behavior.](screenshots/13.jpg)

### Transparency & support — provides access to help documentation, data source credits (USGS, PHIVOLCS, PAGASA), privacy policy, and sign-out. Builds trust by showing where the data comes from.

![Transparency & support — provides access to help documentation, data source credits (USGS, PHIVOLCS, PAGASA), privacy policy, and sign-out. Builds trust by showing where the data comes from.](screenshots/14.jpg)

## Features

- Real-time earthquake tracking from USGS/PHIVOLCS via Supabase Realtime WebSocket
- Typhoon/cyclone tracking from GDACS/PAGASA
- Live weather for 40+ Philippine cities via Open-Meteo
- Family location sharing (Life360-style circles) with foreground/background GPS
- Emergency SOS from app / Quick Settings tile / widget / power-button panic gesture
- Offline P2P mesh relay via Nearby Connections
- In-app encrypted chat
- Emergency contacts with auto-SMS/call
- First aid overlay and alarm siren
- 5 switchable launcher icon themes

## Tech Stack

- Kotlin
- Jetpack Compose
- Room + SQLCipher
- Firebase
- Supabase
- MapLibre
- Nearby Connections
- WorkManager
- Glance
- Coil

## About

Single-Activity Compose-only UI with MVVM + Repository pattern (13 repositories). Dual backend: Firebase (Auth, Firestore, FCM, App Check) for social/location layer, Supabase (PostgREST, Realtime) for hazard data — all on free tiers. Room + SQLCipher for encrypted local storage. MapLibre for open-source OSM map tiles (no API key). Foreground services for persistent earthquake monitoring, location sharing, and mesh relay. Nearby Connections (BLE + Wi-Fi) for offline P2P SOS relay. Glance for home-screen widgets. Supabase Edge Functions (pg_cron) scrape PHIVOLCS + PAGASA data into Supabase Postgres.

---

Built by [Mark Crysler Baddo](https://github.com/Kuraisura) · [Portfolio](https://kuraisler.xyz)
