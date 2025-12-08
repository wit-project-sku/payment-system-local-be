package com.wit.localpayment.domain.pay.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로컬 → 중앙 서버 결제 성공 보고 DTO")
public record PaySuccessReportRequest(
    @Schema(description = "키오스크에서 받은 원본 결제 요청") PayRequest payRequest,
    @Schema(description = "실제 승인된 금액(원 단위)") long approvedAmount,
    @Schema(description = "TL3800 응답 코드 (성공=0)") int respCode,
    @Schema(description = "TL 전체 응답 패킷 HEX 문자열") String tlPacketHex
) {

}