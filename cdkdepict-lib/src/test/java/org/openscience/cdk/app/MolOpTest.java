/*
 * =====================================
 *  Copyright (c) 2019 NextMove Software
 * =====================================
 */

package org.openscience.cdk.app;

import org.junit.jupiter.api.Test;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

class MolOpTest {

  @Test
  void NO2() throws InvalidSmilesException {
    SmilesParser   smipar = new SmilesParser(SilentChemObjectBuilder.getInstance());
    IAtomContainer mol    = smipar.parseSmiles("[Co][N+]([O-])(=O)");
    MolOp.perceiveDativeBonds(mol);
    int count = 0;
    for (IBond bond : mol.bonds()) {
      if (bond.getDisplay() != IBond.Display.Solid)
        count++;
    }
    assertThat(count, is(2));
  }

  @Test
  void NO3() throws InvalidSmilesException {
    SmilesParser   smipar = new SmilesParser(SilentChemObjectBuilder.getInstance());
    IAtomContainer mol    = smipar.parseSmiles("[O-][N+]([O-])=O");
    MolOp.perceiveDativeBonds(mol);
    int count = 0;
    for (IBond bond : mol.bonds()) {
      if (bond.getDisplay() != IBond.Display.Solid)
        count++;
    }
    assertThat(count, is(1));
  }

}