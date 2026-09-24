package dev.gavinfecko.triagedesk.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class MdcUserFilterTest {

    private final MdcUserFilter filter = new MdcUserFilter();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void noSecurityContextMeansNoUserInMdcOrRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(request.getAttribute(MdcUserFilter.USER_ATTRIBUTE)).isNull();
        assertThat(MDC.get(MdcUserFilter.MDC_KEY)).isNull();
    }

    @Test
    void authenticatedUserIsVisibleDuringTheRequestAndGoneAfter() throws ServletException, IOException {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("agent.ana", "n/a", "ROLE_AGENT"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        String[] seen = new String[1];
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> seen[0] = MDC.get(MdcUserFilter.MDC_KEY));
        assertThat(seen[0]).isEqualTo("agent.ana");
        assertThat(request.getAttribute(MdcUserFilter.USER_ATTRIBUTE)).isEqualTo("agent.ana");
        assertThat(MDC.get(MdcUserFilter.MDC_KEY)).isNull();
    }
}
