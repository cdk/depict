# Depict Endpoint Contract (Agent-Friendly)

This document describes the HTTP contract for the `depict` endpoint implemented by `DepictController`.

## Endpoint

- Method: `GET`
- Path: `/depict/{style}/{fmt}`
- Required query param: `smi`

### Path params

- `style`: depiction style preset
- `fmt`: output format

Supported `style` values:

- `cow` (color on white)
- `cot` (color on transparent)
- `bow` (black on white)
- `bot` (black on transparent)
- `wob` (white on black)
- `wot` (white on transparent)
- `cob` (color-on-black adjusted)
- `nob` (neon on black)
- `not` (neon on transparent)
- `wcot` (white-friendly color on transparent)

Supported `fmt` values (case-insensitive):

- `svg`
- `pdf`
- `png`
- `jpg`
- `gif`

## Required query param

- `smi`: structure input string.

Accepted input forms:

- Molecule SMILES
- Reaction SMILES (contains `>`)
- Molfile text containing `V2000`
- Molfile text containing `V3000`

Important:

- One request = one `smi` payload.
- Multi-line processing is frontend behavior (send one request per line).

## Optional query params

All params are optional unless noted.

- `sma` (default `""`): SMARTS query used for highlighting.
- `smalim` (default `100`): max SMARTS matches considered.
- `hdisp` (effective default `Smart`): hydrogen display mode.
- `suppressh` (default `true`): if false, `hdisp` is ignored and provided hydrogens are preserved.
- `alignrxnmap` (default `true`): align mapped reaction components.
- `anon` (default `false`): anonymized atom-symbol visibility mode.
- `annotate` (default `none`): annotation mode.
- `abbr` (default `reagents`): abbreviation mode.
- `bgcolor` (default `default`): background color override.
- `fgcolor` (default `default`): atom color override.
- `showtitle` (default `false`): embed title into depiction.
- `arw` (default `FORWARD`): reaction arrow type.
- `dat` (default `metals`): dative bond perception mode.
- `zoom` (default `1.3`): depiction zoom.
- `ratio` (default `1.1`): stroke ratio.
- `r` (default `0`): rotation in degrees.
- `f` (default `false`): horizontal flip.
- `w` (default `-1`): width.
- `h` (default `-1`): height.
- `svgunits` (default `mm`): unit string passed to SVG renderer.

## Enumerated option values

### `annotate`

- `none`
- `number`
- `mapidx`
- `colmap`
- `rxnchg`
- `atomvalue`
- `cip`

### `abbr`

For reactions:

- `on`, `true`, `yes`, `groups+agents`
- `groups`
- `reagents`, `agents`
- `off` (or omit)

For molecules:

- `on`, `true`, `yes`, `groups`
- `off` (or omit)

### `hdisp`

Accepted values map to:

- `M` or `Minimal` -> minimal hydrogens
- `P` or `Provided` -> provided hydrogens
- `S` or `Smart` or `default` -> smart hydrogens
- `C` or `Stereo` -> stereo-relevant hydrogens
- `X` or `Explicit` -> explicit hydrogens

### `arw`

- `EQU` -> equilibrium
- `NGO` -> no-go
- `RET` -> retrosynthetic
- `RES` -> resonance

### `dat`

- `y` -> always perceive dative bonds
- `m` -> metals only
- `n` -> never

### Boolean params

Boolean params accept:

- true: `t`, `true`, `on`, `1`
- false: `f`, `false`, `off`, `0`

## Color params

`bgcolor` and `fgcolor` support:

- `default` (no override)
- For `bgcolor` only: `clear`, `transparent`, `null`
- Hex-like strings parsed as RGBA pairs, e.g.:
  - `#RRGGBB`
  - `#RRGGBBAA`
  - `0xRRGGBB`

## Response contract

Success responses:

- `svg` -> `Content-Type: image/svg+xml`
- `pdf` -> `Content-Type: application/pdf`
- `png|jpg|gif` -> `Content-Type: image/{fmt}`

Headers:

- `Access-Control-Allow-Origin: *`
- `Content-Length` is set

## Error contract

- Invalid SMILES -> HTTP `400`, HTML error body with title `Invalid SMILES`.
- Any other exception -> HTTP `500`, HTML error body with exception name/message.
- Unsupported `fmt` currently results in server error path (`500`) via exception handling.

## Frontend integration notes

- Always `encodeURIComponent` the `smi` value.
- For line-based input, split lines client-side and issue one request per non-empty/non-comment line.
- If your input line includes labels/titles, strip them before sending unless you know backend parsing supports your format.

## Minimal examples

### Basic SVG depiction

`/depict/cot/svg?smi=CCO`

### Reaction depiction with highlighting and arrow

`/depict/bot/svg?smi=CCO%3EO%3ECC%3ECO&annotate=rxnchg&arw=RET`

### Styled PNG with rotation and zoom

`/depict/cow/png?smi=c1ccccc1&zoom=1.5&r=90`

