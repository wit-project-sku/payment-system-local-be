package com.wit.localpayment.global.proto;

import java.nio.charset.Charset;
import java.util.Arrays;

public record TLRejectInfo(
    String code,        // 2자리 응답코드 (예: "6B", "0A")
    String message,     // 37바이트 한글 메세지 trim
    String raw          // 전체 원문 ("-6B네트워크 오류!!\r재거래 요청")
) {

  private static final int REJECT_FIELD_LEN = 40;

  public static TLRejectInfo from(TLPacket packet) {
    byte[] data = packet.data;
    if (data == null || data.length < REJECT_FIELD_LEN) {
      return null; // 거절 메세지 필드가 없는 응답
    }

    // 스펙상 맨 끝 40바이트가 "거래 거절 응답메세지"라고 가정
    byte[] field = Arrays.copyOfRange(data, data.length - REJECT_FIELD_LEN, data.length);

    // EUC-KR 디코딩
    String raw = new String(field, Charset.forName("EUC-KR")).trim();

    if (raw.length() < 3 || raw.charAt(0) != '-') {
      // 기대 포맷이 아니면 원문만 보존
      return new TLRejectInfo(null, raw, raw);
    }

    String code = raw.substring(1, 3);      // 2 문자 응답코드
    String msg = raw.substring(3).trim();   // 나머지 = 한글 메세지

    return new TLRejectInfo(code, msg, raw);
  }
}