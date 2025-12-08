package com.wit.localpayment.domain.pay.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로컬 결제 응답 DTO")
public record PayResponse(
    @Schema(description = "성공 여부") boolean success,
    @Schema(description = "상태 (결제완료/잔액부족/통신보류)") String message
) {

}