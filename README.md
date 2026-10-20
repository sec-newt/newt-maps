# Newt Maps

A private Android app, part of the Newt family (built from
[newt-android-template](https://github.com/sec-newt/newt-android-template)).
It follows the [house style](NEWT_STYLE.md).

> **A personal project, in early development.**

## Install

Releases are built and signed by GitHub Actions. Install and update with
Obtainium from this repository's Releases page.

## What it does

Tap a Google Maps or Apple Maps link (or Share → Newt Maps), and it shows the place in
big type with one button per installed map app (Organic Maps, CoMaps, Magic Earth,
OsmAnd). Text-only Google links are looked up on OpenStreetMap's Nominatim; nothing is
sent to Google. Short links are followed only through Google's or Apple's own servers.

**One-time setup:** Android won't let a third-party app take google.com or apple.com links
by itself. Open Newt Maps → **Turn on link opening** → Add links → turn on every link.
