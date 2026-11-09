# Motion frame diagnostics

Capture Android frame stats after a bounded interaction, then summarize each window separately:

```sh
adb shell dumpsys gfxinfo com.island.dynamicisland reset > /dev/null
# Exercise a hold and outside dismissal, without changing playback.
adb shell dumpsys gfxinfo com.island.dynamicisland framestats > /tmp/island-frames.txt
python3 tools/summarize_frame_stats.py /tmp/island-frames.txt
```

The JSON reports frame latency (`FrameCompleted - IntendedVsync`), reported-deadline misses, and excluded rows. Frame latency includes scheduling and rendering; it is not pure GPU execution time. Frames over the refresh interval are reported separately from deadline misses because pipeline deadlines can differ. Keep long completed frames; exclude nonzero Flags and incomplete/sentinel timestamps.

Use the same device, build type, interactions and warm-up sequence for comparisons. A cold sample should not be compared directly to a warmed sample. Short samples guide investigation and do not establish stable frame pacing or an Apple-equivalent implementation. Avoid interpreting AOD/lock-screen transitions as isolated island animation cost.

## Standby / always-on display check

The overlay is hidden and its composition disposed whenever the default display is not
fully on or the device is non-interactive. This cancels equalizer/progress animations,
removes the touch target, and cancels UI clock updates. The media listener has no
recurring position ticker: session callbacks publish track, playback, and seek changes.
Only the visible expanded timeline projects that snapshot using monotonic time, at
four updates per second; compact music, paused/invalid timelines, and completed
timelines schedule no progress updates. Wake refreshes the session snapshot.
Compact stopwatch text wakes on each displayed second rather than polling four
times per second; expanded hundredths retain their existing refresh rate.

On a device, verify with music both playing and paused:

1. Confirm the island animates and transport controls work while awake.
2. Turn the screen off, including with AOD enabled. The island should disappear.
3. Reset `dumpsys gfxinfo com.island.dynamicisland`, leave the device in standby for
   several minutes, and capture frame stats. There should be no recurring island frames.
4. Change tracks from another device/headset during standby, then wake the phone.
   Confirm the current artwork, playback position, gestures, and touch target recover.
5. Repeat sleep/wake and test enabling the accessibility service while already asleep.

Measure standby CPU/battery on the same device and playback workload before and after;
a successful build alone cannot establish a temperature or battery improvement.

## Live Updates and simultaneous music

Paused music stays available in the expanded view. Once compact, five seconds
without interaction hides its slot; interacting restarts the countdown. With no
Live Updates remaining, the island contracts toward the physical camera over
380 ms while its elements move inward and fade out, and releases its touch targets.
Playback resuming or a new Live Update restores the island with a 520 ms entrance:
the silhouette grows from the lens and the artwork and indicator fade outward
into their compact positions. Interrupted transitions reverse from their current
position. Content remains mounted until the exit finishes.
The calibration guide remains available while idle.

The collapsed presentation supports up to three activities on one row: a central
camera pill, the second activity in a detached bubble on the left, and the third
in a detached bubble on the right. Live Updates and music have separate slots.
Activities rank navigation first, other ongoing activities (rides, deliveries,
recordings) next, running stopwatches next, and paused stopwatches last. Google
Maps service updates retain navigation priority even if their category is omitted.
Equal-priority activities keep arrival order; routine updates never promote them.
Music follows the visible Live Updates. Other active updates remain tracked and
fill a vacant slot when an activity ends.
Hold any activity to expand its own preview; tap to open its app.

Holding an activity grows its own bubble into the expanded card. Nearby bubbles,
including the primary when a side activity is selected, merge toward the selected
bubble behind it. The selected preview then settles into the centered expanded
layout. Dismissal shrinks the card back to the selected activity's own slot.
When a secondary was opened, its absorbed companions then emerge from the camera
and travel to their original slots. Cancelling a partial hold reverses its current path. Very quick
taps open the source app without moving companions.
Each bubble has an independent
touch window; gaps pass touches to the app below. Activity arrivals animate from
the camera into their assigned slots; music moving from the center retains its
position and morphs into its minimal side bubble. Interrupted arrivals continue
from the currently displayed geometry.

The debug build includes shell-only notification fixtures (absent from release):

```sh
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --ei id 1701
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --ei id 1702
# Update in place, without adding an activity:
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --ei id 1701 --ei progress 85
# Remove each fixture after verification:
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --ei id 1701 --ez clear true
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --ei id 1702 --ez clear true

# ESPN live sports score activity preview (defaults to TENN 21 - 17 SC, Halftime):
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type espn
# Custom team names and scores:
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type espn --es team1 TENN --es team2 SC --ei score1 21 --ei score2 17 --es status Halftime
# Clear ESPN score demo:
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type espn --ez clear true
```

Use DroidMirror for visual checks: verify both compact and expanded layouts,
hold each compact activity separately, use music transport from its own preview,
and remove the selected activity. Check short notifications have no spare card
height and longer notification status text appears in the expanded body.

## Ongoing notification activities

The app's **Live activity history** card remembers encountered ongoing sources by
app and notification channel, with their latest title and last-seen time. Switches
hide/show that category immediately and survive app/service restarts. Hidden sources
stay in history and future updates do not reset their switch. Only metadata is saved;
notification bodies, custom views and PendingIntents are not persisted. Hiding a source
only affects the island, not the original notification or the underlying service.
Other categories from the same app keep their own switches.

Tapping a compact activity, or its expanded icon/text/timer, sends the source
notification's content PendingIntent. Activity, broadcast and service click tokens
are supported. If the token is missing or expired, the app launcher is used when
available. Expanded action buttons retain their separate notification actions.

Debug-only history inspection and preference control:

```sh
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --ez inspectHistory true
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es sourcePackage android --es sourceChannel DEVELOPER_IMPORTANT --ez sourceEnabled false
```

Notifications must have Android's ongoing-event flag to appear as live activities.
Promotion requests, foreground-service status and no-clear flags alone do not qualify.
Auto-cancel notifications, group summaries and media-session notifications are excluded;
media sessions use the music activity instead. Removal or loss of the ongoing flag
removes the activity immediately. Custom-layout notifications such as Pixel Recorder
use their app name/icon and host their public RemoteViews in the expanded island.
For custom count-up notifications, the public inflated chronometer and controls are
reparented into one compact row: one app label, elapsed time, and 44 dp circular
controls on the right. The collapsed preview hosts a separate copy of the original
chronometer, preserving its app-authored start/stop state instead of showing “Live”.
Other custom layouts retain the full-view fallback. Updates replace the hosted
widgets; collapse blocks their touches, and disposing the island detaches its timers.
No private fields or invented start timestamps are used.

Paused Recorder supplies Resume/Save as standard notification actions. These appear
as two full-width shared-row buttons below the timer. Their 320 ms vertical reveal
drives the bubble's measured height; resuming reverses the reveal. The inline Pause
control fades and expands horizontally over the same interval, moving the mounted
timer continuously. Exiting controls remain drawn until their exit finishes but are
immediately non-interactive. Compact timer styling is independent of whether the
expanded row currently has an inline control.

Debug checks through the real listener:

```sh
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --ez inspectActivities true
# Inflate custom content and report measured height, timer and wired-control counts:
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --ez inspectCustom true
# A temporary notification requesting promotion must not enter the repository:
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --ei id 1799 --ez temporary true
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --ez inspectActivities true
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --ei id 1799 --ez clear true
```

## Stopwatch and timer time sources

Android 17 MetricStyle stopwatch values use the notification's monotonic time base
or fixed paused duration. Older promoted chronometer notifications use their
published start timestamp. Compact mode shows minutes/seconds; expanded mode
shows hundredths with Clock's own notification actions. UI tickers are disposed
with the island when the display is inactive.

MetricStyle countdowns use the published monotonic deadline (or convert an Instant
once). Paused timers retain their published remaining duration without scheduling
UI ticks. Legacy countdown chronometers still use notification.when. Metric labels
such as “11m Timer” supply the heading when Clock omits its notification title.

Read the active Clock notification through the production parser, or exercise
running/paused stopwatch and timer metrics without changing Clock:

```sh
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --ez inspectClockNotification true
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --ez inspectClockApi true
```

Read the parsed stopwatch time without manipulating Clock (debug builds only):

```sh
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --ez inspectStopwatch true
```

## Notification shade visibility

The accessibility service observes interactive-window changes and hides the visual,
main touch, and secondary touch windows while a focused full-screen System UI
window (notification shade, Quick Settings, or lock screen) is present. It inspects
only window bounds and the root package name, without traversing notification
content. Android requires the window-content capability for this window API.
While hidden by the shade, the island subtree is removed so its equalizer,
progress, stopwatch, and hosted RemoteViews timers are cancelled. Dismissing the
shade recreates the compact island with current repository data. Accessibility
window events are ignored during standby and refreshed on wake.

Verify in DroidMirror with an active Live Update or music: pull down notifications,
expand Quick Settings, then dismiss each surface. The island and all touch targets
should disappear while the shade is open and return after dismissal. Also check
opening the shade from an expanded island and waking with the shade already open.
Check permission dialogs and volume controls do not hide the island unnecessarily.

## Event processing efficiency

Notifications without the ongoing flag, group summaries, and media-session notifications
are rejected before stopwatch/style recovery or visual asset loading. Auto-cancel
notifications still go through stopwatch recognition so paused Clock activities remain
supported. Removing an untracked notification does not rebuild the active activity list.

Authentication polling runs only while the interactive keyguard still requires
authentication. After successful authentication, window events and USER_PRESENT
handle dismissal. Polls that do not change keyguard visibility do not synchronize touch
windows. Repeated events from the current foreground app skip launcher/package lookup,
and shade dismissal state is updated only when entering the shade.

## Android Battery Saver

The island observes PowerManager's power-save-mode broadcast while its composition
is mounted. When Battery Saver is enabled, all decorative equalizer bars snap to
static low levels and request no animation frames. Disabling Battery Saver resumes
normal motion on the same bar instances. Playback controls, artwork, timers, and
interaction transitions remain available. There is no polling timer or new permission.

Verify in DroidMirror with music playing: toggle Battery Saver, confirm static bars,
then turn it off and confirm motion returns. Keep charging state and system animation
scale consistent when comparing frame counters; those can affect Battery Saver and
motion independently. Restore the original Battery Saver setting after testing.

## Screen orientation

Island orientation follows WindowManager's current screen bounds and the default
display's rotation, refreshed by display/configuration callbacks. It uses no
OrientationEventListener, accelerometer, or gyroscope subscriptions. With rotation
locked in portrait, physically tilting the phone must not hide or recreate the island.
The island is hidden when the logical display rotates away from upright portrait.

## In-call controls

Ongoing Android CallStyle notifications get priority in the island, a green phone
symbol and a source-provided elapsed timer when available. Hold to show the caller,
circular notification action controls and an info button that opens the call.
The source avatar appears beside the name and timer; hang-up is red and stays on
the right. Unavailable controls do not leave placeholder buttons. The heading prefers a meaningful public
CallStyle person name or notification title; generic titles such as “Your video call”
fall back to the source app name. Actions use the calling app's original
PendingIntents; mute, speaker, hold and other controls appear only when that app
publishes them. No dialer-role or microphone permission is required. Incoming,
screening and ambiguous legacy call notifications are excluded, even when promoted.
The activity disappears when the source removes it or leaves the ongoing state.

Debug fixture (no real call is placed):

```sh
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type call
# Mute/Speaker update their labels; End call removes the fixture.
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type call --ez incoming true
# The incoming replacement must remove the call island.
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type call --ez clear true
```

Legacy calling notifications (including Discord voice channels) are detected
without package or channel allowlists. They must be ongoing, foreground-service
or call-category notifications with an end-call action plus independent evidence:
call category, connected-call text, or mute/deafen/hold controls. Incoming and
screening CallStyle states, ringing text, answer/decline actions, media sessions,
group summaries and auto-cancel legacy notifications do not qualify. VPN disconnect,
recording stop and message-mute actions alone are insufficient.

Control types come from action labels and public mute/unmute semantic hints,
not their position. End-call is retained and placed on the right. Known controls
get fallback icons; unknown actions retain their source icon/label and intent.
Muted/deafened and other explicitly identified toggle states use white backgrounds.
English labels/text drive legacy fallbacks; localized CallStyle calls still use
Android's explicit call state and hang-up intent. No control is synthesized when
the calling app does not publish it, and blocked notifications remain unavailable.
The header prefers a contact or meeting/channel name over generic connection text.
Without a source chronometer it omits the expanded timer/status line.

Package-independent legacy fixture (no real call):

```sh
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type call --ez legacy true --es title "Call in progress" --es text "QA Room"
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --ez inspectActivities true
# Incoming replacement must remove the call activity:
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type call --ez legacy true --ez incoming true
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type call --ez clear true
```

## Numeric ticker (WalkCloser port)

`NumericTickerText` ports `walkcloser-android`'s `CountdownTicker` push, scale,
opacity and focus curves (360 ms). Increases roll downward and decreases roll
upward, comparing whole numeric fields so carries and borrows move together.
Only changing digits move, staggered left to right by 65 ms (195 ms maximum
spread) on one animation clock. Each digit retains its 360 ms motion. Glyph alpha masks
are prepared before motion and cached per style, bounded to ten digits with nine
blur samples each; the draw loop does not extract bitmaps. Punctuation and units
remain sharp. Stopwatch hundredths update directly at the existing 50 ms cadence;
only whole-second changes start a transition. Animations respect battery saver
and the system duration scale. Hidden expanded readouts disable digit motion.
Source-hosted Chronometers retain their own text updates, running/paused state and
native actions; expanded readouts observe text changes without adding a clock.

Compact numeric readouts, including clocks, target 30 animation frames per second.
Their animation clocks share a 30 Hz time grid and wait between frame requests;
they do not run full-rate animations and discard intermediate draws. Expanded readouts use the display frame
rate. The 360 ms motion runs only after a digit changes, with no animation work
between ticks. A single event-driven thermal listener disables compact motion at
Android thermal status LIGHT or higher; normal thermals restore it automatically.
Battery saver also disables motion. Native compact Chronometers use their original
TextView while motion is disabled.

The debug-only, DUMP-protected `TickerBenchmarkActivity` renders three identical
readouts, closes after 15 seconds, and finishes when backgrounded. Example:

```sh
# Compact candidate (animated=false compares against ordinary Text):
adb shell am start -W -f 0x10008000 -n com.island.dynamicisland/.TickerBenchmarkActivity --ez animated true
# Use --ez native true for actual source Chronometer text observation,
# --ez expanded true --ez precise true for fractional stopwatches,
# or --ez idle true for a frozen readout.
# Warm up for two seconds, reset gfxinfo, sample for six seconds, then summarize:
adb shell dumpsys gfxinfo com.island.dynamicisland reset
adb shell dumpsys gfxinfo com.island.dynamicisland framestats > /tmp/ticker-frames.txt
python3 tools/summarize_frame_stats.py /tmp/ticker-frames.txt
```

Initial Pixel 8 samples (2026-10-25, debug build) were confounded by unrelated
phone overheating, as reported by the user. Some samples had no deadline misses;
others had misses under load. They are not evidence that compact ticking is too
expensive at normal temperature. A later thermal check confirmed status LIGHT
with CPU cooling active. Frozen readouts produced zero frames after warmup.
The 30 fps diagnostic records thermal state before/after sampling. Normal-
temperature frame pacing and long-run battery impact remain unproven until an
unthrottled sample is available. Unit tests cover the shared timing grid, carry
logic, fractional-second handling and thermal policy. Later shared-clock phone
diagnostics timed out at launch and yielded no benchmark window, so those samples
are excluded rather than counted as zero-cost rendering.
The diagnostic can force compact motion while testing; the live island retains its
thermal fallback. The existing island/music overlay may also be active during
sampling. Frame-stat rings retain at most 120 rows per window here. Inspect the
benchmark window separately, and reject runs that switch foreground apps. No phone
screenshots or recordings are collected by this diagnostic.

## Event-driven interaction polish

- Live progress bars and their source markers share a bounded, interruptible spring.
  Corrections start from the visible value. Hidden cards and battery saver apply
  the latest value immediately without requesting animation frames. Thermal level
  never disables expanded interaction animations.
- Album art springs to 82% of its playing size when paused and returns on resume.
  This works on the persistent thumbnail in compact and expanded layouts. Track
  flips and playback emphasis remain animated regardless of thermal level;
  battery saver still requests immediate transitions.
- Expanded music scrubbing springs the timeline thickness from 6 to 9 dp and
  brightens both track and fill, then settles on release. Scrubbing retains its
  thicker/brighter target even when battery saver requests immediate transitions.
  The existing seek gesture/commands
  retain ownership; no new clock or media polling is introduced.
- Call controls compress under the finger, animate selected backgrounds/tints, and
  briefly emphasize the symbol when the source confirms a state change. Their
  48 dp hit targets stay fixed. Initial/repeated states and changes consumed while
  hidden do not replay a confirmation animation on opening. Native actions remain
  unchanged; no real calls are made or ended by diagnostic tests.

Frame-clock regressions cover progress correction continuity, bounds, immediate
hidden updates, confirmation settling and the absence of idle animation requests.
Subjective visual timing still requires DroidMirror access when its usage limit
is lifted; source/unit checks do not establish iOS equivalence or battery impact.

## Mobile hotspot live activity

Ongoing Android System, Settings, System UI and Android/Google tethering-module
notifications with an active English hotspot heading qualify as live activities.
The compact view shows a blue hotspot symbol and the source's connected-device
count, or “On” when no count is published. Expanded content retains the original
notification text, settings PendingIntent and published actions. Notification
removal or replacement with an ineligible notification removes the activity.
The existing live-activity source switch applies. No tethering permissions,
client polling, network changes or fabricated connection counts are used.
Localized headings and OEM packages outside the system allowlist currently use
the normal notification eligibility rules. USB/Bluetooth-only notices, setup
prompts, stopped notices, summaries and auto-cancel notices do not qualify through
hotspot detection. Android's generic “Tethering or hotspot active” heading cannot
distinguish tethering transports and is retained as supplied by the system.

Debug parser/UI fixture (does not enable the real hotspot or post a notification;
it parses a synthetic Android System notification through the production reader):

```sh
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type hotspot
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type hotspot --es text 'No connected devices'
# Each ineligible replacement must remove the fixture:
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type hotspot --ez ongoing false
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type hotspot --ez autoCancel true
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type hotspot --es title 'Hotspot turned off'
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type hotspot --ez clear true
```

## Apple Music artwork diagnostics

The debug receiver can report artwork sources for the active track, or exercise the
public Apple catalog lookup on the phone without changing playback:

```sh
adb shell am broadcast -n com.island.dynamicisland/.MediaActionDemoReceiver --ez diagnose_artwork true
adb shell am broadcast -n com.island.dynamicisland/.MediaActionDemoReceiver --ez lookup_artwork true --es title cardigan --es artist 'Taylor Swift' --es album folklore
```

Lookup reports `appleStatic` and `appleMotion` independently. Motion availability varies
by album/edition. Matching requires the title, artist, album (if supplied), and a
compatible duration. Lookups run off the main thread, cache up to 48 results for
30 minutes, and discard results after a track change. Apple page layout changes,
network failures, and unmatched recordings leave the source artwork in place.
Compact artwork prefers the source bitmap; expanded artwork layers silent, looping
Apple motion above it with a 420 ms fade after the first video frame renders.
Video keeps its decoded aspect ratio and fills the square thumbnail with a horizontally
centered, top-aligned crop, so portrait motion does not lose its upper cover composition.
The player prepares during expansion, and opacity follows expansion in both directions.
The visualizer uses the selected static bitmap's cached color, including Apple fallback art.
Compact mode has no video player.
Power saving and reduced-motion policy disable the video; pausing music pauses it.
Always use DroidMirror for visual verification.

## Sports possession and turns

Sports score changes use NumericTickerText in expanded and compact views, including
score-only three-activity bubbles. The stats section heading is a toggle with a
rotating chevron; collapsing it hides notes/player rows while retaining the
scoreboard and field context. Collapsed stats use a 4 dp bottom inset and a compact
heading row. Scoring-event explanations are rendered separately at readable sizes,
without the stats chevron, and remain visible when player stats are collapsed.
The stats state is keyed to the activity and is not reset
by routine stat-title changes. Reduced motion and power saving disable the
section/chevron transition; compact scores retain the ticker thermal policy.

Native game clocks: a visible `Chronometer` explicitly identified as a game,
match, period or primary-status clock is retained directly in the expanded card.
The original RemoteViews controls its start/stop state, base and count direction.
The timer's text changes feed the existing numeric ticker animation. ESPN's text
snapshots also animate their changed digits when notifications arrive; multiline
status is rendered as separate ticker lines. No additional game-clock timer is created.
Updates reinflate the timer, and hidden cards stop ticking through view visibility.
Shot/play/timeout clocks are excluded. Plain text clocks (including ESPN's current
football layout) remain notification snapshots; no running state is inferred from
successive values. This avoids drifting through football stoppages.

The ESPN debug fixture accepts `--ez nativeClock true --ez clockRunning true
--ei clockSeconds 318`; repost the same fixture ID with `clockRunning false` to
exercise a stopped clock, or `--ez clockCountdown false` for a count-up sport.

Expanded scoreboards also render source-backed sport context: football field
position with a first-down target when possession and down/distance are known;
ESPN's published baseball base-occupancy graphic and explicit out count; and
serving/batting summaries plus explicitly named set-score fields. Unknown team
aliases, missing down/distance, unsupported court coordinates and missing source
fields retain their original text. Field orientation fixes team one's goal line
at the left and team two's at the right; it does not claim physical stadium direction.
The first-down line is always orange-yellow. Ball/target changes use bounded,
interruptible springs while expanded. A translucent possession-team trail spans
the previous and current observed yard lines; clock-only updates preserve it.
Confirmed turnovers, scores and period changes reset movement inference. Missing
field/possession panels retain the last known field and trail at 55% opacity rather
than erasing them. Fresh source context restores full brightness; historical context
never creates a new play. Finals, changed matchups and notification removal clear
the retained field. Missing possession retains a dim last-known badge within the
same score/period; confirmed possession replaces it, while score/period changes
clear an unconfirmed owner. Movement is inferred from notification snapshots and can
include penalties or multiple plays, rather than a verified individual play result.
Hidden cards and reduced-motion/power-saving policies snap to the latest position.
There is no sports API polling or independent game-clock timer.

Football fixture: use `--es type espn --es team1 WIS --es team2 Iowa
--es status Q1 --ei possession 2` and the `context` string
`1st & 10\nIowa 29` (with an actual newline) on the debug receiver. `possession 0`
keeps the ball position but omits the first-down target rather than guessing direction.

Scoreboards show a larger white dot with a black rim over the bottom-inner
corner of the source-identified team's logo. Score-only compact pills place a
smaller dot centered beneath the score. The field graphic omits duplicate team labels. The parser accepts visible, team-associated possession,
serving and batting fields. ESPN's generic indicator is also matched against its
possession/tennis-ball assets; generic winner/favorite arrows do not qualify.
Omitted indicators retain a dim last-known marker while the same score/period continues; confirmed turn updates replace it. Pregame and final scores do
not show a turn marker. This uses notification data and makes no sports API requests.

Debug fixture (through the real notification parser):

```sh
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type espn --es status Q1 --ei possession 1 --ei id 1788
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type espn --es status Q1 --ei possession 2 --ei id 1788
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type espn --es status Q1 --ei possession 0 --ei id 1788
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --ez inspectActivities true
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type espn --ei id 1788 --ez clear true
```

Verify marker switching/clearing and compact/expanded placement in DroidMirror.
Unit tests cover explicit turn semantics, winner rejection and conflicting/missing
team evidence. Phone verification was blocked by a disconnected ADB device and
DroidMirror timeouts when this feature was added.

## Synced lyrics

Expanded music adds one current lyric line, with word/syllable highlighting where
the source supplies it. Lines fade and slide between one another. Overflowing
lines scroll horizontally with faded edges, with no ellipsis. Line-synced scrolling
uses each line's playback duration, including short holds at the start/end, rather
than a fixed marquee speed. Pause, seek, and playback speed affect it immediately.
Timing controls and sync/source labels are hidden. The island reveals newly loaded
lyrics with a 360 ms height animation. During an upward swipe, content resizing
is deferred at the visible height. Releasing back to expanded animates to the latest
lyric height; a committed collapse retains the held height until fully closed. Gaps and music-note placeholders longer than two seconds reserve no space;
the same height animation reveals the next actual sung line. Gaps up to two seconds
retain the row and show three dots that brighten and grow sequentially toward the
next line, following the playback clock. Timed syllables subtly lift and brighten over their first 160 ms, then gently
return to baseline. This follows the actual playback clock, respecting pause,
seek, and speed. Playback controls stay
available. No lyric panel appears when matching timed lyrics cannot be found.
AMLL TTML DB supplies community word timings; LRCLIB supplies the line fallback
(and enhanced-LRC word timings if present). This does not use Apple account tokens.
Matching preserves version qualifiers and checks album and duration when available.
Community timing/coverage varies by recording.

Lyrics are requested only while music is expanded. The AMLL metadata index caches
for one day; up to 64 lyric documents cache for seven days and remain usable offline.
Failed/missing lookups are not cached; while expanded, the app retries twice
with a short backoff. LRCLIB exact misses fall back to search, allowing equivalent
standard/deluxe album labels while preserving title/version, artist, and duration checks. Track changes cancel lookup publication, so
an old recording cannot replace the new one's lyrics. The lyric row shares the expanded content fade, blur, and vertical travel in both
directions, entering after the controls and retreating first during collapse.
Compact and hidden cards
schedule no lyric ticks; expanded playback wakes at lyric timing boundaries and requests frames during
active syllable emphasis only.

Previously saved per-recording/player timing adjustments remain applied. Reduced
motion and power saving disable lyric transitions and marquee animation.

Shell-only diagnostics (no playback changes, no lyric text in output):

```sh
adb shell am broadcast -n com.island.dynamicisland/.MediaActionDemoReceiver --ez diagnose_lyrics true
adb shell am broadcast -n com.island.dynamicisland/.MediaActionDemoReceiver --ez lookup_lyrics true --ez current true
adb shell am broadcast -n com.island.dynamicisland/.MediaActionDemoReceiver --ez lookup_lyrics true
adb shell am broadcast -n com.island.dynamicisland/.MediaActionDemoReceiver --ez lookup_lyrics true --es title cardigan --es artist 'Taylor Swift' --es album folklore --el duration 239000
```

The default fixture checks Cruel Summer / Taylor Swift / Lover (178426 ms). Inspect
expanded lyrics with DroidMirror: pause/resume, seek into an instrumental gap,
skip rapidly, observe a long line scrolling through the fade, and collapse. Confirm the controls remain usable,
no stale lyrics appear after skipping, and compact mode continues to show artwork.

## File transfer activity fallback

Transfer detection uses notification content rather than an app allowlist. English
upload/download, backup, sending/receiving and file-sync/copy headings can qualify
with ongoing or structured progress evidence. Text percentages are a fallback;
Android progress takes precedence, and indeterminate progress has no percentage.
Summaries, media, incoming calls and auto-cancel notifications retain their exclusions.
Completed/failed text cannot qualify through this fallback. Other languages retain
normal category/progress eligibility; this is not universal semantic detection.
Expanded transfers show direction, status, a percentage when known, a clean progress
track, and the source notification's PendingIntent controls.

The debug receiver's `--es type transfer` posts a generic notification through the
real listener. It accepts `title`, `text`, `progress`, `textOnly`, `indeterminate`,
`ongoing`, and `autoCancel`. For example:

```sh
adb shell 'am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type transfer --es title "Uploading photos" --ei progress 42'
adb shell 'am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type transfer --es title "Syncing clipboard file" --es text "Receiving from Mac · 29%" --ez textOnly true'
adb shell 'am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --es type transfer --es title "Downloading archive.zip" --ez indeterminate true'
adb shell am broadcast -n com.island.dynamicisland/.LiveUpdateDemoReceiver --ez clear true
```

Use DroidMirror to inspect compact and expanded layouts. Unit tests also cover
backup, copying, paused state, stale indeterminate values and unrelated messages.
