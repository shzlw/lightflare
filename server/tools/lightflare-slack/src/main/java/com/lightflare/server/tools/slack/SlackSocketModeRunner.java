package com.lightflare.server.tools.slack;

import com.slack.api.bolt.jakarta_socket_mode.SocketModeApp;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

@RequiredArgsConstructor
@Slf4j
public class SlackSocketModeRunner implements ApplicationRunner {

    private final SocketModeApp socketModeApp;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        socketModeApp.startAsync(); // non-blocking; keeps the WS open in background
        log.info("socketModeApp startAsync");
    }

    @PreDestroy
    public void shutdown() throws Exception {
        socketModeApp.close();
    }
}