/* 
 * Copyright (c) WIT Global 
 */
package com.wit.localpayment.global.proto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

public final class Proto {

  private Proto() {}

  // 제어 코드
  public static final byte STX = 0x02;
  public static final byte ETX = 0x03;
  public static final byte ACK = 0x06;
  public static final byte NACK = 0x15;

  // 고정 길이
  public static final int HEADER_BYTES = 35; // STX~DataLen
  public static final int CATMID_LEN = 16;
  public static final int DATETIME_LEN = 14; // YYYYMMDDhhmmss

  public static byte[] asciiLeftPadZero(String s, int len) {
    byte[] dst = new byte[len];
    byte[] src = s == null ? new byte[0] : s.getBytes(StandardCharsets.US_ASCII);
    int copy = Math.min(src.length, len);
    int start = len - copy; // 우측 정렬
    for (int i = 0; i < start; i++) {
      dst[i] = '0';
    }
    System.arraycopy(src, 0, dst, start, copy);
    return dst;
  }

  /** 좌측 정렬, 우측 공백 패딩. */
  public static byte[] rpadSpaces(String s, int len) {
    byte[] dst = new byte[len];
    int copied = 0;
    if (s != null) {
      byte[] src = s.getBytes(StandardCharsets.US_ASCII);
      copied = Math.min(src.length, len);
      System.arraycopy(src, 0, dst, 0, copied);
    }
    for (int i = copied; i < len; i++) {
      dst[i] = 0x20;
    }
    return dst;
  }

  public static String nowYYYYMMDDhhmmss() {
    return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
  }

  public static String nowYYYYMMDD() {
    return LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
  }

  /** STX ~ ETX(포함)까지 XOR. */
  public static byte bccXor(byte[] frame, int from, int toInclusive) {
    byte x = 0x00;
    for (int i = from; i <= toInclusive; i++) {
      x ^= frame[i];
    }
    return x;
  }

  public static byte[] leUShort(int v) {
    ByteBuffer bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
    bb.putShort((short) (v & 0xFFFF));
    return bb.array();
  }

  public static int leUShortToInt(byte lo, byte hi) {
    return (ByteBuffer.wrap(new byte[] {lo, hi}).order(ByteOrder.LITTLE_ENDIAN).getShort())
        & 0xFFFF;
  }

  public static String printableOrHex(byte[] bytes) {
    if (bytes == null) {
      return "";
    }

    boolean printable = true;
    for (byte v : bytes) {
      int u = v & 0xFF;
      // 공백(0x20) 또는 출력 가능 ASCII(0x21~0x7E)만 허용
      if (!(u == 0x20 || (u >= 0x21 && u <= 0x7E))) {
        printable = false;
        break;
      }
    }
    if (printable) {
      String s = new String(bytes, StandardCharsets.US_ASCII);
      // 우측 0x00 패딩 제거 (안전상 한 번 더 제거)
      return s.replaceAll("\u0000+$", "");
    }
    // 비-ASCII가 섞이면 HEX(대문자)로 반환
    return HexFormat.of().withUpperCase().formatHex(bytes);
  }
}
