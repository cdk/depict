package org.openscience.cdk.app;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

@Configuration
@ComponentScan
public class Context {

  @EnableWebMvc
  static class MvConfig implements WebMvcConfigurer {
    @Bean
    public InternalResourceViewResolver defaultViewResolver() {
      return new InternalResourceViewResolver();
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
      registry.addViewController("/")
              .setViewName("redirect:depict.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
      registry.addResourceHandler("/**")
              .addResourceLocations("/WEB-INF/static/")
              .setCachePeriod(3600);

      registry.addResourceHandler("/webjars/**")
              .addResourceLocations("/webjars/")
	      .resourceChain(false);

    }
  }
}
