/* 
 * Copyright (c) WIT Global 
 */
package com.wit.localpayment.global.payload;

import static java.nio.charset.StandardCharsets.US_ASCII;

import com.wit.localpayment.global.proto.JobCode;
import com.wit.localpayment.global.proto.Proto;
import com.wit.localpayment.global.proto.TLPacket;
import java.nio.ByteBuffer;

public final class Requests {

  private final String terminalId;

  public Requests(String terminalId) {
    this.terminalId = terminalId;
  }

  // A: 장치체크 (Data 없음)
  public TLPacket deviceCheck() {
    return TLPacket.builder().catOrMid(terminalId).jobCode(JobCode.A).data(new byte[0]).build();
  }

  // B: 거래승인 (필수 30B + AuthNo(12, space) + D8(8) + 확장길이(2,"00") = 52B)
  public TLPacket approve(String amount, String tax, String svc, String inst, boolean noSign) {
    ByteBuffer bb = ByteBuffer.allocate(30);
    bb.put("1".getBytes(US_ASCII)); // 거래구분 1
    bb.put(Proto.asciiLeftPadZero(amount, 10)); // 금액(10)
    bb.put(Proto.asciiLeftPadZero(tax, 8)); // 부가세(8)
    bb.put(Proto.asciiLeftPadZero(svc, 8)); // 봉사료(8)
    bb.put(Proto.asciiLeftPadZero(inst, 2)); // 할부(2)
    bb.put((noSign ? "1" : "2").getBytes(US_ASCII)); // 서명여부(1)

    // ★ 꼭 position() 만큼만 잘라서 payload 만들기
    bb.flip();
    byte[] payload = new byte[bb.remaining()];
    bb.get(payload);

    return TLPacket.builder().catOrMid(terminalId).jobCode(JobCode.B).data(payload).build();
  }

  /**
   * 거래 취소 요청 (JobCode.C)
   *
   * <p>프로토콜: 취소구분코드(1) + 거래구분코드(1) + 승인금액(10) + 세금(8) + 봉사료(8) + 할부개월(2) + 서명여부(1) + 승인번호(12) +
   * 원거래일자(8) + 원거래시간(6) + 부가정보길이(2) + 부가정보(N) → DataLength = 57 or 57+2+N
   */
  public TLPacket cancel(
      String cancelType,
      String tranType,
      String amount,
      String tax,
      String svc,
      String inst,
      boolean noSign,
      String approvalNo,
      String orgDate,
      String orgTime,
      String extra) {

    String signFlag = noSign ? "1" : "2"; // “1”=비서명, “2”=서명

    String normAmount = lpad(amount, 10, '0');
    String normTax = lpad(tax, 8, '0');
    String normSvc = lpad(svc, 8, '0');
    String normInst = lpad(inst, 2, '0');

    String normApprovalNo = rpad(approvalNo, 12, ' '); // 좌측정렬, space 패딩

    String normExtra = (extra == null) ? "" : extra;
    int extraLen = normExtra.isEmpty() ? 0 : normExtra.length();
    String extraLenStr = lpad(String.valueOf(extraLen), 2, '0');

    StringBuilder sb = new StringBuilder(57 + (extraLen > 0 ? 2 + extraLen : 2));

    sb.append(cancelType); // 1
    sb.append(tranType); // 1
    sb.append(normAmount); // 10
    sb.append(normTax); // 8
    sb.append(normSvc); // 8
    sb.append(normInst); // 2
    sb.append(signFlag); // 1
    sb.append(normApprovalNo); // 12
    sb.append(orgDate); // 8
    sb.append(orgTime); // 6
    sb.append(extraLenStr); // 2
    if (extraLen > 0) {
      sb.append(normExtra); // N
    }

    byte[] data = sb.toString().getBytes(US_ASCII);

    return TLPacket.builder().catOrMid(terminalId).jobCode(JobCode.C).data(data).build();
  }

  // 간단한 padding 유틸
  private static String lpad(String src, int len, char pad) {
    if (src == null) {
      src = "";
    }
    if (src.length() >= len) {
      return src.substring(src.length() - len);
    }
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len - src.length(); i++) {
      sb.append(pad);
    }
    sb.append(src);
    return sb.toString();
  }

  private static String rpad(String src, int len, char pad) {
    if (src == null) {
      src = "";
    }
    if (src.length() >= len) {
      return src.substring(0, len);
    }
    StringBuilder sb = new StringBuilder(len);
    sb.append(src);
    for (int i = 0; i < len - src.length(); i++) {
      sb.append(pad);
    }
    return sb.toString();
  }
}
