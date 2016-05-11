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

import org.openscience.cdk.depict.Abbreviations;
import org.openscience.cdk.depict.Depiction;
import org.openscience.cdk.depict.DepictionGenerator;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IChemObject;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.interfaces.IReaction;
import org.openscience.cdk.io.MDLV2000Reader;
import org.openscience.cdk.renderer.RendererModel;
import org.openscience.cdk.renderer.SymbolVisibility;
import org.openscience.cdk.renderer.color.CDK2DAtomColors;
import org.openscience.cdk.renderer.color.IAtomColorer;
import org.openscience.cdk.renderer.color.UniColor;
import org.openscience.cdk.renderer.generators.standard.StandardGenerator;
import org.openscience.cdk.renderer.generators.standard.StandardGenerator.Visibility;
import org.openscience.cdk.silent.AtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.smiles.smarts.SmartsPattern;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
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

    public DepictController() throws IOException {
        int count = this.abbreviations.loadFromFile("/org/openscience/cdk/app/abbreviations.smi");
        this.reagents.loadFromFile("/org/openscience/cdk/app/reagents.smi");
    }

    /**
     * Home page redirect.
     */
    @RequestMapping("/")
    public String redirect() {
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
                                @RequestParam(value = "suppressh", defaultValue = "true") boolean suppressh,
                                @RequestParam(value = "anon", defaultValue = "off") String anon,
                                @RequestParam(value = "zoom", defaultValue = "1.3") double zoom,
                                @RequestParam(value = "annotate", defaultValue = "none") String annotate,
                                @RequestParam(value = "w", defaultValue = "-1") int w,
                                @RequestParam(value = "h", defaultValue = "-1") int h,
                                @RequestParam(value = "abbr", defaultValue = "reagents") String abbr,
                                @RequestParam(value = "sma", defaultValue = "") String sma,
                                @RequestParam(value = "showtitle", defaultValue = "false") boolean showTitle,
                                @RequestParam(value = "smalim", defaultValue = "100") int smaLimit) throws
                                                                                                      CDKException,
                                                                                                      IOException {

        // Note: DepictionGenerator is immutable
        DepictionGenerator myGenerator = generator.withSize(w, h)
                                                  .withZoom(zoom);

        // Configure style preset
        myGenerator = withStyle(myGenerator, style).withParam(StandardGenerator.Highlighting.class,
                                                              StandardGenerator.HighlightStyle.Colored);

        // Add annotations
        switch (annotate) {
            case "number":
                myGenerator = myGenerator.withAtomNumbers();
                abbr = "false";
                break;
            case "mapidx":
                myGenerator = myGenerator.withAtomMapNumbers();
                abbr = "false";
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
        }

        // Improved depiction of anatomised graphs, e.g. ***1*****1**
        if (anon.equalsIgnoreCase("on")) {
            myGenerator = myGenerator.withParam(Visibility.class,
                                                new SymbolVisibility() {
                                                    @Override
                                                    public boolean visible(IAtom iAtom, List<IBond> list,
                                                                           RendererModel rendererModel) {
                                                        return list.isEmpty();
                                                    }
                                                });
        }

        final boolean isRxn = !smi.contains("V2000") && isRxnSmi(smi);
        final boolean isRgp = smi.contains("RG:");
        IReaction rxn = null;
        IAtomContainer mol = null;
        List<IAtomContainer> mols = null;

        Set<IChemObject> highlight;

        if (isRxn) {
            rxn = smipar.parseReactionSmiles(smi);
            if (suppressh) {
                for (IAtomContainer component : rxn.getReactants().atomContainers())
                    AtomContainerManipulator.suppressHydrogens(component);
                for (IAtomContainer component : rxn.getProducts().atomContainers())
                    AtomContainerManipulator.suppressHydrogens(component);
                for (IAtomContainer component : rxn.getAgents().atomContainers())
                    AtomContainerManipulator.suppressHydrogens(component);
            }

            highlight = findHits(sma, rxn, mol, smaLimit);

            for (IAtomContainer component : rxn.getReactants().atomContainers())
                abbreviate(component, abbr, highlight);
            for (IAtomContainer component : rxn.getProducts().atomContainers())
                abbreviate(component, abbr, highlight);
            for (IAtomContainer component : rxn.getAgents().atomContainers())
                abbreviate(component, abbr, highlight);

        } else {
            mol = loadMol(smi);
            if (suppressh) {
                AtomContainerManipulator.suppressHydrogens(mol);
            }
            highlight = findHits(sma, rxn, mol, smaLimit);
            abbreviate(mol, abbr, highlight);
        }

        // add highlight from atom/bonds hit by the provided SMARTS
        myGenerator = myGenerator.withHighlight(highlight, Color.RED);

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

    private void abbreviate(IAtomContainer mol, String mode, Set<IChemObject> highlight) {
        switch (mode.toLowerCase()) {
            case "true":
            case "on":
            case "yes":
                reagents.apply(mol);
                abbreviations.apply(mol);
                break;
            case "reagents":
                reagents.apply(mol);
                break;
        }
    }

    private boolean isRxnSmi(String smi) {
        return smi.split(" ")[0].contains(">");
    }

    private IAtomContainer loadMol(String str) throws CDKException {
        if (str.contains("V2000")) {
            try (MDLV2000Reader mdlr = new MDLV2000Reader(new StringReader(str))) {
                return mdlr.read(new AtomContainer(0, 0, 0, 0));
            } catch (CDKException | IOException e3) {
                throw new CDKException("Could not parse input");
            }
        } else {
            return smipar.parseSmiles(str);
        }
    }

    private HttpEntity<byte[]> makeResponse(byte[] bytes, String contentType) {
        HttpHeaders header = new HttpHeaders();
        String type = contentType.substring(0, contentType.indexOf('/'));
        String subtype = contentType.substring(contentType.indexOf('/') + 1, contentType.length());
        header.setContentType(new MediaType(type, subtype));
        header.add("Access-Control-Allow-Origin", "*");
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
    private static DepictionGenerator withStyle(DepictionGenerator generator, String style) {
        switch (style) {
            case "cow":
                generator = generator.withAtomColors(new CDK2DAtomColors())
                                     .withBackgroundColor(Color.WHITE);
                break;
            case "bow":
                generator = generator.withAtomColors(new UniColor(Color.BLACK))
                                     .withBackgroundColor(Color.WHITE);
                break;
            case "wob":
                generator = generator.withAtomColors(new UniColor(Color.WHITE))
                                     .withBackgroundColor(Color.BLACK);
                break;
            case "cob":
                generator = generator.withAtomColors(new CobColorer())
                                     .withBackgroundColor(Color.BLACK);
                break;
            case "nob":
                generator = generator.withAtomColors(new NobColorer())
                                     .withBackgroundColor(Color.BLACK);
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
        private final Color           NEON   = new Color(0x00FF0E);

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
    private Set<IChemObject> findHits(final String sma, final IReaction rxn, final IAtomContainer mol,
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
                                                                    .uniqueAtoms()
                                                                    .toAtomBondMap()) {
                    for (Map.Entry<IChemObject, IChemObject> e : m.entrySet()) {
                        highlight.add(e.getValue());
                    }
                }
            } else if (rxn != null) {
                // match all together
                IAtomContainer combined = rxn.getBuilder().newInstance(IAtomContainer.class);

                for (IAtomContainer reac : rxn.getReactants().atomContainers())
                    combined.add(reac);
                for (IAtomContainer prod : rxn.getProducts().atomContainers())
                    combined.add(prod);
                for (IAtomContainer agnt : rxn.getAgents().atomContainers())
                    combined.add(agnt);

                for (Map<IChemObject, IChemObject> m : smartsPattern.matchAll(combined)
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
