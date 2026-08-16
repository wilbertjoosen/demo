package com.example.chat.model;

/** Outbound WS frame shape: a type discriminator plus a type-specific payload (a DirectMessage for
 * MESSAGE/MESSAGE_UPDATED, a TypingEvent for TYPING). */
public record WsEnvelope(String type, Object payload) {
}
