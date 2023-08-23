package org.openscience.cdk.app;

import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IReaction;
import org.openscience.cdk.sgroup.Sgroup;
import org.openscience.cdk.sgroup.SgroupType;
import org.openscience.cdk.tools.manipulator.ReactionManipulator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Operations on reactions.
 */
final class RxnOp {

  /**
   * Mark the atoms in a mapped reaction which changed degree or hydrogen count.
   * If the reaction does not have a mapping nothing is done. The atoms are placed
   * into the 'atomSet' map and labelled sequentially starting at the specified 'group'.
   *
   * @param atomSet the results
   * @param reaction the reaction
   * @param group the label/group/set id to start at
   * @return the new label/group/set id
   */
  static int markChangedAtoms(Map<IAtom, Integer> atomSet,
                              IReaction reaction,
                              int group) {

    List<IAtom> atoms = new ArrayList<>();
    for (IAtomContainer mol : ReactionManipulator.getAllAtomContainers(reaction)) {
      for (IAtom atom : mol.atoms()) {
        if (atom.getMapIdx() == 0) continue;
        atoms.add(atom);
      }
    }
    if (atoms.isEmpty())
      return group;

    atoms.sort(Comparator.comparingInt(IAtom::getMapIdx));
    int mark = 0;
    boolean changed = false;
    for (int i = 1; i < atoms.size(); i++) {
      IAtom prev = atoms.get(mark);
      IAtom atom = atoms.get(i);
      if (prev.getMapIdx() != atom.getMapIdx()) {
        for (int j = mark; changed && j < i; j++)
          atomSet.put(atoms.get(j), ++group);
        mark = i;
        changed = false;
      } else if (prev.getBondCount() != atom.getBondCount() ||
              !Objects.equals(prev.getImplicitHydrogenCount(), atom.getImplicitHydrogenCount())) {
        changed = true;
      }
    }
    for (int j = mark; changed && j < atoms.size(); j++)
      atomSet.put(atoms.get(j), ++group);
    return group;
  }

  private static final class AbbreviationMap {
    private IAtom[]  amap   = new IAtom[64];
    private Sgroup[] abbrvs = new Sgroup[64];
    private int maxMapIdx = 0;
    private boolean unique = true;

    private void extract(IAtomContainer mol) {
      for (IAtom atom : mol.atoms()) {
        int mapIdx = atom.getMapIdx();
        if (mapIdx == 0)
          continue;
        this.maxMapIdx = Math.max(this.maxMapIdx, mapIdx);
        this.amap = grow(this.amap, mapIdx);
        if (this.amap[mapIdx] != null)
          this.unique = false;
        this.amap[mapIdx] = atom;
      }
      List<Sgroup> sgroups = mol.getProperty(CDKConstants.CTAB_SGROUPS);
      if (sgroups != null) {
        for (Sgroup sgroup : sgroups) {
          if (sgroup.getType() != SgroupType.CtabAbbreviation)
            continue;
          for (IAtom atom : sgroup.getAtoms()) {
            int mapIdx = atom.getMapIdx();
            if (mapIdx == 0)
              continue;
            this.abbrvs = grow(this.abbrvs, mapIdx);
            this.abbrvs[mapIdx] = sgroup;
          }
        }
      }
    }

    private <T> T[] grow(T[] a, int req) {
      if (req < a.length)
        return a;
      int cap = Math.max(a.length + (a.length >> 2), req+1);
      return Arrays.copyOf(a, cap);
    }
  }

  static void syncAbbreviations(IReaction reaction) {
    AbbreviationMap rmap = new AbbreviationMap();
    AbbreviationMap pmap = new AbbreviationMap();

    // determine atom mapping and gather sgroups
    for (IAtomContainer mol : reaction.getReactants().atomContainers())
      rmap.extract(mol);
    for (IAtomContainer mol : reaction.getProducts().atomContainers())
      pmap.extract(mol);

    if (!rmap.unique || !pmap.unique)
      return;

    List<Sgroup> sgroups = new ArrayList<>();
    int lastIndex = Math.min(rmap.maxMapIdx, pmap.maxMapIdx);
    for (int mapIdx = 1; mapIdx <= lastIndex; mapIdx++) {
      Sgroup rAbbrv = rmap.abbrvs[mapIdx];
      Sgroup pAbbrv = pmap.abbrvs[mapIdx];
      if (pAbbrv != null && rAbbrv == null) {
        rAbbrv = new Sgroup();
        if (copySgroup(rmap.amap, rmap.abbrvs, pAbbrv, rAbbrv)) {
          for (IAtom atom : rAbbrv.getAtoms())
            rmap.abbrvs[atom.getMapIdx()] = rAbbrv;
          sgroups.add(rAbbrv);
        }
      } else if (rAbbrv != null && pAbbrv == null) {
        pAbbrv = new Sgroup();
        if (copySgroup(pmap.amap, pmap.abbrvs, rAbbrv, pAbbrv)) {
          for (IAtom atom : rAbbrv.getAtoms())
            pmap.abbrvs[atom.getMapIdx()] = pAbbrv;
          sgroups.add(pAbbrv);
        }
      }
    }

    for (Sgroup sgroup : sgroups) {
      if (sgroup.getAtoms().isEmpty())
        continue;;
      IAtomContainer mol = null;
      for (IAtom atom : sgroup.getAtoms()) {
        if (mol == null)
          mol = atom.getContainer();
        else if (atom.getContainer() != mol) {
          mol = null;
          break;
        }
      }
      if (mol != null) {
        List<Sgroup> newSgroups = new ArrayList<>();
        List<Sgroup> curSgroups = mol.getProperty(CDKConstants.CTAB_SGROUPS);
        if (curSgroups != null)
          newSgroups.addAll(curSgroups);
        newSgroups.add(sgroup);
        mol.setProperty(CDKConstants.CTAB_SGROUPS, newSgroups);
      }
    }
  }

  private static boolean copySgroup(IAtom[] rmap, Sgroup[] rAbbrvs, Sgroup src, Sgroup dst) {
    dst.setType(src.getType());
    dst.setSubscript(src.getSubscript());

    int srcDegree = 0;
    int dstDegree = 0;

    for (IAtom srcAtom : src.getAtoms()) {
      int mapIdx = srcAtom.getMapIdx();
      IAtom dstAtom = rmap[mapIdx];
      if (mapIdx == 0 || dstAtom == null || rAbbrvs[mapIdx] != null)
        return false;
      dst.addAtom(dstAtom);
      if (!Objects.equals(srcAtom.getImplicitHydrogenCount(),
                          dstAtom.getImplicitHydrogenCount()))
        return false;
      srcDegree += srcAtom.getBondCount();
      dstDegree += dstAtom.getBondCount();
    }

    // fail fast!
    if (srcDegree != dstDegree)
      return false;

    for (IBond srcBnd : src.getBonds()) {
      IAtom a = rmap[srcBnd.getBegin().getMapIdx()];
      IAtom b = rmap[srcBnd.getEnd().getMapIdx()];
      if (a == null || b == null)
        return false;
      IBond dstBnd = a.getBond(b);
      if (dstBnd == null)
        return false;
      dst.addBond(dstBnd);
    }

    // verify bonding
    Set<IBond> bonds = dst.getBonds();
    Set<IAtom> atoms = dst.getAtoms();
    for (IAtom atom : atoms) {
      for (IBond bond : atom.bonds()) {
        if (bonds.contains(bond))
          continue;
        if (!atoms.contains(bond.getOther(atom)))
          return false;
      }
    }

    return true;
  }

}
