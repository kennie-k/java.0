package com.kenyarealestate.verification.dto.ownership;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DocumentRequirementResponse {
    private String documentCategory;
    private Boolean isMandatory;
    private String description;
    private String kenyaLawRef;
    private Boolean uploaded;
}
