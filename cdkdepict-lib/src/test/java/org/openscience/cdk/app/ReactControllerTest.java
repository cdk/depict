package org.openscience.cdk.app;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openscience.cdk.exception.CDKException;

class ReactControllerTest {

    void assertMap(String smirks, String smilesIn, String expected) throws CDKException {
        ReactController controller = new ReactController();
        String result = controller.react(ReactController.Direction.map,
                                         ReactController.Mode.once,
                                         smirks,
                                         new String[]{smilesIn},
                                         new String[]{"Daylight"},
                                         16);
        Assertions.assertEquals(expected, result.replaceAll("\n", ""));
    }

    void assertReact(String smirks, String smilesIn, String expected, String opts) throws CDKException {
        ReactController controller = new ReactController();
        String result = controller.react(ReactController.Direction.forward,
                                         ReactController.Mode.once,
                                         smirks,
                                         new String[]{smilesIn},
                                         new String[]{opts},
                                         16);
        Assertions.assertEquals(expected, result.replaceAll("\n", ""));
    }

    @Test
    public void testMapMode() throws CDKException {
        assertMap("[C:1][H]>>[C:1]Cl",
                  "CCC>>CC(Cl)C",
                  "[CH3:1][CH2:2][CH3:3]>>[CH3:1][CH:2](Cl)[CH3:3]");
    }

    @Test
    public void testMapModeWithMolecule() throws CDKException {
        assertMap("[C:1][H]>>[C:1]Cl",
                  "CCC",
                  "CCC");
    }

    @Test
    public void testMapModeWithHalfReaction() throws CDKException {
        assertMap("[C:1][H]>>[C:1]Cl",
                  "CCC>>",
                  "CCC>>");
    }

    @Test
    public void testMapMode_cyclohexane() throws CDKException {
        assertMap("[#6:1][H]>>[*:1]Cl",
                  "c1ccccc1>>Clc1ccccc1",
                  "[cH:1]1[cH:2][cH:3][cH:4][cH:5][cH:6]1>>Cl[c:1]1[cH:2][cH:3][cH:4][cH:5][cH:6]1");
    }


    @Test
    public void testMapModeSuzukiCoupling() throws CDKException {
        assertMap("[#6:1][Cl,Br,F,I].[#6:2]B(O)(O)>>[*:1][*:2]",
                  "B(c1cccc(c1)n2c(cc(n2)C)C)(O)O.CC(C)(C)OC(=O)CBr>>Cc1cc(n(n1)c2cccc(c2)CC(=O)OC(C)(C)C)C\n",
                  "[B:1]([c:2]1[cH:3][cH:4][cH:5][c:6]([cH:7]1)-[n:8]2[c:9]([cH:10][c:11]([n:12]2)[CH3:13])[CH3:14])([OH:15])[OH:16].[CH3:17][C:18]([CH3:19])([CH3:20])[O:21][C:22](=[O:23])[CH2:24][Br:25]>>[CH3:13][c:11]1[cH:10][c:9]([n:8]([n:12]1)-[c:6]2[cH:5][cH:4][cH:3][c:2]([cH:7]2)[CH2:24][C:22](=[O:23])[O:21][C:18]([CH3:17])([CH3:19])[CH3:20])[CH3:14]");
    }


    @Test
    public void testMapModeSuzukiCouplingWithAgents() throws CDKException {
        assertMap("[#6:1][Cl,Br,F,I].[#6:2]B(O)(O)>>[*:1][*:2]",
                  "B(c1cccc(c1)n2c(cc(n2)C)C)(O)O.CC(C)(C)OC(=O)CBr>O>Cc1cc(n(n1)c2cccc(c2)CC(=O)OC(C)(C)C)C\n",
                  "[B:1]([c:2]1[cH:3][cH:4][cH:5][c:6]([cH:7]1)-[n:8]2[c:9]([cH:10][c:11]([n:12]2)[CH3:13])[CH3:14])([OH:15])[OH:16].[CH3:17][C:18]([CH3:19])([CH3:20])[O:21][C:22](=[O:23])[CH2:24][Br:25]>[OH2:26]>[CH3:13][c:11]1[cH:10][c:9]([n:8]([n:12]1)-[c:6]2[cH:5][cH:4][cH:3][c:2]([cH:7]2)[CH2:24][C:22](=[O:23])[O:21][C:18]([CH3:17])([CH3:19])[CH3:20])[CH3:14]");
    }

    @Test
    public void testOption_IgnoreElementChange() throws CDKException {
        assertReact("[Pb:1]>>[Au:1]",
                    "[Pb]",
                    "[Pb]>>[Au]",
                    "");
        assertReact("[Pb:1]>>[Au:1]",
                    "[Pb]",
                    "[Pb]>>[Pb]",
                    "e");
    }

}