package com.music.musicwebapplication.config;

import com.music.musicwebapplication.service.CustomUserDetailService;
import com.music.musicwebapplication.utils.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class AppSecurity {


    private final JwtAuthenticationFilter filter;

    @Autowired
    public AppSecurity(JwtAuthenticationFilter filter) {
        this.filter = filter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity https) throws Exception {
        https.csrf(csrf-> csrf
                        .ignoringRequestMatchers("/h2-console/**",
                                "/api/music/**",
                                "/app/music/**"
                        ))
                .headers(header-> header
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/h2-console/**",
                                "/api/music/**",
                                "/app/music/ws/**",
                                "/app/music/login",
                                "app/music/authenticate",
                                "/app/music/register")
                        .permitAll()
                        .requestMatchers("/app/music/**","/app/music/chat/**").authenticated()
                        .anyRequest().authenticated())

                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);

        return https.build();
    }

    @Bean
    PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }
    @Bean
    public CustomUserDetailService customUserDetailService(){
        return  new CustomUserDetailService();
    }
    @Bean
    AuthenticationManager manager(CustomUserDetailService service, PasswordEncoder encoder) throws Exception{
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(service);
        authenticationProvider.setPasswordEncoder(encoder);
        return new ProviderManager(authenticationProvider);
    }


}
