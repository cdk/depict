package org.openscience.cdk.app;

import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.ReactionRole;
import org.openscience.cdk.aromaticity.Aromaticity;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.graph.ConnectedComponents;
import org.openscience.cdk.graph.ConnectivityChecker;
import org.openscience.cdk.graph.Cycles;
import org.openscience.cdk.graph.GraphUtil;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IAtomContainerSet;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.isomorphism.Transform;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmiFlavor;
import org.openscience.cdk.smiles.SmilesGenerator;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.smirks.Smirks;
import org.openscience.cdk.smirks.SmirksOption;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import org.openscience.cdk.tools.manipulator.HydrogenState;
import org.openscience.cdk.tools.manipulator.ReactionManipulator;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.openscience.cdk.smirks.SmirksOption.EXPAND_HYDROGENS;
import static org.openscience.cdk.smirks.SmirksOption.PEDANTIC;

@CrossOrigin
@Controller
public class ReactController {

    private final IChemObjectBuilder builder = SilentChemObjectBuilder.getInstance();
    private final SmilesParser smipar = new SmilesParser(builder);
    private final SmilesGenerator smigen = new SmilesGenerator(SmiFlavor.Default + SmiFlavor.UseAromaticSymbols + SmiFlavor.AtomAtomMap);
    private final SmilesGenerator cansmi = new SmilesGenerator(SmiFlavor.Unique + SmiFlavor.UseAromaticSymbols);

    enum Direction {
        forward(ReactionRole.Reactant, ReactionRole.Product),
        backward(ReactionRole.Product, ReactionRole.Reactant),
        normalize(ReactionRole.None, ReactionRole.None),
        map(ReactionRole.None, ReactionRole.None);

        ReactionRole roleIn;
        ReactionRole roleOut;

        Direction(ReactionRole in, ReactionRole out) {
            this.roleIn = in;
            this.roleOut = out;
        }
    }

    enum Mode {
        once(Transform.Mode.All),
        unique(Transform.Mode.Unique),
        all(Transform.Mode.All),
        exclusive(Transform.Mode.Exclusive);

        Transform.Mode mode;

        Mode(Transform.Mode mode) {
            this.mode = mode;
        }
    }

    private SmirksOption charToOpt(char ch) {
        switch (ch) {
            case 'w': return PEDANTIC;
            case 'p': return SmirksOption.DIFF_PART;
            case 'e': return SmirksOption.IGNORE_SET_ELEM;
            case 'h': return SmirksOption.IGNORE_IMPL_H;
            case 'H': return SmirksOption.IGNORE_TOTAL_H;
            case '0': return SmirksOption.IGNORE_TOTAL_H0;
            case 'o': return SmirksOption.OVERWRITE_BOND;
            case 'm': return SmirksOption.REMOVE_UNMAPPED_FRAGMENTS;
            case 'r': return SmirksOption.RECOMPUTE_HYDROGENS;
            case 'x': return SmirksOption.EXPAND_HYDROGENS;
            default: return null;
        }
    }

    @RequestMapping({"react/{dir}/{mode}",
                     "react/{dir}",
                     "react"})
    public @ResponseBody String react(@PathVariable(name = "dir", required = false) Direction dir,
                                      @PathVariable(name = "mode", required = false) Mode mode,
                                      @RequestParam("r") String smirks,
                                      @RequestParam("s") String[] smilesInputs,
                                      @RequestParam(value = "o", required = false)
                                          String[] opts,
                                      @RequestParam(value = "limit", defaultValue = "16")
                                          int limit) throws CDKException {
        if (dir == null)
            dir = Direction.forward;
        if (mode == null)
            mode = dir == Direction.normalize ? Mode.exclusive : Mode.once;

        Set<SmirksOption> optset = EnumSet.noneOf(SmirksOption.class);

        // do not move to defaultValue= as we want to differentiate null vs empty
        // string
        if (opts == null)
            opts = new String[]{"Daylight"};

        for (String opt : opts) {
            if (opt.equalsIgnoreCase("Daylight")) {
                optset.addAll(SmirksOption.DAYLIGHT);
            } else if (opt.equalsIgnoreCase("RDKit")) {
                optset.addAll(SmirksOption.RDKIT);
            } else {
                for (int i=0; i<opt.length(); i++) {
                    if (charToOpt(opt.charAt(i)) == null)
                        throw new IllegalArgumentException("Unsupported SMIRKS option char: " + opt.charAt(i));
                }
                for (int i=0; i<opt.length(); i++) {
                    optset.add(charToOpt(opt.charAt(i)));
                }
            }
        }

        if (dir == Direction.backward)
            optset.add(SmirksOption.REVERSE);

        Transform tform = new Transform();
        if (!Smirks.parse(tform, smirks, optset)) {
            return "ERROR: " + tform.message();
        }

        StringBuilder res = new StringBuilder();
        for (String smiles : smilesInputs) {

            IAtomContainer molIn;
            int numParts = 0;
            boolean isRxn = true;

            try {
                molIn = ReactionManipulator.toMolecule(smipar.parseReactionSmiles(smiles));
                Set<IAtom> atomsToDelete = new HashSet<>();
                for (IAtom atom : molIn.atoms()) {
                    Integer rxnComp = atom.getProperty(CDKConstants.REACTION_GROUP);
                    ReactionRole rxnRole = atom.getProperty(CDKConstants.REACTION_ROLE);
                    if (rxnRole.equals(dir.roleOut))
                        atomsToDelete.add(atom);
                    if (rxnComp > numParts)
                        numParts = rxnComp;
                }
                for (IAtom atom : atomsToDelete)
                    molIn.removeAtom(atom);
            } catch (CDKException ex) {
                isRxn = false;
                molIn = smipar.parseSmiles(smiles);
                if (dir.roleIn != ReactionRole.None) {
                    setRole(molIn, dir.roleIn);
                    numParts = setComponentIds(molIn, 0);
                }
            }

            Cycles.markRingAtomsAndBonds(molIn);
            Aromaticity.apply(Aromaticity.Model.Daylight, molIn);

            boolean empty = true;

            if (dir == Direction.map) {

                IAtomContainer reactants = SilentChemObjectBuilder.getInstance().newAtomContainer();
                IAtomContainer expectedProduct = SilentChemObjectBuilder.getInstance().newAtomContainer();
                AtomContainerManipulator.copy(reactants, molIn, a -> a.getProperty(CDKConstants.REACTION_ROLE) != ReactionRole.Product);
                AtomContainerManipulator.copy(expectedProduct, molIn, a -> a.getProperty(CDKConstants.REACTION_ROLE) == ReactionRole.Product);

                // number the reactant atoms 1..n
                int uniqueMapIdx = 0;
                for (IAtom atom : reactants.atoms()) {
                    atom.setMapIdx(++uniqueMapIdx);
                }

                if (optset.contains(EXPAND_HYDROGENS))
                    AtomContainerManipulator.normalizeHydrogens(expectedProduct, HydrogenState.Minimal);

                outer:
                for (IAtomContainer productMixture : tform.apply(reactants, mode.mode, limit)) {

                    if (optset.contains(EXPAND_HYDROGENS))
                        AtomContainerManipulator.normalizeHydrogens(productMixture, HydrogenState.Minimal);

                    for (IAtomContainer product : ConnectivityChecker.partitionIntoMolecules(productMixture)) {
                        if (product.getAtomCount() != expectedProduct.getAtomCount())
                            continue;
                        int[] actualOrder = new int[product.getAtomCount()];
                        int[] expectedOrder = new int[expectedProduct.getAtomCount()];
                        String actualSmi = cansmi.create(product, actualOrder);
                        String expectedSmi = cansmi.create(expectedProduct, expectedOrder);
                        if (actualSmi.equals(expectedSmi)) {

                            int[] invExpectedOrder = new int[expectedOrder.length];
                            int[] invActualOrder = new int[actualOrder.length];

                            for (int i=0; i<expectedOrder.length; i++) {
                                invExpectedOrder[expectedOrder[i]] = i;
                                invActualOrder[actualOrder[i]] = i;
                            }

                            for (int i=0; i<actualOrder.length; i++) {
                                expectedProduct.getAtom(invExpectedOrder[i])
                                               .setMapIdx(product.getAtom(invActualOrder[i])
                                                                        .getMapIdx());
                            }
                            empty = false;

                            res.append(smigen.create(ReactionManipulator.toReaction(molIn)));
                            if (molIn.getTitle() != null && !molIn.getTitle().isEmpty())
                                res.append('\t').append(molIn.getTitle());
                            res.append('\n');

                            if (mode == Mode.once)
                                break outer;
                        }
                    }
                }
            } else {
                for (IAtomContainer molOut : tform.apply(molIn, mode.mode, limit)) {

                    Cycles.markRingAtomsAndBonds(molOut);
                    Aromaticity.apply(Aromaticity.Model.Daylight, molOut);

                    if (optset.contains(EXPAND_HYDROGENS))
                        AtomContainerManipulator.normalizeHydrogens(molOut, HydrogenState.Minimal);

                    if (dir.roleOut != ReactionRole.None) {
                        setRole(molOut, dir.roleOut);
                        adjustGroup(molOut, numParts);
                        AtomContainerManipulator.copy(molOut, molIn, a -> true);
                        resync(molOut, dir.roleOut);
                        res.append(smigen.create(ReactionManipulator.toReaction(molOut)));
                    } else if (isRxn) {
                        resync(molOut, ReactionRole.Product);
                        res.append(smigen.create(ReactionManipulator.toReaction(molOut)));
                    } else {
                        res.append(smigen.create(molOut));
                    }

                    if (molIn.getTitle() != null && !molIn.getTitle().isEmpty())
                        res.append('\t').append(molIn.getTitle());
                    res.append('\n');
                    empty = false;
                    if (mode == Mode.once)
                        break;
                }
            }

            if (empty) {
                if (dir.roleOut != ReactionRole.None) {
                    res.append(smigen.create(ReactionManipulator.toReaction(molIn)));
                    // empty a blank line if there was no about for this
                    // molecule/reaction
                    if (molIn.getTitle() != null && !molIn.getTitle().isEmpty())
                        res.append('\t').append(molIn.getTitle());
                }
                else
                    res.append(smiles);
                res.append('\n');
            }
        }

        return res.toString();
    }

    private void propagate(IAtom atom, ReactionRole role, Integer grp) {
        atom.setProperty(CDKConstants.REACTION_GROUP, grp);
        atom.setProperty(CDKConstants.REACTION_ROLE, role);
        for (IBond bond : atom.bonds()) {
            IAtom nbor = bond.getOther(atom);
            if (!grp.equals(nbor.getProperty(CDKConstants.REACTION_GROUP)) ||
                !role.equals(nbor.getProperty(CDKConstants.REACTION_ROLE)))
                propagate(nbor, role, grp);
        }
    }

    private void resync(IAtomContainer mol, ReactionRole defaultRole) {
        Integer maxGrp = 0;
        for (IAtom atom : mol.atoms()) {
            ReactionRole role = atom.getProperty(CDKConstants.REACTION_ROLE);
            Integer grp = atom.getProperty(CDKConstants.REACTION_GROUP);
            if (role != null && grp != null) {
                propagate(atom, role, grp);
            }
            if (grp != null)
                maxGrp = Math.max(maxGrp, grp);
        }
        for (IAtom atom : mol.atoms()) {
            ReactionRole rxnRole = atom.getProperty(CDKConstants.REACTION_ROLE);
            if (rxnRole == null) {
                propagate(atom, defaultRole, ++maxGrp);
            } else if (atom.getProperty(CDKConstants.REACTION_GROUP) == null) {
                propagate(atom, rxnRole, ++maxGrp);
            }
        }

        // fix new atoms!
    }

    private static int setComponentIds(IAtomContainer molIn, int baseId) {
        ConnectedComponents cc = new ConnectedComponents(GraphUtil.toAdjList(molIn));
        int[] parts = cc.components();
        for (IAtom atom : molIn.atoms()) {
            atom.setProperty(CDKConstants.REACTION_GROUP, baseId + parts[atom.getIndex()]);
        }
        return cc.nComponents();
    }

    private static void setRole(IAtomContainer mol, ReactionRole role) {
        for (IAtom atom : mol.atoms()) {
            atom.setProperty(CDKConstants.REACTION_ROLE, role);
        }
    }

    private static void adjustGroup(IAtomContainer mol, int baseGrp) {
        for (IAtom atom : mol.atoms()) {
            Integer grpId = atom.getProperty(CDKConstants.REACTION_GROUP);
            if (grpId != null)
                atom.setProperty(CDKConstants.REACTION_GROUP, baseGrp + grpId);
        }
    }
}
