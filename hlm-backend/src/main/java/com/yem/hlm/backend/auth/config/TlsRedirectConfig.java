package com.yem.hlm.backend.auth.config;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds an additional HTTP connector that redirects all traffic to HTTPS.
 *
 * <p>Only active when {@code server.ssl.enabled=true}.
 * The HTTP connector listens on {@code HTTP_REDIRECT_PORT} (default 8080) and
 * redirects to the HTTPS port configured via {@code SERVER_PORT} (default 8443).
 */
@Configuration
@ConditionalOnProperty(name = "server.ssl.enabled", havingValue = "true")
public class TlsRedirectConfig {

    @Bean
    public TomcatServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory() {
            @Override
            protected void postProcessContext(Context context) {
                SecurityConstraint constraint = new SecurityConstraint();
                constraint.setUserConstraint("CONFIDENTIAL");
                SecurityCollection collection = new SecurityCollection();
                collection.addPattern("/*");
                constraint.addCollection(collection);
                context.addConstraint(constraint);
            }
        };
        // HTTP connector on redirect port that forwards to the HTTPS port
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setScheme("http");
        connector.setPort(Integer.parseInt(
                System.getenv().getOrDefault("HTTP_REDIRECT_PORT", "8080")));
        connector.setSecure(false);
        connector.setRedirectPort(Integer.parseInt(
                System.getenv().getOrDefault("SERVER_PORT", "8443")));
        factory.addAdditionalTomcatConnectors(connector);
        return factory;
    }
}
