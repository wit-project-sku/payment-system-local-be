/* 
 * Copyright (c) WIT Global 
 */
package com.wit.localpayment.global.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "결제 취소 요청 DTO")
public record CancelRequest(
    @Schema(description = "취소 구분 코드. 2는 직전 거래 취소 (1~6)", example = "2")
        @NotBlank
        @Pattern(regexp = "[1-6]")
        String cancelType,
    @Schema(description = "거래 구분 코드 (1,2,3,4,5,6,8)", example = "1")
        @NotBlank
        @Pattern(regexp = "[1234568]")
        String tranType,
    @Schema(description = "취소 금액 (원). 1~10자리 정수", example = "10")
        @NotBlank
        @Pattern(regexp = "\\d{1,10}")
        String amount,
    @Schema(description = "부가세. 1~8자리 정수", example = "0") @NotBlank @Pattern(regexp = "\\d{1,8}")
        String tax,
    @Schema(description = "봉사료. 1~8자리 정수", example = "0") @NotBlank @Pattern(regexp = "\\d{1,8}")
        String svc,
    @Schema(description = "할부개월. 2자리 (00 = 일시불)", example = "00")
        @NotBlank
        @Pattern(regexp = "\\d{2}")
        String inst,
    @Schema(description = "비서명 여부 (true=비서명, false=서명)", example = "true") boolean noSign,
    @Schema(description = "원 승인번호 (최대 12자)", example = "03304901") @NotBlank @Size(max = 12)
        String approvalNo,
    @Schema(description = "원승인일자 (YYYYMMDD 형식)", example = "20251203")
        @NotBlank
        @Pattern(regexp = "\\d{8}")
        String orgDate,
    @Schema(description = "원승인시간 (hhmmss 형식)", example = "185306")
        @NotBlank
        @Pattern(regexp = "\\d{6}")
        String orgTime,
    @Schema(description = "부가 정보 (선택)", example = "") @Size(max = 200) String extra) {}
