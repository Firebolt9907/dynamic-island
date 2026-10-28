# Agent instructions

- Use DroidMirror.app for visual testing of the app on the phone. Do not capture screenshots or screen recordings through ADB for visual verification. A separate app may place a secure overlay over the phone display to turn off the physical screen, which can interfere with capture.
- Use the native computer-use tools to inspect and interact with DroidMirror. The known working app path is `/Applications/DroidMirror.app/Contents/Helpers/DroidMirror Renderer.app`; the window is named `DroidMirror — Rishu's Pixel`. Do not ask the user to unlock the phone as a prerequisite for mirroring.
- ADB may still be used to build/install, reload the already-enabled service, and collect nonvisual diagnostics such as window bounds and frame timings.
