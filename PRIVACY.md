# ModPoll Mobile privacy policy

Effective date: 2026-11-14

ModPoll Mobile is a Modbus master for Android. This policy covers the builds
distributed through Google Play and F-Droid.

## Summary

- The F-Droid and Google Play Pro builds contain no advertising SDK and no
  Google Play services.
- The Google Play free build contains the Google Mobile Ads (AdMob) SDK and
  shows banner ads.
- The app has no developer-operated server, no account system, no analytics,
  and no telemetry.

## Data stored on the device

Profiles, signal definitions, connection settings, traffic logs, and trend
history are stored locally in the app's private storage. They are not uploaded
to the developer. Android backup may copy app data if the device backup
setting is enabled.

## Modbus connections

When you connect over Modbus TCP, the app opens a socket to the host and port
you enter. The data exchanged with that device is controlled by you and the
device. The developer does not receive it.

USB serial traffic stays between the Android device and the USB adapter.

## Advertising in the Google Play free build

The Google Play free build uses Google Mobile Ads (AdMob) to show banner ads.
The AdMob SDK may collect and process:

- device or app identifiers, including the advertising ID;
- IP address and approximate location derived from it;
- app and ad interactions, such as ad requests, impressions, and clicks;
- diagnostic and performance information.

Google uses this information for advertising, measurement, and fraud
prevention. See Google's privacy policy at
<https://policies.google.com/privacy> and its advertising technologies page at
<https://policies.google.com/technologies/ads>.

The app uses Google's User Messaging Platform (UMP) in release builds to
request consent where required. The F-Droid and Pro builds do not contain
AdMob or UMP.

## Donations

The F-Droid build can open a Liberapay donation page. Liberapay processes the
donation and its own data; the app does not receive payment details. See
<https://liberapay.com/about/privacy>.

## Children

The app is a technical tool and is not directed at children.

## Changes

This policy may be updated when the app or its distribution changes. The
effective date at the top will be updated.

## Contact

Hans Baier — foss@hans-baier.de
