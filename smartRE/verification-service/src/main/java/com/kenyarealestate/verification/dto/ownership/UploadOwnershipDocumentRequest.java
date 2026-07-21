package com.kenyarealestate.verification.dto.ownership;

import com.kenyarealestate.verification.enums.OwnershipDocumentCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UploadOwnershipDocumentRequest {
    @NotNull  private OwnershipDocumentCategory documentCategory;
    @NotBlank private String documentUrl;
    private Long fileSizeBytes;
    private String mimeType;
}
