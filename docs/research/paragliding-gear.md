# Paragliding gear: research notes

Researched 2026-10-11 to inform the domain model. These are **findings from public sources, not requirements**: intervals vary by manufacturer and model, so the app must treat them as per-item data, never as constants in code.

## Gear types
- **Wing (paraglider)**: certified by EN 926-2 (current edition is the 2013 one with a 2021 amendment). It classifies flight-safety characteristics by the pilot skill a wing demands. LTF (Germany, formerly DHV) has used the same class names as EN since 2010. [BSI standard entry](https://knowledge.bsigroup.com/products/paragliding-equipment-paragliders-requirements-and-test-methods-for-classifying-flight-safety-characteristics-2), [Dudek on certification](https://dudek.eu/en/testy-i-certyfikaty/). Certification class definitions were not in the sources found; to be confirmed.
- **Harness (seat)**: certified under EN 1651 (current certificates cite the 2018 edition with a 2020 amendment) and EN 12491 for the reserve-related parts; older ones cite LTF NfL II 91/09. Protectors may be integrated airbags or removable foam. [Niviuk certificate example](https://niviuk.com/biblioteca/items/1880_A/Homologation_WATSON_2_niviuk.pdf), [BSI harness standard](https://knowledge.bsigroup.com/products/paragliding-equipment-harnesses-safety-requirements-and-strength-tests-1).
- **Reserve parachute**: has a repack cycle and a maximum service life, both model-specific.

## Maintenance findings
| Item | Finding | Source |
|------|---------|--------|
| Wing | Periodic check by time **or** usage, whichever comes first; the time and the flight-hours limit differ by manufacturer and model | [Advance](https://www.advance.swiss/en/services/maintenance/glider-check), [Niviuk](https://niviuk.com/en/guide-maintaining-caring-paraglider), [Dudek](https://dudek.eu/en/przeglady-i-naprawy/) |
| Wing | Commercial use (schools, tandem) shortens the interval | [Dudek](https://dudek.eu/en/przeglady-i-naprawy/), [MACPARA manual](https://www.macpara.com/media/1348/service-manual-elan2.pdf) |
| Wing | Checks should be done by the manufacturer or an authorised workshop; Advance ties warranty to it | [Advance](https://www.advance.swiss/en/services/maintenance/glider-check) |
| Wing | A full check covers porosity, line measurement and trim, line and fabric visual inspection. A separate "zero inspection" exists for new wings (Dudek) and a trim check early in a wing's life (Ozone) | [Niviuk](https://niviuk.com/en/glider-check), [Planet Paragliding](https://planetparagliding.com.au/products/full-paraglider-service), [Dudek](https://dudek.eu/en/przeglady-i-naprawy/) |
| Harness | Swiss association advises regular checks together with the wing check; airbag protection can fade without visible signs | [SHV/CIAM airbag paper](https://ciam.fai.org/sites/default/files/basic_page/document/airbagproblematik_en.pdf) |
| Reserve | Repack interval and life limit are model-specific; some also require periodic full inspections by a specialist | [GIN manuals](https://www.gingliders.com/pdf/rs-g-lite-manual-en.pdf), [Advance SQR](https://manual.advance.ch/en/sqr_classic/934), [High Adventure Beamer 3](https://www.highadventure.ch/wp-content/uploads/2015/01/Beamer_3_manual_en.pdf) |

**Not found:** any source using "partial check" as an official tier. The user's own terms (partial / full) are therefore a product vocabulary, to be mapped onto manufacturer service names. Open question for the brainstorm.

## IGC files
The IGC format is the FAI standard text format for GNSS flight recorders. Records are identified by their first letter: A (recorder id), H (header), I/J (extensions), C (task), B (position fixes), E/F/K/L (events, satellites, extra data), G (security signature). The authority is the *Technical Specification for IGC-Approved GNSS Flight Recorders* published by the FAI Gliding Commission ([2024 edition](https://fai.org/sites/default/files/igc_specification_2024_with_al9.pdf)); overview: [Wikipedia](https://en.wikipedia.org/wiki/IGC_(file_format)).

Implications:
- Flight date, start/end time and therefore duration can be derived from the B-record fixes; the header supplies the date.
- Files may carry glider/pilot metadata in H records, which is free text and unreliable for matching a wing.
- A signature (G record) may exist; importing should keep the original file untouched.

## Implications for the domain (to validate with the user)
- Wing maintenance due-dates depend on **time and usage**, whichever comes first, which makes flight hours per wing a first-class derived value.
- The interval rule must be configurable per item (and may change when use turns commercial).
- Reserves have two clocks: repack cycle and total life.
