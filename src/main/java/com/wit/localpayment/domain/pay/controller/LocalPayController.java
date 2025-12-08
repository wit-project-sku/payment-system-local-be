package com.wit.localpayment.domain.pay.controller;

import com.wit.localpayment.domain.pay.dto.request.PayRequest;
import com.wit.localpayment.domain.pay.dto.response.PayResponse;
import com.wit.localpayment.domain.pay.service.LocalPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Pay", description = "키오스크 로컬 결제 API")
@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class LocalPayController {

  private final LocalPayService localPayService;

  @Operation(
      summary = "장바구니 결제 요청 API(로컬)",
      description =
          "키오스크에서 상품 ID/수량/총 금액/할부 여부를 받아 TL3800 단말 승인 요청을 수행하고, "
              + "결과를 중앙 서버에 success/failure로 전송합니다.")
  @PostMapping("/pay")
  public ResponseEntity<PayResponse> pay(@Valid @RequestBody PayRequest request) {

    PayResponse response = localPayService.pay(request);

    HttpStatus status = response.success() ? HttpStatus.CREATED : HttpStatus.OK;

    return ResponseEntity.status(status).body(response);
  }
}