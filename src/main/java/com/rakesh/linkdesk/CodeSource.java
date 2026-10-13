package com.rakesh.linkdesk;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class CodeSource {
  private static final char[] ALPHABET =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
  private final SecureRandom random = new SecureRandom();

  public String next() {
    char[] code = new char[8];
    for (int i = 0; i < code.length; i++) code[i] = ALPHABET[random.nextInt(ALPHABET.length)];
    return new String(code);
  }
}
