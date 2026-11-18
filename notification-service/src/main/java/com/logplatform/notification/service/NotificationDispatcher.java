package com.logplatform.notification.service;

import com.logplatform.notification.config.NotificationProperties;
import com.logplatform.notification.domain.NotificationDelivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.InetAddress;
import java.util.regex.Pattern;

@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");

    private final NotificationProperties props;
    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate;
    private final Counter notificationsDeliveredTotal;
    private final Counter notificationsDeliveredFailed;

    public NotificationDispatcher(NotificationProperties props, JavaMailSender mailSender, RestTemplateBuilder restTemplateBuilder, MeterRegistry registry) {
        this.props = props;
        this.mailSender = mailSender;
        this.restTemplate = restTemplateBuilder.build();
        this.notificationsDeliveredTotal = Counter.builder("notifications.delivered.total")
                .tag("application", "notification-service")
                .register(registry);
        this.notificationsDeliveredFailed = Counter.builder("notifications.delivered.failed")
                .tag("application", "notification-service")
                .register(registry);
    }

    public void send(NotificationDelivery delivery) {
        try {
            switch (delivery.getChannel()) {
                case "EMAIL" -> sendEmail(delivery);
                case "SLACK" -> sendSlack(delivery);
                case "WEBHOOK" -> sendWebhook(delivery);
                default -> throw new IllegalArgumentException("Unknown channel: " + delivery.getChannel());
            }
            notificationsDeliveredTotal.increment();
        } catch (Exception ex) {
            notificationsDeliveredFailed.increment();
            throw ex;
        }
    }

    private void sendEmail(NotificationDelivery d) {
        if (!props.emailEnabled() || props.smtpPassword() == null) {
            throw new IllegalStateException("Email not configured");
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            msg.setFrom(new InternetAddress(props.smtpUsername()));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(d.getDestination()));
            msg.setSubject("Log Platform Alert");
            msg.setText(d.getPayload());
            mailSender.send(msg);
        } catch (MessagingException ex) {
            throw new RuntimeException("Email send failed: " + ex.getMessage(), ex);
        }
    }

    private void sendSlack(NotificationDelivery d) {
        if (!props.slackEnabled() || props.slackWebhookUrl() == null) {
            throw new IllegalStateException("Slack not configured");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"text\":\"Log Platform Alert: " + d.getPayload() + "\"}";
        restTemplate.postForObject(props.slackWebhookUrl(), new HttpEntity<>(body, headers), String.class);
    }

    private void sendWebhook(NotificationDelivery d) {
        if (!props.webhookEnabled() || d.getDestination() == null) {
            throw new IllegalStateException("Webhook not configured or no destination");
        }
        URI uri = URI.create(d.getDestination());
        validateSSRF(uri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForObject(uri, new HttpEntity<>(d.getPayload(), headers), String.class);
    }

    void validateSSRF(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("Invalid webhook URL: no host");
        }
        String ip;
        if ("localhost".equalsIgnoreCase(host)) {
            throw new IllegalArgumentException("SSRF blocked: localhost");
        }
        if (IPV4_LITERAL.matcher(host).matches()) {
            ip = host;
        } else {
            try {
                InetAddress addr = InetAddress.getByName(host);
                ip = addr.getHostAddress();
                if ("::1".equals(ip) || ip.startsWith("::1")) {
                    throw new IllegalArgumentException("SSRF blocked: localhost");
                }
                if (!IPV4_LITERAL.matcher(ip).matches()) {
                    throw new IllegalArgumentException("SSRF blocked: non-IPv4 host " + host);
                }
            } catch (Exception ex) {
                throw new IllegalArgumentException("SSRF validation failed: " + ex.getMessage(), ex);
            }
        }
        if (isPrivateIPv4(ip)) {
            throw new IllegalArgumentException("SSRF blocked: private IP " + ip);
        }
        if (ip.equals("169.254.169.254") || ip.startsWith("169.254.")) {
            throw new IllegalArgumentException("SSRF blocked: cloud metadata IP " + ip);
        }
    }

    private boolean isPrivateIPv4(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        try {
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            if (a == 10) return true;
            if (a == 127) return true; // loopback
            if (a == 172 && b >= 16 && b <= 31) return true;
            if (a == 192 && b == 168) return true;
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
