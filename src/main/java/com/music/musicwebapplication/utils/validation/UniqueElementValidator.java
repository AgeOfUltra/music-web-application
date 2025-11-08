package com.music.musicwebapplication.utils.validation;

import com.music.musicwebapplication.repo.RoomRepo;
import com.music.musicwebapplication.repo.SongRepo;
import com.music.musicwebapplication.repo.UserRepo;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UniqueElementValidator implements ConstraintValidator<UniqueValidator,String> {
    private final UserRepo userRepository;
    private final RoomRepo roomRepo;
    private final SongRepo songRepo;

    private String fieldName;

    public UniqueElementValidator(UserRepo userRepository, RoomRepo roomRepo, SongRepo songRepo) {
        this.userRepository = userRepository;
        this.roomRepo = roomRepo;
        this.songRepo = songRepo;
    }

    @Override
    public void initialize(UniqueValidator annotation) {
        this.fieldName = annotation.fieldName();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        try {
            boolean exists = switch(fieldName) {
                case "username" -> userRepository.existsByUsername(value);
                case "email" -> userRepository.existsByEmail(value);
                case "roomName" -> roomRepo.existsByRoomName(value);
                case "fileName" -> songRepo.existsByFileName(value);
                case "songName" -> songRepo.existsBySongName(value);
                default -> false;
            };

            return !exists;
        } catch (Exception e) {
            return true;
        }
    }
}
