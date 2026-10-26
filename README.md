# SeismicWatch

An Android app that brings Philippine hazard updates, family location sharing, and emergency tools into one place.

**Android** · **In Development** · 2026

[Portfolio](https://kuraisler.xyz)

## Purpose

SeismicWatch helps people check nearby hazards and coordinate with family before and during an emergency. It combines earthquake, typhoon, and weather information with location sharing and SOS tools, reducing the need to switch between separate apps.

## Intended users

Philippine residents and families who want a shared view of hazard updates, family locations, and emergency contacts.

## Main capabilities

- View earthquake and typhoon information on a map, with weather information for Philippine locations.
- Create family circles, share location, and exchange messages.
- Access SOS, emergency contacts, and quick-access emergency tools.
- Use nearby-device relay features when participating phones are within reach.

## How it was built

### Native Android interface

Kotlin and Jetpack Compose provide the screens; view models and repositories separate interface state from data access.

### Separate data services

Firebase supports accounts, family circles, messaging, and notifications. Supabase supplies the hazard-data layer, while Room and SQLCipher support local storage.

### Maps and device integration

MapLibre displays map layers. Android location services, background work, widgets, and Nearby Connections connect the app to phone capabilities.

## Technology stack

| Responsibility | Technologies |
| --- | --- |
| Android | Kotlin, Jetpack Compose, Navigation Compose |
| Data & accounts | Firebase Auth, Cloud Firestore, Supabase, Room, SQLCipher |
| Maps & emergency tools | MapLibre, Google Play Location, Nearby Connections |
| Background & widgets | WorkManager, Firebase Cloud Messaging, Glance |

## Project scope and current state

A personal project in development. Hazard updates depend on upstream data and connectivity; nearby relay requires compatible devices, permissions, and proximity. It supplements official advisories and emergency channels.

## Source guide

- [`app/src/main/java/com/gising/ui/`](app/src/main/java/com/gising/ui/)
- [`app/src/main/java/com/gising/data/`](app/src/main/java/com/gising/data/)
- [`app/build.gradle`](app/build.gradle)
- [`firestore.rules`](firestore.rules)

## Development setup

Import the Gradle project into Android Studio with JDK 17 and the Android SDK configured in app/build.gradle. Configure Firebase and the Supabase hazard-data services used by the application. Test location, notification, background-service, and Nearby permissions on a compatible device. External data ingestion is configured separately from the Android build.

## Screenshot and module walkthrough

Expand a screen below to see the capture and an explanation of the module, its visible controls, and its role in the application.

<details open>
<summary><strong>01 · Account access</strong></summary>

Email/phone credentials, Google sign-in, phone OTP, and password recovery provide entry points to the account-based hazard and family features. The screenshot shows sign-in choices rather than a completed login.

<p><img src="screenshots/1.jpg" alt="Account access" width="360"></p>

</details>

<details>
<summary><strong>02 · Hazard dashboard</strong></summary>

Active alerts, earthquake totals, and storm signals summarize current activity. The featured typhoon card and recent earthquake list lead into detailed tracking and event reports.

<p><img src="screenshots/2.jpg" alt="Hazard dashboard" width="360"></p>

</details>

<details>
<summary><strong>03 · Combined hazard map</strong></summary>

Earthquake and typhoon markers share a regional map. Magnitude filters and the color legend help users narrow events and see where activity is concentrated.

<p><img src="screenshots/3.jpg" alt="Combined hazard map" width="360"></p>

</details>

<details>
<summary><strong>04 · Typhoon track and timeline</strong></summary>

Storm-track points, intensity colors, weather imagery, and a playback timeline let users inspect the storm position over time. The legend distinguishes storm intensity and projected track markers.

<p><img src="screenshots/4.jpg" alt="Typhoon track and timeline" width="360"></p>

</details>

<details>
<summary><strong>05 · Earthquake event details</strong></summary>

Magnitude, location, depth, distance, and a location-based assessment explain one selected earthquake. Area, sharing, and felt-report actions support follow-up; the displayed assessment is an app estimate, not an official safety determination.

<p><img src="screenshots/5.jpg" alt="Earthquake event details" width="360"></p>

</details>

<details>
<summary><strong>06 · Earthquake report browser</strong></summary>

Search, severity filters, totals, and sorting organize a list of earthquake reports. Each entry provides magnitude, location, depth, and time before the user opens detailed information.

<p><img src="screenshots/6.jpg" alt="Earthquake report browser" width="360"></p>

</details>

<details>
<summary><strong>07 · Typhoon reports and local weather</strong></summary>

Storm-category filters and signal counts sit above a Philippine weather list. Regional filters and the location card let users move between storm monitoring and local weather conditions.

<p><img src="screenshots/7.jpg" alt="Typhoon reports and local weather" width="360"></p>

</details>

<details>
<summary><strong>08 · Family and friends directory</strong></summary>

Contact cards show activity and location-sharing state alongside communication actions. The directory gathers family connections so the user can choose whom to contact or locate.

<p><img src="screenshots/8.jpg" alt="Family and friends directory" width="360"></p>

</details>

<details>
<summary><strong>09 · Family circles and live location</strong></summary>

The screen combines a sharing switch, create/join circle actions, a map, and member information. It lets users organize family circles and control their own location-sharing preference.

<p><img src="screenshots/9.jpg" alt="Family circles and live location" width="360"></p>

</details>

<details>
<summary><strong>10 · Family emergency-alert settings</strong></summary>

A master family-alert switch, auto-SMS and auto-call settings, saved contacts, and test/share shortcuts configure intended emergency communication. Several controls are disabled in the captured state; actual delivery is not demonstrated.

<p><img src="screenshots/10.jpg" alt="Family emergency-alert settings" width="360"></p>

</details>

<details>
<summary><strong>11 · Notification preferences</strong></summary>

Earthquake, typhoon, flood, sound/vibration, and emergency-override controls appear beneath the profile. Users can select alert categories and open emergency-contact setup.

<p><img src="screenshots/11.jpg" alt="Notification preferences" width="360"></p>

</details>

<details>
<summary><strong>12 · Profile and app preferences</strong></summary>

The profile form collects a name, mobile number, and home address. Alert threshold, language, background synchronization, and appearance settings control the wider app experience.

<p><img src="screenshots/12.jpg" alt="Profile and app preferences" width="360"></p>

</details>

<details>
<summary><strong>13 · Appearance and emergency options</strong></summary>

Theme and display-mode choices appear above strobe, SMS-fallback, mesh-relay, and panic-gesture switches. This screen configures optional behaviors; it does not demonstrate successful emergency delivery or nearby relay.

<p><img src="screenshots/13.jpg" alt="Appearance and emergency options" width="360"></p>

</details>

<details>
<summary><strong>14 · Help, privacy, and account controls</strong></summary>

Help Center, Data Sources, Privacy Policy, version information, sign-out, and account deletion give users a place to inspect application information and manage their account.

<p><img src="screenshots/14.jpg" alt="Help, privacy, and account controls" width="360"></p>

</details>

---

Project by [Mark Crysler Baddo](https://github.com/Kuraisura) · [Portfolio](https://kuraisler.xyz)
