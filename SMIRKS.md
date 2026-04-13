# Advanced SMIRKS options

This page explains what the advanced SMIRKS options do to fine tune the 
behaviour of a transformation. In each section there are vignettes which
show the output of turning this option **off** (default) and **on**.

## Different Parts (p)

A SMIRKS that selects multiple parts (separated by ``.``) can match anywhere 
including the same molecule. In the example below we pick two carbon 
atoms and make a new bond (as well as updating the hydrogen count). This option 
forces the multiple parts to match separate molecules.

```
SMIRKS:       [#6:1][H].[#6:2][H]>>[*:1]-[*:2]
INPUT:        CCC.CCC
OUTPUT(off):  C1CC1.CCC
OUTPUT(on):   CCCCCC
```

This is also possible by wrapping each part in a separate component group, in 
SMIRKS this is done with 0-level parenthesis: 

```
SMIRKS:       ([#6:1][H]).([#6:2][H])>>[*:1]-[*:2]
INPUT:        CCC.CCC
OUTPUT(off):  CCCCCC
OUTPUT(on):   CCCCCC
```

It is also possible to force the component multiple parts to match the same input
molecule. This will work even if the "Different Parts" option is set. 

```
SMIRKS:      ([#6:1][H].[#6:2][H])>>[*:1]-[*:2]
INPUT:       CCC.CCC
OUTPUT(off): C1CC1.CCC
OUTPUT(on):  C1CC1.CCC
```

## Ignore Element Changes (e)

A SMIRKS may specify a change of element, if this option is enabled any element
changes are ignored.

```
SMIRKS:       [Pb:1]>>[Au:1]
INPUT:        [Pb]
OUTPUT(off):  [Au]
OUTPUT(on):   [Pb] (unchanged)
```

Allowing elements to change is an optimisation and can be useful to make a 
pattern run faster.

## Ignore Impl H Changes (h)

Changing implicit hydrogen count can be specified as shown below, this option
allows you to ignore such changes:

```
SMIRKS:       [Ch3:1]>>[Ch2:1]
INPUT:        CC
OUTPUT(off):  [CH2]C
OUTPUT(on):   CC (unchanged)
```

This option allows you to ignore such changes. If the hydrogens on the input
are explicit the transform will not apply because no match can be found (there
are no implicit hydrogens).

```
SMIRKS:       [Ch3:1]>>[Ch2:1]
INPUT:        [H]C([H])([H])C([H])([H])[H]
OUTPUT(off):  <none>
OUTPUT(on):   <none>
```

The more portable way of changing hydrogen count is to specify the removal of
the explicit hydrogen in the SMIRKS. Internally is optimised and is efficient
to run working correctly even if the input hydrogens are not explicit.

```
SMIRKS:       [C:1][H]>>[C:1]
INPUT:        CC
OUTPUT(off):  [CH2]C
OUTPUT(on):   [CH2]C
```

## Ignore Total H Changes (H)

Changing total hydrogen count can be specified as show below, this will update
both the implicit and/or explicit hydrogens as needed. This option allows you
to ignore such changes:

```
SMIRKS:       [CH3:1]>>[CH2:1]
INPUT:        CC
OUTPUT(off):  [CH2]C
OUTPUT(on):   CC
```

If the hydrogens on the input are explicit or a mixture the transform will still
apply correctly.

```
SMIRKS:       [CH3:1]>>[CH1:1]
INPUT:        C([H])([H])C([H])([H])[H]
OUTPUT(off):  C([H])C([H])([H])[H]
OUTPUT(on):   C([H])([H])C([H])([H])[H] (unchanged)
```

The more portable way of changing hydrogen count is to specify the removal of
the explicit hydrogen in the SMIRKS. Internally is optimised and is efficient
to run working correctly even if the input hydrogens are not explicit.

```
SMIRKS:       [C:1][H]>>[C:1]
INPUT:        CC
OUTPUT(off):  [CH2]C
OUTPUT(on):   [CH2]C
```

## Ignore H0 (0)

Changing total hydrogen count to 0 can be specified by setting ``H0`` some 
toolkits treat this the same as not specifying the hydrogens at all. This option
allows you to change the total hydrogen count to anything except 0.

```
SMIRKS:       [CH3:1]>>[CH0:1]
INPUT:        CC
OUTPUT(off):  [C]C
OUTPUT(on):   CC
```

## Overwrite Bond (o)

This option controls what to do if a bond is created between atoms which are
already bonded. By default, if atoms are already bonded and a bond is made the
transform will fail to apply. Enabling this option allows the existing bond to
be overwritten and changed.

```
SMIRKS:       ([*:1][H].[*:2][H])>>[*:1]=[*:2]
INPUT:        CC
OUTPUT(off):  <none>
OUTPUT(on):   C=C
```

## Remove Unmapped (m)

This option allows you to automatically delete parts of the structure
which aren't mapped/selected by the SMIRKS transform. In the example below
we are removing an oxygen connected to an aromatic carbon. We have not 
constrained any open valence on the oxygen and so when we run the transform on
a molecule with an **-OEt** group we end up with ``[CH2]C`` (*ethyl*) left over. 

```
SMIRKS:       [c:1]O>>[c:1][H]
INPUT:        c1ccccc1OCC
OUTPUT(off):  c1ccccc1.[CH2]C
OUTPUT(on):   c1ccccc1
```

This is a very useful option as it allows fewer/simpler transforms. A 
suzuki-coupling may work on ``*B(O)O`` (*boronic acid*) or 
``*B(OC(C)(C)C1(C)C)O1`` (*pinacol boronic ester*). Without this option we would
need to write multiple transforms to handle the different functional groups. 


Despite the utility of this option, care should be taken when writing patterns 
to precisely specify what to match/change. For example consider if the oxygen
was in a ring: 

```
SMIRKS:       [c:1]O>>[c:1][H]
INPUT:        c1ccccc2c1OCC2
OUTPUT(off):  c1cccc(c1)C[CH2]
OUTPUT(on):   c1cccc(c1)C[CH2]
```

We can avoid this my make sure we are precise with what select specifying 
ring/chain expressions as needed:

```
SMIRKS:       [cx2:1]-!@[OHD1]>>[c:1][H]
INPUT:        c1ccccc2c1OCC2
OUTPUT(off):  <none>
OUTPUT(on):   <none>
```

## Recalculate Hydrogens (r)

Some toolkits allow bonds to be added/removed from atoms and have the hydrogen 
count automatically adjust as needed based on standard valence. Enabling this 
option will recalcuate the hydrogen count on any atoms which had bond changes. 
In the example below we have removed an oxygen but not updated the hydrogen
count on the connect carbon, with this option this can be done automatically.

```
SMIRKS:       [C:1]O>>[C:1]
INPUT:        c1ccc([CH])cc1CO
OUTPUT(off):  c1ccc([CH])cc1[CH2]
OUTPUT(on):   c1ccc([CH])cc1C
```

Note the other under-valent carbon is not modified. This option currently uses
the Chemistry Development Kit's (CDK) atom types to determine the expected 
hydrogen count.

## Explicit Hydrogens (x)

Automatically expand implicit hydrogens to explicit hydrogens before a 
transform is run. Hydrogens are only added if the pattern can match an explicit
hydrogen (including ``[*]``).

```
SMIRKS:       ([*:1].[*:2])>>[*:1][*:2]
INPUT:        CC
OUTPUT(off):  <none>
OUTPUT(on):   C1[CH3][H]1
```

# Presets

Using combination of these presets allows the emulation of other toolkits.

## RDKit

To run transforms like *RDKit Reaction "SMARTS"* use the options:

- Different Parts (p)
- Ignore Impl H Changes (h)
- Ignore H0 (0)
- Overwrite Bond (o)
- Remove Unmapped (m)
- Recalculate Hydrogens (r)

Due to subtle differences in valence/aromaticity models and internal ordering 
the output may not exactly match RDKit but these options should make simple 
unambiguous patterns work similarly.

## Daylight

To run transforms like *Daylight* use the options:

- Ignore Element Changes (e)
- Ignore Impl H Changes (h)
- Ignore Total H Changes (H)
- Explicit Hydrogens (x)