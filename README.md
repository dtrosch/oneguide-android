# OneGuide for Fire TV

A tiny Fire TV app: a full-screen web view that shows the hosted OneGuide page and can open the
streaming apps installed on the device (Netflix, Hulu, Max, Disney+, Paramount+, Peacock, ESPN,
Apple TV, Prime Video, Spectrum/Cox).

- No usernames or passwords pass through this app; logins stay inside each streaming app.
- The page itself is hosted on Netlify, so updating the guide does not need a new APK.
- `START_URL` in `MainActivity.java` is the hosted page.

## Build
Push to the `main` branch. GitHub Actions builds `OneGuide.apk` and publishes it on the
Releases page as "latest". Or open this folder in Android Studio and run `assembleDebug`.
