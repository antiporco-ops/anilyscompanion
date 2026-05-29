# Changelog

## 2026-05-29
- Added a minimal Wear OS module for AniLys Battery that shows the latest phone battery snapshot from the Wear Data Layer and can request a refresh from the phone.

## 2026-05-26
- Refined Battery tab wording and protection-limit semantics so the columns map to protection level, regular use, and battery life.
- Reorganized the Battery tab into "Battery on watch" and "Protect battery", with fixed care limit choices and more compact notice controls.
- Added configurable phone battery alerts with high/low limits, notification permission handling, sound/vibration options, test alert, and anti-spam state.
- Applied status-bar WindowInsets to the Faces header only when it overlaps the system bar, preserving the existing layout on devices already inset by the decor.
- Finalized the promotion coupon hierarchy with a stronger primary redeem action, compact secondary copy action, and localized default promo-code titles.
- Refined the AniLys promotion details card into a compact coupon layout and added localized promotion JSON fields with backward compatibility for plain strings.
- Added remote-catalog promotion support for watch face details, including hidden promo-code reveal, clipboard copy, and Google Play redeem links.

## 2026-03-28
- Polished the Faces footer note so it now follows the selected watch face status for production, closed testing, and coming soon states.
- Refined the `Coming soon` CTA presentation with a slightly more intentional disabled appearance while keeping the layout and behavior unchanged.

## 2026-03-28
- Added contextual Faces CTA states driven by remote catalog status: `Install on watch`, `How to test`, and `Coming soon`.
- Added a closed-testing bottom sheet with localized fallback copy and optional Join Group / Opt-in / Install links from the remote catalog.
- Hardened Faces selection persistence across remote catalog refreshes by reselecting items by stable id/package instead of index only.
- Applied stable remote catalog sorting from the `sort` field while keeping backward compatibility with older catalog items.

## 2026-03-07
- Refactored phone navigation to a single-activity host with persistent bottom tabs (`Faces`, `Battery`, `Settings`) backed by three top-level fragments.
- Moved watch face catalog/home UI and behavior to `FacesFragment`.
- Added `BatteryFragment` for phone-battery-on-watch controls as a first-class tab.
- Replaced dialog-based settings with a full-screen `SettingsFragment` with immediate theme/language apply.
- Added AniLys symbol logo in the Faces tab top app bar before the `AniLys WatchFaces` title.
- Polished Faces visual balance with a larger header logo and unified top/bottom surface framing colors.
- Polished the Faces watch-card selected state with a subtle premium highlight (stronger stroke, refined surface/elevation, and clearer label emphasis) while preserving existing selection behavior.
