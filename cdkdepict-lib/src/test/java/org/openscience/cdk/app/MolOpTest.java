/*
 * =====================================
 *  Copyright (c) 2019 NextMove Software
 * =====================================
 */

package org.openscience.cdk.app;

import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import static org.junit.Assert.*;

public class MolOpTest {

  @Test public void NO2() throws InvalidSmilesException {
    SmilesParser   smipar = new SmilesParser(SilentChemObjectBuilder.getInstance());
    IAtomContainer mol    = smipar.parseSmiles("[Co][N+]([O-])(=O)");
    MolOp.perceiveDativeBonds(mol);
    int count = 0;
    for (IBond bond : mol.bonds()) {
      if (bond.getDisplay() != IBond.Display.Solid)
        count++;
    }
    assertThat(count, CoreMatchers.is(2));
  }

  @Test public void NO3() throws InvalidSmilesException {
    SmilesParser   smipar = new SmilesParser(SilentChemObjectBuilder.getInstance());
    IAtomContainer mol    = smipar.parseSmiles("[O-][N+]([O-])=O");
    MolOp.perceiveDativeBonds(mol);
    int count = 0;
    for (IBond bond : mol.bonds()) {
      if (bond.getDisplay() != IBond.Display.Solid)
        count++;
    }
    assertThat(count, CoreMatchers.is(1));
  }

}