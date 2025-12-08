/* 
 * Copyright (c) WIT Global 
 */
package com.wit.localpayment.domain.pay.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "결제 요청 상품 항목")
public record CartItemRequest(
    @Schema(description = "상품 ID", example = "101") @NotNull Long productId,
    @Schema(description = "상품 개수", example = "2") @Min(1) int quantity) {}
