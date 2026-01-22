package com.assignment.draftly.config;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
public class AppConfig {

    @Bean
    ModelMapper getModelMapper() {
        return new ModelMapper();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

//    @Bean
//    UserDetailsService userDetailsService(
//            PasswordEncoder passwordEncoder,
//            @Value("${app.security.inmemory.username}") String username,
//            @Value("${app.security.inmemory.password}") String password,
//            @Value("${app.security.inmemory.roles}") String roles) {
//        UserDetails user = User.builder()
//                .username(username)
//                .password(passwordEncoder.encode(password))
//                .roles(roles.split(","))
//                .build();
//
//        return new InMemoryUserDetailsManager(user);
//    }
}
