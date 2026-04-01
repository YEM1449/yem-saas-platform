package com.yem.hlm.backend.auth.config;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds an additional HTTP connector that redirects all traffic to HTTPS.
 *
 * <p>Only active when {@code server.ssl.enabled=true}.
 * The HTTP connector listens on {@code app.tls.http-redirect-port} (default 8080) and
 * redirects to the HTTPS port configured via {@code app.tls.https-port} (default 8443).
 * Set {@code app.tls.http-redirect-port} to -1 to disable the redirect connector (e.g. in tests).
 */
@Configuration
@ConditionalOnProperty(name = "server.ssl.enabled", havingValue = "true")
public class TlsRedirectConfig {

    @Value("${app.tls.http-redirect-port:8080}")
    private int httpRedirectPort;

    @Value("${app.tls.https-port:8443}")
    private int httpsPort;

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
        // HTTP connector on redirect port that forwards to the HTTPS port.
        // A negative port disables the connector (useful in integration tests).
        if (httpRedirectPort > 0) {
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setScheme("http");
            connector.setPort(httpRedirectPort);
            connector.setSecure(false);
            connector.setRedirectPort(httpsPort);
            factory.addAdditionalTomcatConnectors(connector);
        }
        return factory;
    }
}
