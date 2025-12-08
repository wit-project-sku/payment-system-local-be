/* 
 * Copyright (c) WIT Global 
 */
package com.wit.localpayment.global.controller;

import com.wit.localpayment.global.TL3800Gateway;
import com.wit.localpayment.global.dto.request.ApproveRequest;
import com.wit.localpayment.global.dto.request.CancelRequest;
import com.wit.localpayment.global.dto.response.PacketResponse;
import com.wit.localpayment.global.proto.TLPacket;
import com.wit.localpayment.global.util.Hex;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/tl3800", produces = MediaType.APPLICATION_JSON_VALUE)
public class TL3800Controller {

  private final TL3800Gateway gateway;

  public TL3800Controller(TL3800Gateway gateway) {
    this.gateway = gateway;
  }

  @PostMapping("/device-check")
  public PacketResponse deviceCheck() throws Exception {
    TLPacket p = gateway.deviceCheck();
    return new PacketResponse(
        p.catOrMid,
        p.dateTime14,
        String.valueOf(p.jobCode.code),
        Byte.toUnsignedInt(p.responseCode),
        Hex.toHex(p.data));
  }

  @PostMapping("/approve")
  public PacketResponse approve(@Valid @RequestBody ApproveRequest req) throws Exception {
    TLPacket p = gateway.approve(req.amount(), req.tax(), req.svc(), req.inst(), req.noSign());
    return new PacketResponse(
        p.catOrMid,
        p.dateTime14,
        String.valueOf(p.jobCode.code),
        Byte.toUnsignedInt(p.responseCode),
        Hex.toHex(p.data));
  }

  @PostMapping("/cancel")
  public PacketResponse cancel(@Valid @RequestBody CancelRequest req) throws Exception {
    TLPacket p =
        gateway.cancel(
            req.cancelType(),
            req.tranType(),
            req.amount(),
            req.tax(),
            req.svc(),
            req.inst(),
            req.noSign(),
            req.approvalNo(),
            req.orgDate(),
            req.orgTime(),
            req.extra());

    return new PacketResponse(
        p.catOrMid,
        p.dateTime14,
        String.valueOf(p.jobCode.code),
        Byte.toUnsignedInt(p.responseCode),
        Hex.toHex(p.data));
  }
}
