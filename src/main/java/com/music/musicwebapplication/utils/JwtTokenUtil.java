package com.music.musicwebapplication.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtTokenUtil {
    private static final long EXPIRY_DATE = 1000*60*30;

//    @Value("${jwt.security.secret-key}")
    private final String secretString = "*************************************<Own secret key>******************************";

    private final SecretKey key = Keys.hmacShaKeyFor(secretString.getBytes());

    public String generateToken(String username){
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRY_DATE))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUserNameFromToken(String token){
        return extractorMethod(token).getSubject();
    }

    private Claims extractorMethod(String token){
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    public boolean validateToken(String username, String username1, String token) {
        return username.equals(username1) && !isTokenValid(token) ;
    }

    private boolean isTokenValid(String token) {
        return extractorMethod(token).getExpiration().before(new Date());
    }


}
