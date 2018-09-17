/*
 * Copyright (c) 2018. NextMove Software Ltd.
 */

package org.openscience.cdk.app;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class WebController {

  /**
   * Home page redirect.
   */
  @RequestMapping("/")
  public String redirect()
  {
    return "redirect:/depict.html";
  }
}
