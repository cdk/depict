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
import org.openscience.cdk.renderer.generators.standard.StandardGenerator;
import org.openscience.cdk.renderer.generators.standard.StandardGenerator.Visibility;
import org.openscience.cdk.sgroup.Sgroup;
import org.openscience.cdk.sgroup.SgroupKey;
import org.openscience.cdk.sgroup.SgroupType;
import org.openscience.cdk.silent.AtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.smiles.smarts.SmartsPattern;
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
import java.util.Collection;
import java.util.Collections;
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

  /**
   * Restful entry point.
   *
   * @param smi      SMILES to depict
   * @param fmt      output format
   * @param style    preset style COW (Color-on-white), COB, BOW, COW
   * @param anon     rendering anonymised graphs
   * @param zoom     zoom factor (1=100%=none)
   * @param annotate annotations to add
   * @param w        width of the image
   * @param h        height of the image
   * @param sma      highlight SMARTS pattern
   * @return the depicted structure
   * @throws CDKException something not okay with input
   * @throws IOException  problem reading/writing request
   */
  @RequestMapping("depict/{style}/{fmt}")
  public HttpEntity<?> depict(@RequestParam("smi") String smi,
                              @PathVariable("fmt") String fmt,
                              @PathVariable("style") String style,
                              @RequestParam(value = "suppressh", defaultValue = "true") String suppressh,
                              @RequestParam(value = "hdisp", defaultValue = "bridgehead") String hDisplayParam,
                              @RequestParam(value = "anon", defaultValue = "off") String anon,
                              @RequestParam(value = "zoom", defaultValue = "1.3") double zoom,
                              @RequestParam(value = "annotate", defaultValue = "none") String annotate,
                              @RequestParam(value = "w", defaultValue = "-1") int w,
                              @RequestParam(value = "h", defaultValue = "-1") int h,
                              @RequestParam(value = "abbr", defaultValue = "reagents") String abbr,
                              @RequestParam(value = "sma", defaultValue = "") String sma,
                              @RequestParam(value = "showtitle", defaultValue = "false") boolean showTitle,
                              @RequestParam(value = "smalim", defaultValue = "100") int smaLimit,
                              @RequestParam(value = "alignrxnmap", defaultValue = "true") boolean alignRxnMap,
                              @RequestParam Map<String,String> params) throws
          CDKException,
          IOException
  {

    // backwards compatibility
    HydrogenDisplayType hDisplayType = HydrogenDisplayType.Minimal;
    if ("false".equalsIgnoreCase(suppressh) || "f".equalsIgnoreCase(suppressh)) {
      hDisplayType = HydrogenDisplayType.Provided;
    } else {
      if (hDisplayParam != null) {
        switch (hDisplayParam.toLowerCase()) {
          case "suppressed":
            hDisplayType = HydrogenDisplayType.Minimal;
            break;
          case "provided":
            hDisplayType = HydrogenDisplayType.Provided;
            break;
          case "stereo":
            hDisplayType = HydrogenDisplayType.StereoOnly;
            break;
          case "bridgeheadtetrahedral":
          case "bridgehead":
          case "default":
          case "smart":
            hDisplayType = HydrogenDisplayType.BridgeHeadTetrahedralOnly;
            break;
        }
      }
    }

    // Note: DepictionGenerator is immutable
    DepictionGenerator myGenerator = generator.withSize(w, h)
                                              .withZoom(zoom);

    // Configure style preset
    myGenerator = withStyle(myGenerator, style);

    myGenerator = myGenerator.withAnnotationScale(0.7)
                             .withAnnotationColor(Color.RED);

    // align rxn maps
    myGenerator = myGenerator.withMappedRxnAlign(alignRxnMap);

    // Improved depiction of anatomised graphs, e.g. ***1*****1**
    if (anon.equalsIgnoreCase("on")) {
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
      for (IAtomContainer component : rxn.getReactants().atomContainers())
        setHydrogenDisplay(component, hDisplayType);
      for (IAtomContainer component : rxn.getProducts().atomContainers())
        setHydrogenDisplay(component, hDisplayType);
      for (IAtomContainer component : rxn.getAgents().atomContainers())
        setHydrogenDisplay(component, hDisplayType);
      highlight = findHits(sma, rxn, mol, smaLimit);
      abbreviate(rxn, abbr, annotate);
    } else {
      mol = loadMol(smi);
      setHydrogenDisplay(mol, hDisplayType);
      highlight = findHits(sma, rxn, mol, smaLimit);
      abbreviate(mol, abbr, annotate);
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
        myGenerator = myGenerator.withHighlight(highlight,
                                                new Color(0xff0000));
        break;
      default:
        myGenerator = myGenerator.withHighlight(highlight,
                                                new Color(0xaaffaa));
        break;
    }


    if (showTitle) {
      if (isRxn)
        myGenerator = myGenerator.withRxnTitle();
      else
        myGenerator = myGenerator.withMolTitle();
    }

    // pre-render the depiction
    final Depiction depiction = isRxn ? myGenerator.depict(rxn)
            : isRgp ? myGenerator.depict(mols, mols.size(), 1)
            : myGenerator.depict(mol);

    final String fmtlc = fmt.toLowerCase(Locale.ROOT);
    switch (fmtlc) {
      case Depiction.SVG_FMT:
        return makeResponse(depiction.toSvgStr().getBytes(), "image/svg+xml");
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

  private void annotateCip(IAtomContainer part)
  {
    CdkLabeller.label(part);
    for (IAtom atom : part.atoms()) {
      if (atom.getProperty("cip.label") != null)
        atom.setProperty(StandardGenerator.ANNOTATION_LABEL,
                         StandardGenerator.ITALIC_DISPLAY_PREFIX + atom.getProperty("cip.label"));
    }
    for (IBond bond : part.bonds()) {
      if (bond.getProperty("cip.label") != null)
        bond.setProperty(StandardGenerator.ANNOTATION_LABEL,
                         StandardGenerator.ITALIC_DISPLAY_PREFIX + bond.getProperty("cip.label"));
    }
  }

  private void setHydrogenDisplay(IAtomContainer mol, HydrogenDisplayType hDisplayType)
  {
    switch (hDisplayType) {
      case Minimal:
        AtomContainerManipulator.suppressHydrogens(mol);
        break;
      case StereoOnly: {
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
      case BridgeHeadTetrahedralOnly: {
        Cycles.markRingAtomsAndBonds(mol);
        AtomContainerManipulator.suppressHydrogens(mol);
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
                ses.add(new TetrahedralChirality(focus,
                                                 (IAtom[]) tmp.getCarriers().toArray(new IAtom[4]),
                                                 tmp.getConfig()));
              } else {
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
      if (bond.isInRing()) {
        ++count;
      } else {
        IAtom nbr = bond.getOther(atom);
        for (IStereoElement se : mol.stereoElements()) {
          if (se.getConfigClass() == IStereoElement.TH &&
                  se.getFocus().equals(nbr)) {
            count++;
          }
        }
      }
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
    return hydrogen;
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
  private static DepictionGenerator withStyle(DepictionGenerator generator, String style)
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
