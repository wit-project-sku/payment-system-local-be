/* 
 * Copyright (c) WIT Global 
 */
package com.wit.localpayment.global.transport;

public interface TLTransport extends AutoCloseable {

  void open() throws Exception;

  void close();

  /** 바이트 블록 전송 */
  void write(byte[] bytes) throws Exception;

  /** 지정 바이트 수만큼 차오를 때까지 읽기(타임아웃 ms). */
  int readFully(byte[] buf, int len, int timeoutMs) throws Exception;

  /** 1바이트 읽기(타임아웃 ms), 없으면 -1 */
  int readByte(int timeoutMs) throws Exception;
}
