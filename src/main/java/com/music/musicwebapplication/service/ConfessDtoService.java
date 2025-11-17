package com.music.musicwebapplication.service;

import com.music.musicwebapplication.dto.ConfessDto;
import com.music.musicwebapplication.entity.Confess;
import com.music.musicwebapplication.support.Role;
import com.music.musicwebapplication.support.Status;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.random.RandomGenerator;


@Service
public class ConfessDtoService {
    private static final RandomGenerator RNG = RandomGenerator.of("L128X256MixRandom");
    private static final String CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final ConfessService service;

    private final ModelMapper mapper;

    public ConfessDtoService(ConfessService service, ModelMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    public String buildConfessData(ConfessDto confess){
        //generate : room-name
        String roomHash = generateRoomName(confess.getReceiverAlias(),confess.getConfessType(),confess.getSingerName(),confess.getSongName(),12);

//        passcode generate
        String passCode = generatePassCode(5);

//        created time stamp need to update and duration will be handled later upon open.

        Status status = Status.IN_PROGRESS;

        Confess confess1 = new Confess();

    return  "";
    }

    private String generateRoomName(String alias,String type,String sender,String song,int length){
        String newStr = alias+type+sender+song;
        SecureRandom random = new SecureRandom(newStr.getBytes());
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private String generatePassCode(int length){

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = RNG.nextInt(CHARSET.length());
            sb.append(CHARSET.charAt(index));
        }
        return sb.toString();
    }

}
