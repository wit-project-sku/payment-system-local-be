package com.wit.localpayment.domain.pay.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로컬 → 중앙 서버 결제 실패/예외 보고 DTO")
public record PayFailureReportRequest(
    @Schema(description = "키오스크에서 받은 원본 결제 요청") PayRequest payRequest,
    @Schema(description = "요청 금액(원 단위)") long requestedAmount,
    @Schema(description = "TL3800 응답 코드 (없으면 null)") Integer respCode,
    @Schema(description = "실패/예외 사유 메시지") String reason,
    @Schema(description = "가능한 경우 TL 응답 패킷 HEX 문자열") String tlPacketHex
) {

}