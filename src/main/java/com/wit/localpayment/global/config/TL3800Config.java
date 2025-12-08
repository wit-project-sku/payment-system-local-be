package com.wit.localpayment.global.config;

import com.wit.localpayment.global.client.TL3800Client;
import com.wit.localpayment.global.payload.Requests;
import com.wit.localpayment.global.transport.SerialPortTransport;
import com.wit.localpayment.global.transport.TLTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TL3800Config {

  // --- properties 주입 ---
  @Value("${tl3800.terminal-id}")
  private String terminalId;

  @Value("${tl3800.port}")
  private String port;

  @Value("${tl3800.baud-rate:115200}")
  private int baudRate;

  @Value("${tl3800.data-bits:8}")
  private int dataBits;

  @Value("${tl3800.stop-bits:1}")
  private int stopBits;

  @Value("${tl3800.parity:0}") // 0:NONE, 1:ODD, 2:EVEN
  private int parity;

  @Value("${tl3800.ack-wait-ms:3000}")
  private int ackWaitMs;

  @Value("${tl3800.resp-wait-ms:25000}")
  private int respWaitMs;

  @Value("${tl3800.max-ack-retry:3}")
  private int maxAckRetry;

  // --- beans ---
  @Bean
  public TLTransport tlTransport() {
    if (port == null || port.isBlank()) {
      throw new IllegalStateException("Property 'tl3800.port' is missing or blank.");
    }
    return new SerialPortTransport(port, baudRate, dataBits, stopBits, parity, respWaitMs);
  }

  @Bean
  public TL3800Client tl3800Client(TLTransport t) {
    return new TL3800Client(t, ackWaitMs, respWaitMs, maxAckRetry);
  }

  @Bean
  public Requests requests() {
    return new Requests(terminalId);
  }
}

