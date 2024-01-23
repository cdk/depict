/*
 * Copyright (c) 2018. NextMove Software Ltd.
 */

package org.openscience.cdk.app;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

enum HydrogenDisplayType {
  Provided('P'),
  Minimal('M'),
  Stereo('C'),
  Smart('S'),
  Explicit('X');

  private final char ch;
  private static Map<String, HydrogenDisplayType> map = new HashMap<>();

  static {
    for (HydrogenDisplayType hdisp : values()) {
      map.put(hdisp.name(), hdisp);
      map.put(hdisp.name().toLowerCase(Locale.ROOT), hdisp);
      map.put(Character.toString(hdisp.ch), hdisp);
    }
    map.put("suppressed", HydrogenDisplayType.Minimal);
    map.put("bridgeheadtetrahedral", HydrogenDisplayType.Smart);
    map.put("bridgehead", HydrogenDisplayType.Smart);
    map.put("default", HydrogenDisplayType.Smart);
  }

  HydrogenDisplayType(char ch) {
    this.ch = ch;
  }

  static HydrogenDisplayType parse(String key) {
    return map.getOrDefault(key, HydrogenDisplayType.Smart);
  }
}