/*
 * Copyright (c) 2017. NextMove Software Ltd.
 */

package org.openscience.cdk.app;

public enum DisplayOption {
  HydrogenDisplay("hdisp", HydrogenDisplayType.class, HydrogenDisplayType.Minimal),
  Style("hdisp", StyleType.class, StyleType.BlackOnWhite),
  Annotate("annotate", String.class, null),
  Zoom("zoom", Double.class, 1.4),
  Width("w", Integer.class, -1),
  Height("h", Integer.class, -1);

  private final String   param;
  private final Class<?> valueClass;
  private final Object   defaultValue;

  DisplayOption(String param, Class<?> valueClass, Object defaultValue)
  {
    this.param = param;
    this.valueClass = valueClass;
    this.defaultValue = defaultValue;
  }
}

enum HydrogenDisplayType {
  Provided,
  Minimal,
  StereoOnly,
  BridgeHeadTetrahedralOnly;
}

enum StyleType {
  BlackOnWhite,
  WhiteOnBlack,
  ColorOnWhite,
  ColorOnBlack,
  NeonOnBlack
}