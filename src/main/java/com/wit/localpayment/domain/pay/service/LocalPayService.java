package com.wit.localpayment.domain.pay.service;

import com.wit.localpayment.domain.pay.client.CentralPayClient;
import com.wit.localpayment.domain.pay.dto.request.PayFailureReportRequest;
import com.wit.localpayment.domain.pay.dto.request.PayRequest;
import com.wit.localpayment.domain.pay.dto.request.PaySuccessReportRequest;
import com.wit.localpayment.domain.pay.dto.response.PayResponse;
import com.wit.localpayment.global.TL3800Gateway;
import com.wit.localpayment.global.proto.TL3800ApprovalInfo;
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
    log.info("[LOCAL PAY] 결제 요청 수신 - items={}, totalAmount={}", request.items(),
        request.totalAmount());

    long amount = request.totalAmount();
    String amountStr = String.valueOf(amount);

    try {
      // 1. 승인 요청
      System.out.println("[LOCAL PAY] 8");
      TLPacket resp = tl3800Gateway.approve(amountStr, "0", "0", request.inst(), true);
      System.out.println("[LOCAL PAY] 7");
      String packetHex = Hex.toHex(resp.toBytes());
      System.out.println("[LOCAL PAY] 5");
      // 2. 패킷 해석
      TL3800ApprovalInfo info = TL3800ApprovalInfo.fromHex(packetHex);
      System.out.println("[LOCAL PAY] 6");
      // ----------------------
      // (A) 승인 성공 판단
      // ----------------------
      if (info.isApproved()) {

        PaySuccessReportRequest report =
            new PaySuccessReportRequest(request, amount, 0, packetHex);

        centralPayClient.notifySuccess(report);

        return new PayResponse(true, "결제가 완료되었습니다.");
      }

      // ----------------------
      // (B) 승인 FAILURE (거절)
      // ----------------------
      String rejectCode = info.rejectCode();
      String rejectMsg = info.rejectMessage();
      System.out.println("[LOCAL PAY] 1");

      String userMessage = mapRejectMessage(rejectCode, rejectMsg);
      System.out.println("[LOCAL PAY] 2");

      PayFailureReportRequest report =
          new PayFailureReportRequest(request, amount, rejectCode, userMessage, packetHex);
      System.out.println("[LOCAL PAY] 3");

      centralPayClient.notifyFailure(report);
      System.out.println("[LOCAL PAY] 1");

      return new PayResponse(false, userMessage);

    } catch (Exception ex) {

      // ----------------------
      // (C) 단말/통신 오류
      // ----------------------
      String reason = "[예외] " + ex.getMessage();

      PayFailureReportRequest report =
          new PayFailureReportRequest(request, amount, null, reason, null);

      centralPayClient.notifyFailure(report);

      return new PayResponse(false, "통신 오류로 결제가 실패했습니다.");
    }
  }

  private String mapRejectMessage(String rejectCode, String rawMessage) {

    return switch (rejectCode) {

      case "6B" -> "잔액이 부족하여 결제할 수 없습니다.";  // 선불카드 잔액 부족
      case "0A" -> "네트워크 오류로 결제가 실패했습니다. 다시 시도해주세요.";
      case "0C" -> "서버 응답 지연으로 결제를 진행할 수 없습니다.";
      case "6D" -> "선불 사용할 수 없는 카드입니다.";
      case "69" -> "선불 미등록으로 사용할 수 없는 카드입니다.";
      case "71" -> "등록되지 않은 카드입니다.";
      case "6F" -> "카드를 다시 인식해주세요.";
      case "74" -> "결제 중 카드가 변경되었습니다.";

      default -> (rawMessage != null && !rawMessage.isBlank())
          ? rawMessage
          : "승인 오류가 발생했습니다. 다른 결제 수단을 이용해주세요.";
    };
  }
}