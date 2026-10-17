# 28. The ground under a track comes from terrain tiles behind a port

Date: 2026-10-17

## Status

Accepted

## Context

The altitude profile of a flight shows the pilot's altitude; pilots also want to see the ground under them, to read the height above take-off, ridges and landing. A track records no terrain, so a terrain model is needed. Options were the open Terrain Tiles dataset (tiles that encode heights in PNG colours, worldwide, with detailed sources in the Alps), a web elevation API (coarser, rate-limited, needs a connection for every new flight) and the Swiss national height service (precise, but Switzerland only).

## Decision

- **Port.** The application layer defines a `TerrainElevationSource` port in the supporting `maps` context: heights for a list of points, null where a height cannot be had. The `ReadGroundProfile` use case samples the track and gives the ground over time; nothing derived is stored, like the track's own statistics (ADR 0018).
- **Adapter.** `infrastructure-map-tiles` implements it with Terrain Tiles in the Terrarium encoding, read at one zoom level and interpolated between pixels. Tiles are fetched and cached the same way as map tiles (ADR 0027), in their own folder of the platform cache with their own age and size limits. The address, zoom level and limits are constants of the adapter.
- **Display.** The profile draws the ground under the flight, adds the ground and the height above it to its readout, names the data sources, and says when part of the ground could not be had or reading it failed. The flight is shown either way.

## Consequences

The ground is available worldwide and offline for flights viewed before, with no account or rate limit, and the tile cache is shared code. Heights are only as good as the terrain model at that place, about 30 m of detail in the Alps. Loggers write GPS altitude either above the geoid or above the ellipsoid, while the terrain is above sea level, so in Switzerland the pilot can appear about 50 m above or below the ground at landing; no correction is made. The dataset is free public data whose hosting could change, which only touches the adapter.
