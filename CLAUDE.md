# Newt Maps

Opens Google and Apple Maps links without Google, shows the place in big type, hands it to a map app, and has its own map, search, saved places and spoken directions. The owner has low vision; readability beats everything else. What it does is in
[README.md](README.md), the plan in [PLAN.md](PLAN.md), the shared rules in the
[Newt house style](NEWT_STYLE.md).

## Stack

Kotlin, Jetpack Compose, Material 3. The theme and shared pieces come from `newt-ui`, and
the screenshot and accessibility test helpers (`newtScreenshot()`) from `newt-test`. Both
live in [newt-android-lib](https://github.com/sec-newt/newt-android-lib). Code is in
`app/src/main/java/io/github/secnewt/maps/`, tests in `app/src/test/`.

## Checks: run them and show the output

CI runs the same checks on every PR (see `.github/workflows/build.yml`).

```sh
./gradlew recordRoborazziDebug   # logic, screenshot and accessibility tests
./gradlew lintDebug              # lint, including Android security checks
./gradlew assembleDebug && scripts/check-permissions.sh app/build/outputs/apk/debug/app-debug.apk
```

Paste the result. Don't say "tests pass" without the output. If Gradle can't download
the Newt packages (a 401), the GitHub login needs `read:packages`: ask the owner to run
`gh auth refresh -s read:packages`, and lean on CI meanwhile, saying so in the PR.

## Rules that are easy to break

- **The internet is only for the places already listed** in `config/allowed-permissions.txt`
  and the code: Google/Apple short links, Nominatim and Photon for search, OpenFreeMap
  tiles, and the owner's own Valhalla route server over Tailscale. A new server,
  or sending anything more to one, needs the owner's OK and a README line. Location is
  asked for only when "where am I" is tapped, and never sent anywhere except as a search.
- **7:1 contrast** for every text color, **no text under 16sp**, buttons at least 56dp
  and rows at least 48dp tall, words next to icons. Must work at 200% text size and with
  TalkBack, and no two things on a screen read out the same.
- **Animations stop when Android's animations are off.**
- **Nothing slow on the main thread**: every network call and route runs off it, with a
  plain message when the phone is offline.
- **Directions are used while driving.** Spoken turns, street names and the next step
  must stay right; anything that touches them needs a test or a practice drive.
- **README.md is the user's manual.** Update it when behavior changes, in plain words.

## Before opening a PR, and after every push to one

1. Run the checks above.
2. **Review it.** Run `/code-review` on the branch with [REVIEW.md](REVIEW.md) as the rules,
   plus `/security-review`. Fix what they find, or say why not. Check every item under
   "What was asked" against what the code really does.
3. Open the PR with every section of the template filled in (What was asked, What
   changes, Testing, Risk, How to revert), then **post the review as a PR comment**:
   start with the count ("Nothing to fix." or "1 important, fixed in abc123."), then each
   finding with its file and line, its tag, and what was done about it. The owner reads
   the review there.
4. **Every later push to the PR gets the same review** of what changed since the last
   one, however small, posted as a new comment that names the commits it covers and the
   reviews that ran. If a review was skipped, the comment says so.

Merge only after the owner says "merge it". Merges to `main` publish a signed release.
