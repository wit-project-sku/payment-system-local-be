/* 
 * Copyright (c) WIT Global 
 */
package com.wit.localpayment.global.transport;

import com.fazecast.jSerialComm.SerialPort;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class SerialPortTransport implements TLTransport {

  private final String portName;
  private final int baudRate, dataBits, stopBits, parity, readTimeoutMs;
  private SerialPort port;

  public SerialPortTransport(
      String portName, int baudRate, int dataBits, int stopBits, int parity, int readTimeoutMs) {
    this.portName = portName;
    this.baudRate = baudRate;
    this.dataBits = dataBits;
    this.stopBits = stopBits;
    this.parity = parity;
    this.readTimeoutMs = readTimeoutMs;
  }

  @Override
  public void open() {
    port = SerialPort.getCommPort(portName);
    port.setComPortParameters(baudRate, dataBits, stopBits, parity);
    port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);

    if (!port.openPort()) {
      throw new IllegalStateException("Cannot open " + portName);
    }

    // 일부 단말은 DTR/RTS가 올라가야 응답함
    try {
      port.setDTR();
    } catch (Throwable ignore) {
    }
    try {
      port.setRTS();
    } catch (Throwable ignore) {
    }

    // 블로킹 타임아웃 구성
    port.setComPortTimeouts(
        SerialPort.TIMEOUT_READ_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING,
        readTimeoutMs,
        readTimeoutMs);

    // 입력 버퍼 드레인(라이브러리 상수 없이 호환 방식)
    drainInput(250);

    log.info(
        "[Serial] OPEN {} {}bps {}-{}-{} timeouts={}ms",
        portName,
        baudRate,
        dataBits,
        stopBits,
        parity,
        readTimeoutMs);
  }

  @Override
  public void close() {
    if (port != null) {
      try {
        drainInput(50);
      } catch (Exception ignore) {
      }
      try {
        port.closePort();
      } catch (Exception ignore) {
      }
      log.info("[Serial] CLOSE {}", portName);
    }
  }

  @Override
  public void write(byte[] bytes) {
    int w = port.writeBytes(bytes, bytes.length);
    if (w != bytes.length) {
      throw new IllegalStateException("short write: " + w + "/" + bytes.length);
    }
  }

  @Override
  public int readFully(byte[] buf, int len, int timeoutMs) {
    if (timeoutMs > 0) {
      port.setComPortTimeouts(
          SerialPort.TIMEOUT_READ_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING,
          timeoutMs,
          timeoutMs);
    }
    int off = 0;
    while (off < len) {
      int r = port.readBytes(buf, len - off);
      if (r < 0) {
        throw new IllegalStateException("read error");
      }
      off += r;
    }
    return off;
  }

  @Override
  public int readByte(int timeoutMs) {
    if (timeoutMs > 0) {
      port.setComPortTimeouts(
          SerialPort.TIMEOUT_READ_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING,
          timeoutMs,
          timeoutMs);
    }
    byte[] b = new byte[1];
    int r = port.readBytes(b, 1);
    return (r == 1) ? (b[0] & 0xFF) : -1;
  }

  /** 라이브러리 purge 의존 없이 입력 버퍼를 비웁니다. */
  private void drainInput(int windowMs) {
    long end = System.currentTimeMillis() + windowMs;
    byte[] tmp = new byte[256];

    // 1) 잠시 논블로킹으로 전환
    port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

    try {
      while (System.currentTimeMillis() < end) {
        int avail;
        try {
          avail = port.bytesAvailable(); // 구버전에서도 대부분 제공됨
        } catch (Throwable ignore) {
          // bytesAvailable()이 없거나 예외가 나면, 그냥 한번 읽어본다.
          int r = port.readBytes(tmp, tmp.length);
          if (r <= 0) {
            break;
          }
          continue;
        }

        if (avail <= 0) {
          break;
        }
        int toRead = Math.min(avail, tmp.length);
        int r = port.readBytes(tmp, toRead);
        if (r <= 0) {
          break;
        }

        try {
          Thread.sleep(5);
        } catch (InterruptedException ignored) {
        }
      }
    } finally {
      // 2) 우리 기본 블로킹 타임아웃으로 복원
      port.setComPortTimeouts(
          SerialPort.TIMEOUT_READ_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING,
          readTimeoutMs,
          readTimeoutMs);
    }
  }
}
