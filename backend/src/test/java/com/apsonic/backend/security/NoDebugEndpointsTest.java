package com.apsonic.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard: a public, state-changing debug endpoint ("/api/v1/pages/debug-create2")
 * once shipped. No controller may expose a debug/test route.
 */
class NoDebugEndpointsTest {

    @Test
    void noControllerExposesDebugOrTestRoutes() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> offenders = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.apsonic.backend")) {
            Class<?> controller = Class.forName(bd.getBeanClassName());
            if (controller.getSimpleName().toLowerCase(Locale.ROOT).contains("debug")) {
                offenders.add(controller.getName());
            }
            for (String path : pathsOf(controller)) {
                if (path.toLowerCase(Locale.ROOT).matches(".*/(debug|test)[^/]*.*")) {
                    offenders.add(controller.getName() + " -> " + path);
                }
            }
        }
        assertThat(offenders).as("debug/test routes must not ship").isEmpty();
    }

    private static List<String> pathsOf(Class<?> controller) {
        List<String> prefixes = new ArrayList<>(List.of(""));
        RequestMapping classMapping = controller.getAnnotation(RequestMapping.class);
        if (classMapping != null && classMapping.value().length > 0) {
            prefixes = List.of(classMapping.value());
        }
        List<String> paths = new ArrayList<>();
        for (Method m : controller.getDeclaredMethods()) {
            for (var ann : m.getAnnotations()) {
                var attrs = org.springframework.core.annotation.AnnotatedElementUtils
                        .findMergedAnnotation(m, RequestMapping.class);
                if (attrs == null) {
                    continue;
                }
                String[] values = attrs.value().length > 0 ? attrs.value() : attrs.path();
                for (String prefix : prefixes) {
                    if (values.length == 0) {
                        paths.add(prefix);
                    }
                    for (String v : values) {
                        paths.add(prefix + v);
                    }
                }
                break;
            }
        }
        return paths;
    }
}
