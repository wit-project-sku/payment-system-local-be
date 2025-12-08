/*
 * Copyright (c) WIT Global
 */
package com.wit.localpayment.domain.pay.client;


import com.wit.localpayment.domain.pay.dto.request.PayFailureReportRequest;
import com.wit.localpayment.domain.pay.dto.request.PaySuccessReportRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class CentralPayClient {

  private final RestTemplate restTemplate;

  @Value("${central.api-base-url}")
  private String centralBaseUrl;

  public void notifySuccess(PaySuccessReportRequest request) {
    String url = centralBaseUrl + "/api/pay/success";

    try {
      ResponseEntity<Void> response = restTemplate.postForEntity(url, request, Void.class);
      log.info(
          "[LOCAL] 중앙서버 성공 보고 전송 완료 - status={}, url={}",
          response.getStatusCode(),
          url);
    } catch (Exception e) {
      log.warn("[LOCAL] 중앙서버 성공 보고 전송 실패 - url={}, ex={}", url, e.toString());
    }
  }

  public void notifyFailure(PayFailureReportRequest request) {
    String url = centralBaseUrl + "/api/pay/failure";

    try {
      ResponseEntity<Void> response = restTemplate.postForEntity(url, request, Void.class);
      log.info(
          "[LOCAL] 중앙서버 실패/예외 보고 전송 완료 - status={}, url={}",
          response.getStatusCode(),
          url);
    } catch (Exception e) {
      log.warn("[LOCAL] 중앙서버 실패/예외 보고 전송 실패 - url={}, ex={}", url, e.toString());
    }
  }
}