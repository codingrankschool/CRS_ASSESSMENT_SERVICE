package com.codingrank.assessment.dto;

import com.codingrank.assessment.common.enums.AssessmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LaunchTokenDto {
    private String userId;
    private String email;
    private String name;
    private String role;
    private String courseId;
    private String batchId;
    private String assessmentRefId;
    private AssessmentType assessmentType;
    private String returnUrl;
}
