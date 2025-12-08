/* 
 * Copyright (c) WIT Global 
 */
package com.wit.localpayment.global.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "결제 승인 요청 DTO")
public record ApproveRequest(
    @Schema(description = "결제 금액(원 단위). 1~10 자리 정수", example = "10")
        @NotBlank
        @Pattern(regexp = "\\d{1,10}")
        String amount,
    @Schema(description = "부가세. 1~8자리 정수.", example = "0") @NotBlank @Pattern(regexp = "\\d{1,8}")
        String tax,
    @Schema(description = "봉사료. 1~8자리 정수.", example = "0") @NotBlank @Pattern(regexp = "\\d{1,8}")
        String svc,
    @Schema(description = "할부개월. 2자리 정수 형식 (00 = 일시불)", example = "00")
        @NotBlank
        @Pattern(regexp = "\\d{2}")
        String inst,
    @Schema(description = "비서명 여부 (true = 무서명, false = 서명)", example = "true") boolean noSign) {}
