package com.apsonic.backend.shared.upload;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class UploadResponseHeadersFilterTest {

    private final UploadResponseHeadersFilter filter = new UploadResponseHeadersFilter();

    @Test
    void uploadsAreServedNosniffAndSandboxed() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/uploads/legacy.svg") {{
            setRequestURI("/uploads/legacy.svg");
        }}, response, new MockFilterChain());

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Content-Security-Policy")).contains("sandbox").contains("default-src 'none'");
    }

    @Test
    void otherPathsAreNotTouched() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/news") {{
            setRequestURI("/api/v1/news");
        }}, response, new MockFilterChain());

        assertThat(response.getHeader("Content-Security-Policy")).isNull();
    }
}
