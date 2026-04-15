package com.lightflare.server.messaging;

public interface MessagingAppConnector {

    String process(MessagingAppConnectorRequest request);
}
