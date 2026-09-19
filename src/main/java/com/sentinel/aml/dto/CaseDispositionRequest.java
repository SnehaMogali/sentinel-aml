package com.sentinel.aml.dto;

import com.sentinel.aml.entity.CaseDispositionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CaseDispositionRequest {

    @NotNull
    private CaseDispositionStatus dispositionStatus;

    @NotBlank
    private String dispositionReason;

    @NotBlank
    private String analystId;
}
