# BashirConnect — UI spec (v1)

Reference for the home screen redesign, agreed over chat. Implemented in
`MainActivity.kt` / `Type.kt`.

## Naming
The app name is **BashirConnect** — shown on every system-facing surface:
launcher icon/label, in-app title bar, notification title, Quick
Settings tile label (all pull from `@string/app_name`, don't hardcode
it separately). **Mr. Bashir** is the mascot's name — used only in
in-app flavor text: hero card status lines, activity log entries,
Snackbar confirmations. Don't let these bleed into each other.

## Fonts
- Headings, numbers, buttons: **Fredoka** (SemiBold 600, Medium 500)
- Body text, inputs: **Nunito** (Regular 400, SemiBold 600)
- Loaded via Android XML font resources (res/font/), NOT the Compose
  `GoogleFont.Provider` runtime API — that approach is async with no
  delivery guarantee and can silently fall back to the system font if
  Play Services doesn't serve it before first composition. XML resources
  route through `ResourcesCompat` and can be pre-warmed via
  `preloaded_fonts` in the manifest, so the font is ready before the
  first frame.

## Screen states

| State | Creds card | Save & start | Stop | Forget me | Hero card |
|---|---|---|---|---|---|
| No creds saved | visible | enabled | disabled | disabled | "Not set up yet" |
| Creds saved / running | hidden | disabled | enabled | enabled | "Logged in!" (or "Paused" after Stop) |

- Save & start: validates fields aren't empty, saves via `CredentialStore`,
  starts `CaptivePortalService`, flips state to "running", shows a
  Snackbar confirmation.
- Stop: stops the service, keeps credentials, flips hero card to
  "Paused", shows a Snackbar confirmation. Save & start stays disabled
  (creds already exist) — only Forget me brings the form back.
- Forget me: clears `CredentialStore`, stops the service, resets to the
  "no creds" state, shows a Snackbar confirmation.

## Layout (top to bottom)
1. Header row — "Mr. Bashir" title (Fredoka) + settings gear icon
2. Hero card (soft green bg) — mascot with idle bob animation, status
   headline + subtext, confetti burst on successful login
3. Stat row (3 cards) — day streak (🔥), logins today (⚡), avg login
   speed (⏱). Only shown once `stats.streak > 0 || stats.loginsToday > 0`
   — no point showing "0 day streak" before the first login ever happens.
4. Credentials card — username + password fields (shown once only)
5. Recent activity — collapsible drawer, newest entry on top, backed by
   `AppStatus.log`
6. Action buttons — Save & start / Stop / Forget me, state-driven per
   the table above
7. Footer — "© 2026 BashirConnect · v1.0"

## Stats tracking (StatsStore.kt / AppStats.kt)
Plain (unencrypted) SharedPreferences — just counters, not sensitive.
Recorded once per successful login, in `CaptivePortalService` right
after `PortalLoginClient` returns `LoggedIn`:
- **Streak**: consecutive calendar days with at least one login. Reads
  as 0 if the last login wasn't today or yesterday (streak considered
  broken at *read* time, not just write time).
- **Logins today**: resets automatically when the stored date rolls
  over.
- **Avg speed**: lifetime rolling average of `LOGGING_IN` → `LOGGED_IN`
  duration, measured with `System.currentTimeMillis()` around the
  `PortalLoginClient.attemptLogin()` call.

`AppStats` is the same live-StateFlow pub/sub pattern as `AppStatus` —
`StatsStore` writes, the UI just observes `AppStats.stats`.

## Mobile-data false-positive fix (PortalLoginClient.kt)
On a phone with Wi-Fi + mobile data both active, Android's default
network routing can send the canary HTTP request over cellular instead
of the captive Wi-Fi — so it sees a real 200 from neverssl.com and
wrongly concludes "already online" while Wi-Fi is still stuck behind
the portal. This was the "had to toggle mobile data off and back on"
bug in the original desktop script.

Fix: `PortalLoginClient` binds BOTH the socket (`socketFactory`) AND DNS
resolution (custom `Dns` backed by `network.getAllByName()`) to the
specific captive `Network` object Android handed us. Binding only the
socket isn't enough — DNS needs pinning too, or the hostname lookup can
still resolve via whichever network the OS considers "default."

## Streak semantics (StatsStore.kt) — corrected
Streak is continuous runtime since the last explicit Start, NOT
consecutive calendar days. Key behaviors:
- `markStarted()` is idempotent — only stamps a fresh start time if one
  isn't already set, so it's safe to call on every `onStartCommand`
  (including a reboot-triggered restart) without resetting the clock.
- `markStopped()` lives in `CaptivePortalService.stop()` (the companion
  function), NOT in `onDestroy()` — onDestroy fires on ANY teardown,
  including the OS killing the process and START_STICKY silently
  restarting it, which would have wrongly reset the streak on every
  such kill. Only a genuine user/tile-requested stop should reset it.
- `BootReceiver` now checks `statsStore.isServiceEnabled()` in addition
  to `hasCredentials()` — previously it auto-resumed on every reboot
  regardless of whether the user had explicitly stopped the service,
  silently undoing their Stop.
- The Start/Stop buttons are a real toggle now (previously Stop had no
  way back except Forget me + re-entering credentials — a dead end).

## Quick Settings tile — platform limitation
Android gives apps no public API for custom long-press behavior on QS
tiles; that gesture is owned by the system (shows an app-info popup,
not developer-controllable). The tile is a plain tap-to-toggle, same as
the flashlight tile — no long-press-to-open-app, because that isn't
implementable via the public SDK.

## Mascot
Original wizard illustration, licensed asset (see `/design/` for the
source file reference) — chibi wizard, wide-brim hat with teal band,
flowing mustache-beard, staff wrapped in a teal cable topped with a
glowing wifi-signal orb. Not to be confused with earlier discarded
concepts (70s disco version, pixel-art version) — those are dead ends,
don't resurrect them.
