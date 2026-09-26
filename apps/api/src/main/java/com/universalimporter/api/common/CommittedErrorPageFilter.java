package com.universalimporter.api.common;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Keeps the error page out of a response that has already started. When an exception reaches Tomcat after the
 * first byte (a download failing midway, BE-F10), Tomcat includes the error page into what was sent, then drops
 * the connection. The drop is what the client must see; the included page would land inside the file.
 */
@Configuration(proxyBeanMethods = false)
public class CommittedErrorPageFilter {

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> committedErrorPage(@Value("${server.error.path:/error}") String path) {
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                if (!response.isCommitted()) {
                    chain.doFilter(request, response);
                }
            }

            @Override
            protected boolean shouldNotFilterErrorDispatch() {
                return false;
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns(path);
        registration.setDispatcherTypes(DispatcherType.INCLUDE, DispatcherType.ERROR);
        return registration;
    }
}
