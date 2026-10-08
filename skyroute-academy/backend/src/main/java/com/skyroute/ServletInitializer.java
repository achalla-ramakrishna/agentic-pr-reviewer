package com.skyroute;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Only used when the app is packaged as a WAR and deployed to an external Tomcat
 * (see docs/03-engineering-manual.md, "Tomcat: embedded vs external").
 * The default `java -jar` run uses embedded Tomcat and ignores this class.
 */
public class ServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(SkyRouteApplication.class);
    }
}
