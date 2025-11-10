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
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/h2-console/**",
                                "/api/music/**",
                                "/app/music/ws/**",
                                "/app/music/public/**",
                                "/app/music/audio/public/streamSong/**",
                                "/favicon.ico",
                                "/app_logo.png",
                                "/css/**",
                                "/js/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                .headers(headers ->
                        headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                )

                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }


//    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
//
//        http
//                // Disable CSRF for API usage (your app uses JWT)
//                .csrf(AbstractHttpConfigurer::disable)
//
//                // H2 console support
//                .headers(headers -> headers
//                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
//                )
//
//                // Authorization rules
//                .authorizeHttpRequests(auth -> auth
//                        .requestMatchers(
//                                "/", "/h2-console/**",
//                                "/api/music/**",
//                                "/app/music/ws/**",
//                                "/app/music/public/**",
//                                "/app/music/audio/public/streamSong/**",
//                                "/favicon.ico",
//                                "/app_logo.png",
//                                "/css/**",
//                                "/js/**"
//                        ).permitAll()
//
//                        .requestMatchers("/app/music/admin/**").hasRole("ADMIN")
//
//                        .requestMatchers(
//                                "/app/music/room/**",
//                                "/app/music/chat/**",
//                                "/app/music/dashboard",
//                                "/app/music/audio/searchSong",
//                                "/app/music/audio/fetchAllSongs"
//                        ).authenticated()
//
//                        .anyRequest().authenticated()
//                )
//
//                // Login config
//                .formLogin(form -> form
//                        .loginPage("/app/music/public/login")
//                        .defaultSuccessUrl("/app/music/dashboard", true)
//                        .failureUrl("/app/music/public/login?error=true")
//                        .permitAll()
//                )
//
//                // Logout config
//                .logout(logout -> logout
//                        .logoutUrl("/app/music/public/logout")
//                        .logoutSuccessUrl("/app/music/public/login?logout=true").invalidateHttpSession(true)
//                        .permitAll()
//                )
//
//                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
//
//                .sessionManagement(session ->
//                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
//                );
//
//        return http.build();
//    }


    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CustomUserDetailService customUserDetailService() {
        return new CustomUserDetailService();
    }

    @Bean
    AuthenticationManager manager(CustomUserDetailService service, PasswordEncoder encoder) throws Exception {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(service);
        authenticationProvider.setPasswordEncoder(encoder);
        return new ProviderManager(authenticationProvider);
    }


}
