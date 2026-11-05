package dev.jobfly.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter();

    private MockHttpServletResponse run(MockHttpServletRequest request, MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void rejectsBodyAboveLimitWithoutCallingTheApplication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/jobs");
        request.setContent(new byte[(int) RequestSizeLimitFilter.MAX_BODY_BYTES + 1]);
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("payload_too_large");
        assertThat(chain.getRequest()).as("a cadeia não pode ser chamada").isNull();
    }

    @Test
    void rejectsChunkedBodyWithoutLength() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/profiles/x");
        request.addHeader("Transfer-Encoding", "chunked");
        assertThat(run(request, new MockFilterChain()).getStatus()).isEqualTo(413);
    }

    @Test
    void acceptsNormalBodiesAndGets() throws Exception {
        MockHttpServletRequest post = new MockHttpServletRequest("POST", "/api/v1/jobs");
        post.setContent("{}".getBytes());
        MockFilterChain chain = new MockFilterChain();
        assertThat(run(post, chain).getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();

        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/v1/jobs");
        get.addHeader("Transfer-Encoding", "chunked");
        assertThat(run(get, new MockFilterChain()).getStatus()).isEqualTo(200);
    }
}
