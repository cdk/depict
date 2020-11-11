/*
 * Copyright (c) 2018. NextMove Software Ltd.
 */

package org.openscience.cdk.app;

import org.junit.Test;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import java.awt.*;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class DepictControllerTest {

  @Test
  public void getColor() throws Exception {
    Color color = DepictController.getColor("#ff00ff");
    assertThat(color.getRed(), is(255));
    assertThat(color.getGreen(), is(0));
    assertThat(color.getBlue(), is(255));
    assertThat(color.getAlpha(), is(255));
  }

  @Test
  public void getColorTruncated() throws Exception {
    Color color = DepictController.getColor("#ff00f");
    assertThat(color.getRed(), is(255));
    assertThat(color.getGreen(), is(0));
    assertThat(color.getBlue(), is(0));
    assertThat(color.getAlpha(), is(255));
  }

  @Test
  public void getColorTooLong() throws Exception {
    Color color = DepictController.getColor("#ff00ffffff");
    assertThat(color.getRed(), is(255));
    assertThat(color.getGreen(), is(0));
    assertThat(color.getBlue(), is(255));
    assertThat(color.getAlpha(), is(255));
  }

}