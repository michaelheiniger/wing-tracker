# 27. The flight map is drawn with MapCompose from tiles behind a port

Date: 2026-10-17

## Status

Accepted

## Context

A flight's page shows its track on a map. Compose Multiplatform has no map. Options were drawing tiles ourselves on Compose Canvas (as the charts are, ADR 0016), the MapCompose library, MapLibre Compose (vector maps, young desktop support, native parts), or the track alone without a base map. Map tiles come from an online service; pilots read terrain, so a topographic map serves them better than a street map, and Swiss national maps cover too little abroad. Tile services ask clients to identify themselves and to cache what they fetch.

## Decision

- **Rendering.** The map is drawn with MapCompose's multiplatform artifact (version in the version catalog), confined to `ui`. It provides panning, zooming, paths and markers. Its desktop POM brings a macOS-only Compose runtime, which is excluded so it does not reach the packages.
- **Tiles.** `ui` asks the `ReadMapTile` use case for tiles; the application layer defines the `MapTileSource` port, in the supporting `maps` context, and checks that a requested tile exists at its zoom level. The port is implemented by a new JVM-only module, `infrastructure-map-tiles`, which fetches OpenTopoMap tiles with the HTTP plumbing of `infrastructure-http`, sending the application's user agent. The provider's address and its highest zoom level are constants of that adapter.
- **Cache.** Fetched tiles are kept in the platform cache folder, next to update downloads (ADR 0024), never in the data folder. A tile younger than the maximum age is served from the cache; an older one is fetched again and the cached copy serves when that fails. The cache is trimmed, oldest first, once it grows past its limit. The age and the limit are constants of the adapter.
- **Offline.** A tile that cannot be had is not drawn. The track is drawn on a plain background, and the map says that it is shown without the base map.
- **Track.** The part from take-off to landing (ADR 0018) is drawn strongly, coloured by climb and sink when the track has altitudes, the recording before and after it faded. Take-off and landing are markers with an icon and text (ADR 0019). The map shows the provider's attribution, as its licence requires.

## Consequences

The map works with a few calls instead of tile and gesture code of our own, and changing the tile provider only touches the adapter. We follow a small third-party library whose release cycle must keep up with Compose Multiplatform; replacing it touches the map composable only, since the port and the cache stay. Viewing a flight online loads tiles from a volunteer-run service, which the cache keeps to a minimum; tiles of flights never viewed online are not available offline.

## Amendments

- 2026-10-17: The flight can also be coloured by climb and sink, in bands whose limits are named constants next to the colouring in `ui`. The climb rates come from the application layer with the track statistics (ADR 0018), so both use the same averaging. The colours are map colours of the theme, each with a legend in metres per second (ADR 0019). The pilot's choice was to be remembered in the settings.
- 2026-10-17: The climb and sink colouring is the only one; a single colour added nothing, so the switch and its setting are gone before release, and the settings format stays at the version it had.
