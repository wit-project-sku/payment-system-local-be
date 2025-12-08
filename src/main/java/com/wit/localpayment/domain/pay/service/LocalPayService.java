package com.wit.localpayment.domain.pay.service;

import com.wit.localpayment.domain.pay.client.CentralPayClient;
import com.wit.localpayment.domain.pay.dto.request.PayFailureReportRequest;
import com.wit.localpayment.domain.pay.dto.request.PayRequest;
import com.wit.localpayment.domain.pay.dto.request.PaySuccessReportRequest;
import com.wit.localpayment.domain.pay.dto.response.PayResponse;
import com.wit.localpayment.global.TL3800Gateway;
import com.wit.localpayment.global.proto.TLPacket;
import com.wit.localpayment.global.util.Hex;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalPayService {

  private final TL3800Gateway tl3800Gateway;
  private final CentralPayClient centralPayClient;

  /**
   * 요청 → 단말 승인 → 결과 보고(성공/실패) → 표준화된 상태 메시지 반환
   */
  public PayResponse pay(PayRequest request) {
    log.info("[LOCAL PAY] 결제 요청 수신 - items={}, totalAmount={}", request.items(),
        request.totalAmount());

    final long amount = request.totalAmount();
    final String amountStr = String.valueOf(amount);

    try {
      // 1) TL3800 승인 요청
      TLPacket resp = tl3800Gateway.approve(amountStr, "0", "0", request.inst(), true);
      String packetHex = Hex.toHex(resp.toBytes());
      log.debug("[LOCAL PAY] 단말 응답 hex={}", packetHex);

      // 2) 성공/실패 판정: responseCode == 0 이면 성공
      if (isApproved(resp)) {
        // 중앙 서버 성공 보고
        PaySuccessReportRequest report = new PaySuccessReportRequest(request, amount, 0, packetHex);
        centralPayClient.notifySuccess(report);

        return new PayResponse(true, "결제완료");
      }

      // 3) 실패: 거절코드/메시지 추출 후 표준 상태로 매핑
      String rejectCode = extractRejectCode(resp); // e.g. "6B", "0A" 등 (없으면 null)
      String status = mapStatus(rejectCode);

      PayFailureReportRequest report =
          new PayFailureReportRequest(request, amount, rejectCode, status, packetHex);
      centralPayClient.notifyFailure(report);

      return new PayResponse(false, status);

    } catch (Exception ex) {
      log.warn("[LOCAL PAY] 예외 발생 - {}", ex.toString());

      // 통신/프로토콜 예외 계열은 일괄 '통신보류'로 표준화
      PayFailureReportRequest report =
          new PayFailureReportRequest(request, amount, null, "통신오류", null);
      centralPayClient.notifyFailure(report);

      return new PayResponse(false, "통신오류");
    }
  }

  /**
   * 승인 성공 여부: TL 프로토콜 상 responseCode == 0 을 성공으로 간주.
   */
  private boolean isApproved(TLPacket resp) {
    // TLPacket가 public 필드인 경우
    try {
      return resp.responseCode == 0;
    } catch (Throwable ignore) {
      // 게터가 있는 경우
      try {
        return resp.getResponseCode() == 0;
      } catch (Throwable t) {
        // 정보가 없으면 실패로 간주
        return false;
      }
    }
  }

  /**
   * 실패 시 데이터의 ASCII에서 마지막 '-' 이후를 거절코드 후보로 추출. 코드가 2자리(예: 6B, 0A, 0C...)로 오는 케이스를 우선 처리.
   */
  private String extractRejectCode(TLPacket resp) {
    byte[] data;
    try {
      data = resp.data; // public 필드
    } catch (Throwable ignore) {
      try {
        data = resp.getData(); // 게터
      } catch (Throwable t) {
        return null;
      }
    }
    if (data == null || data.length == 0) {
      return null;
    }

    // 단말 데이터는 ASCII/KS 계열 혼재 가능하나, 코드 영역은 ASCII라 가정
    String ascii = new String(data, StandardCharsets.US_ASCII).trim();
    int dash = ascii.lastIndexOf('-');
    if (dash < 0 || dash == ascii.length() - 1) {
      return null;
    }
    String tail = ascii.substring(dash + 1).trim();

    // 앞쪽에서 2~4자리 영숫자(특히 HEX 0-9A-F)만 코드로 간주
    StringBuilder code = new StringBuilder();
    for (int i = 0; i < tail.length(); i++) {
      char c = tail.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        code.append(Character.toUpperCase(c));
        if (code.length() >= 4) {
          break;
        }
      } else {
        break;
      }
    }
    if (code.length() >= 2) {
      return code.substring(0, 2); // 프로토콜상 2자리 우선
    }
    return null;
  }

  /**
   * 거절코드를 표준 상태로 매핑. - 6B: 잔액 부족 → "잔액부족" - 0A/0C 등 통신/지연 → "통신오류" - 그 외 일반 실패도 일괄 "통신보류" (표준화된 3상태
   * 유지)
   */
  private String mapStatus(String rejectCode) {
    if (rejectCode == null || rejectCode.isBlank()) {
      return "통신오류";
    }
    return switch (rejectCode) {
      case "6B" -> "잔액부족";    // 선불 잔액부족
      case "0A", "0C" -> "통신오류"; // 네트워크/지연
      default -> "통신오류";
    };
  }
}