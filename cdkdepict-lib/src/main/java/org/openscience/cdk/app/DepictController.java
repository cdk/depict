/*
 * Copyright (c) 2018. NextMove Software Ltd.
 */

package org.openscience.cdk.app;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.simolecule.centres.BaseMol;
import com.simolecule.centres.CdkLabeller;
import com.simolecule.centres.Descriptor;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.depict.Abbreviations;
import org.openscience.cdk.depict.Depiction;
import org.openscience.cdk.depict.DepictionGenerator;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.geometry.GeometryUtil;
import org.openscience.cdk.graph.Cycles;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IChemObject;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.interfaces.IReaction;
import org.openscience.cdk.interfaces.IReactionSet;
import org.openscience.cdk.interfaces.IStereoElement;
import org.openscience.cdk.io.MDLV2000Reader;
import org.openscience.cdk.io.MDLV3000Reader;
import org.openscience.cdk.layout.StructureDiagramGenerator;
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
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smarts.SmartsPattern;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.stereo.ExtendedTetrahedral;
import org.openscience.cdk.stereo.Octahedral;
import org.openscience.cdk.stereo.SquarePlanar;
import org.openscience.cdk.stereo.Stereocenters;
import org.openscience.cdk.stereo.TetrahedralChirality;
import org.openscience.cdk.stereo.TrigonalBipyramidal;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import org.openscience.cdk.tools.manipulator.ReactionManipulator;
import org.openscience.cdk.tools.manipulator.ReactionSetManipulator;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.WebRequest;

import javax.imageio.ImageIO;
import javax.vecmath.Point2d;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Chemical structure depiction controller.
 */
@CrossOrigin
@Controller
public class DepictController {

  private final Object lock = new Object();

  private Color[] COLORS = new Color[]{
          new Color(0xe6194b),
          new Color(0x3cb44b),
          new Color(0xffe119),
          new Color(0x0082c8),
          new Color(0xf58231),
          new Color(0x911eb4),
          new Color(0x46f0f0),
          new Color(0xf032e6),
          new Color(0xd2f53c),
          new Color(0xfabebe),
          new Color(0x008080),
          new Color(0xe6beff),
          new Color(0xaa6e28),
          new Color(0xfffac8),
          new Color(0x800000),
          new Color(0xaaffc3),
          new Color(0x808000),
          new Color(0xffd8b1),
          new Color(0x000080),
          new Color(0x808080),
          new Color(0xE3E3E3),
          new Color(0x000000)
  };

  private final ExecutorService smartsExecutor = Executors.newFixedThreadPool(4);

  // chem object builder to create objects with
  private final IChemObjectBuilder builder = SilentChemObjectBuilder.getInstance();

  // we make are raster depictions slightly smalled by default (40px bond length)
  private final DepictionGenerator generator = new DepictionGenerator();
  private SmilesParser smipar = new SmilesParser(builder);

  private final Abbreviations groupAbbr = new Abbreviations();
  private final Abbreviations agentAbbr = new Abbreviations();

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
    ARROW("arw", IReaction.Direction.FORWARD),
    DATIVE("dat", MolOp.DativeBond.Metals),
    ZOOM("zoom", 1.3),
    RATIO("ratio", 1.1),
    ROTATE("r", 0),
    FLIP("f", false),
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

  public DepictController() throws IOException {
    this.groupAbbr.loadFromFile("/org/openscience/cdk/app/group_abbr.smi");
    this.agentAbbr.loadFromFile("/org/openscience/cdk/app/reagent_abbr.smi");
    this.agentAbbr.loadFromFile("/org/openscience/cdk/app/group_abbr.smi");
    this.agentAbbr
            .with(Abbreviations.Option.ALLOW_SINGLETON)
            .with(Abbreviations.Option.AUTO_CONTRACT_TERMINAL)
            .without(Abbreviations.Option.AUTO_CONTRACT_HETERO);
    this.groupAbbr
            .without(Abbreviations.Option.ALLOW_SINGLETON)
            .with(Abbreviations.Option.AUTO_CONTRACT_TERMINAL)
            .without(Abbreviations.Option.AUTO_CONTRACT_HETERO);
  }

  private <T> T getParam(Param param,
                         Map<String, String> params,
                         Function<String, T> converter) {
    T value = (T) param.defaultValue;
    String str = params.get(param.name);
    if (str != null && !str.isEmpty())
      value = converter.apply(str);
    return value;
  }

  private String getString(Param param, Map<String, String> params) {
    String value = params.get(param.name);
    if (value != null)
      return value;
    return param.defaultValue != null ? param.defaultValue.toString() : "";
  }

  private double getDouble(Param param, Map<String, String> params) {
    String value = getString(param, params);
    if (value.isEmpty()) {
      if (param.defaultValue != null)
        return (double) param.defaultValue;
      throw new IllegalArgumentException(param.name + " not provided and no default!");
    }
    return Double.parseDouble(value);
  }

  private int getInt(Param param, Map<String, String> params) {
    String value = getString(param, params);
    if (value.isEmpty()) {
      if (param.defaultValue != null)
        return (int) param.defaultValue;
      throw new IllegalArgumentException(param.name + " not provided and no default!");
    }
    return Integer.parseInt(value);
  }

  private boolean getBoolean(Param param, Map<String, String> params) {
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
    int vals[] = new int[]{0, 0, 0, 255}; // r,g,b,a
    int pos = 0;
    int beg = 0;
    if (color.startsWith("0x"))
      beg = 2;
    else if (color.startsWith("#"))
      beg = 1;
    for (; pos < 4 && beg + 1 < color.length(); beg += 2) {
      vals[pos++] = Integer.parseInt(color.substring(beg, beg + 2), 16);
    }
    return new Color(vals[0], vals[1], vals[2], vals[3]);
  }

  private HydrogenDisplayType getHydrogenDisplay(Map<String, String> params) {
    if (!getBoolean(Param.SUPRESSH, params)) {
      return HydrogenDisplayType.Provided;
    } else {
      return HydrogenDisplayType.parse(getString(Param.HDISPLAY, params));
    }
  }

  /**
   * Restful entry point.
   *
   * @param smi   SMILES to depict
   * @param fmt   output format
   * @param style preset style COW (Color-on-white), COB, BOW, COW
   * @return the depicted structure
   * @throws CDKException something not okay with input
   * @throws IOException  problem reading/writing request
   */
  @RequestMapping("depict/{style}/{fmt}")
  public HttpEntity<?> depict(@RequestParam("smi") String smi,
                              @PathVariable("fmt") String fmt,
                              @PathVariable("style") String style,
                              @RequestParam Map<String, String> extra) throws
          CDKException,
          IOException {

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
    myGenerator = myGenerator.withParam(StandardGenerator.StrokeRatio.class, getDouble(Param.RATIO, extra));


    // align rxn maps
    myGenerator = myGenerator.withMappedRxnAlign(getBoolean(Param.ALIGNRXNMAP, extra));

    // Improved depiction of anatomised graphs, e.g. ***1*****1**
    if (getBoolean(Param.ANON, extra)) {
      myGenerator = myGenerator.withParam(Visibility.class,
                                          new SymbolVisibility() {
                                            @Override
                                            public boolean visible(IAtom iAtom, List<IBond> list,
                                                                   RendererModel rendererModel) {
                                              return list.isEmpty();
                                            }
                                          });
    }

    final boolean isRxn = !smi.contains("V2000") && !smi.contains("V3000") && isRxnSmi(smi);
    final boolean isRgp = smi.contains("RG:");
    IReactionSet rxns = null;
    IAtomContainer mol = null;
    List<IAtomContainer> mols = null;

    Set<IChemObject> highlight = new HashSet<>();

    StructureDiagramGenerator sdg = new StructureDiagramGenerator();
    sdg.setAlignMappedReaction(getBoolean(Param.ALIGNRXNMAP, extra));
    MolOp.DativeBond doDative = getParam(Param.DATIVE, extra, this::parseDativeParam);

    if (isRxn) {
      try {
        rxns = smipar.parseReactionSetSmiles(smi);
      } catch (CDKException ex) {
        SmilesParser smipar2 = new SmilesParser(builder);
        smipar2.kekulise(false);
        rxns = smipar2.parseReactionSetSmiles(smi);
      }

      for (IReaction rxn : rxns.reactions()) {
        if (rxn.getDirection() == IReaction.Direction.FORWARD)
          rxn.setDirection(getParam(Param.ARROW, extra, this::parseArrowParam));
      }

      highlight = new HashSet<>();
      for (IReaction rxn : rxns.reactions()) {
        Set<IChemObject> hits = findHits(getString(Param.SMARTSQUERY, extra),
                                         rxn,
                                         null,
                                         getInt(Param.SMARTSHITLIM, extra));
        highlight.addAll(hits);
        abbreviate(rxn, abbr, highlight);
        for (IAtomContainer component : rxn.getReactants().atomContainers()) {
          setHydrogenDisplay(component, hDisplayType);
          MolOp.perceiveRadicals(component);
          MolOp.perceiveDativeBonds(component, doDative);
        }
        for (IAtomContainer component : rxn.getProducts().atomContainers()) {
          setHydrogenDisplay(component, hDisplayType);
          MolOp.perceiveRadicals(component);
          MolOp.perceiveDativeBonds(component, doDative);
        }
        for (IAtomContainer component : rxn.getAgents().atomContainers()) {
          setHydrogenDisplay(component, hDisplayType);
          MolOp.perceiveRadicals(component);
          MolOp.perceiveDativeBonds(component, doDative);
        }
        if (!GeometryUtil.has2DCoordinates(rxn))
          sdg.generateCoordinates(rxn);
      }
    } else {
      mol = loadMol(smi);
      setHydrogenDisplay(mol, hDisplayType);
      highlight = findHits(getString(Param.SMARTSQUERY, extra),
                           null,
                           mol,
                           getInt(Param.SMARTSHITLIM, extra));
      abbreviate(mol, abbr, annotate, highlight);
      MolOp.perceiveRadicals(mol);
      MolOp.perceiveDativeBonds(mol, doDative);
      if (!GeometryUtil.has2DCoordinates(mol))
        sdg.generateCoordinates(mol);
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
        if (isRxn) {
          myGenerator = myGenerator.withAtomMapHighlight(new Color[]{new Color(169, 199, 255),
                                           new Color(185, 255, 180),
                                           new Color(255, 162, 162),
                                           new Color(253, 139, 255),
                                           new Color(255, 206, 86),
                                           new Color(227, 227, 227)})
                                   .withOuterGlowHighlight(6d);
        } else {
          myGenerator = myGenerator.withOuterGlowHighlight();
          myGenerator = myGenerator.withParam(StandardGenerator.Visibility.class,
                                              SymbolVisibility.iupacRecommendationsWithoutTerminalCarbon());
          for (IAtom atom : mol.atoms()) {
            Integer mapidx = atom.getProperty(CDKConstants.ATOM_ATOM_MAPPING);
            if (mapidx != null && mapidx < COLORS.length)
              atom.setProperty(StandardGenerator.HIGHLIGHT_COLOR, COLORS[mapidx]);
          }
        }
        break;
      case "cip":
        if (isRxn) {
          for (IReaction rxn : rxns.reactions()) {
            for (IAtomContainer part : ReactionManipulator.getAllAtomContainers(rxn)) {
              annotateCip(part);
            }
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

    // reactions are laid out in the main depiction gen
    if (getBoolean(Param.FLIP, extra)) {
      if (isRxn) {
        for (IAtomContainer part : ReactionSetManipulator.getAllAtomContainers(rxns))
          flip(part);
      } else
        flip(mol);
    }
    int rotate = getInt(Param.ROTATE, extra);
    if (rotate != 0) {
      if (isRxn) {
        for (IAtomContainer part : ReactionSetManipulator.getAllAtomContainers(rxns))
          rotate(part, rotate);
      } else {
        rotate(mol, rotate);
      }
    }

    final String fmtlc = fmt.toLowerCase(Locale.ROOT);

    // pre-render the depiction
    final Depiction depiction = isRxn ? myGenerator.depict(rxns)
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

  private MolOp.DativeBond parseDativeParam(String s) {
    if (s == null || s.isEmpty())
      return null;
    switch (s.toLowerCase(Locale.ROOT)) {
      case "y":
        return MolOp.DativeBond.Always;
      case "m":
        return MolOp.DativeBond.Metals;
      case "n":
        return MolOp.DativeBond.Never;
      default:
        return null;
    }
  }

  private IReaction.Direction parseArrowParam(String s) {
    if (s == null || s.isEmpty())
      return null;
    switch (s.toLowerCase(Locale.ROOT)) {
      case "equ":
        return IReaction.Direction.BIDIRECTIONAL;
      case "ngo":
        return IReaction.Direction.NO_GO;
      case "ret":
        return IReaction.Direction.RETRO_SYNTHETIC;
      case "res":
        return IReaction.Direction.RESONANCE;
      default:
        return null;
    }
  }

  private void rotate(IAtomContainer mol, int rotate) {
    Point2d c = GeometryUtil.get2DCenter(mol);
    GeometryUtil.rotate(mol, c, Math.toRadians(rotate));
  }

  private void flip(IBond bond) {
    switch (bond.getDisplay()) {
      case WedgeBegin:
        bond.setDisplay(IBond.Display.WedgedHashBegin);
        break;
      case WedgeEnd:
        bond.setDisplay(IBond.Display.WedgedHashEnd);
        break;
      case WedgedHashBegin:
        bond.setDisplay(IBond.Display.WedgeBegin);
        break;
      case WedgedHashEnd:
        bond.setDisplay(IBond.Display.WedgeEnd);
        break;
      case Bold:
        bond.setDisplay(IBond.Display.Hash);
        break;
      case Hash:
        bond.setDisplay(IBond.Display.Bold);
        break;
    }
  }

  private void flip(IAtomContainer mol) {
    for (IAtom atom : mol.atoms()) {
      atom.getPoint2d().x = -atom.getPoint2d().x;
    }
    for (IStereoElement<?, ?> se : mol.stereoElements()) {
      if (se.getConfigClass() == IStereoElement.Tetrahedral) {
        for (IBond bond : ((IAtom) se.getFocus()).bonds())
          flip(bond);
      } else if (se.getConfigClass() == IStereoElement.Allenal) {
        IAtom[] ends = ExtendedTetrahedral.findTerminalAtoms(mol, (IAtom) se.getFocus());
        for (IBond bond : ends[0].bonds())
          flip(bond);
        for (IBond bond : ends[1].bonds())
          flip(bond);
      }
      // Note: inorganic stereo does not flip, but the layout might be wrong if chiral.
      // Atropisomers not currently possiblet to input via SMILES
    }
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

  private void annotateCip(IAtomContainer part) {
    Stereocenters stereocenters = Stereocenters.of(part);
    for (IAtom atom : part.atoms()) {
      if (stereocenters.isStereocenter(atom.getIndex()) &&
              stereocenters.elementType(atom.getIndex()) == Stereocenters.Type.Tetracoordinate) {
        atom.setProperty(StandardGenerator.ANNOTATION_LABEL,
                         "(?)");
      }
    }
    for (IBond bond : part.bonds()) {
      if (bond.getOrder() != IBond.Order.DOUBLE)
        continue;
      int begIdx = bond.getBegin().getIndex();
      int endIdx = bond.getEnd().getIndex();
      if (stereocenters.elementType(begIdx) == Stereocenters.Type.Tricoordinate &&
              stereocenters.elementType(endIdx) == Stereocenters.Type.Tricoordinate &&
              stereocenters.isStereocenter(begIdx) &&
              stereocenters.isStereocenter(endIdx)) {
        // only if not in a small ring <7
        if (Cycles.smallRingSize(bond, 7) == 0) {
          bond.setProperty(StandardGenerator.ANNOTATION_LABEL,
                           "(?)");
        }
      }
    }

    // no defined stereo?
    if (!part.stereoElements().iterator().hasNext())
      return;

    CdkLabeller.label(part);
    // update to label appropriately for racmic and relative stereochemistry
    for (IStereoElement<?, ?> se : part.stereoElements()) {
      if (se.getConfigClass() == IStereoElement.TH &&
              se.getGroupInfo() != 0) {
        IAtom focus = (IAtom) se.getFocus();
        Object label = focus.getProperty(BaseMol.CIP_LABEL_KEY);
        if (label instanceof Descriptor &&
                label != Descriptor.ns &&
                label != Descriptor.Unknown) {
          if ((se.getGroupInfo() & IStereoElement.GRP_RAC) != 0) {
            Descriptor inv = null;
            switch ((Descriptor) label) {
              case R:
                inv = Descriptor.S;
                break;
              case S:
                inv = Descriptor.R;
                break;
            }
            if (inv != null)
              focus.setProperty(BaseMol.CIP_LABEL_KEY, label.toString() + inv.name());
          } else if ((se.getGroupInfo() & IStereoElement.GRP_REL) != 0) {
            switch ((Descriptor) label) {
              case R:
              case S:
                focus.setProperty(BaseMol.CIP_LABEL_KEY, label.toString() + "*");
                break;
            }
          }
        }
      }
    }

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

  private void setHydrogenDisplay(IAtomContainer mol, HydrogenDisplayType hDisplayType) {
    switch (hDisplayType) {
      case Minimal:
        AtomContainerManipulator.suppressHydrogens(mol);
        break;
      case Explicit:
        AtomContainerManipulator.convertImplicitToExplicitHydrogens(mol);
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
                IAtom hydrogen = sproutHydrogen(mol, focus);
                IStereoElement tmp = se.map(Collections.singletonMap(focus, hydrogen));
                // need to keep focus same
                TetrahedralChirality e = new TetrahedralChirality(focus,
                                                                  (IAtom[]) tmp.getCarriers().toArray(new IAtom[4]),
                                                                  tmp.getConfig());
                e.setGroupInfo(se.getGroupInfo());
                ses.add(e);
              } else {
                ses.add(se);
              }
            }
            break;
            case IStereoElement.CisTrans: {
              IBond focus = (IBond) se.getFocus();
              IAtom beg = focus.getBegin();
              IAtom end = focus.getEnd();
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
                // need to keep focus same
                TetrahedralChirality e = new TetrahedralChirality(focus,
                                                                  getExplHCarriers(mol, se, focus),
                                                                  se.getConfig());
                e.setGroupInfo(se.getGroupInfo());
                ses.add(e);
              } else {
                ses.add(se);
              }
            }
            break;
            case IStereoElement.SquarePlanar:
            {
              IAtom focus = (IAtom) se.getFocus();
              if (focus.getImplicitHydrogenCount() > 0) {
                ses.add(new SquarePlanar(focus, getExplHCarriers(mol, se, focus), se.getConfig()));
              } else {
                ses.add(se);
              }
            }
            break;
            case IStereoElement.TrigonalBipyramidal:
            {
              IAtom focus = (IAtom) se.getFocus();
              if (focus.getImplicitHydrogenCount() > 0) {
                ses.add(new TrigonalBipyramidal(focus, getExplHCarriers(mol, se, focus), se.getConfig()));
              } else {
                ses.add(se);
              }
            }
            break;
            case IStereoElement.Octahedral:
            {
              IAtom focus = (IAtom) se.getFocus();
              if (focus.getImplicitHydrogenCount() > 0) {
                ses.add(new Octahedral(focus, getExplHCarriers(mol, se, focus), se.getConfig()));
              } else {
                ses.add(se);
              }
            }
            break;
            case IStereoElement.CisTrans: {
              IBond focus = (IBond) se.getFocus();
              IAtom begin = focus.getBegin();
              IAtom end = focus.getEnd();
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
                Map<IAtom, IAtom> map = new HashMap<>();
                map.put(begin, hydrogenBegin);
                map.put(end, hydrogenEnd);
                ses.add(se.map(map));
              } else {
                ses.add(se);
              }
            }
            break;
            case IStereoElement.Allenal: {
              IAtom focus = (IAtom) se.getFocus();
              IAtom[] terminals = ExtendedTetrahedral.findTerminalAtoms(mol, focus);
              IAtom hydrogen1 = null;
              IAtom hydrogen2 = null;
              if (terminals[0].getImplicitHydrogenCount() == 1) {
                terminals[0].setImplicitHydrogenCount(0);
                hydrogen1 = sproutHydrogen(mol, terminals[0]);
              }
              if (terminals[1].getImplicitHydrogenCount() == 1) {
                terminals[1].setImplicitHydrogenCount(0);
                hydrogen2 = sproutHydrogen(mol, terminals[1]);
              }
              if (hydrogen1 != null || hydrogen2 != null) {
                Map<IAtom, IAtom> map = new HashMap<>();
                if (hydrogen1 != null)
                  map.put(terminals[0], hydrogen1);
                if (hydrogen2 != null)
                  map.put(terminals[1], hydrogen2);
                // find as focus is not one of the terminals
                IStereoElement<IAtom, IAtom> tmp = se.map(map);
                ses.add(tmp);
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

  // utility to sprout multiple hydrogens for stereo centres
  private IAtom[] getExplHCarriers(IAtomContainer mol, IStereoElement se, IAtom focus) {
    Deque<IAtom> hydrogens = new ArrayDeque<>();
    for (int i = 0; i < focus.getImplicitHydrogenCount(); i++) {
      hydrogens.add(sproutHydrogen(mol, focus));
    }
    focus.setImplicitHydrogenCount(0);
    List<IAtom> carriers = new ArrayList<>();
    for (IAtom carrier : (List<IAtom>) se.getCarriers()) {
        carriers.add(carrier.equals(focus) && !hydrogens.isEmpty() ? hydrogens.poll() : carrier);
    }
    return carriers.toArray(new IAtom[0]);
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

  private IAtom sproutHydrogen(IAtomContainer mol, IAtom focus) {
    IAtom hydrogen = mol.getBuilder().newAtom();
    hydrogen.setAtomicNumber(1);
    hydrogen.setSymbol("H");
    hydrogen.setImplicitHydrogenCount(0);
    mol.addAtom(hydrogen);
    mol.addBond(mol.indexOf(focus), mol.getAtomCount() - 1, IBond.Order.SINGLE);
    return mol.getAtom(mol.getAtomCount() - 1);
  }

  private void contractHydrates(IAtomContainer mol) {
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

    if (sgroups.size() == 1 &&
            sgroups.get(0).getType() == SgroupType.CtabAbbreviation) {
      Sgroup sgrp = sgroups.get(0);

      boolean okay = true;
      Set<IAtom> atoms = sgrp.getAtoms();
      for (IAtom a : hydrate) {
        if (atoms.contains(a)) {
          okay = false;
          break;
        }
      }

      if (okay && sgrp.getAtoms().size() + hydrate.size() == mol.getAtomCount()) {
        for (IAtom a : hydrate)
          sgrp.addAtom(a);
        sgrp.setSubscript(sgrp.getSubscript() + '·' + hydrate.size() + "H2O");
      }
    } else {
      Sgroup sgrp = new Sgroup();
      for (IAtom atom : hydrate)
        sgrp.addAtom(atom);
      sgrp.putValue(SgroupKey.CtabParentAtomList,
                    Collections.singleton(hydrate.iterator().next()));
      sgrp.setType(SgroupType.CtabMultipleGroup);
      sgrp.setSubscript(Integer.toString(hydrate.size()));
      sgroups.add(sgrp);
    }
  }

  private void abbreviate(IReaction rxn,
                          String mode,
                          Set<IChemObject> highlight) {

    Map<IAtom, Integer> atomSet = new HashMap<>();
    for (IChemObject obj : highlight) {
      if (obj instanceof IAtom)
        atomSet.put((IAtom) obj, 1);
    }

    Multimap<IAtomContainer, Sgroup> sgroupmap = ArrayListMultimap.create();
    switch (mode.toLowerCase()) {
      case "true":
      case "on":
      case "yes":
      case "groups+agents":
        for (IAtomContainer mol : rxn.getReactants().atomContainers()) {
          groupAbbr.apply(mol, atomSet);
          contractHydrates(mol);
        }
        for (IAtomContainer mol : rxn.getProducts().atomContainers()) {
          Set<IAtom> atoms = new HashSet<>();
          List<Sgroup> newSgroups = new ArrayList<>();
          groupAbbr.apply(mol, atomSet);
          contractHydrates(mol);
        }
        for (IAtomContainer mol : rxn.getAgents().atomContainers()) {
          agentAbbr.apply(mol, atomSet);
          contractHydrates(mol);
        }
        break;
      case "groups":
        for (IAtomContainer mol : rxn.getAgents().atomContainers()) {
          groupAbbr.apply(mol, atomSet);
          contractHydrates(mol);
        }
        break;
      case "reagents":
      case "agents":
        for (IAtomContainer mol : rxn.getAgents().atomContainers()) {
          agentAbbr.apply(mol, atomSet);
          contractHydrates(mol);
        }
        break;
    }

    Set<String> include = new HashSet<>();
    for (Map.Entry<IAtomContainer, Sgroup> e : sgroupmap.entries()) {
      final IAtomContainer mol = e.getKey();
      final Sgroup abbrv = e.getValue();
      int numAtoms = mol.getAtomCount();
      if (abbrv.getBonds().isEmpty()) {
        include.add(abbrv.getSubscript());
      } else {
        int numAbbr = abbrv.getAtoms().size();
        double f = numAbbr / (double) numAtoms;
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

  private void abbreviate(IAtomContainer mol,
                          String mode,
                          String annotate,
                          Set<IChemObject> highlight) {

    Map<IAtom, Integer> atomSet = new HashMap<>();
    for (IChemObject obj : highlight) {
      if (obj instanceof IAtom)
        atomSet.put((IAtom) obj, 1);
    }

    // block abbreviations of mapped atoms which will be coloured if this option is set
    if ("mapidx".equals(annotate)) {
      for (IAtom atom : mol.atoms()) {
        if (atom.getMapIdx() != 0)
          atomSet.put(atom, 2);
      }
    }

    switch (mode.toLowerCase()) {
      case "true":
      case "on":
      case "yes":
      case "groups":
        contractHydrates(mol);
        groupAbbr.apply(mol, atomSet);
        break;
    }
  }

  private boolean isRxnSmi(String smi) {
    return smi.split(" ")[0].contains(">");
  }

  private IAtomContainer loadMol(String str) throws CDKException {
    if (str.contains("V2000")) {
      try (MDLV2000Reader mdlr = new MDLV2000Reader(new StringReader(str))) {
        return mdlr.read(SilentChemObjectBuilder.getInstance().newAtomContainer());
      } catch (CDKException | IOException e3) {
        throw new CDKException("Could not parse input");
      }
    } else if (str.contains("V3000")) {
      try (MDLV3000Reader mdlr = new MDLV3000Reader(new StringReader(str))) {
        return mdlr.read(SilentChemObjectBuilder.getInstance().newAtomContainer());
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

  private HttpEntity<byte[]> makeResponse(byte[] bytes, String contentType) {
    HttpHeaders header = new HttpHeaders();
    String type = contentType.substring(0, contentType.indexOf('/'));
    String subtype = contentType.substring(contentType.indexOf('/') + 1, contentType.length());
    header.setContentType(new MediaType(type, subtype));
    header.add("Access-Control-Allow-Origin", "*");
    // header.set(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000");
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
                                              String style) {
    switch (style) {
      case "cow":
        generator = generator.withAtomColors(new CDK2DAtomColors())
                             .withBackgroundColor(Color.WHITE)
                             .withOuterGlowHighlight();
        break;
      case "cot":
        generator = generator.withAtomColors(new CDK2DAtomColors())
                             .withBackgroundColor(new Color(0, 0, 0, 0))
                             .withOuterGlowHighlight();
        break;
      case "bow":
        generator = generator.withAtomColors(new UniColor(Color.BLACK))
                             .withBackgroundColor(Color.WHITE);
        break;
      case "bot":
        generator = generator.withAtomColors(new UniColor(Color.BLACK))
                             .withBackgroundColor(new Color(0, 0, 0, 0));
        break;
      case "wob":
        generator = generator.withAtomColors(new UniColor(Color.WHITE))
                             .withBackgroundColor(Color.BLACK);
        break;
      case "wot":
        generator = generator.withAtomColors(new UniColor(Color.WHITE))
                             .withBackgroundColor(new Color(0, 0, 0, 0));
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
    public Color getAtomColor(IAtom atom) {
      Color res = colors.getAtomColor(atom);
      if (res.equals(Color.BLACK))
        return Color.WHITE;
      else
        return res;
    }

    @Override
    public Color getAtomColor(IAtom atom, Color color) {
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
    private final Color NEON = new Color(0x00FF0E);

    @Override
    public Color getAtomColor(IAtom atom) {
      Color res = colors.getAtomColor(atom);
      if (res.equals(Color.BLACK))
        return NEON;
      else
        return res;
    }

    @Override
    public Color getAtomColor(IAtom atom, Color color) {
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
  private Set<IChemObject> findHits(final String sma,
                                    final IReaction rxn,
                                    final IAtomContainer mol,
                                    final int limit) {

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
                                                            .exclusiveAtoms()
                                                            .toAtomBondMap()) {
          for (Map.Entry<IChemObject, IChemObject> e : m.entrySet()) {
            highlight.add(e.getValue());
          }
        }
      } else if (rxn != null) {
        for (Map<IChemObject, IChemObject> m : smartsPattern.matchAll(rxn)
                                                            .limit(limit)
                                                            .exclusiveAtoms()
                                                            .toAtomBondMap()) {
          for (Map.Entry<IChemObject, IChemObject> e : m.entrySet()) {
            highlight.add(e.getValue());
          }
        }
      }
    }
    return highlight;
  }

  @ExceptionHandler({Exception.class, InvalidSmilesException.class})
  public static ResponseEntity<Object> handleException(Exception ex, WebRequest request) {
    if (ex instanceof InvalidSmilesException) {
      InvalidSmilesException ise = (InvalidSmilesException) ex;
      String mesg = ise.getMessage();
      String disp = "";
      if (mesg.endsWith("^")) {
        int i = mesg.indexOf(":\n");
        if (i >= 0) {
          disp = mesg.substring(i + 2);
          mesg = mesg.substring(0, i);
        }
      }
      return new ResponseEntity<>("<!DOCTYPE html><html>" +
                                          "<title>400 - Invalid SMILES</title>" +
                                          "<body><div>" +
                                          "<h1>Invalid SMILES</h1>" +
                                          mesg +
                                          "<pre>" + disp + "</pre>" +
                                          "</div></body>" +
                                          "</html>",
                                  new HttpHeaders(),
                                  HttpStatus.BAD_REQUEST);
    } else {
      LoggerFactory.getLogger(DepictController.class).error("Unexpected Error: ", ex);
      return new ResponseEntity<>("<!DOCTYPE html><html><title>500 - Internal Server Error</title><body><div>" +
                                          "<h1>" + ex.getClass().getSimpleName() + "</h1>" +
                                          ex.getMessage() +
                                          "</div></body></html>",
                                  new HttpHeaders(),
                                  HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  public static void main(String[] args) throws IOException, CDKException {
    new DepictController().depict("ClC1=NC=2N(C(=C1)N(CC3=CC=CC=C3)CC4=CC=CC=C4)N=CC2C(OCC)=O>C1(=CC(=CC(=N1)C)N)N2C[C@H](CCC2)O.O1CCOCC1.CC1(C2=C(C(=CC=C2)P(C3=CC=CC=C3)C4=CC=CC=C4)OC5=C(C=CC=C15)P(C6=CC=CC=C6)C7=CC=CC=C7)C.C=1C=CC(=CC1)\\C=C\\C(=O)\\C=C\\C2=CC=CC=C2.C=1C=CC(=CC1)\\C=C\\C(=O)\\C=C\\C2=CC=CC=C2.C=1C=CC(=CC1)\\C=C\\C(=O)\\C=C\\C2=CC=CC=C2.[Pd].[Pd].[Cs]OC(=O)O[Cs]>C1(=CC(=CC(=N1)C)NC2=NC=3N(C(=C2)N(CC4=CC=CC=C4)CC5=CC=CC=C5)N=CC3C(OCC)=O)N6C[C@H](CCC6)O>CO.C1CCOC1.O.O[Li]>C1(=CC(=CC(=N1)C)NC2=NC=3N(C(=C2)N(CC4=CC=CC=C4)CC5=CC=CC=C5)N=CC3C(O)=O)N6C[C@H](CCC6)O>CN(C)C(=[N+](C)C)ON1C2=C(C=CC=N2)N=N1.F[P-](F)(F)(F)(F)F.[NH4+].[Cl-].CN(C)C=O.CCN(C(C)C)C(C)C>C1(=CC(=CC(=N1)C)NC2=NC=3N(C(=C2)N(CC4=CC=CC=C4)CC5=CC=CC=C5)N=CC3C(N)=O)N6C[C@H](CCC6)O>>C1(=CC(=CC(=N1)C)NC2=NC=3N(C(=C2)N)N=CC3C(N)=O)N4C[C@H](CCC4)O |f:4.5.6.7.8,16.17,18.19|  US20190241576A1",
                                  "svg",
                                  "bot",
                                  new HashMap<>());
  }
}
