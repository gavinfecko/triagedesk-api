package dev.gavinfecko.triagedesk.notification.application;

/** One message to one person: a plain-text body and the same content as simple HTML. */
public record Email(String to, String subject, String text, String html) {}
