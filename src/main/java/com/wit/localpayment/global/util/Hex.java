/* 
 * Copyright (c) WIT Global 
 */
package com.wit.localpayment.global.util;

public final class Hex {

  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  private Hex() {}

  public static String toHex(byte[] b) {
    if (b == null) {
      return "";
    }
    char[] out = new char[b.length * 2];
    for (int i = 0; i < b.length; i++) {
      int v = b[i] & 0xFF;
      out[i * 2] = HEX[v >>> 4];
      out[i * 2 + 1] = HEX[v & 0x0F];
    }
    return new String(out);
  }
}
