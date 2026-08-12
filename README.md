# volcoloc-core

The Volumetric Colocalization engine, as an embeddable module.

**Status (2026-08-05): built, adopted and shipping inside the plugin.**
51 tests green here; Volumetric Colocalization runs on it, its own copy of the
engine deleted, 111 tests green and **299 golden dumps unmoved, bit-for-bit.**

**Pattern:** `../PLUGIN_CORE_PATTERN.md`
**Depends on:** `net.imagej:ij` only. **Not** `oc3d-core` yet — the spec's
"via `oc3d-core`" mechanism does not work, because `provided` scope is not
transitive. See `DECISIONS.md` § 3.
**Never shipped as a jar.**

| Class | Role |
|---|---|
| `DirectionalPairRunner` | entry point — validates, scans once, measures every pair in both directions |
| `VolumeOverlap` | the measure: occupied voxels, occupied %, strongest partner, partner count, thresholded flag |
| `MultiTargetSummary` | combination patterns; `None` and `— Any —` always emitted |
| `BoundingBoxOverlap` | optional BBColoc / BB-CPC / BBVolColoc families |
| `OverlapParameters` / `OverlapResult` | input bundle and result model — **no ImageJ tables** |

Build and test:

```
mvn package     # BSD-3 only, one dependency, no Swing on any path
mvn install     # needed before the plugin's equivalence harness can run
```

## Where this stands

Migration stages 1, 2 and 3 are done.

- **The engine, extracted verbatim** — same arithmetic, traversal order,
  tie-breaks and rejection messages. Validation and channel-name normalisation
  came with it, because the normalised names are what combination patterns are
  built from and are therefore output, not presentation.
- **The plugin runs on it.** `VolColocAnalysis`, `BoundingBoxAnalysis` and
  `PrimitiveMaps` are deleted; `VolColocResult` is now a table adapter over
  `OverlapResult`.
- **Shaded and relocated** into the plugin jar as `volcoloc.internal.volcoloc`,
  alongside `oc3d-core` as `volcoloc.internal.core`. Verified on the built
  artifact and by running it with nothing on the classpath but `ij`.
- **Gated.** The extraction was first proved by running both engines side by
  side over 23 corpus cases × 13 configurations — 299 comparisons, every Tier 1
  field as raw double bits, zero differences. That test could only live while
  both engines existed, so its output was captured as goldens before the
  rewire; `GoldenEquivalenceTest` has gated every change since.

Still open:

- **Stage 4, first release** — wiki page, update site, `sites.yml` PR, Zenodo
  DOI. All three are now **drafted and validated but not submitted**, in
  `../01 - Volumetric Colocalization/docs/RELEASE_STAGE4.md` and the shared
  submission bundle. What is left needs a person: interactive Fiji acceptance
  testing, the accounts, and the live upload. Nothing here publishes anything.
- **The batch and macro-option classes stay in the plugin.** `oc3d-core` offers
  parsing primitives, not an option model, and this plugin's model is its own
  vocabulary. Sharing them needs generalisation work in the chassis that does
  not exist yet.

Decisions taken during extraction, including the two the plan asked to be
settled by reading: `DECISIONS.md`.

---

## What this is

Volumetric Colocalization's analysis engine with the dialog and entry class
stripped out, so any other plugin can compile it in and report volume overlap
without the user installing Volumetric Colocalization.

**The measure:** what fraction of object A's voxels lie inside objects in
channel B? Directional — A-in-B and B-in-A use different denominators and are
reported separately.

## Why

This plugin is at `0.1.0-SNAPSHOT` and **not yet published**. That makes it the
cheapest extraction in the family: no existing users whose numbers must not move,
and no update-site transition to manage.

It is also the strongest case for the pattern. Volumetric Colocalization was
built by copying CPC's chassis — its README says the multi-channel output "copies
CPC's source-anchored output" — so nine-plus files exist twice in the tree today.
**Extracting before publication means it never ships its duplicate.** Unlike the
StarDist variant, where the copy was written and released, here the cost is
avoidable rather than sunk.

Consumers: **3D Objects Counter+** (optional overlap columns) and
`06 - Colocalization Suite`.

## Scope

### In

```
volcoloc-core                   BSD-3, depends on oc3d-core only
  VolumeOverlap                 occupied voxels, occupied %, strongest partner,
                                partner count, thresholded flag
  DirectionalPairRunner         all pairs, both directions, correct denominators
  MultiTargetSummary            Target Coloc / Target Partner / Targets Hit;
                                `None` and `— Any —` rows always present
  OverlapResult                 per-object rows, detail rows, summary model —
                                no ImageJ tables
```

The 30% threshold is a **reporting convention, not a truth claim**, and the
continuous percentage is always retained. That must survive extraction — it is a
correctness property, not a default.

### Out — belongs to `oc3d-core`

Label/ROI ingest, macro parsing, batch discovery and running, `ToggleSwitch`,
shared dialog widgets.

**Important:** this repo's `LabelUtils` appears **stricter** than CPC's. It
rejects line/polyline/angle/point selections (no volume to measure), rejects ROIs
positioned beyond the reference stack rather than silently projecting them, and
rejects hyperstacks as label images (extra channels and frames would otherwise be
counted as further Z layers and multiply every object's volume).

CPC's version is the one being promoted into `oc3d-core`. **These stricter rules
must survive into core, not be lost to it.** Any rule present here and absent
there is a gap in CPC that core should close.

### Out — stays in the plugin

Entry class, `plugins.config`, public API (`volcoloc.VolColoc`),
`VolColocDialog` and its overlap-threshold panels, ImageJ `ResultsTable`
construction, auto-save tree wiring.

### Settled 2026-08-05 — both stay here

`BoundingBoxAnalysis` and `PrimitiveMaps` were read before deciding. **Both stay
in `volcoloc-core`**; neither is promoted to `oc3d-core`.

- `PrimitiveMaps` — generic in form, but the map that matters is keyed on a
  packed *ordered label pair*, and voxel-wise pair enumeration is this algorithm
  alone. `oc3d-core` has no equivalent today, so promoting would add shared
  surface for one consumer. Now `BoundingBoxOverlap`'s and `VolumeOverlap`'s
  package-private detail.
- `BoundingBoxAnalysis` → renamed **`BoundingBoxOverlap`**. Three named
  reporting families with this engine's thresholds and result rows: specific,
  not general.

Also noticed and deliberately **not** acted on: its private `Geometry` re-derives
bounding boxes and centroids that `oc3d-core`'s `LabelFeatureAccumulator`
already computes. Substituting it must be proved bit-identical first — its own
change, its own gate.

Full reasoning, and the presentation-flag and validation findings: `DECISIONS.md`.

## No dialog, no Swing, no `IJ.error`

Must run headless. Throws; the plugin presents.

## Consuming it

```xml
<relocation>
  <pattern>sc.fiji.volcoloc.core</pattern>
  <shadedPattern>sc.fiji.oc3dplus.internal.volcoloc</shadedPattern>
</relocation>
```

Never relocate `volcoloc.VolColoc` or the public API.

## Licence

BSD-3-Clause — see `LICENSE`, with attribution in `NOTICE`. Links
`net.imagej:ij` only — declared **directly**, not inherited through
`oc3d-core`.

The earlier wording here said "via `oc3d-core`". That cannot work: `oc3d-core`
declares `ij` at `provided` scope, and Maven does not propagate `provided`
transitively, so nothing would reach this module's compile classpath. The
dependency is declared here, `provided`, under the same `pom-scijava` parent the
plugin uses, so both resolve the identical `ij` build — which the bit-identity
gate depends on.

## Ship gate

`../oc3d-core/EQUIVALENCE_HARNESS.md`. Tier 1 (bit-identical, no tolerance):

- occupied voxel counts and occupied percentages
- strongest-partner labels and overlapping-partner counts
- thresholded flags and `Targets Hit`
- combination-pattern counts, **including `None` and `— Any —`**
- summary counts, means, medians and percentages

Corpus must include the documented ROI edge cases: ROIs with and without a slice
position, ROIs straddling the stack edge (kept and clipped), overlapping ROIs
where the later wins, rejected line/point selections, rejected RGB, rejected
hyperstack, 8-bit with a colour LUT. Sweep the threshold at 0%, 30%, 100%.

## Plan

`../01 - Volumetric Colocalization/docs/VOLCOLOC_CORE_MIGRATION_PLAN.md`
