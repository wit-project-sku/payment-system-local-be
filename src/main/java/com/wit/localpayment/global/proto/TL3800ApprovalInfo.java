/*
 * Copyright (c) WIT Global
 */
package com.wit.localpayment.global.proto;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public record TL3800ApprovalInfo(

    // Header
    String terminalId,          // CAT/MID
    int responseCode,           // 헤더 응답코드 (0=성공)

    // 거래 속성
    String tranTypeCode,        // 거래구분코드
    String mediaType,           // 거래매체

    // 금액
    int approvedAmount,         // 승인금액(원거래금액+세금+봉사료)
    int vatAmount,              // 세금
    int svcAmount,              // 봉사료
    String installment,         // 할부개월

    // 승인정보
    String approvalNoRaw,       // 승인번호 원문 12자리
    String approvalNo,          // Trim된 승인번호 (거절이면 null)

    // 매출일시
    LocalDate approvedDate,     // YYYY-MM-DD
    LocalTime approvedTime,     // HH:mm:ss

    // 부가영역(raw)
    String vanExtraRaw,

    // 거절 정보 (성공 시 null)
    String rejectCode,          // 예: "6B"
    String rejectMessage        // 예: "카드 잔액 부족"
) {

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HHmmss");

  /**
   * 성공 여부
   */
  public boolean isApproved() {
    return rejectCode == null;
  }

  /**
   * 실패 여부
   */
  public boolean isRejected() {
    return rejectCode != null;
  }

  /**
   * TL 응답 전문(HEX) -> TL3800ApprovalInfo
   */
  public static TL3800ApprovalInfo fromHex(String hex) {

    TLPacket pkt = TLPacket.parse(hexToBytes(hex));
    byte[] data = pkt.getData();

    if (data.length < 56) {
      throw new IllegalArgumentException("TL3800 approval data too short: len=" + data.length);
    }

    // ------------------------------
    // 1) 거래속성/금액/할부
    //    (네가 써 둔 오프셋 그대로 사용)
    // ------------------------------
    String tranType = ascii(data, 0, 1);
    String mediaType = ascii(data, 1, 1);

    int approvedAmt = intVal(data, 2, 10);   // 승인금액
    int vatAmt = intVal(data, 12, 8);   // 세금
    int svcAmt = intVal(data, 20, 8);   // 봉사료
    String inst = ascii(data, 28, 2);    // 할부개월

    // ------------------------------
    // 2) 승인번호 OR 거절전문 앞 12B
    // ------------------------------
    String approvalOrReject12 = ascii(data, 30, 12);

    String approvalNo = null;
    String rejectCode = null;
    String rejectMsg = null;

    // ------------------------------
    // 3) 매출일시
    // ------------------------------
    System.out.println("[LOCAL PAY] HEX6");

// 패킷 분석 결과: YYYYMMDDhhmmss 는 data[38]부터 14바이트
    String dt = ascii(data, 38, 14); // 공백 패딩 없음, 14자리 그대로 들어옴

    if (dt.length() < 14) {
      throw new IllegalArgumentException("TL3800 approval datetime field too short: '" + dt + "'");
    }

    LocalDate approvedDate = LocalDate.parse(dt.substring(0, 8), DATE_FMT);
    LocalTime approvedTime = LocalTime.parse(dt.substring(8, 14), TIME_FMT);

// ------------------------------
// 4) 뒤쪽 extra data (거절메시지 포함 전체 RAW)
// ------------------------------
    System.out.println("[LOCAL PAY] HEX5");
    String vanExtra = ascii(data, 56, data.length - 56);

    // ------------------------------
    // 5) 마지막 40B → 거래거절 응답메시지
    //    "-"(1) + CODE(2) + MESSAGE(37, EUC-KR)
    // ------------------------------
    System.out.println("[LOCAL PAY] HEX1");
    if (data.length >= 40) {
      int off = data.length - 40;
      String msg40 = new String(data, off, 40, Charset.forName("EUC-KR")).trim();

      System.out.println("[LOCAL PAY] HEX2");
      if (msg40.startsWith("-") && msg40.length() >= 3) {
        rejectCode = msg40.substring(1, 3);
        rejectMsg = msg40.substring(3).trim();
      }
    }
    System.out.println("[LOCAL PAY] HEX3");
    // ------------------------------
    // 6) 승인/거절 구분
    // ------------------------------
    if (rejectCode == null) {
      // 정상 승인인 경우에만 승인번호 세팅
      approvalNo = approvalOrReject12.trim();
    }
    System.out.println("[LOCAL PAY] HEX4");
    return new TL3800ApprovalInfo(
        pkt.catOrMid,                          // terminalId
        Byte.toUnsignedInt(pkt.responseCode),  // header resp code

        tranType,
        mediaType,

        approvedAmt,
        vatAmt,
        svcAmt,
        inst,

        approvalOrReject12,
        approvalNo,

        approvedDate,
        approvedTime,

        vanExtra,

        rejectCode,
        rejectMsg
    );
  }

  /* ---------------------- 유틸 --------------------------- */

  private static String ascii(byte[] data, int off, int len) {
    if (off < 0 || len <= 0 || off >= data.length) {
      return "";
    }
    int safeLen = Math.min(len, data.length - off);
    return new String(data, off, safeLen, StandardCharsets.US_ASCII);
  }

  private static int intVal(byte[] data, int off, int len) {
    String s = ascii(data, off, len).trim();
    if (s.isEmpty()) {
      return 0;
    }
    try {
      return Integer.parseInt(s);
    } catch (Exception e) {
      return 0;
    }
  }

  private static byte[] hexToBytes(String hex) {
    return java.util.HexFormat.of().parseHex(hex);
  }
}