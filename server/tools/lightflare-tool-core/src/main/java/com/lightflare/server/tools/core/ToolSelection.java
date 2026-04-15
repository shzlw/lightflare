package com.lightflare.server.tools.core;

import java.util.List;

public interface ToolSelection {

    String integrationId();

    boolean enabled();

    List<String> enabledTools();
}
