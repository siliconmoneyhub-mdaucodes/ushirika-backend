package com.mdau.ushirika.module.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.mdau.ushirika.module.auth.service.UserDetailsServiceImpl;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final CorsConfigurationSource corsConfigurationSource;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    private static final String[] PUBLIC_ROUTES = {
            "/auth/**",
            "/webhooks/**",
            "/health",
            "/actuator/health",
            "/actuator/info",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/public/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ROUTES).permitAll()
                        // Superadmin-only: user role management
                        .requestMatchers("/superadmin/**").hasRole("SUPERADMIN")
                        // Secretary & Chief Whip share the Meetings workspace (meetings, attendance, fines) —
                        // scheduling/attendance-taking and fines/excuses are the same day-to-day workflow.
                        .requestMatchers(HttpMethod.GET, "/admin/meetings/**", "/admin/fines/**", "/admin/attendance/**")
                                .hasAnyRole("SECRETARY", "CHIEF_WHIP", "ADMIN", "SUPERADMIN", "LEADERSHIP")
                        .requestMatchers("/admin/meetings/**", "/admin/fines/**", "/admin/attendance/**")
                                .hasAnyRole("SECRETARY", "CHIEF_WHIP", "ADMIN", "SUPERADMIN")
                        // Secretary — read-only membership records (records-keeping, not decision-making)
                        .requestMatchers(HttpMethod.GET, "/admin/membership/**")
                                .hasAnyRole("SECRETARY", "ADMIN", "SUPERADMIN", "LEADERSHIP")
                        // Member directory — Secretary (records) and Financial Admin (dues/contribution lookups) both need it
                        .requestMatchers(HttpMethod.GET, "/admin/members/**")
                                .hasAnyRole("SECRETARY", "FINANCIAL_ADMIN", "ADMIN", "SUPERADMIN", "LEADERSHIP")
                        // Compliance — governing documents, reinstatement petitions, read-only audit trail
                        .requestMatchers(HttpMethod.GET, "/admin/constitution/**", "/admin/reinstatement/**", "/admin/audit-logs/**")
                                .hasAnyRole("COMPLIANCE", "ADMIN", "SUPERADMIN", "LEADERSHIP")
                        .requestMatchers("/admin/constitution/**", "/admin/reinstatement/**")
                                .hasAnyRole("COMPLIANCE", "ADMIN", "SUPERADMIN")
                        // Financial roles — their own nav's admin sub-paths, previously only reachable via /financial/**.
                        // Dues/contributions: recording payments is a mutation, so both roles get full access.
                        .requestMatchers(HttpMethod.GET,
                                "/admin/dues/**", "/admin/contributions/**")
                                .hasAnyRole("FINANCIAL_ADMIN", "FINANCIAL_OFFICIAL", "ADMIN", "SUPERADMIN", "LEADERSHIP")
                        .requestMatchers(
                                "/admin/dues/**", "/admin/contributions/**")
                                .hasAnyRole("FINANCIAL_ADMIN", "FINANCIAL_OFFICIAL", "ADMIN", "SUPERADMIN")
                        // Notifications: view-only for both financial roles — broadcasting stays ADMIN/SUPERADMIN.
                        .requestMatchers(HttpMethod.GET, "/admin/notifications/**")
                                .hasAnyRole("FINANCIAL_ADMIN", "FINANCIAL_OFFICIAL", "ADMIN", "SUPERADMIN", "LEADERSHIP")
                        // Payment links/benevolence/MGR/loans/CSV reports: Financial Admin only (not delegated to officials).
                        .requestMatchers(HttpMethod.GET,
                                "/admin/payment-links/**", "/admin/benevolence/**", "/admin/mgr/**", "/admin/loans/**", "/admin/reports/**")
                                .hasAnyRole("FINANCIAL_ADMIN", "ADMIN", "SUPERADMIN", "LEADERSHIP")
                        .requestMatchers(
                                "/admin/payment-links/**", "/admin/benevolence/**", "/admin/mgr/**", "/admin/loans/**")
                                .hasAnyRole("FINANCIAL_ADMIN", "ADMIN", "SUPERADMIN")
                        // Role-scoped dashboard summaries
                        .requestMatchers(HttpMethod.GET, "/admin/dashboard/records").hasAnyRole("SECRETARY", "ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.GET, "/admin/dashboard/discipline").hasAnyRole("CHIEF_WHIP", "ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.GET, "/admin/dashboard/compliance").hasAnyRole("COMPLIANCE", "ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.GET, "/admin/dashboard").hasAnyRole("FINANCIAL_ADMIN", "ADMIN", "SUPERADMIN", "LEADERSHIP")
                        .requestMatchers(HttpMethod.GET, "/admin/reports/financial").hasAnyRole("FINANCIAL_ADMIN", "ADMIN", "SUPERADMIN", "LEADERSHIP")
                        // Leadership (read-only): full GET access to admin data, no mutations
                        .requestMatchers(HttpMethod.GET, "/admin/**").hasAnyRole("ADMIN", "SUPERADMIN", "LEADERSHIP")
                        // Admin + Superadmin: all other (mutating) admin operations
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "SUPERADMIN")
                        // Financial admin delegation management (must be before /financial/**)
                        .requestMatchers("/financial/admin/**").hasRole("FINANCIAL_ADMIN")
                        // Manual payment operations: financial roles + read-only admins (write guards in service)
                        .requestMatchers("/financial/**").hasAnyRole("FINANCIAL_ADMIN", "FINANCIAL_OFFICIAL", "ADMIN", "SUPERADMIN")
                        // Applicant onboarding — restricted to APPLICANT role only, never full members
                        .requestMatchers("/onboarding/**").hasRole("APPLICANT")
                        // Everything else: any authenticated user
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
