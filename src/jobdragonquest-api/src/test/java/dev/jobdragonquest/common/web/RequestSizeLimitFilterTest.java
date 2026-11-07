package dev.jobdragonquest.common.web;

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

    /** Issue #124: até 6 MiB só em multipart nas rotas de documentos; JSON grande nelas continua recusado. */
    @Test
    void uploadRoutesAcceptLargerMultipartOnly() throws Exception {
        String profileDocs = "/api/v1/profiles/123e4567-e89b-12d3-a456-426614174000/documents";
        MockHttpServletRequest upload = new MockHttpServletRequest("POST", profileDocs);
        upload.setContentType("multipart/form-data; boundary=x");
        upload.setContent(new byte[5 * 1024 * 1024]);
        MockFilterChain chain = new MockFilterChain();
        assertThat(run(upload, chain).getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();

        MockHttpServletRequest tooBig = new MockHttpServletRequest("POST", profileDocs);
        tooBig.setContentType("multipart/form-data; boundary=x");
        tooBig.setContent(new byte[(int) RequestSizeLimitFilter.MAX_UPLOAD_BYTES + 1]);
        assertThat(run(tooBig, new MockFilterChain()).getStatus()).isEqualTo(413);

        MockHttpServletRequest jsonOnUpload = new MockHttpServletRequest("POST", profileDocs);
        jsonOnUpload.setContentType("application/json");
        jsonOnUpload.setContent(new byte[(int) RequestSizeLimitFilter.MAX_BODY_BYTES + 1]);
        assertThat(run(jsonOnUpload, new MockFilterChain()).getStatus()).isEqualTo(413);

        MockHttpServletRequest multipartElsewhere = new MockHttpServletRequest("POST", "/api/v1/jobs");
        multipartElsewhere.setContentType("multipart/form-data; boundary=x");
        multipartElsewhere.setContent(new byte[(int) RequestSizeLimitFilter.MAX_BODY_BYTES + 1]);
        assertThat(run(multipartElsewhere, new MockFilterChain()).getStatus()).isEqualTo(413);
    }
}
