package org.openscience.cdk.app;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.depict.Abbreviations;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IReaction;
import org.openscience.cdk.sgroup.Sgroup;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.manipulator.ReactionManipulator;

import java.util.List;

class RxnOpTest {

  @Test
  public void testAbbrCopy() throws InvalidSmilesException {
    String smi = "CC(=O)[O:1][CH2:2][C@@H:3]1[O:4][C@@H:5]([C@H:6]([C@H:7]1[O:8]C(C)=O)[O:9]C(C)=O)[n:10]1[c:11]2[cH:12][c:13]([c:14]([cH:15][c:16]2[n:17][c:18]1Br)[Cl:19])[Cl:20].[CH3:21][CH2:22][CH2:23][CH2:24][NH2:25]>CCO>[CH3:21][CH2:22][CH2:23][CH2:24][NH:25][c:18]1[n:17][c:16]2[cH:15][c:14]([c:13]([cH:12][c:11]2[n:10]1[C@H:5]1[O:4][C@H:3]([C@@H:7]([C@@H:6]1[OH:9])[OH:8])[CH2:2][OH:1])[Cl:20])[Cl:19]";
    SmilesParser smipar = new SmilesParser(SilentChemObjectBuilder.getInstance());
    IReaction reaction = smipar.parseReactionSmiles(smi);
    Abbreviations abbreviations = new Abbreviations();
    abbreviations.add("*CCCC nBu");
    abbreviations.without(Abbreviations.Option.AUTO_CONTRACT_HETERO);
    for (IAtomContainer mol : ReactionManipulator.getAllAtomContainers(reaction))
      abbreviations.apply(mol);
    String label = "nBu";
    assertNumAbbreviations(reaction, label, 1);
    RxnOp.syncAbbreviations(reaction);
    assertNumAbbreviations(reaction, label, 2);
  }

  @Test
  public void testNoAbbrCopy() throws InvalidSmilesException {
    String smi = "CC(=O)[O:1][CH2:2][C@@H:3]1[O:4][C@@H:5]([C@H:6]([C@H:7]1[O:8]C(C)=O)[O:9]C(C)=O)[n:10]1[c:11]2[cH:12][c:13]([c:14]([cH:15][c:16]2[n:17][c:18]1Br)[Cl:19])[Cl:20].[CH3:21][CH2:22][CH2:23][CH2:24][NH2:25]>CCO>[CH3:21][CH2:22][CH2:23][CH2:24][NH:25][c:18]1[n:17][c:16]2[cH:15][c:14]([c:13]([cH:12][c:11]2[n:10]1[C@H:5]1[O:4][C@H:3]([C@@H:7]([C@@H:6]1[OH:9])[OH:8])[CH2:2][OH:1])[Cl:20])[Cl:19]";
    SmilesParser smipar = new SmilesParser(SilentChemObjectBuilder.getInstance());
    IReaction reaction = smipar.parseReactionSmiles(smi);
    Abbreviations abbreviations = new Abbreviations();
    abbreviations.add("*NCCCC NHnBu");
    abbreviations.with(Abbreviations.Option.AUTO_CONTRACT_HETERO);
    for (IAtomContainer mol : ReactionManipulator.getAllAtomContainers(reaction))
      abbreviations.apply(mol);
    String label = "NHnBu";
    assertNumAbbreviations(reaction, label, 1);
    RxnOp.syncAbbreviations(reaction);
    assertNumAbbreviations(reaction, label, 1);
  }


  private static void assertNumAbbreviations(IReaction reaction, String label, int expected) {
    int count = 0;
    for (IAtomContainer mol : ReactionManipulator.getAllAtomContainers(reaction)) {
      List<Sgroup> sgroups = mol.getProperty(CDKConstants.CTAB_SGROUPS);
      if (sgroups != null) {
        for (Sgroup sgroup : sgroups) {
          if (sgroup.getSubscript().equals(label))
            count++;
        }
      }
    }
    Assertions.assertEquals(expected, count);
  }

}