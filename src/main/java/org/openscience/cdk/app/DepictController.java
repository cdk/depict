/*
 * Copyright (C) 2015  John May
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package org.openscience.cdk.app;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.simolecule.centres.BaseMol;
import com.simolecule.centres.CdkLabeller;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.depict.Abbreviations;
import org.openscience.cdk.depict.Depiction;
import org.openscience.cdk.depict.DepictionGenerator;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.graph.Cycles;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IChemObject;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.interfaces.IReaction;
import org.openscience.cdk.interfaces.IStereoElement;
import org.openscience.cdk.io.MDLV2000Reader;
import org.openscience.cdk.renderer.RendererModel;
import org.openscience.cdk.renderer.SymbolVisibility;
import org.openscience.cdk.renderer.color.CDK2DAtomColors;
import org.openscience.cdk.renderer.color.IAtomColorer;
import org.openscience.cdk.renderer.color.UniColor;
import org.openscience.cdk.renderer.generators.BasicSceneGenerator;
import org.openscience.cdk.renderer.generators.standard.StandardGenerator;
import org.openscience.cdk.renderer.generators.standard.StandardGenerator.Visibility;
import org.openscience.cdk.sgroup.Sgroup;
import org.openscience.cdk.sgroup.SgroupKey;
import org.openscience.cdk.sgroup.SgroupType;
import org.openscience.cdk.silent.AtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.smiles.smarts.SmartsPattern;
import org.openscience.cdk.stereo.ExtendedTetrahedral;
import org.openscience.cdk.stereo.TetrahedralChirality;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import org.openscience.cdk.tools.manipulator.ReactionManipulator;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Chemical structure depiction controller.
 */
@Controller
public class DepictController {

  private final ExecutorService smartsExecutor = Executors.newFixedThreadPool(4);

  // chem object builder to create objects with
  private final IChemObjectBuilder builder = SilentChemObjectBuilder.getInstance();

  // we make are raster depictions slightly smalled by default (40px bond length)
  private final DepictionGenerator generator = new DepictionGenerator();
  private       SmilesParser       smipar    = new SmilesParser(builder);

  private final Abbreviations abbreviations = new Abbreviations();
  private final Abbreviations reagents      = new Abbreviations();

  private enum Param {
    // match highlighting
    SMARTSHITLIM("smalim", 100),
    SMARTSQUERY("sma", ""),
    // model options
    HDISPLAY("hdisp", false),
    ALIGNRXNMAP("alignrxnmap", true),
    ANON("anon", false),
    SUPRESSH("suppressh", true),
    ANNOTATE("annotate", "none"),
    ABBREVIATE("abbr", "reagents"),
    // rendering param
    BGCOLOR("bgcolor", "default"),
    FGCOLOR("fgcolor", "default"),
    SHOWTITLE("showtitle", false),
    ZOOM("zoom", 1.3),
    WIDTH("w", -1),
    HEIGHT("h", -1),
    SVGUNITS("svgunits", "mm");
    private final String name;
    private final Object defaultValue;

    Param(String name, Object defaultValue) {
      this.name = name;
      this.defaultValue = defaultValue;
    }
  }

  public DepictController() throws IOException
  {
    int count = this.abbreviations.loadFromFile("/org/openscience/cdk/app/abbreviations.smi");
    this.reagents.loadFromFile("/org/openscience/cdk/app/reagents.smi");
    abbreviations.setContractOnHetero(false);
  }

  /**
   * Home page redirect.
   */
  @RequestMapping("/")
  public String redirect()
  {
    return "redirect:/depict.html";
  }

  private String getString(Param param, Map<String,String> params) {
    String value = params.get(param.name);
    if (value != null)
      return value;
    return param.defaultValue != null ? param.defaultValue.toString() : "";
  }

  private double getDouble(Param param, Map<String,String> params) {
    String value = getString(param, params);
    if (value.isEmpty()) {
      if (param.defaultValue != null)
        return (double) param.defaultValue;
      throw new IllegalArgumentException(param.name + " not provided and no default!");
    }
    return Double.parseDouble(value);
  }

  private int getInt(Param param, Map<String,String> params) {
    String value = getString(param, params);
    if (value.isEmpty()) {
      if (param.defaultValue != null)
        return (int) param.defaultValue;
      throw new IllegalArgumentException(param.name + " not provided and no default!");
    }
    return Integer.parseInt(value);
  }

  private boolean getBoolean(Param param, Map<String,String> params) {
    String value = params.get(param.name);
    if (value != null) {
      switch (value.toLowerCase(Locale.ROOT)) {
        case "f":
        case "false":
        case "off":
        case "0":
          return false;
        case "t":
        case "true":
        case "on":
        case "1":
          return true;
        default:
          throw new IllegalArgumentException("Can not interpret boolean string param: " + value);
      }
    }
    return param.defaultValue != null && (boolean) param.defaultValue;
  }

  static Color getColor(String color) {
    int vals[] = new int[]{0,0,0,255}; // r,g,b,a
    int pos = 0;
    int beg = 0;
    if (color.startsWith("0x"))
      beg = 2;
    else if (color.startsWith("#"))
      beg = 1;
    for (; pos < 4 && beg+1 < color.length(); beg += 2) {
      vals[pos++] = Integer.parseInt(color.substring(beg,beg+2), 16);
    }
    return new Color(vals[0], vals[1], vals[2], vals[3]);
  }

  private HydrogenDisplayType getHydrogenDisplay(Map<String,String> params) {
    if (!getBoolean(Param.SUPRESSH, params)) {
      return HydrogenDisplayType.Provided;
    } else {
      switch (getString(Param.HDISPLAY, params)) {
          case "suppressed":
            return HydrogenDisplayType.Minimal;
          case "provided":
            return HydrogenDisplayType.Provided;
          case "stereo":
            return HydrogenDisplayType.Stereo;
          case "bridgeheadtetrahedral":
          case "bridgehead":
          case "default":
          case "smart":
            return HydrogenDisplayType.Smart;
          default:
            return HydrogenDisplayType.Smart;
        }
      }
  }

  private int calcValence(IAtomContainer mol, IAtom atom) {
    int v = atom.getImplicitHydrogenCount();
    for (IBond bond : mol.getConnectedBondsList(atom)) {
        IBond.Order order = bond.getOrder();
        if (order != null && order != IBond.Order.UNSET)
          v += order.numeric();
    }
    return v;
  }

  private void perceiveRadicals(IAtomContainer mol) {
    for (IAtom atom : mol.atoms()) {
      int v;
      Integer q = atom.getFormalCharge();
      if (q == null) q = 0;
      switch (atom.getAtomicNumber()) {
        case 6:
          if (q == 0) {
            v = calcValence(mol, atom);
            if (v == 2)
              mol.addSingleElectron(mol.indexOf(atom));
            if (v < 4)
              mol.addSingleElectron(mol.indexOf(atom));
          }
          break;
        case 7:
          if (q == 0) {
            v = calcValence(mol, atom);
            if (v < 3)
              mol.addSingleElectron(mol.indexOf(atom));
          }
          break;
        case 8:
          if (q == 0) {
            v = calcValence(mol, atom);
            if (v < 2)
              mol.addSingleElectron(mol.indexOf(atom));
            if (v < 1)
              mol.addSingleElectron(mol.indexOf(atom));
          }
          break;
      }
    }
  }

  /**
   * Restful entry point.
   *
   * @param smi      SMILES to depict
   * @param fmt      output format
   * @param style    preset style COW (Color-on-white), COB, BOW, COW
   * @return the depicted structure
   * @throws CDKException something not okay with input
   * @throws IOException  problem reading/writing request
   */
  @RequestMapping("depict/{style}/{fmt}")
  public HttpEntity<?> depict(@RequestParam("smi") String smi,
                              @PathVariable("fmt") String fmt,
                              @PathVariable("style") String style,
                              @RequestParam Map<String,String> extra) throws
          CDKException,
          IOException
  {

    String abbr = getString(Param.ABBREVIATE, extra);
    String annotate = getString(Param.ANNOTATE, extra);

    HydrogenDisplayType hDisplayType = getHydrogenDisplay(extra);

    // Note: DepictionGenerator is immutable
    DepictionGenerator myGenerator = generator.withSize(getDouble(Param.WIDTH, extra),
                                                        getDouble(Param.HEIGHT, extra))
                                              .withZoom(getDouble(Param.ZOOM, extra));

    // Configure style preset
    myGenerator = withStyle(myGenerator, style);
    myGenerator = withBgFgColors(extra, myGenerator);
    myGenerator = myGenerator.withAnnotationScale(0.7)
                             .withAnnotationColor(Color.RED);

    // align rxn maps
    myGenerator = myGenerator.withMappedRxnAlign(getBoolean(Param.ALIGNRXNMAP, extra));

    // Improved depiction of anatomised graphs, e.g. ***1*****1**
    if (getBoolean(Param.ANON, extra)) {
      myGenerator = myGenerator.withParam(Visibility.class,
                                          new SymbolVisibility() {
                                            @Override
                                            public boolean visible(IAtom iAtom, List<IBond> list,
                                                                   RendererModel rendererModel)
                                            {
                                              return list.isEmpty();
                                            }
                                          });
    }

    final boolean        isRxn = !smi.contains("V2000") && isRxnSmi(smi);
    final boolean        isRgp = smi.contains("RG:");
    IReaction            rxn   = null;
    IAtomContainer       mol   = null;
    List<IAtomContainer> mols  = null;

    Set<IChemObject> highlight;

    if (isRxn) {
      rxn = smipar.parseReactionSmiles(smi);
      for (IAtomContainer component : rxn.getReactants().atomContainers()) {
        setHydrogenDisplay(component, hDisplayType);
        perceiveRadicals(component);
      }
      for (IAtomContainer component : rxn.getProducts().atomContainers()) {
        setHydrogenDisplay(component, hDisplayType);
        perceiveRadicals(component);
      }
      for (IAtomContainer component : rxn.getAgents().atomContainers()) {
        setHydrogenDisplay(component, hDisplayType);
        perceiveRadicals(component);
      }
      highlight = findHits(getString(Param.SMARTSQUERY, extra),
                           rxn,
                           mol,
                           getInt(Param.SMARTSHITLIM, extra));
      abbreviate(rxn, abbr, annotate);
    } else {
      mol = loadMol(smi);
      setHydrogenDisplay(mol, hDisplayType);
      highlight = findHits(getString(Param.SMARTSQUERY, extra),
                           rxn,
                           mol,
                           getInt(Param.SMARTSHITLIM, extra));
      abbreviate(mol, abbr, annotate);
      perceiveRadicals(mol);
    }

    // Add annotations
    switch (annotate) {
      case "number":
        myGenerator = myGenerator.withAtomNumbers();
        abbr = "false";
        break;
      case "mapidx":
        myGenerator = myGenerator.withAtomMapNumbers();
        break;
      case "atomvalue":
        myGenerator = myGenerator.withAtomValues();
        break;
      case "colmap":
        myGenerator = myGenerator.withAtomMapHighlight(new Color[]{new Color(169, 199, 255),
                new Color(185, 255, 180),
                new Color(255, 162, 162),
                new Color(253, 139, 255),
                new Color(255, 206, 86),
                new Color(227, 227, 227)})
                                 .withOuterGlowHighlight(6d);
        break;
      case "cip":
        if (isRxn) {
          for (IAtomContainer part : ReactionManipulator.getAllAtomContainers(rxn)) {
            annotateCip(part);
          }
        } else {
          annotateCip(mol);
        }
        break;
    }

    // add highlight from atom/bonds hit by the provided SMARTS
    switch (style) {
      case "nob":
        myGenerator = myGenerator.withHighlight(highlight,
                                                new Color(0xffaaaa));
        break;
      case "bow":
      case "wob":
      case "bot":
        myGenerator = myGenerator.withHighlight(highlight,
                                                new Color(0xff0000));
        break;
      default:
        myGenerator = myGenerator.withHighlight(highlight,
                                                new Color(0xaaffaa));
        break;
    }


    if (getBoolean(Param.SHOWTITLE, extra)) {
      if (isRxn)
        myGenerator = myGenerator.withRxnTitle();
      else
        myGenerator = myGenerator.withMolTitle();
    }

    final String fmtlc = fmt.toLowerCase(Locale.ROOT);

    // pre-render the depiction
    final Depiction depiction = isRxn ? myGenerator.depict(rxn)
            : isRgp ? myGenerator.depict(mols, mols.size(), 1)
            : myGenerator.depict(mol);

    switch (fmtlc) {
      case Depiction.SVG_FMT:
        return makeResponse(depiction.toSvgStr(getString(Param.SVGUNITS, extra))
                                     .getBytes(), "image/svg+xml");
      case Depiction.PDF_FMT:
        return makeResponse(depiction.toPdfStr().getBytes(), "application/pdf");
      case Depiction.PNG_FMT:
      case Depiction.JPG_FMT:
      case Depiction.GIF_FMT:
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        ImageIO.write(depiction.toImg(), fmtlc, bao);
        return makeResponse(bao.toByteArray(), "image/" + fmtlc);
    }

    throw new IllegalArgumentException("Unsupported format.");
  }

  private DepictionGenerator withBgFgColors(
          @RequestParam Map<String, String> extra,
          DepictionGenerator myGenerator) {
    final String bgcolor = getString(Param.BGCOLOR, extra);
    switch (bgcolor) {
      case "clear":
      case "transparent":
      case "null":
        myGenerator = myGenerator.withBackgroundColor(new Color(0, 0, 0, 0));
        break;
      case "default":
        // do nothing
        break;
      default:
        myGenerator = myGenerator.withBackgroundColor(getColor(bgcolor));
        break;
    }

    final String fgcolor = getString(Param.FGCOLOR, extra);
    switch (fgcolor) {
      case "cpk":
      case "cdk":
        myGenerator = myGenerator.withAtomColors(new CDK2DAtomColors());
        break;
      case "default":
        // do nothing
        break;
      default:
        myGenerator = myGenerator.withAtomColors(new UniColor(getColor(fgcolor)));
        break;
    }
    return myGenerator;
  }

  private void annotateCip(IAtomContainer part)
  {
    CdkLabeller.label(part);
    for (IAtom atom : part.atoms()) {
      if (atom.getProperty(BaseMol.CONF_INDEX) != null)
        atom.setProperty(StandardGenerator.ANNOTATION_LABEL,
                         StandardGenerator.ITALIC_DISPLAY_PREFIX + atom.getProperty(BaseMol.CONF_INDEX));
      else if (atom.getProperty(BaseMol.CIP_LABEL_KEY) != null)
        atom.setProperty(StandardGenerator.ANNOTATION_LABEL,
                         StandardGenerator.ITALIC_DISPLAY_PREFIX + atom.getProperty(BaseMol.CIP_LABEL_KEY));
    }
    for (IBond bond : part.bonds()) {
      if (bond.getProperty(BaseMol.CIP_LABEL_KEY) != null)
        bond.setProperty(StandardGenerator.ANNOTATION_LABEL,
                         StandardGenerator.ITALIC_DISPLAY_PREFIX + bond.getProperty(BaseMol.CIP_LABEL_KEY));
    }
  }

  private void setHydrogenDisplay(IAtomContainer mol, HydrogenDisplayType hDisplayType)
  {
    switch (hDisplayType) {
      case Minimal:
        AtomContainerManipulator.suppressHydrogens(mol);
        break;
      case Stereo: {
        AtomContainerManipulator.suppressHydrogens(mol);
        List<IStereoElement> ses = new ArrayList<>();
        for (IStereoElement se : mol.stereoElements()) {
          switch (se.getConfigClass()) {
            case IStereoElement.Tetrahedral: {
              IAtom focus = (IAtom) se.getFocus();
              if (focus.getImplicitHydrogenCount() == 1) {
                focus.setImplicitHydrogenCount(0);
                IAtom          hydrogen = sproutHydrogen(mol, focus);
                IStereoElement tmp      = se.map(Collections.singletonMap(focus, hydrogen));
                // need to keep focus same
                ses.add(new TetrahedralChirality(focus,
                                                 (IAtom[]) tmp.getCarriers().toArray(new IAtom[4]),
                                                 tmp.getConfig()));
              } else {
                ses.add(se);
              }
            }
            break;
            case IStereoElement.CisTrans: {
              IBond focus = (IBond) se.getFocus();
              IAtom beg   = focus.getBegin();
              IAtom end   = focus.getEnd();
              if (beg.getImplicitHydrogenCount() == 1) {
                beg.setImplicitHydrogenCount(0);
                sproutHydrogen(mol, beg);
              }
              if (end.getImplicitHydrogenCount() == 1) {
                end.setImplicitHydrogenCount(0);
                sproutHydrogen(mol, end);
              }
              // don't need to update stereo element
              ses.add(se);
            }
            break;
            default:
              ses.add(se);
              break;
          }
        }
        mol.setStereoElements(ses);
      }
      break;
      case Smart: {
        AtomContainerManipulator.suppressHydrogens(mol);
        Cycles.markRingAtomsAndBonds(mol);
        List<IStereoElement> ses = new ArrayList<>();
        for (IStereoElement se : mol.stereoElements()) {
          switch (se.getConfigClass()) {
            case IStereoElement.Tetrahedral: {
              IAtom focus = (IAtom) se.getFocus();
              if (focus.getImplicitHydrogenCount() == 1 &&
                  shouldAddH(mol, focus, mol.getConnectedBondsList(focus))) {
                focus.setImplicitHydrogenCount(0);
                IAtom          hydrogen = sproutHydrogen(mol, focus);
                IStereoElement tmp      = se.map(Collections.singletonMap(focus, hydrogen));
                // need to keep focus same
                ses.add(new TetrahedralChirality(focus,
                                                 (IAtom[]) tmp.getCarriers().toArray(new IAtom[4]),
                                                 tmp.getConfig()));
              } else {
                ses.add(se);
              }
            }
            break;
            case IStereoElement.CisTrans: {
              IBond focus = (IBond) se.getFocus();
              IAtom begin = focus.getBegin();
              IAtom end   = focus.getEnd();
              IAtom hydrogenBegin = null;
              IAtom hydrogenEnd = null;

              if (begin.getImplicitHydrogenCount() == 1 &&
                  shouldAddH(mol, begin, mol.getConnectedBondsList(begin))) {
                begin.setImplicitHydrogenCount(0);
                hydrogenBegin = sproutHydrogen(mol, begin);
              }

              if (end.getImplicitHydrogenCount() == 1 &&
                  shouldAddH(mol, end, mol.getConnectedBondsList(end))) {
                end.setImplicitHydrogenCount(0);
                hydrogenEnd = sproutHydrogen(mol, end);
              }

              if (hydrogenBegin != null || hydrogenEnd != null) {
                Map<IAtom,IAtom> map = new HashMap<>();
                map.put(begin, hydrogenBegin);
                map.put(end, hydrogenEnd);
                ses.add(se.map(map));
              }
              else {
                ses.add(se);
              }
            }
            break;
            case IStereoElement.Allenal: {
              IAtom focus = (IAtom) se.getFocus();
              IAtom[] terminals = ExtendedTetrahedral.findTerminalAtoms(mol, focus);
              IAtom  hydrogen1 = null;
              IAtom  hydrogen2 = null;
              if (terminals[0].getImplicitHydrogenCount() == 1) {
                terminals[0].setImplicitHydrogenCount(0);
                hydrogen1 = sproutHydrogen(mol, terminals[0]);
              }
              if (terminals[1].getImplicitHydrogenCount() == 1) {
                terminals[1].setImplicitHydrogenCount(0);
                hydrogen2 = sproutHydrogen(mol, terminals[1]);
              }
              if (hydrogen1 != null || hydrogen2 != null) {
                Map<IAtom,IAtom> map = new HashMap<>();
                if (hydrogen1 != null)
                  map.put(terminals[0], hydrogen1);
                if (hydrogen2 != null)
                  map.put(terminals[1], hydrogen2);
                // find as focus is not one of the terminals
                IStereoElement<IAtom,IAtom> tmp = se.map(map);
                ses.add(tmp);
              }
              else {
                ses.add(se);
              }
            }
            break;
            default:
              ses.add(se);
              break;
          }
        }
        mol.setStereoElements(ses);
      }
      break;
      case Provided:
      default:
        break;
    }
  }

  private boolean shouldAddH(IAtomContainer mol, IAtom atom, Iterable<IBond> bonds) {
    int count = 0;
    for (IBond bond : bonds) {
      IAtom nbr = bond.getOther(atom);
      if (bond.isInRing()) {
        ++count;
      } else {
        for (IStereoElement se : mol.stereoElements()) {
          if (se.getConfigClass() == IStereoElement.TH &&
                  se.getFocus().equals(nbr)) {
            count++;
          }
        }
      }
      // hydrogen isotope
      if (nbr.getAtomicNumber() == 1 &&
          nbr.getMassNumber() != null)
        return true;
    }
    return count == 3;
  }

  private IAtom sproutHydrogen(IAtomContainer mol, IAtom focus)
  {
    IAtom hydrogen = mol.getBuilder().newAtom();
    hydrogen.setAtomicNumber(1);
    hydrogen.setSymbol("H");
    hydrogen.setImplicitHydrogenCount(0);
    mol.addAtom(hydrogen);
    mol.addBond(mol.indexOf(focus), mol.getAtomCount() - 1, IBond.Order.SINGLE);
    return mol.getAtom(mol.getAtomCount()-1);
  }

  private void contractHydrates(IAtomContainer mol)
  {
    Set<IAtom> hydrate = new HashSet<>();
    for (IAtom atom : mol.atoms()) {
      if (atom.getAtomicNumber() == 8 &&
          atom.getImplicitHydrogenCount() == 2 &&
          mol.getConnectedAtomsList(atom).size() == 0)
        hydrate.add(atom);
    }
    if (hydrate.size() < 2)
      return;
    @SuppressWarnings("unchecked")
    List<Sgroup> sgroups = mol.getProperty(CDKConstants.CTAB_SGROUPS, List.class);

    if (sgroups == null)
      mol.setProperty(CDKConstants.CTAB_SGROUPS,
                      sgroups = new ArrayList<>());
    else
      sgroups = new ArrayList<>(sgroups);

    Sgroup sgrp = new Sgroup();
    for (IAtom atom : hydrate)
      sgrp.addAtom(atom);
    sgrp.putValue(SgroupKey.CtabParentAtomList,
                  Collections.singleton(hydrate.iterator().next()));
    sgrp.setType(SgroupType.CtabMultipleGroup);
    sgrp.setSubscript(Integer.toString(hydrate.size()));

    sgroups.add(sgrp);
  }

  private boolean add(Set<IAtom> set, Set<IAtom> atomsToAdd)
  {
    boolean res = true;
    for (IAtom atom : atomsToAdd) {
      if (!set.add(atom))
        res = false;
    }
    return res;
  }

  private void abbreviate(IReaction rxn, String mode, String annotate)
  {
    Multimap<IAtomContainer, Sgroup> sgroupmap = ArrayListMultimap.create();
    switch (mode.toLowerCase()) {
      case "true":
      case "on":
      case "yes":
        for (IAtomContainer mol : rxn.getReactants().atomContainers()) {
          contractHydrates(mol);
          Set<IAtom>   atoms      = new HashSet<>();
          List<Sgroup> newSgroups = new ArrayList<>();
          for (Sgroup sgroup : abbreviations.generate(mol)) {
            if (add(atoms, sgroup.getAtoms()))
              newSgroups.add(sgroup);
          }
          sgroupmap.putAll(mol, newSgroups);
        }
        for (IAtomContainer mol : rxn.getProducts().atomContainers()) {
          contractHydrates(mol);
          Set<IAtom>   atoms      = new HashSet<>();
          List<Sgroup> newSgroups = new ArrayList<>();
          for (Sgroup sgroup : abbreviations.generate(mol)) {
            if (add(atoms, sgroup.getAtoms()))
              newSgroups.add(sgroup);
          }
          sgroupmap.putAll(mol, newSgroups);
        }
        for (IAtomContainer mol : rxn.getAgents().atomContainers()) {
          contractHydrates(mol);
          reagents.apply(mol);
          abbreviations.apply(mol);
        }
        break;
      case "groups":
        for (IAtomContainer mol : rxn.getAgents().atomContainers()) {
          contractHydrates(mol);
          abbreviations.apply(mol);
        }
        break;
      case "reagents":
        for (IAtomContainer mol : rxn.getAgents().atomContainers()) {
          contractHydrates(mol);
          reagents.apply(mol);
        }
        break;
    }

    Set<String> include = new HashSet<>();
    for (Map.Entry<IAtomContainer, Sgroup> e : sgroupmap.entries()) {
      final IAtomContainer mol      = e.getKey();
      final Sgroup         abbrv    = e.getValue();
      int                  numAtoms = mol.getAtomCount();
      if (abbrv.getBonds().isEmpty()) {
        include.add(abbrv.getSubscript());
      } else {
        int    numAbbr = abbrv.getAtoms().size();
        double f       = numAbbr / (double) numAtoms;
        if (numAtoms - numAbbr > 1 && f <= 0.4) {
          include.add(abbrv.getSubscript());
        }
      }
    }

    for (Map.Entry<IAtomContainer, Collection<Sgroup>> e : sgroupmap.asMap().entrySet()) {
      final IAtomContainer mol = e.getKey();

      List<Sgroup> sgroups = mol.getProperty(CDKConstants.CTAB_SGROUPS);
      if (sgroups == null)
        sgroups = new ArrayList<>();
      else
        sgroups = new ArrayList<>(sgroups);
      mol.setProperty(CDKConstants.CTAB_SGROUPS, sgroups);

      for (Sgroup abbrv : e.getValue()) {
        if (include.contains(abbrv.getSubscript()))
          sgroups.add(abbrv);
      }
    }
  }

  private void abbreviate(IAtomContainer mol, String mode, String annotate)
  {
    switch (mode.toLowerCase()) {
      case "true":
      case "on":
      case "yes":
      case "groups":
        contractHydrates(mol);
        abbreviations.apply(mol);
        break;
      case "reagents":
        contractHydrates(mol);
        break;
    }
    // remove abbreviations of mapped atoms
    if ("mapidx".equals(annotate)) {
      List<Sgroup> sgroups  = mol.getProperty(CDKConstants.CTAB_SGROUPS);
      List<Sgroup> filtered = new ArrayList<>();
      if (sgroups != null) {
        for (Sgroup sgroup : sgroups) {
          // turn off display short-cuts
          if (sgroup.getType() == SgroupType.CtabAbbreviation ||
              sgroup.getType() == SgroupType.CtabMultipleGroup) {
            boolean okay = true;
            for (IAtom atom : sgroup.getAtoms()) {
              if (atom.getProperty(CDKConstants.ATOM_ATOM_MAPPING) != null) {
                okay = false;
                break;
              }
            }
            if (okay) filtered.add(sgroup);
          } else {
            filtered.add(sgroup);
          }
        }
        mol.setProperty(CDKConstants.CTAB_SGROUPS, filtered);
      }
    }
  }

  private boolean isRxnSmi(String smi)
  {
    return smi.split(" ")[0].contains(">");
  }

  private IAtomContainer loadMol(String str) throws CDKException
  {
    if (str.contains("V2000")) {
      try (MDLV2000Reader mdlr = new MDLV2000Reader(new StringReader(str))) {
        return mdlr.read(new AtomContainer(0, 0, 0, 0));
      } catch (CDKException | IOException e3) {
        throw new CDKException("Could not parse input");
      }
    } else {
      try {
        return smipar.parseSmiles(str);
      } catch (CDKException ex) {
        SmilesParser smipar2 = new SmilesParser(builder);
        smipar2.kekulise(false);
        return smipar2.parseSmiles(str);
      }
    }
  }

  private HttpEntity<byte[]> makeResponse(byte[] bytes, String contentType)
  {
    HttpHeaders header  = new HttpHeaders();
    String      type    = contentType.substring(0, contentType.indexOf('/'));
    String      subtype = contentType.substring(contentType.indexOf('/') + 1, contentType.length());
    header.setContentType(new MediaType(type, subtype));
    header.add("Access-Control-Allow-Origin", "*");
    header.set(HttpHeaders.CACHE_CONTROL, "max-age=31536000");
    header.setContentLength(bytes.length);
    return new HttpEntity<>(bytes, header);
  }

  /**
   * Set the depiction style.
   *
   * @param generator the generator
   * @param style     style type
   * @return configured depiction generator
   */
  private static DepictionGenerator withStyle(DepictionGenerator generator,
                                              String style)
  {
    switch (style) {
      case "cow":
        generator = generator.withAtomColors(new CDK2DAtomColors())
                             .withBackgroundColor(Color.WHITE)
                             .withOuterGlowHighlight();
        break;
      case "cot":
        generator = generator.withAtomColors(new CDK2DAtomColors())
                             .withBackgroundColor(new Color(0,0,0,0))
                             .withOuterGlowHighlight();
        break;
      case "bow":
        generator = generator.withAtomColors(new UniColor(Color.BLACK))
                             .withBackgroundColor(Color.WHITE);
        break;
      case "bot":
        generator = generator.withAtomColors(new UniColor(Color.BLACK))
                             .withBackgroundColor(new Color(0,0,0,0));
        break;
      case "wob":
        generator = generator.withAtomColors(new UniColor(Color.WHITE))
                             .withBackgroundColor(Color.BLACK);
        break;
      case "cob":
        generator = generator.withAtomColors(new CobColorer())
                             .withBackgroundColor(Color.BLACK)
                             .withOuterGlowHighlight();
        break;
      case "nob":
        generator = generator.withAtomColors(new NobColorer())
                             .withBackgroundColor(Color.BLACK)
                             .withOuterGlowHighlight();
        break;
    }
    return generator;
  }

  /**
   * Color-on-black atom colors.
   */
  private static final class CobColorer implements IAtomColorer {
    private final CDK2DAtomColors colors = new CDK2DAtomColors();

    @Override
    public Color getAtomColor(IAtom atom)
    {
      Color res = colors.getAtomColor(atom);
      if (res.equals(Color.BLACK))
        return Color.WHITE;
      else
        return res;
    }

    @Override
    public Color getAtomColor(IAtom atom, Color color)
    {
      Color res = colors.getAtomColor(atom);
      if (res.equals(Color.BLACK))
        return Color.WHITE;
      else
        return res;
    }
  }

  /**
   * Neon-on-black atom colors.
   */
  private static final class NobColorer implements IAtomColorer {
    private final CDK2DAtomColors colors = new CDK2DAtomColors();
    private final Color           NEON   = new Color(0x00FF0E);

    @Override
    public Color getAtomColor(IAtom atom)
    {
      Color res = colors.getAtomColor(atom);
      if (res.equals(Color.BLACK))
        return NEON;
      else
        return res;
    }

    @Override
    public Color getAtomColor(IAtom atom, Color color)
    {
      Color res = colors.getAtomColor(atom, color);
      if (res.equals(Color.BLACK))
        return NEON;
      else
        return res;
    }
  }

  /**
   * Find matching atoms and bonds in the reaction or molecule.
   *
   * @param sma SMARTS pattern
   * @param rxn reaction
   * @param mol molecule
   * @return set of matched atoms and bonds
   */
  private Set<IChemObject> findHits(final String sma, final IReaction rxn, final IAtomContainer mol,
                                    final int limit)
  {


    Set<IChemObject> highlight = new HashSet<>();
    if (!sma.isEmpty()) {
      SmartsPattern smartsPattern;
      try {
        smartsPattern = SmartsPattern.create(sma, null);
      } catch (Exception | Error e) {
        return Collections.emptySet();
      }
      if (mol != null) {
        for (Map<IChemObject, IChemObject> m : smartsPattern.matchAll(mol)
                                                            .limit(limit)
                                                            .uniqueAtoms()
                                                            .toAtomBondMap()) {
          for (Map.Entry<IChemObject, IChemObject> e : m.entrySet()) {
            highlight.add(e.getValue());
          }
        }
      } else if (rxn != null) {
        for (Map<IChemObject, IChemObject> m : smartsPattern.matchAll(rxn)
                                                            .limit(limit)
                                                            .uniqueAtoms()
                                                            .toAtomBondMap()) {
          for (Map.Entry<IChemObject, IChemObject> e : m.entrySet()) {
            highlight.add(e.getValue());
          }
        }
      }
    }
    return highlight;

  }
}
