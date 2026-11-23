# Build Instructions

Welcome to my spaghetti

Fill these values in local.properties

```
# // https://www.last.fm/api/account/create
lastfm.key=
lastfm.secret=
# // https://developer.spotify.com/dashboard/
spotify.refreshToken=<base64 encoded client_id:client_secret>
```

### For Android (FOSS):

- Optionally, fill these values in local.properties for a signed release build:

```
releaseGithub.alias=
releaseGithub.keystorePath=
releaseGithub.password=
releaseGithub.storePassword=
```

- Run `./gradlew androidApp:assembleReleaseGithub`

- **Info**: the FOSS build includes no non-free dependencies.

### For Android (Play Store):

- Create a Firebase project for Crashlytics and add the google-services.json to composeApp/
  See https://firebase.google.com/docs/android/setup

- If you want to generate the optional custom baseline profile for the app, which can improve its
  startup time, create a file
  `/baselineprofile/src/main/java/com/arn/scrobble/baselineprofile/Secrets.kt`:

```
object Secrets {
    const val type = "lastfm"
    const val username = "<lastfmUsername>"
    const val sessionKey = "<lastfmSessionKey>"
}
```

sessionKey can be obtained by logging in to LastFM with a debug build of this app and tapping on
"Copy last.fm session key" in the settings screen.

Then run `./gradlew :baselineprofile:generateBaselineProfile`

Currently, the builds skip this step.

- Fill these values in local.properties for a signed release build:

```
release.alias=
release.keystorePath=
release.password=
release.storePassword=
```

- Run `./gradlew androidApp:assembleRelease`

- **Info**: the Play Store variant includes Google Play billing, Crashlytics (has opt-out) and
  Google Play Review API as its non-free dependencies

### For desktop:

- Compile the native lib https://github.com/kawaiiDango/pano-native-components

- To run the desktop app without packaging, put the native libraries for your
  OS and architecture in `desktopApp/resources/<os>-<arch>/` and run
  `bash gradlew :desktopApp:run` using JDK 25. The `:composeApp:jvmRun` task is
  not the desktop launcher.

- On Linux, install `desktop-file-utils` and use a desktop session with an
  OpenURI portal so Pano can launch the default browser. The development run
  advertises a hidden OAuth handler under `$XDG_DATA_HOME/applications` without
  changing your defaults. Stop any other running Pano before starting it: the
  development and installed launchers share the native single-instance listener.
  The package's desktop entry provides the handler after installation. For an
  AppImage, use Pano's **Add to app launcher** action (or desktop integration)
  before signing in. If Firefox asks for permission to open Pano, allow it.

- If you intend to package a build for desktop,
  use [GraalVM CE 25](https://github.com/graalvm/graalvm-ce-builds)
  as your JAVA_HOME and GRAALVM_HOME (both should be set). Also
  have [Inno Setup 7](https://jrsoftware.org/isdl.php) installed on Windows.

- Run
  `./gradlew desktopApp:packageNativeImage`

- **Info**: the desktop build includes no non-free dependencies.
