/*
 * Copyright (c) WIT Global
 */
package com.wit.localpayment.global.proto;


import static com.wit.localpayment.global.proto.Proto.CATMID_LEN;
import static com.wit.localpayment.global.proto.Proto.DATETIME_LEN;
import static com.wit.localpayment.global.proto.Proto.ETX;
import static com.wit.localpayment.global.proto.Proto.HEADER_BYTES;
import static com.wit.localpayment.global.proto.Proto.STX;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import lombok.Getter;

@Getter
public final class TLPacket {

  // Header
  public final String catOrMid; // 16 ASCII, 좌측정렬, 우측 0x00 패딩
  public final String dateTime14; // 14 ASCII (YYYYMMDDhhmmss)
  public final JobCode jobCode; // 1 byte
  public final byte responseCode; // 1 byte, 요청 시 항상 0x00
  public final byte[] data; // 가변
  // Tail
  public final byte etx; // 0x03

  // 고정 오프셋(프로토콜 기준)
  private static final int POS_STX = 0; // 0x02
  private static final int POS_ID = 1; // 16 bytes
  private static final int POS_DT = POS_ID + 16; // 14 bytes
  private static final int POS_JOB = POS_DT + 14; // 1 byte
  private static final int POS_RSP = POS_JOB + 1; // 1 byte
  private static final int POS_LEN = POS_RSP + 1; // 2 bytes (little-endian)

  private TLPacket(
      String catOrMid, String dateTime14, JobCode jobCode, byte responseCode, byte[] data) {
    this.catOrMid = catOrMid;
    this.dateTime14 = dateTime14;
    this.jobCode = jobCode;
    this.responseCode = responseCode;
    this.data = (data == null) ? new byte[0] : data;
    this.etx = ETX;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private String catOrMid;
    private String dateTime14 = Proto.nowYYYYMMDDhhmmss();
    private JobCode jobCode;
    private byte responseCode = 0x00;
    private byte[] data = new byte[0];

    public Builder catOrMid(String v) {
      this.catOrMid = v;
      return this;
    }

    public Builder dateTime14(String v) {
      this.dateTime14 = v;
      return this;
    }

    public Builder jobCode(JobCode v) {
      this.jobCode = v;
      return this;
    }

    public Builder responseCode(byte v) {
      this.responseCode = v;
      return this;
    }

    public Builder data(byte[] v) {
      this.data = v;
      return this;
    }

    public TLPacket build() {
      if (catOrMid == null || jobCode == null) {
        throw new IllegalStateException("catOrMid and jobCode are required");
      }
      if (dateTime14 == null || dateTime14.length() != DATETIME_LEN) {
        throw new IllegalStateException("dateTime must be 14 chars (YYYYMMDDhhmmss)");
      }
      return new TLPacket(catOrMid, dateTime14, jobCode, responseCode, data);
    }
  }

  /**
   * 인스턴스 직렬화(호출자: req.toBytes()). 내부적으로 정규 build를 사용합니다.
   */
  public byte[] toBytes() {
    return build(
        this.catOrMid,
        this.dateTime14,
        this.jobCode.code, // JobCode의 코드 값
        this.responseCode & 0xFF, // 부호 확장 방지
        this.data);
  }

  /**
   * 프레임 직렬화(STX~ETX+BCC 포함). ID는 좌정렬로 복사되고 남는 자리는 0x00으로 자연 패딩됩니다.
   */
  public static byte[] build(String id, String dateTime14, int job, int resp, byte[] dataLE) {
    if (dateTime14 == null || dateTime14.length() != 14) {
      throw new IllegalArgumentException("dateTime14 must be 14 chars (YYYYMMDDhhmmss)");
    }
    if (dataLE == null) {
      dataLE = new byte[0];
    }

    // 총 길이 = 헤더(35) + 데이터 + ETX(1) + BCC(1)
    byte[] out = new byte[HEADER_BYTES + dataLE.length + 2];

    int i = 0;
    out[i++] = STX;

    // ID (16B, 좌정렬, 0x00 패딩)
    byte[] idBytes = id.getBytes(StandardCharsets.US_ASCII);
    int idLen = Math.min(idBytes.length, CATMID_LEN);
    System.arraycopy(idBytes, 0, out, i, idLen);
    i += CATMID_LEN;

    // DateTime (14B, ASCII)
    byte[] dt = dateTime14.getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(dt, 0, out, i, DATETIME_LEN);
    i += DATETIME_LEN;

    out[i++] = (byte) (job & 0xFF);
    out[i++] = (byte) (resp & 0xFF);

    // DataLength 2B (LE)
    int dl = dataLE.length;
    out[i++] = (byte) (dl & 0xFF);
    out[i++] = (byte) ((dl >>> 8) & 0xFF);

    // Data
    System.arraycopy(dataLE, 0, out, i, dl);
    i += dl;

    // ETX
    out[i++] = ETX;

    // BCC (STX~ETX 포함, BCC 제외)
    byte bcc = Proto.bccXor(out, 0, i - 1);
    out[i++] = bcc;

    return out;
  }

  /**
   * 기본 파서는 strict 모드로 동작. (STX/ETX/BCC 모두 검증 실패 시 예외) 필요한 곳에서는 parseStrict/parseLenient를 직접 호출해도
   * 된다.
   */
  public static TLPacket parse(byte[] frame) {
    return parseStrict(frame);
  }

  /**
   * 프레임 역직렬화(유효성: STX/ETX/BCC/길이). 헤더의 DataLength(LE) 기준으로 ETX/BCC 위치를 계산하여 엄격하게 검증한다.
   */
  public static TLPacket parseStrict(byte[] frame) {
    if (frame == null || frame.length < HEADER_BYTES + 2) {
      throw new IllegalArgumentException("short frame: len=" + (frame == null ? -1 : frame.length));
    }
    if ((frame[POS_STX] & 0xFF) != (STX & 0xFF)) {
      throw new IllegalArgumentException(String.format("STX mismatch: %02X", frame[POS_STX]));
    }

    // 1) 헤더 필드 파싱
    int p = POS_ID;
    byte[] idBytes = Arrays.copyOfRange(frame, p, p + CATMID_LEN);
    p += CATMID_LEN;
    byte[] dtBytes = Arrays.copyOfRange(frame, p, p + DATETIME_LEN);
    p += DATETIME_LEN;
    JobCode job = JobCode.of(frame[p++]);
    byte resp = frame[p++];

    // DataLength (LE)
    int dataLen = (frame[p++] & 0xFF) | ((frame[p++] & 0xFF) << 8);

    // 2) ETX/BCC 위치 계산
    int posEtx = HEADER_BYTES + dataLen; // 데이터 바로 뒤
    int posBcc = posEtx + 1;
    int expectedTotal = posBcc + 1;

    if (frame.length < expectedTotal) {
      throw new IllegalArgumentException(
          String.format(
              "incomplete frame: len=%d, expected=%d (dataLen=%d)",
              frame.length, expectedTotal, dataLen));
    }
    if ((frame[posEtx] & 0xFF) != (ETX & 0xFF)) {
      throw new IllegalArgumentException(
          String.format(
              "ETX mismatch at %d: %02X (dataLen=%d, headerLen=%d)",
              posEtx, frame[posEtx], dataLen, HEADER_BYTES));
    }

    // 3) BCC 검증 (STX~ETX 포함)
    byte calcBcc = Proto.bccXor(frame, POS_STX, posEtx);
    int recvBcc = frame[posBcc] & 0xFF;
    if ((calcBcc & 0xFF) != recvBcc) {
      throw new IllegalArgumentException(
          String.format(
              "BCC mismatch (calc=%02X, recv=%02X, etxPos=%d, dataLen=%d, total=%d, buf.len=%d)",
              calcBcc & 0xFF, recvBcc, posEtx, dataLen, expectedTotal, frame.length));
    }

    // 4) 본문 추출
    byte[] data = Arrays.copyOfRange(frame, HEADER_BYTES, HEADER_BYTES + dataLen);

    // 5) 문자열 필드 정리(우측 0x00 패딩 제거)
    String catStr = Proto.printableOrHex(idBytes);
    String dtStr = new String(dtBytes, StandardCharsets.US_ASCII);

    return new TLPacket(catStr, dtStr, job, resp, data);
  }

  /**
   * lenient 파서: 헤더/길이는 신뢰하되, ETX/BCC는 "있으면 좋고, 안 맞으면 무시"한다. 프레임 경계가 다소 이상하거나, 벤더 구현이 BCC 규칙을 안 지키는
   * 경우 디버깅/우회용으로 사용.
   */
  public static TLPacket parseLenient(byte[] frame) {
    if (frame == null || frame.length < HEADER_BYTES + 2) {
      throw new IllegalArgumentException("short frame: len=" + (frame == null ? -1 : frame.length));
    }
    if ((frame[POS_STX] & 0xFF) != (STX & 0xFF)) {
      throw new IllegalArgumentException(String.format("STX mismatch: %02X", frame[POS_STX]));
    }

    // 1) 헤더 필드 파싱 (strict와 동일)
    int p = POS_ID;
    byte[] idBytes = Arrays.copyOfRange(frame, p, p + CATMID_LEN);
    p += CATMID_LEN;
    byte[] dtBytes = Arrays.copyOfRange(frame, p, p + DATETIME_LEN);
    p += DATETIME_LEN;
    JobCode job = JobCode.of(frame[p++]);
    byte resp = frame[p++];

    // DataLength (LE)
    int dataLen = (frame[p++] & 0xFF) | ((frame[p++] & 0xFF) << 8);

    // 2) 실제 버퍼 길이를 고려하여 "실질적으로 읽을 수 있는 data 길이" 산정
    //    (마지막 2바이트는 ETX/BCC라고 가정하고 남겨두되, 값 일치 여부는 강제하지 않는다)
    int maxDataArea = Math.max(0, frame.length - HEADER_BYTES - 2);
    int actualDataLen = Math.min(Math.max(dataLen, 0), maxDataArea);

    if (actualDataLen < 0) {
      throw new IllegalArgumentException(
          "frame too short for header (dataLen=" + dataLen + ", buf.len=" + frame.length + ")");
    }

    // 3) 본문 추출 (헤더 기준 dataLen과 실제 버퍼 길이 중 작은 쪽만 사용)
    byte[] data = Arrays.copyOfRange(frame, HEADER_BYTES, HEADER_BYTES + actualDataLen);

    // 4) 문자열 필드 정리
    String catStr = Proto.printableOrHex(idBytes);
    String dtStr = new String(dtBytes, StandardCharsets.US_ASCII);

    return new TLPacket(catStr, dtStr, job, resp, data);
  }
}
