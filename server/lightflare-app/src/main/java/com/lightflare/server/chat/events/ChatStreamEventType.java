package com.lightflare.server.chat.events;

public enum ChatStreamEventType {
    MESSAGE_START,
    PLAN_CREATED,
    STEP_STARTED,
    STEP_PROGRESS,
    STEP_COMPLETED,
    FINAL_RESPONSE,
    MESSAGE_COMPLETE,
    MESSAGE_ERROR
}
