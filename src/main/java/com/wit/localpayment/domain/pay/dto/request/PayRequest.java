package com.wit.localpayment.domain.pay.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장바구니 결제 요청 DTO")
public record PayRequest(
    @Schema(description = "결제할 상품 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty @Valid
    List<CartItemRequest> items,
    @Schema(description = "프론트에서 계산된 총 금액(원 단위)", example = "10")
    @NotNull @Min(1)
    Long totalAmount,
    @Schema(description = "고객 전화번호 (하이픈 없는 10~11자리)", example = "01012345678")
    @NotBlank
    @Pattern(regexp = "\\d{10,11}")
    String phoneNumber,
    @Schema(description = "할부개월 (00 = 일시불, 02,03...)", example = "00")
    @NotBlank
    @Pattern(regexp = "\\d{2}")
    String inst,
    @Schema(description = "키오스크로 촬영한 이미지 URL", example = "https://bucket.s3....")
    String imageUrl,
    @Schema(description = "배송 여부 (true = 배송, false = 현장수령)", example = "true")
    boolean delivery
) {}