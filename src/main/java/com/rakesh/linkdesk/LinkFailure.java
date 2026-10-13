package com.rakesh.linkdesk;

public class LinkFailure extends RuntimeException {
  private final int status;

  public LinkFailure(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }
}
