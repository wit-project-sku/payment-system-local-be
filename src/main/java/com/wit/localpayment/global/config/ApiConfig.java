/*
 * Copyright (c) WIT Global
 */
package com.wit.localpayment.global.config;

import com.wit.localpayment.global.TL3800Gateway;
import com.wit.localpayment.global.client.TL3800Client;
import com.wit.localpayment.global.payload.Requests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiConfig {

  @Bean
  TL3800Gateway tl3800Gateway(TL3800Client client, Requests factory) {
    return new TL3800Gateway(client, factory);
  }
}
