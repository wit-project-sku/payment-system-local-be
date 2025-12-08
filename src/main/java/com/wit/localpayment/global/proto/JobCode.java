/* 
 * Copyright (c) WIT Global 
 */
package com.wit.localpayment.global.proto;

public enum JobCode {
  // 요청 (대문자), 응답 (소문자), 이벤트 '@'
  A('A'),
  a('a'),
  B('B'),
  b('b'),
  C('C'),
  c('c'),
  D('D'),
  d('d'),
  E('E'),
  e('e'),
  F('F'),
  f('f'),
  G('G'),
  g('g'),
  H('H'), // no response
  I('I'),
  i('i'),
  J('J'),
  j('j'),
  K('K'),
  k('k'),
  L('L'),
  l('l'),
  M('M'),
  m('m'),
  N('N'),
  n('n'),
  O('O'),
  o('o'),
  P('P'),
  p('p'),
  Q('Q'),
  q('q'),
  R('R'), // reset (no response frame)
  S('S'),
  s('s'),
  T('T'),
  t('t'),
  U('U'),
  u('u'),
  V('V'),
  v('v'),
  W('W'),
  w('w'),
  X('X'),
  x('x'),
  Y('Y'),
  y('y'),
  Z('Z'),
  z('z'),
  EVENT('@'); // 이벤트: ACK/NACK 미전송 규칙

  public final char code;

  JobCode(char c) {
    this.code = c;
  }

  public static JobCode of(byte b) {
    char c = (char) b;
    for (JobCode jc : values()) {
      if (jc.code == c) {
        return jc;
      }
    }
    throw new IllegalArgumentException("Unknown JobCode: " + (int) b);
  }
}
