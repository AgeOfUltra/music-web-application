package com.music.musicwebapplication.schedulers;

import com.music.musicwebapplication.entity.Confess;
import com.music.musicwebapplication.repo.ConfessRepo;
import com.music.musicwebapplication.service.EmailAgentService;
import com.music.musicwebapplication.enums.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class RequestScheduler {
    private final ConfessRepo confessRepo;

    private final EmailAgentService emailService;

    @Value("${app.music.base.url}")
    private String baseUrl;


    public RequestScheduler(ConfessRepo confessRepo, EmailAgentService emailService) {
        this.confessRepo = confessRepo;
        this.emailService = emailService;
    }


    @Scheduled(cron = "0 */2 * * * *")
    public void sendScheduledEmail() {

        Optional<List<Confess>> approvedConfessions = confessRepo.findByStatus(Status.APPROVED);

        for (Confess c : approvedConfessions.get()) {
            try {

                String joinUrl = String.format(
                        "%s/app/music/nodes/join?sender=%s&roomId=%s",
                        baseUrl, // e.g., "http://localhost:8080" or your production URL
                        c.getSenderOriginalName(),
                        c.getRoomHash()
                );


                Map<String, Object> templateVariables = new HashMap<>();
                templateVariables.put("receiverAlias", c.getReceiverAlias());
                templateVariables.put("confessRoomName", c.getRoomName());
                templateVariables.put("songName", c.getSongName());
                templateVariables.put("singerName", c.getSingerName());
                templateVariables.put("confessType", c.getConfessType());
                templateVariables.put("passCode", c.getPasscode());
                templateVariables.put("joinUrl", joinUrl);

                emailService.sendTemplateEmail(
                        c.getEmail(),
                        "💌 Someone Has a Secret Message for You!",
                        "confess-email", // template name
                        templateVariables
                );

                c.setStatus(Status.SENT);
                confessRepo.save(c);
                log.info("Email sent successfully to {}", c.getEmail());
                log.info("build url {}", joinUrl);

            } catch (Exception e) {
                log.error("Failed to send email for confess id {}: {}",
                        c.getId(), e.getMessage());

            }
        }
    }

    @Scheduled(cron = "0 */2 * * * *")
    public void updateExpiry(){
        Optional<List<Confess>> doneRequest =  confessRepo.findByStatus(Status.DONE);

        doneRequest.ifPresent(c-> c.forEach(a -> {
            try{
                a.setStatus(Status.EXPIRED);
                confessRepo.save(a);
                log.info("updated the status success full to expired");
            }catch (Exception e){
                log.error(Arrays.toString(e.getStackTrace()));
            }

        }));

    }
}
