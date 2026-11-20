# Newt Maps UI refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans.

**Goal:** Mockups 10 (Driving), 11 (Place card) and 12 (first run) on newt-ui 2.0.1. Feature change: step 1 checks whether a map app is installed and shows "<app> found" or what to install.

## Review Focus

1. **No map app installed:** step 1 is current and names what to install; the link step isn't current yet. (Task 2 test.)
2. **Links already on:** steps 1 and 2 are done and step 3 is current. (Task 2 test.)
3. **Voice muted:** the button says "Voice off" in words, not just color. (Task 3 test.)
4. **Place card at 200%:** the Share / Copy / Open in buttons wrap and don't clip. (200% screenshot.)
5. **The street card** (not a place): no Directions button. (Existing streetDark screenshot plus a test.)

### Task 1: newt-ui 2.0.1 (bump; 108/108 pass)
### Task 2: Setup as `NewtSteps`
- `HomeScreen(linksNeedTurningOn, onTurnOn, …, mapApp: String?)`.
- Steps: 1 Install a map app ("<app> found", or "Install Organic Maps, CoMaps or Magic Earth"), 2 Turn on link opening (button "Turn on"), 3 Try it ("Tap any maps link, or Share → Newt Maps").
- A "Then it does this" card. Practice drives becomes a `NewtSwitchRow`, and the data sources go behind ⓘ.
### Task 3: Driving and the place card
- `TripBar`: "9 min" big and mono, then "3.8 mi · arrive 3:45"; worded 64dp buttons "Voice on"/"Voice off" (gradient while on) and "End" (red outline, spoken "Stop navigation").
- `TurnBanner`: "Then …" sits on its own lifted line.
- The place card: the name, then the coordinates (mono, 16sp, copyable). A green full-width **Directions** button, then worded **Share** / **Copy** / **Open in** buttons.
