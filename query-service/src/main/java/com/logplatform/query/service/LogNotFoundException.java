package com.logplatform.query.service;
public class LogNotFoundException extends RuntimeException {
 public LogNotFoundException(String eventId){super("Log event not found: "+eventId);}
}
