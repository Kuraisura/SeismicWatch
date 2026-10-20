# 🌍 SeismicWatch

**Hazard & Emergency Safety App** · `Android`

Comprehensive Philippine disaster preparedness app with earthquake tracking, family location sharing, SOS, and offline mesh relay.

## Why This Project

The Philippines sits on the Pacific Ring of Fire and inside the world's most active typhoon belt — Filipinos face earthquakes, volcanic activity, and tropical cyclones every single year. Yet the alerting infrastructure remains fragmented: PHIVOLCS and PAGASA push warnings through separate channels, cell broadcasts and radio texts arrive without context, and families scattered across provinces learn about the same disaster from different sources at different times. When a strong quake strikes or a typhoon makes landfall, the first thing people need is not another news feed — it is a single answer to two questions: *am I in danger*, and *where is my family*.

SeismicWatch was built solo by Mark Crysler Baddo for Filipino families who need one trustworthy app that covers the whole arc of a disaster — before, during, and after. It consolidates official hazard data, family location sharing, automated alerts, and offline emergency tools into one application, and runs entirely on the free tiers of Firebase and Supabase so it stays accessible to anyone with a phone, with no subscription or paywalled warnings.

## How It Works

A mother in Cavite is checking her phone when a magnitude 6 earthquake strikes offshore Luzon. Within seconds, the foreground monitoring service receives the event through Supabase Realtime and fires a push alert through Firebase Cloud Messaging. The post-quake assessment screen opens and tells her the exact magnitude and epicenter — and, more importantly, shows a clear **"You Are Safe"** verdict because her saved location falls outside the projected danger zone, while areas nearer the epicenter are flagged red.

She immediately starts a family circle and invites her husband and two children. Once they join, she turns on live location sharing and can see everyone's position on one map as the family coordinates where to meet. An aftershock rattles the house and knocks out cell data. Her SOS still goes out: the app first attempts a cloud write, falls back to SMS the moment it detects there is no validated data connection, and — if even the towers are unreachable — the Nearby Connections mesh relay bounces her beacon to other SeismicWatch phones within roughly 100 meters, hopping device to device until one of them has a path to the outside world.

## Modules

### Authentication & Accounts
Firebase-backed sign-in gate guarding all hazard data, supporting email/password, Google sign-in via the Credential Manager API, and phone OTP, with optional email verification and a dismissible reminder banner.
**Scenario:** A first-time user installs the app during an approaching typhoon, signs in with Google in two taps, and reaches the dashboard without ever creating a password.

### Hazard Dashboard (Home)
The command center tab: summarizes active alerts, earthquakes, and signal warnings at a glance, surfaces the most urgent active storm on a typhoon card, and shows live weather (wind, gusts, rain) for the user's current location — with an offline banner that appears the moment connectivity drops.
**Scenario:** Before leaving for work, a user opens the app and instantly sees that a storm signal is raised over their province, so they know to prepare rather than guessing from scattered social media posts.

### Earthquake Monitoring & Alerting
A persistent foreground service continuously watches incoming seismic events against the user's saved magnitude threshold, delivers FCM push alerts, sounds full-screen alerts, and can automatically notify family by SMS or phone call through the alert dispatcher.
**Scenario:** A user sets their alert threshold to magnitude 5.0 to ignore micro-quakes; when a 6.2 event is recorded near their city, their phone rings automatically with a warning even while the app is closed.

### Live Hazard Map & Quake Detail
A MapLibre (open-source OSM tiles, no API key) map plotting recent Philippine earthquakes as magnitude color-coded markers, with optional NASA GIBS satellite imagery overlays; tapping an event opens a detail view with an epicenter mini-map, severity tiering, and the personal safe/danger-zone verdict.
**Scenario:** A user opens the map after feeling shaking, sees a crimson marker 30 km away, taps it, and reads the epicenter depth and magnitude to decide whether to evacuate.

### Hazard Reports & Archive
A two-feed Reports tab toggling between Earthquakes and Typhoons, with keyword search, severity filters, and newest/oldest ordering — served from an encrypted local cache whenever the network is unavailable.
**Scenario:** After a quake cuts their signal, a user still scrolls the full local archive to confirm which nearby barangays were affected.

### Typhoon Tracking
Cyclone data from GDACS/PAGASA rendered as a projected path and intensity timeline, plus archives of active and past storms filterable by tropical cyclone wind signal level, cached on disk for offline use.
**Scenario:** As a typhoon approaches, a user checks the projected track, sees their town falls under Signal No. 3 within 24 hours, and starts securing the house accordingly.

### Family Circles & Location Sharing
Firestore-based circles with invite/join flows, a family hub with member pins, and a foreground location-sharing service that writes the user's live GPS into their own member document — readable only by circle members under Firestore security rules.
**Scenario:** During an evacuation, a father opens the family hub and sees each of his children's live positions on one map, confirming everyone made it to the assembly point.

### SOS & Panic Triggers
A single process-wide SOS controller armed from four surfaces — the in-app button, a Quick Settings tile, a Glance home-screen widget, and a rapid power-button gesture — that captures GPS, runs a countdown, shows a lock-screen panic UI, and fans the beacon out cloud → SMS in order of what still works.
**Scenario:** Trapped under rubble with a cracked screen, a user pulls down the notification shade and taps the SOS tile over the lock screen; the app captures their coordinates and texts them to every emergency contact.

### Offline Mesh Relay
Google Nearby Connections (BLE + Wi-Fi) peer-to-peer relay with a hand-rolled flood protocol: tiny JSON SOS packets with TTL and hop counts bounce between phones within ~100 m, forwarded by opted-in devices even when their owners are not in an emergency themselves.
**Scenario:** After a major quake takes down cell towers, a stranded hiker's SOS hops across three neighboring phones until one device on the ridge finds signal and delivers it.

### Encrypted Chat & Presence
Lightweight one-to-one Firestore chat between circle members with Messenger-style heads-up banners for incoming messages and honest presence labels ("Active now" only on a real heartbeat) — for coordinating when a full calling app is too heavy.
**Scenario:** Two siblings separated during a blackout exchange messages in-app to arrange a meeting point, seeing each other's live presence so they know the other is still reachable.

### Emergency Contacts & Automated Alerts
A trusted-contact list with one-tap call, SMS, and locate actions, plus configurable alert rules that automatically place calls or send SMS to those contacts when the user is detected inside a danger zone — with test buttons to verify each channel.
**Scenario:** A grandmother with no data plan is auto-texted by the system the moment her saved coordinates fall within a shaking-intensity zone, without anyone having to remember to warn her.

### Safety Tools
Fully offline emergency utilities: a Do-Not-Disturb-breaking alarm siren for severe hazards, a Red Cross-sourced first-aid overlay that ships inside the APK, flashlight strobe signaling, and a reactive connectivity observer that flips the whole app into offline mode the instant the network dies.
**Scenario:** During a blackout with no signal, a user opens the first-aid overlay to follow bleeding-control steps and switches on the strobe to signal rescuers — none of it ever touches the network.

### Widgets & Quick Settings
Jetpack Glance home-screen widgets — a hazard monitor summarizing live threats and a one-tap SOS button — plus the Quick Settings SOS tile, all themed to the user's chosen app palette.
**Scenario:** A user glances at their home screen widget during a typhoon and sees the current alert count without opening any app.

### Settings & Privacy
Notification preferences per hazard type, monitored regions, magnitude thresholds, appearance selection (5 switchable launcher icon themes), safety-tool toggles, help/credits (USGS, PHIVOLCS, PAGASA), and a full account-deletion flow that erases all Firestore data before removing the auth account.
**Scenario:** A user annoyed by flood alerts disables them while keeping earthquake and typhoon notifications, and later deletes their account cleanly when they stop using the app.

### Encrypted Offline Storage
Room + SQLCipher encrypted local database (key held via the Android Keystore-backed key manager) caching earthquakes, typhoons, contacts, and settings — the foundation that makes every other module work without a connection.
**Scenario:** Stranded in a typhoon-hit town with zero bars of signal, the user still sees the last known quake list, storm track, and their emergency contacts.

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
