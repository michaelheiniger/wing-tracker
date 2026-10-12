#!/usr/bin/env python3
"""Exports the flight list of a public Syride pilot profile to a JSON file.

It reads the same activity feed the profile page loads while scrolling, so it needs no login and does not touch the
flight pages, which Syride protects with a bot check; tracks (IGC files) are therefore not downloaded. Flights logged
without an instrument have no track at all and are marked "hasTrack": false.

Usage: scripts/syride-export.py <pilot-name> [output.json]
"""
import json
import re
import sys
import time
import urllib.request
from datetime import datetime, timezone
from html import unescape

BASE = "https://syride.com"
PAGE_SIZE = 10
# Be gentle with a small community site: one request per second.
PAUSE_SECONDS = 1.0
MAX_PAGES = 500


def fetch(url: str, referer: str) -> str:
    request = urllib.request.Request(
        url, headers={"User-Agent": "Mozilla/5.0 (personal flight export)", "Referer": referer, "X-Requested-With": "XMLHttpRequest"}
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read().decode("utf-8", "replace")


def rider_id(profile_html: str) -> int:
    found = re.search(r"idRider=(\d+)", profile_html)
    if not found:
        raise SystemExit("Could not find the pilot id on the profile page")
    return int(found.group(1))


def activities(profile_url: str, rider: int):
    before = 0
    for _ in range(MAX_PAGES):
        url = f"{BASE}/ajax/pilotes/activites.php?limit={PAGE_SIZE}&l=en&idRider={rider}&apli=0"
        if before:
            url += f"&append=1&beforeId={before}"
        html = fetch(url, profile_url)
        blocks = [b for b in re.split(r"(?=<div id='activite\d+')", html) if b.startswith("<div id='activite")]
        if not blocks:
            return
        yield from blocks
        before = int(re.match(r"<div id='activite(\d+)'", blocks[-1]).group(1))
        time.sleep(PAUSE_SECONDS)


def text(fragment: str) -> str:
    return unescape(re.sub(r"<[^>]+>", " ", fragment)).replace("\xa0", " ").strip()


def local_iso(raw: str):
    found = re.match(r"(\d{2})/(\d{2})/(\d{4}) - (\d{2})h(\d{2})", raw or "")
    if not found:
        return None
    day, month, year, hour, minute = found.groups()
    return f"{year}-{month}-{day}T{hour}:{minute}:00"


def minutes(raw: str):
    found = re.fullmatch(r"(?:(\d+)h)?\s*(?:(\d+)min)?", (raw or "").strip())
    if not found or not any(found.groups()):
        return None
    return int(found.group(1) or 0) * 60 + int(found.group(2) or 0)


def number(raw: str, unit: str):
    found = re.fullmatch(rf"([\d.,]+)\s*{unit}", (raw or "").strip())
    return float(found.group(1).replace(",", ".")) if found else None


def labelled(block: str) -> dict:
    """The 'Label : value' lines of a flight card."""
    values = {}
    for label, rest in re.findall(r"<ul[^>]*>\s*([^<:]+?)\s*:\s*(.*?)</ul>", block, flags=re.S):
        values[label] = rest
    return values


def flight(block: str):
    if "Flight name" not in block:
        return None  # a photo or another kind of activity
    activity_id = int(re.match(r"<div id='activite(\d+)'", block).group(1))
    published = re.search(r"Published on <font[^>]*>([^<]+)", block)
    flight_id = re.search(r"showFlightInPopup\((\d+)", block)
    kind = re.search(r'id="type\d+">\s*([^<]+)', block)
    values = labelled(block)

    def value(label):
        return text(re.sub(r"\(.*", "", values[label], flags=re.S)) if label in values else None

    site_link = re.search(r"<div style='display:inline' id='site\d+'>\s*at\s*<a href='([^']+)'>([^<]+)</a>", block)
    country = re.search(r"id=siteLocalisation[^>]*>\(<img src='[^']*/flags/([^.']+)\.", block)
    wing_raw = values.get("Used wing", "")
    brand = re.search(r"\(([^<(]+?)\s*<img", wing_raw)
    thumb = re.search(r'<img id="flightThumb_\d+"[^>]*src="([^"]+)"', block)
    comments = re.search(r"title='(\d+) comment", block)
    name = value("Flight name")
    flown = value("Date")
    duration = value("Flight time")
    return {
        "activityId": activity_id,
        "flightId": int(flight_id.group(1)) if flight_id else None,
        "hasTrack": flight_id is not None,
        "kind": text(kind.group(1)) if kind else None,
        "name": None if name in (None, "not set") else name,
        "flownAtLocal": local_iso(flown),
        "publishedAtLocal": local_iso(published.group(1)) if published else None,
        "durationMinutes": minutes(duration),
        "site": {
            "name": text(site_link.group(2)) if site_link else value("Flying site"),
            "url": BASE + site_link.group(1) if site_link else None,
            "country": country.group(1).replace("_", " ") if country else None,
        },
        "wing": {"name": value("Used wing"), "brand": text(brand.group(1)) if brand else None},
        "distanceKm": number(value("Distance"), "km"),
        "topAltitudeM": number(value("Top altitude"), "m"),
        "instrument": value("Used instrument"),
        "thumbnailUrl": BASE + thumb.group(1) if thumb else None,
        "comments": int(comments.group(1)) if comments else 0,
        # What the page said, kept so nothing is lost when a field above could not be understood.
        "raw": {"date": flown, "flightTime": duration, "distance": value("Distance"), "topAltitude": value("Top altitude")},
    }


def main() -> None:
    pilot = sys.argv[1] if len(sys.argv) > 1 else sys.exit(__doc__)
    output = sys.argv[2] if len(sys.argv) > 2 else f"exports/syride-{pilot}.json"
    profile_url = f"{BASE}/en/pilots/{pilot}"
    rider = rider_id(fetch(profile_url, profile_url))
    flights, skipped = [], 0
    for block in activities(profile_url, rider):
        parsed = flight(block)
        if parsed:
            flights.append(parsed)
        else:
            skipped += 1
    flights.sort(key=lambda f: (f["flownAtLocal"] or "", f["activityId"]))
    document = {
        "source": profile_url,
        "pilot": pilot,
        "exportedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "note": "Times are local clock times as shown by Syride. Flights without a track have no IGC file.",
        "flightCount": len(flights),
        "withoutTrackCount": sum(1 for f in flights if not f["hasTrack"]),
        "otherActivitiesSkipped": skipped,
        "flights": flights,
    }
    import os

    os.makedirs(os.path.dirname(output) or ".", exist_ok=True)
    with open(output, "w", encoding="utf-8") as file:
        json.dump(document, file, ensure_ascii=False, indent=2)
        file.write("\n")
    print(f"{len(flights)} flights ({document['withoutTrackCount']} without a track), {skipped} other activities skipped -> {output}")


if __name__ == "__main__":
    main()
