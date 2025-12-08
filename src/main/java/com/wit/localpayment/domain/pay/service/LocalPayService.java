package com.wit.localpayment.domain.pay.service;

import com.wit.localpayment.domain.pay.client.CentralPayClient;
import com.wit.localpayment.domain.pay.dto.request.PayFailureReportRequest;
import com.wit.localpayment.domain.pay.dto.request.PayRequest;
import com.wit.localpayment.domain.pay.dto.request.PaySuccessReportRequest;
import com.wit.localpayment.domain.pay.dto.response.PayResponse;
import com.wit.localpayment.global.TL3800Gateway;
import com.wit.localpayment.global.proto.TLPacket;
import com.wit.localpayment.global.util.Hex;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalPayService {

  private final TL3800Gateway tl3800Gateway;
  private final CentralPayClient centralPayClient;

  public PayResponse pay(PayRequest request) {
    log.info(
        "[LOCAL PAY] 결제 요청 수신 - items={}, totalAmount={}, inst={}, delivery={}",
        request.items(),
        request.totalAmount(),
        request.inst(),
        request.delivery());

    long amount = request.totalAmount();
    String amountStr = String.valueOf(amount);

    try {
      // 1. TL3800 승인 요청
      log.info("[LOCAL PAY] TL3800 승인 요청 시작 - amount={}, inst={}", amountStr, request.inst());

      TLPacket resp = tl3800Gateway.approve(amountStr, "0", "0", request.inst(), true);
      int respCode = Byte.toUnsignedInt(resp.responseCode);
      String packetHex = Hex.toHex(resp.toBytes());

      log.info(
          "[LOCAL PAY] TL3800 승인 응답 수신 - jobCode={}, responseCode={}",
          resp.jobCode,
          respCode);

      if (respCode == 0) {
        // 2-1. 단말 기준 성공 → 중앙 서버에 성공 보고
        PaySuccessReportRequest report =
            new PaySuccessReportRequest(request, amount, respCode, packetHex);
        centralPayClient.notifySuccess(report);

        // 3. 키오스크에 성공 응답
        return new PayResponse(true, "결제가 완료되었습니다.");
      }

      // 2-2. 단말 응답 코드 != 0 → 실패
      String reason = "[단말 거절] 응답코드=" + respCode;
      PayFailureReportRequest report =
          new PayFailureReportRequest(request, amount, respCode, reason, packetHex);

      centralPayClient.notifyFailure(report);
      log.warn("[LOCAL PAY] 단말 거절 - respCode={}", respCode);

      return new PayResponse(false, "단말기 승인 거절: 응답코드=" + respCode);

    } catch (Exception ex) {
      log.error("[LOCAL PAY] 단말/통신 처리 중 예외 발생", ex);

      String reason =
          "[예외] " + ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());

      PayFailureReportRequest report =
          new PayFailureReportRequest(request, amount, null, reason, null);

      centralPayClient.notifyFailure(report);

      return new PayResponse(false, "결제 처리 중 오류가 발생했습니다.");
    }
  }
}