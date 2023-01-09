/*
 * Copyright (c) 2018. NextMove Software Ltd.
 */

package org.openscience.cdk.app;

import org.junit.jupiter.api.Test;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.awt.Color;

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

}