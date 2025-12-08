/*
 * Copyright (c) WIT Global
 */
package com.wit.localpayment.global.client;

import static com.wit.localpayment.global.proto.Proto.HEADER_BYTES;

import com.wit.localpayment.global.proto.JobCode;
import com.wit.localpayment.global.proto.TLPacket;
import com.wit.localpayment.global.transport.TLTransport;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TL3800Client implements AutoCloseable {

  private final TLTransport t;
  private final int ackWaitMs;
  private final int respWaitMs;
  private final int maxAckRetry;

  // 결제 최종 응답까지 여유 있게
  private static final int FOLLOWUP_WINDOW_MS = 180_000;

  // TL 헤더 내 오프셋 (STX 기준)
  // STX(1) + ID(16) + DT(14) + JOB(1) + RESP(1) + LEN(2)
  private static final int POS_DT = 1 + 16; // 17
  private static final int POS_JOB = POS_DT + 14; // 31
  private static final int POS_LEN = POS_JOB + 1 + 1; // 33

  public TL3800Client(TLTransport transport, int ackWaitMs, int respWaitMs, int maxAckRetry) {
    this.t = transport;
    this.ackWaitMs = ackWaitMs;
    this.respWaitMs = respWaitMs;
    this.maxAckRetry = maxAckRetry;
  }

  public void open() throws Exception {
    t.open();
  }

  @Override
  public void close() {
    try {
      t.close();
    } catch (Exception ignore) {
    }
  }

  public TLPacket requestResponse(TLPacket req) throws Exception {
    final byte[] frame = req.toBytes();
    log.info("[TL3800] >> SEND job={} len={} HEX={}", req.jobCode, frame.length, hex(frame));

    // 요청 잡코드에 대응하는 "기대 응답" 잡코드 (예: B → b)
    JobCode expectedFinal = expectedResponseJob(req.jobCode);

    int tries = 0;
    while (true) {
      drainRx(120);
      t.write(frame);
      sleepQuiet(8);

      Integer first = waitAckNakStx(ackWaitMs);
      if (first != null) {
        if (first == 0x15) { // NAK
          if (++tries <= maxAckRetry) {
            log.warn("[TL3800] << NAK → retry {}/{}", tries, maxAckRetry);
            continue;
          }
          throw new IllegalStateException("NAK received (exceeded retry)");
        }
        if (first == 0x06) { // ACK
          log.debug("[TL3800] << ACK");
          byte[] header = readHeaderFromStx(); // 통일
          TLPacket p = readOrFollowUpIfEvent(header, expectedFinal, req);
          if (p != null) {
            return p;
          }
          return waitResendAndReturnExpected(expectedFinal);
        }
        if (first == 0x02) { // 즉시 STX
          log.debug("[TL3800] << STX (immediate)");
          byte[] header = readHeaderFromStx(); // 통일
          TLPacket p = readOrFollowUpIfEvent(header, expectedFinal, req);
          if (p != null) {
            return p;
          }
          return waitResendAndReturnExpected(expectedFinal);
        }
      }

      // 폴백: ACK가 없으면 STX 추가 대기
      log.debug("[TL3800] no-ACK within {} ms → waiting STX up to {} ms", ackWaitMs, respWaitMs);
      long start = System.currentTimeMillis();
      while ((System.currentTimeMillis() - start) < respWaitMs) {
        int b = t.readByte(50);
        if (b == 0x02) {
          byte[] header = readHeaderFromStx(); // 통일
          TLPacket p = readOrFollowUpIfEvent(header, expectedFinal, req);
          if (p != null) {
            return p;
          }
          return waitResendAndReturnExpected(expectedFinal);
        } else if (b == 0x15) {
          if (++tries <= maxAckRetry) {
            log.warn("[TL3800] << late NAK → retry {}/{}", tries, maxAckRetry);
            break;
          }
          throw new IllegalStateException("NAK received (exceeded retry)");
        } else if (b == 0x06) {
          log.debug("[TL3800] << late ACK");
          byte[] header = readHeaderFromStx(); // 통일
          TLPacket p = readOrFollowUpIfEvent(header, expectedFinal, req);
          if (p != null) {
            return p;
          }
          return waitResendAndReturnExpected(expectedFinal);
        }
      }

      throw new IllegalStateException("ACK timeout");
    }
  }

  /**
   * ACK/즉시-STX/late-ACK 모두 여기로 통일: STX를 잡으면 단순히 34바이트를 더 읽어서 헤더로 사용
   */
  /**
   * STX는 이미 읽힌 상태에서 호출된다고 가정하고, 나머지 34바이트만 읽어 헤더 구성
   */
  private byte[] readHeaderFromStx() throws Exception {
    byte[] header = composeHeaderAfterStxWithSliding();
    if (header == null) {
      throw new IllegalStateException("header build failed after STX");
    }
    if (!isSaneHeader(header)) {
      throw new IllegalStateException("invalid header after STX: " + hex(header));
    }
    log.debug("[TL3800] header built HEX={}", hex(header));
    return header;
  }

  /**
   * 헤더 sanity 검사: 날짜 14자리 숫자/잡코드 유효/데이터 길이 상한
   */
  private boolean isSaneHeader(byte[] h) {
    // 1) 날짜 14자리 숫자만
    for (int i = POS_DT; i < POS_DT + 14; i++) {
      int v = h[i] & 0xFF;
      if (v < 0x30 || v > 0x39) {
        return false;
      }
    }
    // 2) 잡코드 유효성 (JobCode.of로 검증)
    int job = h[POS_JOB] & 0xFF;
    if (!isKnownJob(job)) {
      return false;
    }
    // 3) 데이터 길이 합리성
    int dataLen = (h[POS_LEN] & 0xFF) | ((h[POS_LEN + 1] & 0xFF) << 8);
    return dataLen >= 0 && dataLen <= 4096;
  }

  private boolean isKnownJob(int job) {
    try {
      JobCode.of((byte) job);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private JobCode jobFromHeader(byte[] header) {
    return JobCode.of(header[POS_JOB]);
  }

  private int dataLenFromHeader(byte[] header) {
    return (header[POS_LEN] & 0xFF) | ((header[POS_LEN + 1] & 0xFF) << 8);
  }

  /**
   * EVENT 프레임의 tail(데이터+ETX+BCC)을 읽고 버린다. EVENT는 ACK/NACK 미전송.
   */
  private void consumeEventFrame(byte[] header) throws Exception {
    int dataLen = dataLenFromHeader(header);
    int tailLen = dataLen + 2; // ETX + BCC (값은 검증하지 않음)

    byte[] tail = new byte[tailLen];
    int m = t.readFully(tail, tailLen, respWaitMs);

    log.info("[TL3800] << RECV(EVENT) dataLen={} readTail={}", dataLen, m);
    // EVENT 는 ACK/NACK 금지
  }

  /**
   * 헤더를 읽은 뒤 프레임 파싱을 시도. 첫 프레임이 EVENT면 내용을 읽고 버린 뒤 후속 프레임을 기다리고, 비-EVENT는 TLPacket으로 파싱. 파싱 실패
   * 시(NACK 전송됨) null을 반환해 상위에서 재전송을 받게 함.
   */
  private TLPacket readOrFollowUpIfEvent(byte[] header, JobCode expectedFinal, TLPacket req)
      throws Exception {

    JobCode job;
    try {
      job = jobFromHeader(header);
    } catch (IllegalArgumentException ex) {
      log.warn("[TL3800] invalid jobcode in header → waiting for resend: {}", ex.getMessage());
      // 파싱 실패로 간주 → 상위(requestResponse)가 waitResendAndReturnExpected 호출
      return null;
    }

    // 1) EVENT 헤더인 경우
    if (job == JobCode.EVENT) {
      consumeEventFrame(header);
      log.warn(
          "[TL3800] EVENT header received; waiting next non-EVENT frame (expect={})",
          expectedFinal);
      return waitFollowUp(expectedFinal);
    }

    // 2) 정상 JobCode 인 경우 기존 로직 유지
    TLPacket first;
    try {
      first = readTailParseAndAck(header, req);
    } catch (IllegalArgumentException ex) {
      log.warn("[TL3800] first frame parse failed → waiting for resend: {}", ex.getMessage());
      return null;
    }

    if (first.jobCode == JobCode.EVENT) {
      log.warn("[TL3800] EVENT received; waiting next non-EVENT frame (expect={})", expectedFinal);
      return waitFollowUp(expectedFinal);
    }
    return first;
  }

  /**
   * 파싱 실패 후 재전송을 받아 기대 잡코드가 올 때까지 대기
   */
  private TLPacket waitResendAndReturnExpected(JobCode expected) throws Exception {
    long deadline = System.currentTimeMillis() + respWaitMs;
    while (System.currentTimeMillis() < deadline) {
      try {
        TLPacket pkt = readNextFrameAndAck(respWaitMs);
        if (pkt.jobCode == JobCode.EVENT) {
          return waitFollowUp(expected);
        }
        if (matchesExpected(expected, pkt.jobCode)) {
          return pkt;
        }
        log.warn("[TL3800] unexpected job on resend: {} (expect {})", pkt.jobCode, expected);
      } catch (IllegalArgumentException ex) {
        log.warn("[TL3800] resend parse failed: {}", ex.getMessage());
      }
    }
    throw new IllegalStateException("Resend timeout after parse failure");
  }

  /**
   * FOLLOWUP_WINDOW 동안 다음 프레임들을 계속 수신(매번 ACK)하여 expected 잡코드가 오면 반환
   */
  private TLPacket waitFollowUp(JobCode expected) throws Exception {
    long deadline = System.currentTimeMillis() + FOLLOWUP_WINDOW_MS;

    while (true) {
      long remaining = deadline - System.currentTimeMillis();
      if (remaining <= 0) {
        break;
      }

      try {
        int perTry = (int) Math.min(respWaitMs, remaining);
        TLPacket next = readNextFrameAndAck(perTry);

        log.info("[TL3800] << RECV(seq) job={} dataLen={}", next.jobCode, next.data.length);

        if (next.jobCode == JobCode.EVENT) {
          // readNextFrameAndAck 안에서 이미 consumeEventFrame 처리
          continue;
        }
        if (matchesExpected(expected, next.jobCode)) {
          return next;
        }

        log.warn("[TL3800] unexpected job={} (expect={}) — keep waiting", next.jobCode, expected);
      } catch (IllegalArgumentException e) {
        log.warn("[TL3800] follow-up parse failed: {}", e.getMessage());
      } catch (IllegalStateException e) {
        log.debug("[TL3800] follow-up per-try timeout: {}", e.getMessage());
      }
    }

    throw new IllegalStateException(
        "Follow-up window exceeded (" + FOLLOWUP_WINDOW_MS + " ms) without final " + expected);
  }

  /**
   * 다음 STX부터 한 프레임을 읽어 검증 후 ACK 회신. EVENT는 tail만 읽고 버리고 계속 대기, 비-EVENT는 파싱.
   */
  private TLPacket readNextFrameAndAck(int waitMs) throws Exception {
    long deadline = System.currentTimeMillis() + waitMs;
    while (System.currentTimeMillis() < deadline) {
      int b = t.readByte(Math.min(50, waitMs));
      if (b < 0) {
        continue;
      }
      if (b == 0x02) {
        byte[] header = composeHeaderAfterStxWithSliding();
        if (header == null) {
          log.warn("[TL3800] follow-up header build failed → resync");
          continue;
        }
        if (!isSaneHeader(header)) {
          log.warn("[TL3800] follow-up header sanity failed → resync (hex={})", hex(header));
          continue;
        }

        JobCode job = jobFromHeader(header);
        if (job == JobCode.EVENT) {
          consumeEventFrame(header);
          log.info("[TL3800] << RECV(seq) job=EVENT (ignored)");
          // EVENT는 응답이 아니므로 계속 다음 프레임 대기
          continue;
        }

        return readTailParseAndAck(header, null);
      }
      log.debug("[TL3800] << skip 0x{}", String.format("%02X", b));
    }
    throw new IllegalStateException("Follow-up frame timeout");
  }

  /**
   * 꼬리(데이터+ETX+BCC) 수신 → 파싱 → (성공시 ACK / 실패시 NAK 회신 후 예외)
   */
  private TLPacket readTailParseAndAck(byte[] header, TLPacket req) throws Exception {
    int dataLen = dataLenFromHeader(header);
    int tailLen = dataLen + 2; // ETX + BCC

    byte[] tail = new byte[tailLen];
    int m = t.readFully(tail, tailLen, respWaitMs);
    if (m != tailLen) {
      try {
        t.write(new byte[]{0x15});
      } catch (Exception ignore) {
      }
      log.warn("[TL3800] >> NAK (body short: got={} need={})", m, tailLen);
      throw new IllegalArgumentException("short body");
    }

    byte[] resp = new byte[HEADER_BYTES + tailLen];
    System.arraycopy(header, 0, resp, 0, HEADER_BYTES);
    System.arraycopy(tail, 0, resp, HEADER_BYTES, tailLen);
    log.info("[TL3800] << RECV len={} HEX={}", resp.length, hex(resp));

    try {
      // 1차: strict 검증 (STX/ETX/BCC 다 맞는지 확인)
      TLPacket pkt = TLPacket.parseStrict(resp);

      if (req != null && !matchesExpected(req.jobCode, pkt.jobCode)) {
        log.warn("[TL3800] JOB changed: req={} resp={}", req.jobCode, pkt.jobCode);
      }

      t.write(new byte[]{0x06});
      log.debug("[TL3800] >> ACK");
      return pkt;
    } catch (IllegalArgumentException ex) {
      log.warn("[TL3800] strict parse failed: {} → trying lenient parse", ex.getMessage());

      // 2차: lenient 파서로 일단 내용만이라도 살려본다.
      TLPacket pkt = TLPacket.parseLenient(resp);

      try {
        t.write(new byte[]{0x06});
        log.debug("[TL3800] >> ACK (after lenient parse)");
      } catch (Exception ignore) {
      }

      return pkt;
    }
  }

  private Integer waitAckNakStx(int waitMs) {
    long end = System.currentTimeMillis() + waitMs;
    while (System.currentTimeMillis() < end) {
      int b = -1;
      try {
        b = t.readByte(50);
      } catch (Exception ignore) {
      }
      if (b < 0) {
        continue;
      }
      if (b == 0x06 || b == 0x15 || b == 0x02) {
        return b;
      }
    }
    return null;
  }

  private void drainRx(long windowMs) {
    long end = System.currentTimeMillis() + windowMs;
    while (System.currentTimeMillis() < end) {
      try {
        int b = t.readByte(20);
        if (b < 0) {
          break;
        }
      } catch (Exception ignore) {
        break;
      }
    }
  }

  private static void sleepQuiet(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ignored) {
    }
  }

  private static String hex(byte[] b) {
    return java.util.HexFormat.of().formatHex(b);
  }

  /**
   * STX 직후 34B를 읽고, 내부에 다시 STX가 있으면 '마지막 STX'로 슬라이딩해 정상 35B 헤더를 재구성
   */
  private byte[] composeHeaderAfterStxWithSliding() throws Exception {
    final int REST = HEADER_BYTES - 1; // 34
    byte[] rest = new byte[REST];
    int n = t.readFully(rest, REST, respWaitMs);

    if (n != REST) {
      log.warn("[TL3800] header short: got={} need={} raw34={}",
          n, REST, hex(Arrays.copyOf(rest, n)));
      return null;
    }

    // 디버깅용: STX 이후 처음 읽은 34바이트 그대로
    log.debug("[TL3800] raw34 after STX = {}", hex(rest));

    // 시도 #0: [STX] + rest
    byte[] header = new byte[HEADER_BYTES];
    header[0] = 0x02;
    System.arraycopy(rest, 0, header, 1, REST);
    if (isSaneHeader(header)) {
      log.debug("[TL3800] header0 sane HEX={}", hex(header));
      return header;
    } else {
      log.warn("[TL3800] header0 sanity FAILED HEX={}", hex(header));
    }

    // 시도 #1: rest 내부 '마지막 STX'로 슬라이딩
    int idx = lastIndexOf(rest, (byte) 0x02);
    if (idx >= 0 && idx < REST - 1) {
      byte[] header2 = new byte[HEADER_BYTES];
      header2[0] = 0x02;
      int copy = Math.min(REST - (idx + 1), HEADER_BYTES - 1);
      System.arraycopy(rest, idx + 1, header2, 1, copy);
      int missing = (HEADER_BYTES - 1) - copy;
      if (missing > 0) {
        byte[] more = new byte[missing];
        int m = t.readFully(more, missing, respWaitMs);
        if (m != missing) {
          log.warn("[TL3800] header2 short: got={} need={} rawMore={}",
              m, missing, hex(Arrays.copyOf(more, m)));
          return null;
        }
        System.arraycopy(more, 0, header2, 1 + copy, missing);
      }

      if (isSaneHeader(header2)) {
        log.debug("[TL3800] header2 sane HEX={}", hex(header2));
        return header2;
      } else {
        log.warn("[TL3800] header2 sanity FAILED HEX={}", hex(header2));
      }
    } else {
      log.warn("[TL3800] no inner STX in raw34={}", hex(rest));
    }

    // 여기까지 오면 이 헤더는 못 쓰는 것으로 판단
    return null;
  }

  /**
   * 배열 내 '마지막' 바이트 값의 인덱스(없으면 -1)
   */
  private static int lastIndexOf(byte[] arr, byte v) {
    for (int i = arr.length - 1; i >= 0; i--) {
      if (arr[i] == v) {
        return i;
      }
    }
    return -1;
  }

  /**
   * 요청 잡코드에 대응하는 "기대 응답" 잡코드. 기본은 같은 코드, A→a, B→b 식으로 매핑
   */
  private JobCode expectedResponseJob(JobCode reqJob) {
    char c = reqJob.code;
    if (c >= 'A' && c <= 'Z') {
      char respChar = Character.toLowerCase(c);
      try {
        return JobCode.of((byte) respChar);
      } catch (IllegalArgumentException ignored) {
        // 대응하는 소문자 JobCode 가 없으면 그냥 원래 값 사용
      }
    }
    return reqJob;
  }

  /**
   * expected/actual 잡코드가 대소문자만 다른 경우까지 허용
   */
  private boolean matchesExpected(JobCode expected, JobCode actual) {
    if (expected == actual) {
      return true;
    }
    if (expected == JobCode.EVENT || actual == JobCode.EVENT) {
      return false;
    }
    return Character.toLowerCase(expected.code) == Character.toLowerCase(actual.code);
  }
}
