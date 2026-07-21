package com.kenyarealestate.verification.dto.identity;

import com.kenyarealestate.verification.enums.IdentityDocumentCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UploadIdentityDocumentRequest {
    @NotNull  private IdentityDocumentCategory documentCategory;
    @NotBlank private String documentUrl;
    private Long fileSizeBytes;
    private String mimeType;
}
