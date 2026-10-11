# 16. Statistics charts are drawn with Compose Canvas

Date: 2026-10-11

## Status

Accepted

## Context

The statistics screen needs a stacked column chart and a step-line chart. Options were drawing them with Compose Canvas, or adopting a multiplatform chart library (Vico or Koalaplot). A library saves drawing code but adds a dependency whose API and styling we would follow.

## Decision

We draw the charts ourselves with Compose Canvas in `ui`, with no chart dependency. Colors follow the documented data-visualization palette, validated for color-blind separation on the chart surfaces defined in `ChartTheme`; its slot order must not change. A wing keeps its color slot when others are hidden. Wings beyond the number of distinguishable slots are folded into one "other" series instead of inventing hues. Every chart has a legend, which also toggles wings, and a table view carrying the same numbers. Statistics are computed in the application layer, so the charts only draw.

## Consequences

Full control of theming, accessibility and light/dark variants, and nothing to upgrade. Axes, labels and hover behaviour are code we maintain, and each new chart type costs drawing work. Revisit by superseding this ADR if the number or variety of charts grows.
