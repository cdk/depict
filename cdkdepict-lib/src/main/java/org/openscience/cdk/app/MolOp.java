/*
 * =====================================
 *  Copyright (c) 2019 NextMove Software
 * =====================================
 */

package org.openscience.cdk.app;

import org.openscience.cdk.config.Elements;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;

public class MolOp {

  private static int calcValence(IAtom atom) {
    int v = atom.getImplicitHydrogenCount();
    for (IBond bond : atom.bonds()) {
      IBond.Order order = bond.getOrder();
      if (order != null && order != IBond.Order.UNSET)
        v += order.numeric();
    }
    return v;
  }

  private static boolean isDativeDonor(IAtom a) {
    switch (a.getAtomicNumber()) {
      case 7:
      case 15:
        return a.getFormalCharge() == 0 && calcValence(a) == 4;
      case 8:
        return a.getFormalCharge() == 0 && calcValence(a) == 3;
      default:
        return false;
    }
  }

  private static boolean isDativeAcceptor(IAtom a) {
    if (Elements.isMetal(a))
      return true;
    switch (a.getAtomicNumber()) {
      case 5:
        return a.getFormalCharge() == 0 && calcValence(a) == 4;
      case 8:
        return a.getFormalCharge() == 0 && calcValence(a) == 1;
      default:
        return false;
    }
  }

  private static boolean isPosDativeDonor(IAtom a) {
    switch (a.getAtomicNumber()) {
      case 7:
      case 15:
        return a.getFormalCharge() == +1 && calcValence(a) == 4;
      case 8:
        return a.getFormalCharge() == +1 && calcValence(a) == 3;
      default:
        return false;
    }
  }

  private static boolean isNegDativeAcceptor(IAtom a) {
    if (a.getFormalCharge() != -1)
      return false;
    if (Elements.isMetal(a))
      return true;
    switch (a.getAtomicNumber()) {
      case 5:
        return calcValence(a) == 4;
      case 8:
        return calcValence(a) == 1;
      default:
        return false;
    }
  }

  public static void perceiveRadicals(IAtomContainer mol) {
    for (IAtom atom : mol.atoms()) {
      int     v;
      Integer q = atom.getFormalCharge();
      if (q == null) q = 0;
      switch (atom.getAtomicNumber()) {
        case 6:
          if (q == 0) {
            v = calcValence(atom);
            if (v == 2)
              mol.addSingleElectron(mol.indexOf(atom));
            if (v < 4)
              mol.addSingleElectron(mol.indexOf(atom));
          }
          break;
        case 7:
          if (q == 0) {
            v = calcValence(atom);
            if (v < 3)
              mol.addSingleElectron(mol.indexOf(atom));
          }
          break;
        case 8:
          if (q == 0) {
            v = calcValence(atom);
            if (v < 2)
              mol.addSingleElectron(mol.indexOf(atom));
            if (v < 1)
              mol.addSingleElectron(mol.indexOf(atom));
          }
          break;
      }
    }
  }

  public static void perceiveDativeBonds(IAtomContainer mol) {
    for (IBond bond : mol.bonds()) {
      IAtom beg = bond.getBegin();
      IAtom end = bond.getEnd();
      if (isPosDativeDonor(end) && isNegDativeAcceptor(beg)) {
        bond.setDisplay(IBond.Display.ArrowBeg);
        beg.setFormalCharge(beg.getFormalCharge() + 1);
        end.setFormalCharge(end.getFormalCharge() - 1);
      } else if (isPosDativeDonor(beg) && isNegDativeAcceptor(end)) {
        bond.setDisplay(IBond.Display.ArrowEnd);
        beg.setFormalCharge(beg.getFormalCharge() - 1);
        end.setFormalCharge(end.getFormalCharge() + 1);
      }
    }
    for (IBond bond : mol.bonds()) {
      IAtom beg = bond.getBegin();
      IAtom end = bond.getEnd();
      if (isDativeDonor(end) && isDativeAcceptor(beg)) {
        bond.setDisplay(IBond.Display.ArrowBeg);
      } else if (isDativeDonor(beg) && isDativeAcceptor(end)) {
        bond.setDisplay(IBond.Display.ArrowEnd);
      }
    }
  }
}
