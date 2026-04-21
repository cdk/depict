/*
 * Copyright (c) 2026.
 */

package org.openscience.cdk.app;

import org.junit.jupiter.api.Test;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IReaction;
import org.openscience.cdk.interfaces.IReactionSet;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

class ReactionCenterDetectionTest {

  @Test
  void mappedBondOrderChangeMarksMappedAtoms() throws Exception {
    IReaction rxn = parseReaction("[CH3:1][CH3:2]>>[CH2:1]=[CH2:2]");
    Set<IAtom> centers = DepictController.findReactionCenterAtoms(rxn);
    assertThat(mappedIndices(centers), is(new HashSet<>(Arrays.asList(1, 2))));
  }

  @Test
  void mappedConnectivityChangeMarksMappedAtoms() throws Exception {
    IReaction rxn = parseReaction("[CH3:1][CH2:2][Cl:3].[O-:4]>>[CH3:1][CH2:2][O-:4].[Cl-:3]");
    Set<IAtom> centers = DepictController.findReactionCenterAtoms(rxn);
    assertThat(mappedIndices(centers), is(new HashSet<>(Arrays.asList(2, 3, 4))));
  }

  @Test
  void unchangedMappedAtomsAreNotMarked() throws Exception {
    IReaction rxn = parseReaction("[CH3:1][CH3:2]>>[CH3:1][CH3:2]");
    Set<IAtom> centers = DepictController.findReactionCenterAtoms(rxn);
    assertThat(centers.isEmpty(), is(true));
  }

  @Test
  void partiallyMappedReactionUsesFallbackForUnmappedAtoms() throws Exception {
    IReaction rxn = parseReaction("[CH3:1][CH2:2]Cl>>[CH3:1][CH2:2]Br");
    Set<IAtom> centers = DepictController.findReactionCenterAtoms(rxn);
    assertThat(mappedIndices(centers), is(new HashSet<>(Arrays.asList(2))));
    assertThat(unmappedAtomicNumbers(centers), is(new HashSet<>(Arrays.asList(17, 35))));
  }

  @Test
  void fullyUnmappedSameReactionHasNoCenters() throws Exception {
    IReaction rxn = parseReaction("CCO>>CCO");
    Set<IAtom> centers = DepictController.findReactionCenterAtoms(rxn);
    assertThat(centers.isEmpty(), is(true));
  }

  @Test
  void fullyUnmappedTransformedReactionMarksUnmatchedHashes() throws Exception {
    IReaction rxn = parseReaction("CCO>>CCN");
    Set<IAtom> centers = DepictController.findReactionCenterAtoms(rxn);
    assertThat(centers.isEmpty(), is(false));
    assertThat(unmappedAtomicNumbers(centers), hasItems(7, 8));
  }

  @Test
  void aromaticKekuleShiftDoesNotMarkCenters() throws Exception {
    IReaction rxn = parseReaction("[c:1]1[c:2][c:3][c:4][c:5][c:6]1>>[c:1]1[c:2][c:3][c:4][c:5][c:6]1");
    IAtomContainer product = rxn.getProducts().getAtomContainer(0);
    int i = 0;
    for (IBond bond : product.bonds()) {
      bond.setOrder((i++ & 1) == 0 ? IBond.Order.SINGLE : IBond.Order.DOUBLE);
      bond.setIsAromatic(false);
      bond.getBegin().setIsAromatic(true);
      bond.getEnd().setIsAromatic(true);
    }

    Set<IAtom> centers = DepictController.findReactionCenterAtoms(rxn);
    assertThat(centers.isEmpty(), is(true));
  }

  @Test
  void fallbackPrefersComponentWiseMatchingForUnmappedAtoms() throws Exception {
    IReaction rxn = parseReaction("c1ccccc1.COc1ccccc1>>c1ccccc1.Nc1ccccc1");
    Set<IAtom> centers = DepictController.findReactionCenterAtoms(rxn);

    IAtomContainer unchangedReactant = rxn.getReactants().getAtomContainer(0);
    IAtomContainer unchangedProduct = rxn.getProducts().getAtomContainer(0);

    assertThat(anyCenterInContainer(centers, unchangedReactant), is(false));
    assertThat(anyCenterInContainer(centers, unchangedProduct), is(false));
    assertThat(centers.isEmpty(), is(false));
  }

  @Test
  void fallbackAvoidsAmbiguousScatteredSelections() throws Exception {
    IReaction rxn = parseReaction("c1ccccc1>>Cc1ccccc1");
    Set<IAtom> centers = DepictController.findReactionCenterAtoms(rxn);

    IAtomContainer product = rxn.getProducts().getAtomContainer(0);
    int aromaticCenters = 0;
    int nonAromaticCenters = 0;
    for (IAtom atom : product.atoms()) {
      if (!centers.contains(atom))
        continue;
      if (atom.isAromatic())
        aromaticCenters++;
      else
        nonAromaticCenters++;
    }

    assertThat(nonAromaticCenters >= 1, is(true));
    assertThat(aromaticCenters, is(0));
  }

  private static IReaction parseReaction(String smiles) throws Exception {
    SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
    IReactionSet set = parser.parseReactionSetSmiles(smiles);
    return set.reactions().iterator().next();
  }

  private static Set<Integer> mappedIndices(Set<IAtom> atoms) {
    Set<Integer> idxs = new HashSet<>();
    for (IAtom atom : atoms) {
      int map = mapIdx(atom);
      if (map > 0)
        idxs.add(map);
    }
    return idxs;
  }

  private static Set<Integer> unmappedAtomicNumbers(Set<IAtom> atoms) {
    Set<Integer> nums = new HashSet<>();
    for (IAtom atom : atoms) {
      if (mapIdx(atom) == 0 && atom.getAtomicNumber() != null)
        nums.add(atom.getAtomicNumber());
    }
    return nums;
  }

  private static int mapIdx(IAtom atom) {
    if (atom.getMapIdx() > 0)
      return atom.getMapIdx();
    Object val = atom.getProperty(CDKConstants.ATOM_ATOM_MAPPING);
    if (val instanceof Number)
      return ((Number) val).intValue();
    if (val instanceof String) {
      try {
        return Integer.parseInt((String) val);
      } catch (NumberFormatException ignored) {
        return 0;
      }
    }
    return 0;
  }

  private static boolean anyCenterInContainer(Set<IAtom> centers, IAtomContainer container) {
    for (IAtom atom : container.atoms()) {
      if (centers.contains(atom))
        return true;
    }
    return false;
  }
}
