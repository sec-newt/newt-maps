# Newt Android template

The starting point for every Newt app (like [Newt Phone](https://github.com/sec-newt/newt-phone)
and [Newt Home](https://github.com/sec-newt/newt-home)). It's a tiny working app with
the shared Newt pieces already in place:

- **The Newt look** from the shared [Newt library](https://github.com/sec-newt/newt-android-lib):
  Tokyo Night theme, the bundled fonts and shared headers and outlines, following
  [NEWT_STYLE.md](NEWT_STYLE.md). Plus the newt icon, which each app gives its own badge.
- **The CI checks:** tests with screenshots in light, dark and 200% text, accessibility
  checks, lint, a secret scan, a permission allowlist, and signed releases from `main`.
- **Security files:** `SECURITY.md`, Dependabot, actions pinned to exact versions, and
  no permissions or internet access to start.

## Starting a new app

1. **Make the repo:**
   `gh repo create sec-newt/newt-notes --template sec-newt/newt-android-template --public --clone`
2. **Name it:** inside the new folder, run `scripts/new-app.sh "Newt Notes" notes`,
   then commit and push.
3. **Lock it down:** `gh-harden.sh sec-newt/newt-notes`. It applies the repo settings
   a template can't copy (rulesets, scanning, Actions limits) and asks for the shared
   signing key so releases work.
4. **Give it its own badge:** change the badge color and symbol in
   `app/src/main/res/drawable/ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml`.

## Keeping apps in step

The look stays in step on its own: change it in the Newt library, publish a new version,
and each app gets a Dependabot pull request. Everything else here (the workflow, scripts,
docs) is copied once, so later changes to the template don't reach existing apps.
