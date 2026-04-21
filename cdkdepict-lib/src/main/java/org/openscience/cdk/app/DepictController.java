/*
 * Copyright (c) 2018. NextMove Software Ltd.
 */

package org.openscience.cdk.app;

import com.bioinceptionlabs.reactionblast.api.RDT;
import com.bioinceptionlabs.reactionblast.api.ReactionResult;
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
import org.openscience.cdk.smirks.Smirks;
import org.openscience.cdk.smirks.SmirksTransform;
import org.openscience.cdk.smarts.SmartsPattern;
import org.openscience.cdk.smiles.SmilesGenerator;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.stereo.ExtendedTetrahedral;
import org.openscience.cdk.stereo.Octahedral;
import org.openscience.cdk.stereo.SquarePlanar;
import org.openscience.cdk.stereo.Stereocenters;
import org.openscience.cdk.stereo.TetrahedralChirality;
import org.openscience.cdk.stereo.TrigonalBipyramidal;
import org.openscience.cdk.isomorphism.Transform;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
  static final int WL_ROUNDS = 2;
  static final int MAX_REACTION_PRODUCTS = 50;
  static final Color REACTION_CHANGE_COLOR       = new Color(179, 204, 255);
  static final Color REACTION_CHANGE_COLOR_NIGHT = new Color(120, 160, 220);
  static final Color REACTION_CHANGE_COLOR_NEON  = new Color(0, 160, 200);

  static class InvalidSmirksException extends IllegalArgumentException {
    InvalidSmirksException(String message) {
      super(message);
    }

    InvalidSmirksException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  static class InvalidReactionMappingException extends IllegalArgumentException {
    InvalidReactionMappingException(String message) {
      super(message);
    }

    InvalidReactionMappingException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private final Color[] COLORS = new Color[]{
          new Color(179, 204, 255),
          new Color(194, 255, 190),
          new Color(255, 166, 166),
          new Color(249, 163, 251),
          new Color(255, 225, 154),
          new Color(227, 227, 227)
  };
  private static final Color[] COLORS_NIGHT = new Color[]{
          new Color(120, 160, 220),
          new Color(110, 190, 130),
          new Color(210, 120, 120),
          new Color(200, 120, 200),
          new Color(210, 190, 120),
          new Color(170, 170, 170)
  };
  private static final Color[] COLORS_NEON = new Color[]{
          new Color(0, 160, 200),
          new Color(255, 50, 120),
          new Color(255, 160, 0),
          new Color(180, 60, 255),
          new Color(255, 240, 50),
          new Color(255, 100, 220)
  };

  private enum ThemeGroup { DAY, NIGHT, NEON }

  private static ThemeGroup themeGroupOf(String style) {
    switch (style == null ? "" : style) {
      case "nob":
      case "not":
        return ThemeGroup.NEON;
      case "wot":
      case "wcot":
      case "cob":
      case "wob":
        return ThemeGroup.NIGHT;
      default:
        return ThemeGroup.DAY;
    }
  }

  private Color[] colmapColorsForStyle(String style) {
    switch (themeGroupOf(style)) {
      case NEON:  return COLORS_NEON;
      case NIGHT: return COLORS_NIGHT;
      default:    return COLORS;
    }
  }

  private static Color reactionChangeColorForStyle(String style) {
    switch (themeGroupOf(style)) {
      case NEON:  return REACTION_CHANGE_COLOR_NEON;
      case NIGHT: return REACTION_CHANGE_COLOR_NIGHT;
      default:    return REACTION_CHANGE_COLOR;
    }
  }

  private Color[] highlightColorsForStyle(String style) {
    switch (themeGroupOf(style)) {
      case NEON:  return COLORS_NEON;
      case NIGHT: return COLORS_NIGHT;
      default:    return COLORS;
    }
  }

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
    ATOMSUBLISTS("atomlists", ""),
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
    REVERSE("reverse", false),
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
    if (color == null)
      throw new IllegalArgumentException("Color string must not be null");

    String trimmed = color.trim();
    if (trimmed.isEmpty())
      throw new IllegalArgumentException("Color string must not be empty");

    if (trimmed.indexOf(',') >= 0) {
      String[] parts = trimmed.split(",");
      if (parts.length < 3 || parts.length > 4)
        throw new IllegalArgumentException("Expected 3 or 4 comma-separated components");
      int r = parseColorComponent(parts[0].trim());
      int g = parseColorComponent(parts[1].trim());
      int b = parseColorComponent(parts[2].trim());
      int a = parts.length == 4 ? parseColorComponent(parts[3].trim()) : 255;
      return new Color(r, g, b, a);
    }

    int vals[] = new int[]{0, 0, 0, 255}; // r,g,b,a
    int pos = 0;
    int beg = 0;
    if (trimmed.startsWith("0x"))
      beg = 2;
    else if (trimmed.startsWith("#"))
      beg = 1;
    for (; pos < 4 && beg + 1 < trimmed.length(); beg += 2) {
      vals[pos++] = Integer.parseInt(trimmed.substring(beg, beg + 2), 16);
    }
    return new Color(vals[0], vals[1], vals[2], vals[3]);
  }

  private static int parseColorComponent(String component) {
    int value = Integer.parseInt(component);
    if (value < 0 || value > 255)
      throw new IllegalArgumentException("Colour components must be between 0 and 255");
    return value;
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

    Map<IReaction, Set<IAtom>> rxnChangeCenters = new HashMap<>();
    Set<IChemObject> highlight;
    Set<IChemObject> smartsHighlight;
    List<HighlightGroup> atomHighlightGroups = Collections.emptyList();
    boolean hasAtomHighlightGroups = false;

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
        Set<IChemObject> abbrProtect = new HashSet<>();
        Set<IChemObject> hits = findHits(getString(Param.SMARTSQUERY, extra),
                                         rxn,
                                         null,
                                         getInt(Param.SMARTSHITLIM, extra));
        highlight.addAll(hits);
        abbrProtect.addAll(hits);
        if ("rxnchg".equals(annotate)) {
          Set<IAtom> centers = findReactionCenterAtoms(rxn);
          rxnChangeCenters.put(rxn, centers);
          abbrProtect.addAll(centers);
        }
        abbreviate(rxn, abbr, abbrProtect);
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
      smartsHighlight = new HashSet<>(highlight);
    } else {
      mol = loadMol(smi);
      setHydrogenDisplay(mol, hDisplayType);
      highlight = findHits(getString(Param.SMARTSQUERY, extra),
                           null,
                           mol,
                           getInt(Param.SMARTSHITLIM, extra));
      smartsHighlight = new HashSet<>(highlight);
      String atomLists = getString(Param.ATOMSUBLISTS, extra);
      if (!atomLists.isEmpty()) {
        atomHighlightGroups = buildAtomHighlightGroups(mol, atomLists);
        if (!atomHighlightGroups.isEmpty()) {
          hasAtomHighlightGroups = true;
          for (HighlightGroup group : atomHighlightGroups)
            highlight.addAll(group.members);
        }
      }
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
          Color[] colmapColors = colmapColorsForStyle(style);
          myGenerator = myGenerator.withAtomMapHighlight(colmapColors)
                                   .withOuterGlowHighlight(6d)
                                   .withParam(StandardGenerator.Highlighting.class,
                                              StandardGenerator.HighlightStyle.OuterGlowFillRings);
        } else {
          Color[] colmapColors = colmapColorsForStyle(style);
          myGenerator = myGenerator.withOuterGlowHighlight();
          myGenerator = myGenerator.withParam(StandardGenerator.Visibility.class,
                                              SymbolVisibility.iupacRecommendationsWithoutTerminalCarbon());
          for (IAtom atom : mol.atoms()) {
            Integer mapidx = atom.getProperty(CDKConstants.ATOM_ATOM_MAPPING);
            if (mapidx != null && mapidx >= 0)
              atom.setProperty(StandardGenerator.HIGHLIGHT_COLOR,
                               colmapColors[mapidx % colmapColors.length]);
          }
        }
        break;
      case "rxnchg":
        if (isRxn) {
          Color rxnChgColor = reactionChangeColorForStyle(style);
          myGenerator = myGenerator.withOuterGlowHighlight(6d)
                                   .withParam(StandardGenerator.Highlighting.class,
                                              StandardGenerator.HighlightStyle.OuterGlow);
          for (IReaction rxn : rxns.reactions()) {
            Set<IAtom> centers = rxnChangeCenters.get(rxn);
            if (centers == null)
              centers = findReactionCenterAtoms(rxn);
            for (IAtom atom : centers) {
              atom.setProperty(StandardGenerator.HIGHLIGHT_COLOR, rxnChgColor);
            }
            for (IAtomContainer part : ReactionManipulator.getAllAtomContainers(rxn)) {
              for (IBond bond : part.bonds()) {
                if (centers.contains(bond.getBegin()) && centers.contains(bond.getEnd())) {
                  bond.setProperty(StandardGenerator.HIGHLIGHT_COLOR, rxnChgColor);
                }
              }
            }
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
    if (!smartsHighlight.isEmpty()) {
      switch (style) {
        case "nob":
          myGenerator = myGenerator.withHighlight(smartsHighlight,
                                                  new Color(0xffaaaa));
          break;
        case "bow":
        case "wob":
        case "bot":
          myGenerator = myGenerator.withHighlight(smartsHighlight,
                                                  new Color(0xff0000));
          break;
        default:
          myGenerator = myGenerator.withHighlight(smartsHighlight,
                                                  new Color(0xaaffaa));
          break;
      }
    }

    if (hasAtomHighlightGroups && !"colmap".equals(annotate)) {
      myGenerator = myGenerator.withParam(StandardGenerator.Highlighting.class,
                                          StandardGenerator.HighlightStyle.OuterGlow);
    }

    Color[] highlightPalette = highlightColorsForStyle(style);
    for (int i = 0; i < atomHighlightGroups.size(); i++) {
      HighlightGroup group = atomHighlightGroups.get(i);
      if (!group.members.isEmpty()) {
        Color color = group.color != null ? group.color : highlightPalette[i % highlightPalette.length];
        myGenerator = myGenerator.withHighlight(group.members, color);
      }
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

  /**
   * Restful reaction transform endpoint.
   *
   * @param smi    input molecule SMILES
   * @param smirks reaction transform (SMIRKS)
   * @param fmt    output format
   * @param style  preset style COW (Color-on-white), COB, BOW, COW
   * @return the depicted transformed reactions
   * @throws CDKException something not okay with input
   * @throws IOException  problem reading/writing request
   */
  @RequestMapping("react/{style}/{fmt}")
  public HttpEntity<?> react(@RequestParam("smi") String smi,
                             @RequestParam("smirks") String smirks,
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

    IAtomContainer template = loadMol(smi);
    List<String> productSmiles = transformProductSmiles(template,
                                                        smirks,
                                                        getBoolean(Param.REVERSE, extra));
    if (productSmiles.isEmpty()) {
      return makeResponse("No products generated".getBytes(StandardCharsets.UTF_8), "text/plain");
    }

    IReactionSet rxns = builder.newInstance(IReactionSet.class);
    for (String productSmi : productSmiles) {
      IReaction rxn = builder.newReaction();
      rxn.addReactant(cloneMol(template));
      rxn.addProduct(loadMol(productSmi));
      rxn.setDirection(getParam(Param.ARROW, extra, this::parseArrowParam));
      rxn.setProperty(CDKConstants.TITLE, productSmi);
      rxns.addReaction(rxn);
    }

    Set<IChemObject> highlight = new HashSet<>();
    Map<IReaction, Set<IAtom>> rxnChangeCenters = new HashMap<>();

    StructureDiagramGenerator sdg = new StructureDiagramGenerator();
    sdg.setAlignMappedReaction(getBoolean(Param.ALIGNRXNMAP, extra));
    MolOp.DativeBond doDative = getParam(Param.DATIVE, extra, this::parseDativeParam);

    for (IReaction rxn : rxns.reactions()) {
      Set<IChemObject> abbrProtect = new HashSet<>();
      Set<IChemObject> hits = findHits(getString(Param.SMARTSQUERY, extra),
                                       rxn,
                                       null,
                                       getInt(Param.SMARTSHITLIM, extra));
      highlight.addAll(hits);
      abbrProtect.addAll(hits);
      if ("rxnchg".equals(annotate)) {
        Set<IAtom> centers = findReactionCenterAtoms(rxn);
        rxnChangeCenters.put(rxn, centers);
        abbrProtect.addAll(centers);
      }
      abbreviate(rxn, abbr, abbrProtect);
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
        myGenerator = myGenerator.withAtomMapHighlight(colmapColorsForStyle(style))
                                 .withOuterGlowHighlight(6d)
                                 .withParam(StandardGenerator.Highlighting.class,
                                            StandardGenerator.HighlightStyle.OuterGlowFillRings);
        break;
      case "rxnchg":
        Color rxnChgColor = reactionChangeColorForStyle(style);
        myGenerator = myGenerator.withOuterGlowHighlight(6d)
                                 .withParam(StandardGenerator.Highlighting.class,
                                            StandardGenerator.HighlightStyle.OuterGlow);
        for (IReaction rxn : rxns.reactions()) {
          Set<IAtom> centers = rxnChangeCenters.get(rxn);
          if (centers == null)
            centers = findReactionCenterAtoms(rxn);
          for (IAtom atom : centers) {
            atom.setProperty(StandardGenerator.HIGHLIGHT_COLOR, rxnChgColor);
          }
          for (IAtomContainer part : ReactionManipulator.getAllAtomContainers(rxn)) {
            for (IBond bond : part.bonds()) {
              if (centers.contains(bond.getBegin()) && centers.contains(bond.getEnd())) {
                bond.setProperty(StandardGenerator.HIGHLIGHT_COLOR, rxnChgColor);
              }
            }
          }
        }
        break;
      case "cip":
        for (IReaction rxn : rxns.reactions()) {
          for (IAtomContainer part : ReactionManipulator.getAllAtomContainers(rxn)) {
            annotateCip(part);
          }
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
      myGenerator = myGenerator.withRxnTitle();
    }

    // reactions are laid out in the main depiction gen
    if (getBoolean(Param.FLIP, extra)) {
      for (IAtomContainer part : ReactionSetManipulator.getAllAtomContainers(rxns))
        flip(part);
    }
    int rotate = getInt(Param.ROTATE, extra);
    if (rotate != 0) {
      for (IAtomContainer part : ReactionSetManipulator.getAllAtomContainers(rxns))
        rotate(part, rotate);
    }

    final String fmtlc = fmt.toLowerCase(Locale.ROOT);
    final Depiction depiction = myGenerator.depict(rxns);

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

  @RequestMapping("map/{style}/{fmt}")
  public HttpEntity<?> map(@RequestParam("smi") String smi,
                           @PathVariable("fmt") String fmt,
                           @PathVariable("style") String style,
                           @RequestParam Map<String, String> extra) throws
          CDKException,
          IOException {
    return depict(mapReactionSmiles(smi), fmt, style, extra);
  }

  @RequestMapping("map/smi")
  public HttpEntity<?> mapSmiles(@RequestParam("smi") String smi) {
    return makeResponse(mapReactionSmiles(smi).getBytes(StandardCharsets.UTF_8), "text/plain");
  }

  String mapReactionSmiles(String smi) {
    String input = normalizeReactionInput(smi);
    if (hasReactionAgents(input))
      return mapReactionSmilesWithAgents(input);

    return mapReactionSmilesDirect(input);
  }

  private String mapReactionSmilesDirect(String smi) {
    ReactionResult result;
    try {
      result = RDT.map(smi, true, true);
    } catch (RuntimeException ex) {
      String message = ex.getMessage();
      if (message == null || message.isEmpty())
        message = "Could not map reaction SMILES";
      throw new InvalidReactionMappingException(message, ex);
    }

    if (result == null || !result.isMapped() || result.getMappedSmiles() == null || result.getMappedSmiles().isEmpty())
      throw new InvalidReactionMappingException("Could not map reaction SMILES");

    return result.getMappedSmiles();
  }

  private String mapReactionSmilesWithAgents(String smi) {
    String[] parts = smi.split(">", -1);
    if (parts.length != 3)
      throw new InvalidReactionMappingException("Expected reaction SMILES in 'reactants>agents>products' format, e.g. 'CCO>>CC=O' or 'CCO>O>CC=O'");

    String mapped = mapReactionSmilesDirect(parts[0] + ">>" + parts[2]);
    String[] mappedParts = mapped.split(">>", -1);
    if (mappedParts.length != 2)
      throw new InvalidReactionMappingException("Could not map reaction SMILES");

    return mappedParts[0] + ">" + parts[1] + ">" + mappedParts[1];
  }

  private String normalizeReactionInput(String smi) {
    if (smi == null)
      throw new InvalidReactionMappingException("Reaction SMILES must not be null");

    String trimmed = smi.trim();
    if (trimmed.isEmpty())
      throw new InvalidReactionMappingException("Reaction SMILES must not be empty");

    int lastPipe = trimmed.lastIndexOf('|');
    if (lastPipe >= 0) {
      int firstPipe = trimmed.indexOf('|');
      if (firstPipe >= 0 && firstPipe < lastPipe)
        return trimmed.substring(0, lastPipe + 1).trim();
    }

    int space = trimmed.indexOf(' ');
    int tab = trimmed.indexOf('\t');
    int split = -1;
    if (space >= 0 && tab >= 0)
      split = Math.min(space, tab);
    else if (space >= 0)
      split = space;
    else if (tab >= 0)
      split = tab;

    String input = split > 0 ? trimmed.substring(0, split) : trimmed;
    if (!isReactionSmiles(input)) {
      throw new InvalidReactionMappingException("Expected reaction SMILES in 'reactants>agents>products' format, e.g. 'CCO>>CC=O' or 'CCO>O>CC=O'");
    }
    return input;
  }

  private boolean isReactionSmiles(String smi) {
    int first = smi.indexOf('>');
    int last = smi.lastIndexOf('>');
    if (first <= 0 || last <= first || last >= smi.length() - 1)
      return false;
    if (smi.indexOf('>', first + 1) != last)
      return false;
    return true;
  }

  private boolean hasReactionAgents(String smi) {
    int first = smi.indexOf('>');
    int last = smi.lastIndexOf('>');
    return first >= 0 && last > first && !smi.substring(first + 1, last).isEmpty();
  }

  List<String> transformProductSmiles(String smi, String smirks, boolean reverse) throws CDKException {
    return transformProductSmiles(loadMol(smi), smirks, reverse);
  }

  private List<String> transformProductSmiles(IAtomContainer input, String smirks, boolean reverse) throws CDKException {
    String smirksToApply = maybeReverseSmirks(smirks, reverse);
    SmirksTransform transform = compileSmirks(smirksToApply);
    SmilesGenerator smigen = SmilesGenerator.unique();
    Set<String> products = new LinkedHashSet<>();
    int k = 1;
    for (IAtom atom : input.atoms()) {
      atom.setProperty(CDKConstants.ATOM_ATOM_MAPPING, k++);
    }
    for (IAtomContainer product : transform.apply(input, Transform.Mode.Unique, MAX_REACTION_PRODUCTS)) {
      if (products.size() >= MAX_REACTION_PRODUCTS)
        break;
      products.add(smigen.create(product));
    }
    return new ArrayList<>(products);
  }

  private SmirksTransform compileSmirks(String smirks) {
    try {
      return Smirks.compile(smirks);
    } catch (RuntimeException ex) {
      String message = ex.getMessage();
      if (message == null || message.isEmpty())
        message = "Could not parse SMIRKS";
      throw new InvalidSmirksException(message, ex);
    }
  }

  private String maybeReverseSmirks(String smirks, boolean reverse) {
    if (!reverse)
      return smirks;
    String[] parts = smirks.split(">", -1);
    if (parts.length != 3) {
      throw new InvalidSmirksException("SMIRKS must be in 'reactants>agents>products' format");
    }
    return parts[2] + ">" + parts[1] + ">" + parts[0];
  }

  private IAtomContainer cloneMol(IAtomContainer mol) {
    try {
      return (IAtomContainer) mol.clone();
    } catch (CloneNotSupportedException e) {
      throw new IllegalStateException("Could not clone input molecule", e);
    }
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
      case "not":
        generator = generator.withAtomColors(new NobColorer())
                .withBackgroundColor(new Color(0, 0, 0, 0))
                .withOuterGlowHighlight();
        break;
      case "wcot":
        generator = generator.withAtomColors(new CobColorer())
                .withBackgroundColor(new Color(0, 0, 0, 0))
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

  private List<HighlightGroup> buildAtomHighlightGroups(IAtomContainer mol, String specification) {
    if (specification == null || specification.trim().isEmpty())
      return Collections.emptyList();

    List<HighlightGroup> groups = new ArrayList<>();
    String[] sublists = specification.split("[;|]");
    for (String sublist : sublists) {
      String trimmed = sublist.trim();
      if (trimmed.isEmpty())
        continue;

      Color override = null;
      String indices = trimmed;
      int colonIdx = trimmed.indexOf(':');
      if (colonIdx > 0) {
        String colorSpec = trimmed.substring(0, colonIdx).trim();
        if (!colorSpec.isEmpty()) {
          try {
            override = getColor(colorSpec);
          } catch (IllegalArgumentException ex) {
            override = null;
          }
        }
        indices = trimmed.substring(colonIdx + 1).trim();
      }

      if (indices.isEmpty())
        continue;

      Set<IChemObject> members = new LinkedHashSet<>();
      List<IAtom> atoms = new ArrayList<>();
      for (String token : indices.split(",")) {
        String t = token.trim();
        if (t.isEmpty())
          continue;
        try {
          int idx = Integer.parseInt(t);
          if (idx < 0 || idx >= mol.getAtomCount())
            continue;
          IAtom atom = mol.getAtom(idx);
          if (members.add(atom)) {
            for (IAtom other : atoms) {
              IBond bond = mol.getBond(atom, other);
              if (bond != null)
                members.add(bond);
            }
            atoms.add(atom);
          }
        } catch (NumberFormatException ex) {
          // skip invalid index entries
        }
      }

      if (!members.isEmpty())
        groups.add(new HighlightGroup(members, override));
    }

    return groups;
  }

  private static final class HighlightGroup {
    final Set<IChemObject> members;
    final Color color;

    HighlightGroup(Set<IChemObject> members, Color color) {
      this.members = members;
      this.color = color;
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

  static Set<IAtom> findReactionCenterAtoms(IReaction rxn) {
    Set<IAtom> centers = new LinkedHashSet<>();

    List<IAtomContainer> reactants = new ArrayList<>();
    List<IAtomContainer> products = new ArrayList<>();

    Map<IAtom, IAtomContainer> reactantOwner = new HashMap<>();
    Map<IAtom, IAtomContainer> productOwner = new HashMap<>();

    Map<Integer, List<IAtom>> reactantMapped = new LinkedHashMap<>();
    Map<Integer, List<IAtom>> productMapped = new LinkedHashMap<>();

    Set<IAtom> reactantUnmapped = new LinkedHashSet<>();
    Set<IAtom> productUnmapped = new LinkedHashSet<>();

    for (IAtomContainer reactant : rxn.getReactants().atomContainers()) {
      reactants.add(reactant);
      indexReactionAtoms(reactant, reactantOwner, reactantMapped, reactantUnmapped);
    }
    for (IAtomContainer product : rxn.getProducts().atomContainers()) {
      products.add(product);
      indexReactionAtoms(product, productOwner, productMapped, productUnmapped);
    }

    Set<Integer> allMapIdx = new LinkedHashSet<>();
    allMapIdx.addAll(reactantMapped.keySet());
    allMapIdx.addAll(productMapped.keySet());
    for (Integer mapIdx : allMapIdx) {
      List<IAtom> left = reactantMapped.get(mapIdx);
      List<IAtom> right = productMapped.get(mapIdx);
      if (left == null || right == null || left.size() != 1 || right.size() != 1) {
        if (left != null)
          centers.addAll(left);
        if (right != null)
          centers.addAll(right);
        continue;
      }

      IAtom leftAtom = left.get(0);
      IAtom rightAtom = right.get(0);
      String leftSig = mappedAtomSignature(leftAtom, reactantOwner.get(leftAtom));
      String rightSig = mappedAtomSignature(rightAtom, productOwner.get(rightAtom));
      if (!leftSig.equals(rightSig)) {
        centers.add(leftAtom);
        centers.add(rightAtom);
      }
    }

    if (!reactantUnmapped.isEmpty() || !productUnmapped.isEmpty()) {
      Map<IAtomContainer, Set<IAtom>> reactantByContainer = partitionByContainer(reactantUnmapped,
                                                                                  reactantOwner);
      Map<IAtomContainer, Set<IAtom>> productByContainer = partitionByContainer(productUnmapped,
                                                                                 productOwner);

      Map<IAtomContainer, Map<IAtom, String>> reactantHashesByContainer = new LinkedHashMap<>();
      Map<IAtomContainer, Map<IAtom, String>> productHashesByContainer = new LinkedHashMap<>();
      Map<String, Deque<IAtomContainer>> reactantComponentKey = new LinkedHashMap<>();
      Map<String, Deque<IAtomContainer>> productComponentKey = new LinkedHashMap<>();

      for (Map.Entry<IAtomContainer, Set<IAtom>> e : reactantByContainer.entrySet()) {
        Map<IAtom, String> hashes = wlHashesForAtoms(Collections.singleton(e.getKey()),
                                                     e.getValue(),
                                                     WL_ROUNDS);
        reactantHashesByContainer.put(e.getKey(), hashes);
        addContainerByKey(reactantComponentKey, hashMultisetKey(e.getValue(), hashes), e.getKey());
      }
      for (Map.Entry<IAtomContainer, Set<IAtom>> e : productByContainer.entrySet()) {
        Map<IAtom, String> hashes = wlHashesForAtoms(Collections.singleton(e.getKey()),
                                                     e.getValue(),
                                                     WL_ROUNDS);
        productHashesByContainer.put(e.getKey(), hashes);
        addContainerByKey(productComponentKey, hashMultisetKey(e.getValue(), hashes), e.getKey());
      }

      Set<IAtom> reactantResidual = new LinkedHashSet<>();
      Set<IAtom> productResidual = new LinkedHashSet<>();
      Set<String> allComponentKeys = new LinkedHashSet<>();
      allComponentKeys.addAll(reactantComponentKey.keySet());
      allComponentKeys.addAll(productComponentKey.keySet());
      for (String key : allComponentKeys) {
        Deque<IAtomContainer> left = reactantComponentKey.get(key);
        Deque<IAtomContainer> right = productComponentKey.get(key);
        while (left != null && right != null && !left.isEmpty() && !right.isEmpty()) {
          IAtomContainer leftContainer = left.removeFirst();
          IAtomContainer rightContainer = right.removeFirst();
          markUnmatchedByHash(reactantByContainer.get(leftContainer),
                              reactantHashesByContainer.get(leftContainer),
                              productByContainer.get(rightContainer),
                              productHashesByContainer.get(rightContainer),
                              centers);
        }
        if (left != null) {
          while (!left.isEmpty()) {
            reactantResidual.addAll(reactantByContainer.get(left.removeFirst()));
          }
        }
        if (right != null) {
          while (!right.isEmpty()) {
            productResidual.addAll(productByContainer.get(right.removeFirst()));
          }
        }
      }

      if (!reactantResidual.isEmpty() || !productResidual.isEmpty()) {
        Map<IAtom, String> reactantResidualHashes = wlHashesForAtoms(reactants,
                                                                     reactantResidual,
                                                                     WL_ROUNDS);
        Map<IAtom, String> productResidualHashes = wlHashesForAtoms(products,
                                                                    productResidual,
                                                                    WL_ROUNDS);
        markUnmatchedByHash(reactantResidual,
                            reactantResidualHashes,
                            productResidual,
                            productResidualHashes,
                            centers);
      }
    }

    return centers;
  }

  static Map<IAtom, String> wlHashesForAtoms(Collection<IAtomContainer> containers,
                                             Set<IAtom> atoms,
                                             int rounds) {
    Map<IAtom, IAtomContainer> owner = new HashMap<>();
    Set<IAtom> allAtoms = new LinkedHashSet<>();
    for (IAtomContainer container : containers) {
      for (IAtom atom : container.atoms()) {
        allAtoms.add(atom);
        owner.put(atom, container);
      }
    }

    Map<IAtom, String> labels = new HashMap<>();
    for (IAtom atom : allAtoms)
      labels.put(atom, wlBaseLabel(atom));

    int nRounds = Math.max(0, rounds);
    for (int i = 0; i < nRounds; i++) {
      Map<IAtom, String> next = new HashMap<>();
      for (IAtom atom : allAtoms) {
        IAtomContainer container = owner.get(atom);
        List<String> neighborTokens = new ArrayList<>();
        if (container != null) {
          for (IBond bond : container.getConnectedBondsList(atom)) {
            IAtom nbr = bond.getOther(atom);
            String nbrLabel = labels.get(nbr);
            if (nbrLabel == null)
              nbrLabel = wlBaseLabel(nbr);
            neighborTokens.add(bondToken(atom, nbr, bond) + ":" + nbrLabel);
          }
        }
        Collections.sort(neighborTokens);
        String merged = labels.get(atom) + "|" + String.join(",", neighborTokens);
        next.put(atom, Integer.toHexString(merged.hashCode()));
      }
      labels = next;
    }

    Map<IAtom, String> result = new LinkedHashMap<>();
    for (IAtom atom : atoms) {
      if (labels.containsKey(atom))
        result.put(atom, labels.get(atom));
    }
    return result;
  }

  private static void indexReactionAtoms(IAtomContainer container,
                                         Map<IAtom, IAtomContainer> owner,
                                         Map<Integer, List<IAtom>> mapped,
                                         Set<IAtom> unmapped) {
    for (IAtom atom : container.atoms()) {
      owner.put(atom, container);
      int mapIdx = atomMapIdx(atom);
      if (mapIdx > 0) {
        List<IAtom> atoms = mapped.get(mapIdx);
        if (atoms == null) {
          atoms = new ArrayList<>();
          mapped.put(mapIdx, atoms);
        }
        atoms.add(atom);
      } else {
        unmapped.add(atom);
      }
    }
  }

  private static int atomMapIdx(IAtom atom) {
    if (atom.getMapIdx() > 0)
      return atom.getMapIdx();

    Object val = atom.getProperty(CDKConstants.ATOM_ATOM_MAPPING);
    if (val instanceof Number)
      return Math.max(0, ((Number) val).intValue());

    if (val instanceof String) {
      try {
        return Math.max(0, Integer.parseInt((String) val));
      } catch (NumberFormatException ignored) {
        return 0;
      }
    }
    return 0;
  }

  private static String mappedAtomSignature(IAtom atom, IAtomContainer container) {
    if (container == null)
      return "";

    List<String> edgeTokens = new ArrayList<>();
    for (IBond bond : container.getConnectedBondsList(atom)) {
      IAtom nbr = bond.getOther(atom);
      edgeTokens.add(neighborToken(nbr, container) + "|" + bondToken(atom, nbr, bond));
    }
    Collections.sort(edgeTokens);
    return String.join(",", edgeTokens);
  }

  private static String neighborToken(IAtom atom, IAtomContainer container) {
    int mapIdx = atomMapIdx(atom);
    if (mapIdx > 0)
      return "M:" + mapIdx;

    return "U:" + atomicNumber(atom) +
            ":" + formalCharge(atom) +
            ":" + bool(atom.isAromatic()) +
            ":" + degree(atom, container);
  }

  private static String bondToken(IBond bond) {
    return (bond.getOrder() != null ? bond.getOrder().name() : "UNSET") +
            ":" + bool(bond.isAromatic());
  }

  private static String bondToken(IAtom a, IAtom b, IBond bond) {
    // Aromatic bonds are resonance-equivalent: ignore alternating SINGLE/DOUBLE placement.
    if (bond.isAromatic() || (a.isAromatic() && b.isAromatic()))
      return "AROM:1";
    return bondToken(bond);
  }

  private static Map<String, List<IAtom>> groupByHash(Set<IAtom> atoms, Map<IAtom, String> hashes) {
    Map<String, List<IAtom>> grouped = new LinkedHashMap<>();
    for (IAtom atom : atoms) {
      String hash = hashes.get(atom);
      if (hash == null)
        hash = wlBaseLabel(atom);
      List<IAtom> bucket = grouped.get(hash);
      if (bucket == null) {
        bucket = new ArrayList<>();
        grouped.put(hash, bucket);
      }
      bucket.add(atom);
    }
    return grouped;
  }

  private static void markUnmatchedByHash(Set<IAtom> leftAtoms,
                                          Map<IAtom, String> leftHashes,
                                          Set<IAtom> rightAtoms,
                                          Map<IAtom, String> rightHashes,
                                          Set<IAtom> centers) {
    Map<String, List<IAtom>> leftByHash = groupByHash(leftAtoms, leftHashes);
    Map<String, List<IAtom>> rightByHash = groupByHash(rightAtoms, rightHashes);

    Set<String> allHashes = new LinkedHashSet<>();
    allHashes.addAll(leftByHash.keySet());
    allHashes.addAll(rightByHash.keySet());
    for (String hash : allHashes) {
      List<IAtom> left = leftByHash.get(hash);
      List<IAtom> right = rightByHash.get(hash);
      int leftSize = left != null ? left.size() : 0;
      int rightSize = right != null ? right.size() : 0;
      if (leftSize == 0) {
        // Avoid marking aromatic atoms that only appear "new" due to hash mismatch (e.g. ring
        // atoms in benzene->toluene), which would produce ambiguous scattered highlights.
        if (right != null) {
          for (IAtom a : right) {
            if (!a.isAromatic())
              centers.add(a);
          }
        }
        continue;
      }
      if (rightSize == 0) {
        if (left != null)
          centers.addAll(left);
        continue;
      }

      // Ambiguous surplus in duplicate buckets can produce visually scattered highlights.
      // Keep this fallback conservative unless at least one side is unambiguous.
      if (leftSize != rightSize && leftSize > 1 && rightSize > 1)
        continue;

      int keep = Math.min(leftSize, rightSize);
      if (left != null) {
        for (int i = keep; i < left.size(); i++)
          centers.add(left.get(i));
      }
      if (right != null) {
        for (int i = keep; i < right.size(); i++)
          centers.add(right.get(i));
      }
    }
  }

  private static Map<IAtomContainer, Set<IAtom>> partitionByContainer(Set<IAtom> atoms,
                                                                       Map<IAtom, IAtomContainer> owner) {
    Map<IAtomContainer, Set<IAtom>> grouped = new LinkedHashMap<>();
    for (IAtom atom : atoms) {
      IAtomContainer container = owner.get(atom);
      if (container == null)
        continue;
      Set<IAtom> set = grouped.get(container);
      if (set == null) {
        set = new LinkedHashSet<>();
        grouped.put(container, set);
      }
      set.add(atom);
    }
    return grouped;
  }

  private static void addContainerByKey(Map<String, Deque<IAtomContainer>> grouped,
                                        String key,
                                        IAtomContainer container) {
    Deque<IAtomContainer> queue = grouped.get(key);
    if (queue == null) {
      queue = new ArrayDeque<>();
      grouped.put(key, queue);
    }
    queue.add(container);
  }

  private static String hashMultisetKey(Set<IAtom> atoms, Map<IAtom, String> hashes) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (IAtom atom : atoms) {
      String hash = hashes.get(atom);
      if (hash == null)
        hash = wlBaseLabel(atom);
      Integer cnt = counts.get(hash);
      counts.put(hash, cnt == null ? 1 : cnt + 1);
    }
    List<String> keys = new ArrayList<>(counts.keySet());
    Collections.sort(keys);
    List<String> parts = new ArrayList<>();
    for (String key : keys)
      parts.add(key + "x" + counts.get(key));
    return String.join(";", parts);
  }

  private static String wlBaseLabel(IAtom atom) {
    return atomicNumber(atom) +
            ":" + formalCharge(atom) +
            ":" + bool(atom.isAromatic()) +
            ":" + implicitHydrogen(atom);
  }

  private static int implicitHydrogen(IAtom atom) {
    return atom.getImplicitHydrogenCount() != null ? atom.getImplicitHydrogenCount() : 0;
  }

  private static int atomicNumber(IAtom atom) {
    return atom.getAtomicNumber() != null ? atom.getAtomicNumber() : -1;
  }

  private static int formalCharge(IAtom atom) {
    return atom.getFormalCharge() != null ? atom.getFormalCharge() : 0;
  }

  private static int degree(IAtom atom, IAtomContainer container) {
    if (container == null)
      return 0;
    return container.getConnectedBondsCount(atom);
  }

  private static int bool(boolean value) {
    return value ? 1 : 0;
  }

  @ExceptionHandler({Exception.class, InvalidSmilesException.class, InvalidSmirksException.class, InvalidReactionMappingException.class})
  public static ResponseEntity<Object> handleException(Exception ex, WebRequest request) {
    if (ex instanceof InvalidSmirksException) {
      return new ResponseEntity<>("<!DOCTYPE html><html>" +
                                          "<title>400 - Invalid SMIRKS</title>" +
                                          "<body><div>" +
                                          "<h1>Invalid SMIRKS</h1>" +
                                          ex.getMessage() +
                                          "</div></body>" +
                                          "</html>",
                                  new HttpHeaders(),
                                  HttpStatus.BAD_REQUEST);
    } else if (ex instanceof InvalidReactionMappingException) {
      return new ResponseEntity<>("<!DOCTYPE html><html>" +
                                          "<title>400 - Reaction Mapping Error</title>" +
                                          "<body><div>" +
                                          "<h1>Reaction Mapping Error</h1>" +
                                          ex.getMessage() +
                                          "</div></body>" +
                                          "</html>",
                                  new HttpHeaders(),
                                  HttpStatus.BAD_REQUEST);
    } else if (ex instanceof InvalidSmilesException) {
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
