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

  private static final int OFF_TXN_TYPE = 0;   // 거래구분코드(1B)

  private final TL3800Gateway tl3800Gateway;
  private final CentralPayClient centralPayClient;

  /**
   * 성공 기준(최소화): - 거래구분코드 != 'X' - 헤더 responseCode == 0 그 외 전부 "통신오류"
   */
  public PayResponse pay(PayRequest request) {
    log.info("[LOCAL PAY] 결제 요청 수신 - items={}, totalAmount={}",
        request.items(), request.totalAmount());

    final long amount = request.totalAmount();
    final String amountStr = String.valueOf(amount);

    try {
      // 1) TL3800 승인 요청
      TLPacket resp = tl3800Gateway.approve(amountStr, "0", "0", request.inst(), true);
      String packetHex = Hex.toHex(resp.toBytes());
      log.debug("[LOCAL PAY] 단말 응답 HEX={}", packetHex);

      // 2) 거래구분코드 + 헤더 응답코드로만 최종 판정
      if (approvedByTxnTypeAndHeader(resp)) {
        PaySuccessReportRequest report = new PaySuccessReportRequest(request, amount, 0, packetHex);
        safeNotifySuccess(report);
        return new PayResponse(true, "결제완료");
      }

      // 실패(통신오류)로 통일
      String reason = deriveReason(resp);
      PayFailureReportRequest report =
          new PayFailureReportRequest(request, amount, reason, "통신오류", packetHex);
      safeNotifyFailure(report);
      return new PayResponse(false, "통신오류");

    } catch (Exception ex) {
      log.warn("[LOCAL PAY] 예외 발생 - {}", ex.toString());
      PayFailureReportRequest report =
          new PayFailureReportRequest(request, amount, "EX", "통신오류", null);
      safeNotifyFailure(report);
      return new PayResponse(false, "통신오류");
    }
  }

  /* ================= 내부 유틸 ================= */

  /**
   * 최종 승인 판정: (거래구분코드 != 'X') && (header.responseCode == 0)
   */
  private boolean approvedByTxnTypeAndHeader(TLPacket resp) {
    byte[] data = getDataBytes(resp);
    if (data == null || data.length <= OFF_TXN_TYPE) {
      log.debug("[LOCAL PAY] data missing for txnType");
      return false;
    }
    char txnType = ascii(data, OFF_TXN_TYPE, 1).toUpperCase().charAt(0);
    if (txnType == 'X') {
      log.debug("[LOCAL PAY] txnType=X (거래거절)");
      return false;
    }
    boolean headerOk = isHeaderApproved(resp);
    if (!headerOk) {
      log.debug("[LOCAL PAY] header responseCode != 0");
    }
    return headerOk;
  }

  /**
   * 리포팅용 간단 실패 사유: X(거래거절), HDR(헤더 비정상), NO_DATA(데이터 없음), UNK(불명)
   */
  private String deriveReason(TLPacket resp) {
    byte[] data = getDataBytes(resp);
    if (data == null || data.length <= OFF_TXN_TYPE) {
      return "NO_DATA";
    }
    char txnType = ascii(data, OFF_TXN_TYPE, 1).toUpperCase().charAt(0);
    if (txnType == 'X') {
      return "X";
    }
    if (!isHeaderApproved(resp)) {
      return "HDR";
    }
    return "UNK";
  }

  /**
   * 헤더 responseCode == 0
   */
  private boolean isHeaderApproved(TLPacket resp) {
    try {
      return resp.responseCode == 0;   // public 필드
    } catch (Throwable ignore) {
      try {
        return resp.getResponseCode() == 0; // 게터
      } catch (Throwable t) {
        return false;
      }
    }
  }

  private byte[] getDataBytes(TLPacket resp) {
    try {
      return resp.data;
    } catch (Throwable ignore) {
      try {
        return resp.getData();
      } catch (Throwable t) {
        return null;
      }
    }
  }

  private String ascii(byte[] b, int off, int len) {
    if (b == null || off < 0 || len <= 0 || off + len > b.length) {
      return "";
    }
    return new String(b, off, len, StandardCharsets.US_ASCII).trim();
  }

  private void safeNotifySuccess(PaySuccessReportRequest report) {
    try {
      centralPayClient.notifySuccess(report);
    } catch (Exception ignore) {
    }
  }

  private void safeNotifyFailure(PayFailureReportRequest report) {
    try {
      centralPayClient.notifyFailure(report);
    } catch (Exception ignore) {
    }
  }
}