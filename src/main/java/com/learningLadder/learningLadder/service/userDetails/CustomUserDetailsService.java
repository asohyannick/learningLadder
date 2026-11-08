package com.learningLadder.learningLadder.service.userDetails;

import com.learningLadder.learningLadder.entity.user.User;
import com.learningLadder.learningLadder.enums.accountStatus.AccountStatus;
import com.learningLadder.learningLadder.repository.userRepository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /** "username" here is the user's email: it is the subject of your JWTs. */
    @Override
    public @NonNull UserDetails loadUserByUsername(@NonNull String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(email)
                .filter(u -> !u.isAccountDeleted())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        List<GrantedAuthority> authorities = user.getRole() == null
                ? List.of()
                : List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword() == null ? "" : user.getPassword())
                .authorities(authorities)
                .accountLocked(user.isAccountBlocked())
                .disabled(user.getStatus() != AccountStatus.VERIFIED)
                .build();
    }
}