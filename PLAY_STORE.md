# Google Play and F-Droid publishing

ModPoll Mobile has three distribution flavors.

| Flavor | Store | Package | Ads | Support prompt |
|---|---|---|---|---|
| `foss` | F-Droid | `io.github.hansfbaier.modpoll` | None | Donation link |
| `play` | Google Play free | `io.github.hansfbaier.modpoll` | Banner only | Pro + F-Droid |
| `pro` | Google Play paid | `io.github.hansfbaier.modpoll.pro` | None | None |

Both Google Play variants are separate listings. The free app links to the paid
Pro listing; there is no in-app billing integration.

## Build commands

```bash
# F-Droid
./gradlew :app:assembleFossRelease
# Play free app bundle
./gradlew :app:bundlePlayRelease
# Play paid app bundle
./gradlew :app:bundleProRelease

# development
./gradlew :app:assembleFossDebug :app:assemblePlayDebug :app:assembleProDebug
./gradlew :app:testFossDebugUnitTest
```

F-Droid release output:

```text
app/build/outputs/apk/foss/release/app-foss-release-unsigned.apk
```

The Play release artifacts are unsigned unless a signing configuration is
added. Use Play App Signing or configure a private keystore; do not commit it.

## AdMob configuration

The default AdMob IDs are Google's test IDs. They must not be shipped to
production.

Create one AdMob app for the free Google Play package and one banner ad unit.
Pass the real values through Gradle properties:

```properties
# ~/.gradle/gradle.properties or command-line -P flags
admobAppId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
admobBannerUnitId=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
```

The Play free flavor declares the AdMob application ID in its manifest and
loads a single adaptive banner above the bottom navigation. There are no
interstitial or rewarded-video ads.

Release builds use Google's User Messaging Platform (UMP) consent flow before
requesting an ad. Configure the consent message in AdMob, using the public
privacy policy URL. Debug builds skip UMP so test ads can load directly.

Optional URLs:

```properties
fdroidUrl=https://f-droid.org/packages/io.github.hansfbaier.modpoll/
donationUrl=https://liberapay.com/hansfbaier
privacyUrl=https://github.com/hansfbaier/modpoll-mobile/blob/main/PRIVACY.md
fdroidPromo=true
```

`fdroidPromo=false` removes the F-Droid links from the Play free build if store
review or policy requires it.

The default F-Droid URL points to the package page. Do not publish the Play
free build with that link before the F-Droid listing is live, or set
`fdroidPromo=false`; otherwise the button opens a 404 page.

## Google Play Console

1. Create the free app with package `io.github.hansfbaier.modpoll`.
2. Create a second paid app with package `io.github.hansfbaier.modpoll.pro`
   and its own price.
3. Upload the free and Pro `.aab` files to their respective listings.
4. Complete the ads declaration: free = yes, Pro = no.
5. Complete Data safety for both apps. The free app's AdMob SDK collects device
   identifiers and ad interaction data; the Pro app has no ads.
6. Publish a privacy policy URL (the repository copy is in `PRIVACY.md`).
7. Complete content rating and target-audience declarations.
8. Use the same version name/code policy for each package independently.

The free app shows a support prompt after the third launch, then at most once
every seven days. It offers the paid Pro app and the free F-Droid build.

## F-Droid

The `foss` flavor contains no Google Mobile Ads, UMP, or Google Play services.
Its overflow menu and About dialog offer a Liberapay donation link.

F-Droid metadata must build the flavor explicitly and use the flavor output:

```yaml
gradle:
  - foss
output: app/build/outputs/apk/foss/release/app-foss-release-unsigned.apk
```

The F-Droid and Play free builds use the same package name but different
signatures. Android will not install one over the other; users must export
profiles, uninstall, and install the other build.

## Promotion policy

The free Play build advertises the F-Droid build as requested. Promotion of
alternative distribution or external offers can be subject to Google Play
policy and regional program requirements. Review the current policy for the
developer account and target countries before release, or set
`fdroidPromo=false` to disable those links.

## Data migration

The Pro package is separate from the free package. Profiles can be exported as
JSON from one app and imported into the other. Do not expect Android backup or
shared preferences to cross package boundaries.
