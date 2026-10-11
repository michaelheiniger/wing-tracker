# 19. A brand theme with checked contrast and status colours

Date: 2026-10-11

## Status

Accepted

## Context

The default Material colours gave the application no identity and no consistent meaning for states such as "due". The pilot scans lists for what needs attention, in daylight and in the dark, so colour has to carry meaning reliably and stay readable.

## Decision

The theme is Material 3 with colour schemes written out for light and dark in `AppColors`. The palette comes from the application icon: a sky blue as the main colour and the canopy orange as the single accent, used for primary actions only, on neutrals tinted towards the blue. Meaning has fixed colours: critical is the scheme's error, warning and success are added as status colours, and none of them stands alone, because each is paired with its own icon and a text. Cards are flat tonal surfaces with a hairline border. Shapes and type weights are set once in the theme. The chart palette stays separate and is validated for its own surfaces (ADR 0016). `ColorContrastTest` computes the contrast of every text and container pair in both modes and fails below WCAG AA, so no colour change can silently make text unreadable.

## Consequences

The look is consistent and changes in one place. Adding a colour role or a new pair means adding it to the test. The accent is meant to stay rare; spreading it over many elements would dilute it. A new status needs a colour, an icon and a text together.
