/* 
 * Copyright (c) WIT Global 
 */
package com.wit.localpayment.global;

import com.wit.localpayment.global.client.TL3800Client;
import com.wit.localpayment.global.payload.Requests;
import com.wit.localpayment.global.proto.TLPacket;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class TL3800Gateway {

  private final TL3800Client client;
  private final Requests requests;
  private final ReentrantLock lock = new ReentrantLock(true);

  public TL3800Gateway(TL3800Client client, Requests requests) {
    this.client = client;
    this.requests = requests;
  }

  private TLPacket call(Supplier<TLPacket> supplier) throws Exception {
    lock.lock();
    try {
      client.open();
      try {
        return client.requestResponse(supplier.get());
      } finally {
        client.close();
      }
    } finally {
      lock.unlock();
    }
  }

  /** 장치체크 (A/a) */
  public TLPacket deviceCheck() throws Exception {
    return call(requests::deviceCheck);
  }

  /** 거래승인 (B/b) */
  public TLPacket approve(String amount, String tax, String svc, String inst, boolean noSign)
      throws Exception {
    return call(() -> requests.approve(amount, tax, svc, inst, noSign));
  }

  /** 거래취소 (C/c) */
  public TLPacket cancel(
      String cancelType,
      String tranType,
      String amount,
      String tax,
      String svc,
      String inst,
      boolean noSign,
      String approvalNo,
      String orgDate,
      String orgTime,
      String extra)
      throws Exception {

    return call(
        () ->
            requests.cancel(
                cancelType,
                tranType,
                amount,
                tax,
                svc,
                inst,
                noSign,
                approvalNo,
                orgDate,
                orgTime,
                extra));
  }
}
