/*
 * Copyright (c) 2018. NextMove Software Ltd.
 */

package org.openscience.cdk.app;

import org.junit.jupiter.api.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class DepictControllerTest {

  @Test
  void getColor() throws Exception {
    Color color = DepictController.getColor("#ff00ff");
    assertThat(color.getRed(), is(255));
    assertThat(color.getGreen(), is(0));
    assertThat(color.getBlue(), is(255));
    assertThat(color.getAlpha(), is(255));
  }

  @Test
  void getColorTruncated() throws Exception {
    Color color = DepictController.getColor("#ff00f");
    assertThat(color.getRed(), is(255));
    assertThat(color.getGreen(), is(0));
    assertThat(color.getBlue(), is(0));
    assertThat(color.getAlpha(), is(255));
  }

  @Test
  void getColorTooLong() throws Exception {
    Color color = DepictController.getColor("#ff00ffffff");
    assertThat(color.getRed(), is(255));
    assertThat(color.getGreen(), is(0));
    assertThat(color.getBlue(), is(255));
    assertThat(color.getAlpha(), is(255));
  }

  @Test
  void colmapReactionRenders() throws Exception {
    DepictController controller = new DepictController();
    Map<String, String> extra = new HashMap<>();
    extra.put("annotate", "colmap");
    HttpEntity<?> response = controller.depict("[CH3:1][CH3:2]>>[CH2:1]=[CH2:2]",
                                               "svg",
                                               "bot",
                                               extra);
    assertThat(response.getBody(), is(instanceOf(byte[].class)));
    String svg = new String((byte[]) response.getBody(), StandardCharsets.UTF_8);
    assertThat(svg, containsString("<svg"));
  }

  @Test
  void rxnchgReactionRenders() throws Exception {
    DepictController controller = new DepictController();
    Map<String, String> extra = new HashMap<>();
    extra.put("annotate", "rxnchg");
    HttpEntity<?> response = controller.depict("[CH3:1][CH2:2]Cl>>[CH3:1][CH2:2]Br",
                                               "svg",
                                               "bot",
                                               extra);
    assertThat(response.getBody(), is(instanceOf(byte[].class)));
    String svg = new String((byte[]) response.getBody(), StandardCharsets.UTF_8);
    assertThat(svg, containsString("<svg"));
  }

  @Test
  void rxnchgMoleculeNoopRenders() throws Exception {
    DepictController controller = new DepictController();
    Map<String, String> extra = new HashMap<>();
    extra.put("annotate", "rxnchg");
    HttpEntity<?> response = controller.depict("CCO",
                                               "svg",
                                               "bot",
                                               extra);
    assertThat(response.getBody(), is(instanceOf(byte[].class)));
    String svg = new String((byte[]) response.getBody(), StandardCharsets.UTF_8);
    assertThat(svg, containsString("<svg"));
  }

  @Test
  void reactEndpointRenders() throws Exception {
    DepictController controller = new DepictController();
    HttpEntity<?> response = controller.react("CCO",
                                              "[C:1]-[O:2]>>[C:1]-[N:2]",
                                              "svg",
                                              "bot",
                                              new HashMap<>());
    assertThat(response.getBody(), is(instanceOf(byte[].class)));
    String body = new String((byte[]) response.getBody(), StandardCharsets.UTF_8);
    assertThat(body, containsString("<svg"));
  }

  @Test
  void reactReverseOptionRendersFromReversedRule() throws Exception {
    DepictController controller = new DepictController();

    HttpEntity<?> forward = controller.react("CCN",
                                             "[C:1]-[O:2]>>[C:1]-[N:2]",
                                             "svg",
                                             "bot",
                                             new HashMap<>());
    String forwardBody = new String((byte[]) forward.getBody(), StandardCharsets.UTF_8);
    assertThat(forwardBody, containsString("No products generated"));

    Map<String, String> extra = new HashMap<>();
    extra.put("reverse", "true");
    HttpEntity<?> reverse = controller.react("CCN",
                                             "[C:1]-[O:2]>>[C:1]-[N:2]",
                                             "svg",
                                             "bot",
                                             extra);
    String reverseBody = new String((byte[]) reverse.getBody(), StandardCharsets.UTF_8);
    assertThat(reverseBody, containsString("<svg"));
  }

  @Test
  void reactTransformDeduplicatesAndCaps() throws Exception {
    DepictController controller = new DepictController();

    List<String> deduped = controller.transformProductSmiles("CCC",
                                                             "[C:1]>>[C:1]",
                                                             false);
    assertThat(deduped.size(), is(1));

    StringBuilder smi = new StringBuilder("O");
    for (int i = 0; i < 120; i++) {
      smi.append('C');
    }
    List<String> capped = controller.transformProductSmiles(smi.toString(),
                                                            "[C:1]>>[N:1]",
                                                            false);
    assertThat(capped.size(), is(DepictController.MAX_REACTION_PRODUCTS));
  }

  @Test
  void reactNoMatchReturnsMessage() throws Exception {
    DepictController controller = new DepictController();
    HttpEntity<?> response = controller.react("CCO",
                                              "[N:1]>>[O:1]",
                                              "svg",
                                              "bot",
                                              new HashMap<>());
    String body = new String((byte[]) response.getBody(), StandardCharsets.UTF_8);
    assertThat(body, containsString("No products generated"));
  }

  @Test
  void mapReactionSmilesReturnsMappedReaction() throws Exception {
    DepictController controller = new DepictController();
    String mapped = controller.mapReactionSmiles("CC(=O)O.OCC>>CC(=O)OCC.O");
    assertThat(mapped, containsString(">>"));
    assertThat(mapped, containsString(":"));
  }

  @Test
  void mapReactionSmilesWithAgentsReturnsMappedReaction() throws Exception {
    DepictController controller = new DepictController();
    String mapped = controller.mapReactionSmiles("CCO.[CH3:1][C:2](=[O:3])[OH:4]>[H+]>CC[O:4][C:2](=[O:3])[CH3:1].O");
    assertThat(mapped, containsString(">[H+]>"));
    assertThat(mapped, containsString(":"));
  }

  @Test
  void mapEndpointRendersMappedReaction() throws Exception {
    DepictController controller = new DepictController();
    HttpEntity<?> response = controller.map("CC(=O)O.OCC>>CC(=O)OCC.O",
                                            "svg",
                                            "bot",
                                            new HashMap<>());
    assertThat(response.getBody(), is(instanceOf(byte[].class)));
    String body = new String((byte[]) response.getBody(), StandardCharsets.UTF_8);
    assertThat(body, containsString("<svg"));
  }

  @Test
  void invalidReactionInMapReturnsBadRequest() throws Exception {
    DepictController controller = new DepictController();
    Exception ex = assertThrows(Exception.class,
                                () -> controller.mapReactionSmiles("CCO"));

    ResponseEntity<Object> response = DepictController.handleException(ex, null);
    assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
    String body = response.getBody().toString();
    assertThat(body, containsString("Reaction Mapping Error"));
    assertThat(body, containsString("reactants>agents>products"));
  }

  @Test
  void invalidSmirksReturnsBadRequest() throws Exception {
    DepictController controller = new DepictController();
    Exception ex = assertThrows(Exception.class,
                                () -> controller.react("CCO",
                                                       "[C:1]>>[N:1",
                                                       "svg",
                                                       "bot",
                                                       new HashMap<>()));

    ResponseEntity<Object> response = DepictController.handleException(ex, null);
    assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
    String body = response.getBody().toString();
    assertThat(body, containsString("Invalid SMIRKS"));
  }

  @Test
  void invalidSmilesInReactReturnsBadRequest() throws Exception {
    DepictController controller = new DepictController();
    Exception ex = assertThrows(Exception.class,
                                () -> controller.react("C1CC",
                                                       "[C:1]>>[N:1]",
                                                       "svg",
                                                       "bot",
                                                       new HashMap<>()));

    ResponseEntity<Object> response = DepictController.handleException(ex, null);
    assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
    String body = response.getBody().toString();
    assertThat(body, containsString("Invalid SMILES"));
  }

}
