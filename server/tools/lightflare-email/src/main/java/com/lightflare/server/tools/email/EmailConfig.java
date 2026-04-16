package com.lightflare.server.tools.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import java.util.Properties;

@Configuration
@EnableConfigurationProperties(EmailProperties.class)
@ConditionalOnProperty(prefix = "lightflare.tools.email", name = "enabled", havingValue = "true")
public class EmailConfig {

    @Bean
    public JavaMailSender getJavaMailSender(EmailProperties emailProperties) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        
        if (StringUtils.hasText(emailProperties.getHost())) {
            mailSender.setHost(emailProperties.getHost());
        }
        
        if (emailProperties.getPort() != null && emailProperties.getPort() > 0) {
            mailSender.setPort(emailProperties.getPort());
        }

        if (StringUtils.hasText(emailProperties.getUsername())) {
            mailSender.setUsername(emailProperties.getUsername());
        }
        
        if (StringUtils.hasText(emailProperties.getPassword())) {
            mailSender.setPassword(emailProperties.getPassword());
        }

        Properties props = mailSender.getJavaMailProperties();
        if (emailProperties.getProperties() != null) {
            props.putAll(emailProperties.getProperties());
        }
        
        return mailSender;
    }

    @Bean
    public EmailService emailService(JavaMailSender javaMailSender, EmailProperties emailProperties) {
        return new EmailService(javaMailSender, emailProperties);
    }
}
