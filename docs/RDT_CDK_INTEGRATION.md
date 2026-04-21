# Integrating RDT (Reaction Decoder Tool) into a CDK application

This note is for developers (or coding agents) who embed **RDT** (`com.bioinceptionlabs:rdt`) in another **Java** app that already uses the **Chemistry Development Kit (CDK)**. It covers atom–atom mapping from **reaction SMILES** and **MDL RXN**, and how to **detect or tag changing bonds** on real `IBond` / `IAtom` objects.

RDT is **deterministic** and does **not** require machine-learning models. It depends on CDK (same major line as declared in RDT’s `pom.xml`, currently **2.12**) and **SMSD** for subgraph / MCS work during mapping.

---

## 1. Add the dependency

**Maven:**

```xml
<dependency>
  <groupId>com.bioinceptionlabs</groupId>
  <artifactId>rdt</artifactId>
  <version>4.0.0</version>
</dependency>
```

**Gradle (after `mvn install` to `~/.m2` or a reachable repository):**

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}
dependencies {
    implementation("com.bioinceptionlabs:rdt:4.0.0")
}
```

**Java version:** RDT’s build targets **Java 25** in this repository. Your app should use a compatible JDK, or align toolchains with the RDT artifact you depend on.

**CDK alignment:** Prefer using the **same CDK 2.x line** RDT was built against to avoid subtle classpath conflicts. If you must mix versions, test thoroughly.

---

## 2. Two integration styles

| Style | When to use | You get |
|--------|-------------|--------|
| **Facade API** (`RDT.map`) | Quick integration, minimal CDK surface | `ReactionResult`: mapped SMILES, bond-change **strings**, counts, fingerprints, canonical signature |
| **Full CDK pipeline** (`ReactionMechanismTool`) | You already have `IReaction`, need `IBond`/`IAtom` tagging | `BondChangeCalculator` on the **mapped** reaction, lists of `BondChange`, reaction-center atoms, stereo lists |

Both ultimately run **`ReactionMechanismTool`**; the facade just parses SMILES and packages outputs into `ReactionResult`.

---

## 3. Map reaction SMILES (facade)

```java
import com.bioinceptionlabs.reactionblast.api.RDT;
import com.bioinceptionlabs.reactionblast.api.ReactionResult;

ReactionResult result = RDT.map("CC(=O)O.OCC>>CC(=O)OCC.O");
// Optional: RDT.map(smiles, generate2D, complexMapping);  // complex = ring-heavy cases

if (result.isMapped()) {
    String mapped = result.getMappedSmiles();           // AAM in SMILES
    int total = result.getTotalBondChanges();
    // String-level features (good for logs, UI, ML-free similarity)
    var formedCleaved = result.getFormedCleavedBonds();
    var orderChanges = result.getOrderChangedBonds();
    var stereo = result.getStereoChangedBonds();
    var centre = result.getReactionCentreFingerprint();
    String signature = result.getReactionSignature(); // canonical R-string-style summary
    String algo = result.getAlgorithm();              // e.g. RINGS, MIN, MAX, MIXTURE
}
```

`ReactionResult` is **immutable** and avoids tying your UI layer to CDK types. Use **`RDT.compare(smiles1, smiles2)`** if you only need a **Tanimoto-style** similarity on bond-change fingerprints.

---

## 4. Map from CDK: reaction SMILES

Use this when you already build or receive `IReaction` (e.g. from your own parsers).

```java
import org.openscience.cdk.interfaces.IReaction;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import com.bioinceptionlabs.reactionblast.mechanism.ReactionMechanismTool;
import com.bioinceptionlabs.reactionblast.tools.StandardizeReaction;

SmilesParser sp = new SmilesParser(SilentChemObjectBuilder.getInstance());
IReaction reaction = sp.parseReactionSmiles("CC>>CC");
reaction.setID("my_rxn");

ReactionMechanismTool rmt = new ReactionMechanismTool(
        reaction,
        true,   // forcedMapping: recompute mapping even if present
        true,   // generate2D: perceive stereo for 2D
        false,  // generate3D
        true,   // checkComplex: ring / harder cases (more expensive)
        false,  // accept_no_change: set true for transporter-like “no bond change” cases
        new StandardizeReaction());
```

Typical `ReactionMechanismTool` constructor parameters:

- **`forcedMapping`**: `true` = always run RDT mapping; `false` = can **reuse** existing atom–atom mappings on the reaction if they look complete.
- **`checkComplex`**: `true` = enable strategies tuned for **ring systems** (CLI `-c`); slower but broader coverage.
- **`accept_no_change`**: `true` = allow solutions with **no bond-order change** (e.g. transport); `false` = chemistry-style mapping only.

If stoichiometry is **unbalanced**, RDT may **skip** mapping unless `forcedMapping` is `true` (see implementation logs / behaviour in `ReactionMechanismTool`).

---

## 5. Map from MDL RXN (V2000)

RDT ships **`MDLRXNV2000Reader`** under `com.bioinceptionlabs.reactionblast.tools.ChemicalFileIO`. Read an RXN file into CDK’s `IReaction`, then pass it to `ReactionMechanismTool` as above.

```java
import java.io.FileReader;
import org.openscience.cdk.Reaction;
import com.bioinceptionlabs.reactionblast.tools.ChemicalFileIO.MDLRXNV2000Reader;

IReaction reaction;
try (MDLRXNV2000Reader reader = new MDLRXNV2000Reader(new FileReader("reaction.rxn"))) {
    reaction = reader.read(new Reaction());
}
reaction.setId("from_rxn");

ReactionMechanismTool rmt = new ReactionMechanismTool(
        reaction, true, true, false, true, false, new StandardizeReaction());
```

Round-tripping via reaction SMILES (as in `ChemicalFormatParser` in this repo) is optional; for integration, feeding **`IReaction` directly** is usually enough.

---

## 6. Detect and tag changing bonds (CDK objects)

After mapping, read the **selected** solution and its **`BondChangeCalculator`**:

```java
import com.bioinceptionlabs.reactionblast.mechanism.MappingSolution;
import com.bioinceptionlabs.reactionblast.mechanism.BondChangeCalculator;
import com.bioinceptionlabs.reactionblast.mechanism.MechanismHelpers.BondChange;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IAtom;

MappingSolution solution = rmt.getSelectedSolution();
if (solution == null) {
    // mapping failed or was skipped (e.g. unbalanced reaction with forcedMapping false)
    return;
}
BondChangeCalculator bcc = solution.getBondChangeCalculator();
```

### 6.1 Per-bond pairing (formed / cleaved / order change)

```java
for (BondChange bc : bcc.getBondChangeList()) {
    IBond rBond = bc.getReactantBond();  // null if bond is formed
    IBond pBond = bc.getProductBond();   // null if bond is cleaved
    float delta = bc.getBondChangeDelta();
    // Tag atoms/bonds in your model using rBond / pBond and map numbers from atoms
}
```

`BondChange` pairs **reactant-side** and **product-side** `IBond` instances (one side may be `null` for pure formation or cleavage).

### 6.2 Maps keyed by bond (convenience)

`BondChangeCalculator` also exposes categorised maps, for example:

- `getBondFormedProduct()`, `getBondCleavedReactant()`
- `getBondOrderReactant()`, `getBondOrderProduct()`

Use these if you prefer to iterate bonds by role rather than the unified list.

### 6.3 Reaction centre and stereo

- **Reaction-centre atoms:** `bcc.getReactionCenterSet()`
- **Stereo:** `bcc.getStereoChangeList()`, `bcc.getConformationChangeList()`
- **Fingerprints (weighted patterns):** `getFormedCleavedWFingerprint()`, `getOrderChangesWFingerprint()`, `getStereoChangesWFingerprint()`, `getReactionCenterWFingerprint()` (may throw `CDKException` in edge cases)

### 6.4 Mapped reaction for export

```java
IReaction mapped = bcc.getReaction();  // throws Exception in API
```

You can serialize with CDK **`SmilesGenerator`** using `SmiFlavor.AtomAtomMap` to emit **mapped reaction SMILES**, consistent with the `RDT` facade.

### 6.5 Atom–atom mapping map

- `bcc.getMappingMap()` / `bcc.getAtomAtomMappings()` — `Map<IAtom, IAtom>` between reactant and product atoms for the chosen mapping.

---

## 7. What else is useful for an application?

These features are often valuable next to “map + bond changes”:

1. **Reaction signature / canonical hash** (`ReactionResult.getReactionSignature()`, `getCanonicalHash()`) — stable, comparable summaries of **electron/bond-change pattern** for deduplication or search.
2. **Cross-reaction similarity** — `RDT.compare(a, b)` or fingerprint Tanimoto on `ReactionResult` (see `ReactionResult` API).
3. **Algorithm id** — `getAlgorithm()` / `MappingSolution.getAlgorithmID()` to log which strategy (**MIN**, **MAX**, **MIXTURE**, **RINGS**) won.
4. **Stereo and reaction-centre fragments** — `getReactionCenterFragmentList()`, `getReactionCentreTransformationPairs()` for mechanistic reporting or UI highlighting.
5. **Energy heuristics** — `getTotalBondBreakingEnergy()`, `getEnergyDelta()` (approximate, for ranking or display).
6. **User-provided mappings** — build `IReaction` with atom–atom maps already set, call `ReactionMechanismTool` with **`forcedMapping = false`** so RDT can **trust** existing maps when complete.
7. **Transporter / no–bond-change reactions** — `accept_no_change = true` when the chemistry is intentionally “mapping only”.
8. **Unbalanced reactions** — expect **warnings** or **skipped** mapping; fix stoichiometry or set **`forcedMapping`** knowingly.

---

## 8. Troubleshooting checklist

| Symptom | Things to check |
|--------|------------------|
| `getSelectedSolution()` is null | Unbalanced reaction + `forcedMapping` false; parse failure; empty reactants/products |
| Odd bond counts | Run **`StandardizeReaction`** path (constructor already does); ensure implicit H / aromaticity consistent with CDK expectations |
| Slow on large systems | `checkComplex` true is heavier; mapping uses internal timeouts (see codebase `CallableAtomMappingTool`, `GraphMatcher`) |
| Classpath errors | CDK version alignment; single SMSD version on the classpath |

---

## 9. Primary classes to import

| Purpose | Package / class |
|--------|-------------------|
| One-shot mapping + summary | `com.bioinceptionlabs.reactionblast.api.RDT`, `ReactionResult` |
| Full pipeline | `com.bioinceptionlabs.reactionblast.mechanism.ReactionMechanismTool`, `MappingSolution`, `BondChangeCalculator` |
| Bond-level rows | `com.bioinceptionlabs.reactionblast.mechanism.MechanismHelpers.BondChange` |
| Standardization | `com.bioinceptionlabs.reactionblast.tools.StandardizeReaction` |
| RXN V2000 read | `com.bioinceptionlabs.reactionblast.tools.ChemicalFileIO.MDLRXNV2000Reader` |

---

## 10. Licence

RDT is **LGPL-3.0**. Embedding it in another application may impose obligations (especially for distribution); check your legal requirements.

---

*Generated for agent/developer onboarding. For CLI usage and benchmarks, see the repository `README.md`.*
