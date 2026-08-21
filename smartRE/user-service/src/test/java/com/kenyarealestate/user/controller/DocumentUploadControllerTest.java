package com.kenyarealestate.user.controller;

import com.kenyarealestate.user.service.DocumentMetadataService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentUploadControllerTest {

    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 1, 2, 3, 4, 5,
    };
    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0, 1, 2, 3, 4, 5,
    };
    private static final byte[] NOT_AN_IMAGE = "this is definitely not a jpeg".getBytes();

    @TempDir
    Path tempDir;

    @Mock
    private DocumentMetadataService documentMetadataService;

    private DocumentUploadController controller;

    private static final Authentication SELLER_AUTH =
            new UsernamePasswordAuthenticationToken("seller@smartre.co.ke", null,
                    List.of(new SimpleGrantedAuthority("ROLE_SELLER")));

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        controller = new DocumentUploadController(documentMetadataService);
        ReflectionTestUtils.setField(controller, "localDir", tempDir.toString());
        ReflectionTestUtils.setField(controller, "publicUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(controller, "s3Enabled", false);
    }

    private MockMultipartFile jpeg(String originalFilename) {
        return new MockMultipartFile("file", originalFilename, "image/jpeg", JPEG_BYTES);
    }

    @SuppressWarnings("unchecked")
    private String errorOf(ResponseEntity<Map<String, Object>> res) {
        return String.valueOf(res.getBody().get("error"));
    }

    @Test
    void upload_rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "id.jpg", "image/jpeg", new byte[0]);

        var res = controller.upload(empty, "NATIONAL_ID_FRONT", SELLER_AUTH);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(errorOf(res).contains("empty"));
    }

    @Test
    void upload_rejectsPathTraversalInCategory() {
        var res = controller.upload(jpeg("id.jpg"), "../../../etc", SELLER_AUTH);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("Invalid category", errorOf(res));
    }

    @Test
    void upload_rejectsCategoryContainingSlashes() {
        var res = controller.upload(jpeg("id.jpg"), "foo/bar", SELLER_AUTH);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("Invalid category", errorOf(res));
    }

    @Test
    void upload_rejectsDisallowedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.docx", "application/msword", JPEG_BYTES);

        var res = controller.upload(file, "NATIONAL_ID_FRONT", SELLER_AUTH);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(errorOf(res).contains("not allowed"));
    }

    @Test
    void upload_rejectsOversizedFile() {
        byte[] big = new byte[11 * 1024 * 1024];
        System.arraycopy(JPEG_BYTES, 0, big, 0, JPEG_BYTES.length);
        MockMultipartFile file = new MockMultipartFile("file", "id.jpg", "image/jpeg", big);

        var res = controller.upload(file, "NATIONAL_ID_FRONT", SELLER_AUTH);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(errorOf(res).contains("10MB"));
    }

    @Test
    void upload_rejectsContentThatDoesNotMatchDeclaredExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "id.jpg", "image/jpeg", NOT_AN_IMAGE);

        var res = controller.upload(file, "NATIONAL_ID_FRONT", SELLER_AUTH);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(errorOf(res).contains("does not match"));
    }

    @Test
    void upload_acceptsPng() {
        MockMultipartFile file = new MockMultipartFile("file", "selfie.png", "image/png", PNG_BYTES);

        var res = controller.upload(file, "SELFIE_WITH_ID", SELLER_AUTH);

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
    }

    @Test
    void upload_savesLocally_andReturnsRetrievableUrl() throws IOException {
        var res = controller.upload(jpeg("national_id_front.jpg"), "NATIONAL_ID_FRONT", SELLER_AUTH);

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        String url = (String) res.getBody().get("url");
        assertTrue(url.startsWith("http://localhost:8080/api/documents/files/documents/national_id_front/"), url);
        assertTrue(url.endsWith(".jpg"), url);

        String objectKey = (String) res.getBody().get("objectKey");
        Path saved = tempDir.resolve(objectKey);
        assertTrue(Files.exists(saved), "uploaded file should actually exist on disk");
        assertArrayEquals(JPEG_BYTES, Files.readAllBytes(saved));
    }

    @Test
    void upload_recordsUploaderIdentity() {
        var res = controller.upload(jpeg("id.jpg"), "NATIONAL_ID_FRONT", SELLER_AUTH);

        String objectKey = (String) res.getBody().get("objectKey");
        org.mockito.Mockito.verify(documentMetadataService)
                .recordUpload(org.mockito.ArgumentMatchers.eq(objectKey),
                        org.mockito.ArgumentMatchers.eq("national_id_front"),
                        org.mockito.ArgumentMatchers.eq(SELLER_AUTH),
                        anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void upload_lowercasesCategoryInStoredPath() {
        var res = controller.upload(jpeg("id.jpg"), "National_ID_Front", SELLER_AUTH);

        String objectKey = (String) res.getBody().get("objectKey");
        assertTrue(objectKey.startsWith("documents/national_id_front/"), objectKey);
    }

    private HttpServletRequest requestFor(String pathWithinHandler) {
        return requestFor(pathWithinHandler, false);
    }

    private HttpServletRequest requestFor(String pathWithinHandler, boolean internal) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE))
                .thenReturn(pathWithinHandler);
        when(request.getRequestURI()).thenReturn(internal ? "/api/documents/internal" + pathWithinHandler
                : "/api/documents" + pathWithinHandler);
        return request;
    }

    @Test
    void serveFile_returnsStoredFile_forOwner() {
        var uploadRes = controller.upload(jpeg("id.jpg"), "NATIONAL_ID_FRONT", SELLER_AUTH);
        String objectKey = (String) uploadRes.getBody().get("objectKey");
        when(documentMetadataService.canAccess(objectKey, SELLER_AUTH)).thenReturn(true);

        ResponseEntity<Resource> res = controller.serveFile(requestFor("/files/" + objectKey), SELLER_AUTH);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertTrue(res.getBody().exists());
    }

    @Test
    void serveFile_returns404_forNonOwnerNonAdmin() {
        var uploadRes = controller.upload(jpeg("id.jpg"), "NATIONAL_ID_FRONT", SELLER_AUTH);
        String objectKey = (String) uploadRes.getBody().get("objectKey");

        Authentication otherUser = new UsernamePasswordAuthenticationToken("buyer@smartre.co.ke", null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
        when(documentMetadataService.canAccess(objectKey, otherUser)).thenReturn(false);

        ResponseEntity<Resource> res = controller.serveFile(requestFor("/files/" + objectKey), otherUser);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    @Test
    void serveFile_allowsAdmin_regardlessOfOwnership() {
        var uploadRes = controller.upload(jpeg("id.jpg"), "NATIONAL_ID_FRONT", SELLER_AUTH);
        String objectKey = (String) uploadRes.getBody().get("objectKey");

        Authentication admin = new UsernamePasswordAuthenticationToken("admin@smartre.co.ke", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(documentMetadataService.canAccess(objectKey, admin)).thenReturn(true);

        ResponseEntity<Resource> res = controller.serveFile(requestFor("/files/" + objectKey), admin);

        assertEquals(HttpStatus.OK, res.getStatusCode());
    }

    @Test
    void serveFile_skipsOwnershipCheck_forPublicPropertyImages() {
        var uploadRes = controller.upload(
                new MockMultipartFile("file", "listing.jpg", "image/jpeg", JPEG_BYTES),
                "PROPERTY_IMAGE", SELLER_AUTH);
        String objectKey = (String) uploadRes.getBody().get("objectKey");

        // No stubbing of documentMetadataService.canAccess at all: if the controller called it
        // for a public category, Mockito's default (false) would 404 this and fail the test.
        ResponseEntity<Resource> res = controller.serveFile(requestFor("/files/" + objectKey), null);

        assertEquals(HttpStatus.OK, res.getStatusCode());
    }

    @Test
    void serveFile_skipsOwnershipCheck_forInternalServiceCalls() {
        var uploadRes = controller.upload(jpeg("id.jpg"), "NATIONAL_ID_FRONT", SELLER_AUTH);
        String objectKey = (String) uploadRes.getBody().get("objectKey");

        // Internal service-to-service calls are authenticated via InternalSecretFilter, not a
        // user JWT, so Authentication is null here — must not be denied for that reason.
        ResponseEntity<Resource> res = controller.serveFile(
                requestFor("/files/" + objectKey, true), null);

        assertEquals(HttpStatus.OK, res.getStatusCode());
    }

    @Test
    void serveFile_returns404_forPathTraversalAttempt() {

        String traversal = "/files/" + "../".repeat(12) + "etc/passwd";
        when(documentMetadataService.canAccess(any(), any())).thenReturn(true);

        ResponseEntity<Resource> res = controller.serveFile(requestFor(traversal), SELLER_AUTH);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    @Test
    void serveFile_returns404_forNonExistentFile() {
        when(documentMetadataService.canAccess(any(), any())).thenReturn(true);

        ResponseEntity<Resource> res = controller.serveFile(
                requestFor("/files/documents/national_id_front/does-not-exist.jpg"), SELLER_AUTH);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }
}
