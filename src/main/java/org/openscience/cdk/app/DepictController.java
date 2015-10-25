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

import org.openscience.cdk.depict.Depiction;
import org.openscience.cdk.depict.DepictionGenerator;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IChemObject;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.interfaces.IReaction;
import org.openscience.cdk.renderer.RendererModel;
import org.openscience.cdk.renderer.SymbolVisibility;
import org.openscience.cdk.renderer.color.CDK2DAtomColors;
import org.openscience.cdk.renderer.color.CDKAtomColors;
import org.openscience.cdk.renderer.color.IAtomColorer;
import org.openscience.cdk.renderer.color.UniColor;
import org.openscience.cdk.renderer.generators.BasicSceneGenerator.BondLength;
import org.openscience.cdk.renderer.generators.standard.SelectionVisibility;
import org.openscience.cdk.renderer.generators.standard.StandardGenerator.HashSpacing;
import org.openscience.cdk.renderer.generators.standard.StandardGenerator.Visibility;
import org.openscience.cdk.renderer.generators.standard.StandardGenerator.WaveSpacing;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.smiles.smarts.SmartsPattern;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Chemical structure depiction controller.
 */
@Controller
public class DepictController {

    // limit number of SMARTS matches
    public static final int SMA_HIT_LIMIT = 50;

    // chem object builder to create objects with
    private final IChemObjectBuilder builder = SilentChemObjectBuilder.getInstance();

    // we make are raster depictions slightly smalled by default (40px bond length)
    private final Font               font      = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    private final DepictionGenerator generator = new DepictionGenerator(font).withParam(Visibility.class,
                                                                                        SelectionVisibility.disconnected(SymbolVisibility.iupacRecommendationsWithoutTerminalCarbon()))
                                                                             .withParam(BondLength.class, 26d)
                                                                             .withParam(HashSpacing.class, 26 / 6d)
                                                                             .withParam(WaveSpacing.class, 26 / 6d);
    private       SmilesParser       smipar    = new SmilesParser(builder);

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
     * 
     * @throws CDKException something not okay with input
     * @throws IOException  problem reading/writing request
     */
    @RequestMapping("depict/{style}/{fmt}")
    public HttpEntity<?> depict(@RequestParam("smi") String smi,
                                @PathVariable("fmt") String fmt,
                                @PathVariable("style") String style,
                                @RequestParam(value = "anon", defaultValue = "off") String anon,
                                @RequestParam(value = "zoom", defaultValue = "1.3") double zoom,
                                @RequestParam(value = "annotate", defaultValue = "none") String annotate,
                                @RequestParam(value = "w", defaultValue = "-1") int w,
                                @RequestParam(value = "h", defaultValue = "-1") int h,
                                @RequestParam(value = "sma", defaultValue = "") String sma) throws CDKException, IOException {

        // Note: DepictionGenerator is immutable
        DepictionGenerator myGenerator = generator.withSize(w, h)
                                                  .withZoom(zoom);

        // Configure style preset
        myGenerator = withStyle(myGenerator, style);

        // Add annotations
        switch (annotate) {
            case "number":
                myGenerator = myGenerator.withAtomNumbers();
                break;
            case "mapidx":
                myGenerator = myGenerator.withAtomMapNumbers();
                break;
        }

        // Improved depiction of anatomised graphs, e.g. ***1*****1**
        if (anon.equalsIgnoreCase("on")) {
            myGenerator = myGenerator.withParam(Visibility.class,
                                                new SymbolVisibility() {
                                                    @Override public boolean visible(IAtom iAtom, List<IBond> list, RendererModel rendererModel) {
                                                        return list.isEmpty();
                                                    }
                                                });
        }

        final boolean isRxn = smi.contains(">");
        IReaction rxn = null;
        IAtomContainer mol = null;

        if (isRxn)
            rxn = loadRxn(smi);
        else
            mol = loadMol(smi);

        // add highlight from atom/bonds hit by the provided SMARTS
        myGenerator = myGenerator.withHighlight(findHits(sma, rxn, mol),
                                                Color.RED);

        // pre-render the depiction
        final Depiction depiction = isRxn ? myGenerator.depict(rxn) : myGenerator.depict(mol);

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

    private IAtomContainer loadMol(String smi) throws InvalidSmilesException {
        return smipar.parseSmiles(smi);
    }

    private IReaction loadRxn(String smi) throws InvalidSmilesException {
        return smipar.parseReactionSmiles(smi);
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
                generator = generator.withAtomColors(new CDKAtomColors())
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
        }
        return generator;
    }

    /**
     * Color-on-black atom colors.
     */
    private static final class CobColorer implements IAtomColorer {
        private final CDK2DAtomColors colors = new CDK2DAtomColors();

        @Override public Color getAtomColor(IAtom atom) {
            if (atom.getAtomicNumber() == 6 || atom.getAtomicNumber() == 1)
                return Color.WHITE;
            return colors.getAtomColor(atom);
        }

        @Override public Color getAtomColor(IAtom atom, Color color) {
            if (atom.getAtomicNumber() == 6 || atom.getAtomicNumber() == 1)
                return Color.WHITE;
            return colors.getAtomColor(atom, color);
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
    private Set<IChemObject> findHits(String sma, IReaction rxn, IAtomContainer mol) {
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
                                                                    .uniqueAtoms()
                                                                    .limit(SMA_HIT_LIMIT)
                                                                    .toAtomBondMap()) {
                    for (Map.Entry<IChemObject, IChemObject> e : m.entrySet()) {
                        highlight.add(e.getValue());
                    }
                }
            }
            else if (rxn != null) {
                for (IAtomContainer reac : rxn.getReactants().atomContainers()) {
                    for (Map<IChemObject, IChemObject> m : smartsPattern.matchAll(reac)
                                                                        .uniqueAtoms()
                                                                        .limit(SMA_HIT_LIMIT)
                                                                        .toAtomBondMap()) {
                        for (Map.Entry<IChemObject, IChemObject> e : m.entrySet()) {
                            highlight.add(e.getValue());
                        }
                    }
                }
                for (IAtomContainer prod : rxn.getProducts().atomContainers()) {
                    for (Map<IChemObject, IChemObject> m : smartsPattern.matchAll(prod)
                                                                        .uniqueAtoms()
                                                                        .limit(SMA_HIT_LIMIT)
                                                                        .toAtomBondMap()) {
                        for (Map.Entry<IChemObject, IChemObject> e : m.entrySet()) {
                            highlight.add(e.getValue());
                        }
                    }
                }
            }
        }
        return highlight;
    }
}
