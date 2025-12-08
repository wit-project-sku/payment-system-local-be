/* 
 * Copyright (c) WIT Global 
 */
package com.wit.localpayment.global.dto.response;

public record PacketResponse(
    String catOrMid,
    String dateTime14,
    String jobCode,
    int responseCode, // 0~255
    String dataHex // 데이터 필드 원문(HEX) - 빠르게 디버깅하기 위함
    ) {}
