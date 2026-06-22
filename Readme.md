# Callyn

Callyn is a modern, feature-rich Android calling and dialer application built with Jetpack Compose. It acts as a custom phone dialer, integrating device contacts with CRM (work) contacts, employees, and background data synchronization.

## 🚀 Features

- **Custom Dialer UI**: Integrated call handling using Android's `InCallService` and `CallScreeningService`.
- **Unified Contact Directory**: Easy management and search across Personal (device), CRM (work), and Employee contacts.
- **Dual SIM Support**: Smart SIM selection and SIM-specific call initiations.
- **Background Sync**: Automated background workers for logs uploading, user details syncing, and log purging via WorkManager.
- **Secure Persistence**: Local caching using Room Database with SQLCipher encryption.
- **Conflict Resolution**: Custom handling for duplicate contacts and user-initiated merges.

---

## 🛠️ Technology Stack

- **UI Framework**: Jetpack Compose & Material 3
- **Local Database**: Room with SQLCipher
- **Network**: Retrofit & OkHttp (with Auth Interceptor)
- **Background Operations**: WorkManager
- **Platform API**: Min SDK 27, Compile/Target SDK 36 (Java 17)
- **Analytics & Crashlytics**: Firebase

---

## 📂 File Structure

```
app/src/main/java/com/mnivesh/callyn/
├── MainActivity.kt           # Main entry point and compose layout host
├── InCallActivity.kt         # Custom in-call UI activity
├── CallynApplication.kt      # Application initialization (database, workers)
│
├── api/                      # Retrofit networking services and API routes
├── components/               # Reusable Jetpack Compose UI elements
├── data/                     # Repository layer managing remote/local data flow
├── db/                       # Room database models (AppContact, CallLogs) & DAOs
├── managers/                 # Core business managers (CallManager, SimManager, etc.)
├── screens/                  # Feature screens (Contacts, Search, Logs)
├── services/                 # MyInCallService and WorkCallScreeningService
├── sheets/                   # Interactive Bottom Sheets (Modern, Employee, Device)
├── tabs/                     # Custom tabs UI implementation
├── ui/                       # Theme configurations (Color, Type, Theme)
├── utils/                    # Data models, color utils, and helper functions
├── viewmodels/               # ViewModels managing UI state (Contacts, CallLogs, etc.)
└── workers/                  # WorkManager background tasks (Sync, LogUpload)
```

---

## 🏗️ Build & CI/CD

The project includes a GitHub Actions workflow (`.github/workflows/build.yml`) that triggers on commits to `main` to build and upload the release APK and AAB.

### Setup and Local Build
1. Create a `key.properties` file in the root folder with signing details:
   ```properties
   storeFile=../release-keystore.jks
   storePassword=YOUR_STORE_PASSWORD
   keyAlias=YOUR_KEY_ALIAS
   keyPassword=YOUR_KEY_PASSWORD
   ```
2. Build the project using Gradle:
   ```bash
   ./gradlew assembleRelease bundleRelease
   ```
