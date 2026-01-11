package com.music.musicwebapplication.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Slf4j
@Service
public class EmailAgentService {
    @Autowired
    private JavaMailSender mailSender;

    private final TemplateEngine templateEngine;

    public EmailAgentService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public void sendTemplateEmail(
            String to,
            String subject,
            String templateName,
            Map<String, Object> variables) throws MessagingException {

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject(subject);

        String from = "**************";
        helper.setFrom(from);

        Context context = new Context();
        context.setVariables(variables);
        String htmlContent = templateEngine.process(templateName, context);

        helper.setText(htmlContent, true); // true = HTML
        log.debug("email content {} , to : {} , subject : {}, from {} ",htmlContent,to,subject,from);
        mailSender.send(message);
    }

}
