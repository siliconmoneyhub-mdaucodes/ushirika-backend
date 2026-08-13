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
                        // Superadmin-only: user role/capability management. Deliberately role-only, no
                        // capability alternative — granting capabilities is itself the higher-trust action
                        // this gate protects, so it can't be delegated via a capability without letting
                        // someone hand out more access than they were given.
                        .requestMatchers("/superadmin/**").hasRole("SUPERADMIN")
                        // Secretary & Chief Whip share the Meetings workspace (meetings, attendance, fines) —
                        // scheduling/attendance-taking and fines/excuses are the same day-to-day workflow.
                        // Each domain also accepts its matching Capability, independent of role (see User#getAuthorities).
                        .requestMatchers(HttpMethod.GET, "/admin/meetings/**", "/admin/fines/**", "/admin/attendance/**")
                                .hasAnyAuthority("ROLE_SECRETARY", "ROLE_CHIEF_WHIP", "ROLE_ADMIN", "ROLE_SUPERADMIN", "ROLE_LEADERSHIP",
                                        "CAP_MEETINGS_ATTENDANCE", "CAP_DISCIPLINE")
                        .requestMatchers("/admin/meetings/**", "/admin/fines/**", "/admin/attendance/**")
                                .hasAnyAuthority("ROLE_SECRETARY", "ROLE_CHIEF_WHIP", "ROLE_ADMIN", "ROLE_SUPERADMIN",
                                        "CAP_MEETINGS_ATTENDANCE", "CAP_DISCIPLINE")
                        // Applications — review, send form, approve membership
                        .requestMatchers(HttpMethod.GET, "/admin/membership/**")
                                .hasAnyAuthority("ROLE_SECRETARY", "ROLE_ADMIN", "ROLE_SUPERADMIN", "ROLE_LEADERSHIP", "CAP_APPLICATIONS")
                        .requestMatchers("/admin/membership/**")
                                .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPERADMIN", "CAP_APPLICATIONS")
                        // Member directory — Secretary (records) and Financial Admin (dues/contribution lookups) both need it
                        .requestMatchers(HttpMethod.GET, "/admin/members/**")
                                .hasAnyAuthority("ROLE_SECRETARY", "ROLE_FINANCIAL_ADMIN", "ROLE_ADMIN", "ROLE_SUPERADMIN", "ROLE_LEADERSHIP", "CAP_MEMBERS")
                        .requestMatchers("/admin/members/**")
                                .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPERADMIN", "CAP_MEMBERS")
                        // Compliance — governing documents, reinstatement petitions, read-only audit trail
                        .requestMatchers(HttpMethod.GET, "/admin/constitution/**", "/admin/reinstatement/**", "/admin/audit-logs/**")
                                .hasAnyAuthority("ROLE_COMPLIANCE", "ROLE_ADMIN", "ROLE_SUPERADMIN", "ROLE_LEADERSHIP",
                                        "CAP_CONSTITUTION", "CAP_REINSTATEMENT", "CAP_AUDIT_LOG")
                        .requestMatchers("/admin/constitution/**", "/admin/reinstatement/**")
                                .hasAnyAuthority("ROLE_COMPLIANCE", "ROLE_ADMIN", "ROLE_SUPERADMIN", "CAP_CONSTITUTION", "CAP_REINSTATEMENT")
                        // Financial roles — their own nav's admin sub-paths, previously only reachable via /financial/**.
                        // Dues/contributions: recording payments is a mutation, so both roles get full access.
                        .requestMatchers(HttpMethod.GET,
                                "/admin/dues/**", "/admin/contributions/**")
                                .hasAnyAuthority("ROLE_FINANCIAL_ADMIN", "ROLE_FINANCIAL_OFFICIAL", "ROLE_ADMIN", "ROLE_SUPERADMIN", "ROLE_LEADERSHIP", "CAP_FINANCE_DUES")
                        .requestMatchers(
                                "/admin/dues/**", "/admin/contributions/**")
                                .hasAnyAuthority("ROLE_FINANCIAL_ADMIN", "ROLE_FINANCIAL_OFFICIAL", "ROLE_ADMIN", "ROLE_SUPERADMIN", "CAP_FINANCE_DUES")
                        // Notifications — view for financial roles, broadcast for Admin/Superadmin/CAP_NOTIFICATIONS.
                        .requestMatchers(HttpMethod.GET, "/admin/notifications/**")
                                .hasAnyAuthority("ROLE_FINANCIAL_ADMIN", "ROLE_FINANCIAL_OFFICIAL", "ROLE_ADMIN", "ROLE_SUPERADMIN", "ROLE_LEADERSHIP", "CAP_NOTIFICATIONS")
                        .requestMatchers("/admin/notifications/**")
                                .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPERADMIN", "CAP_NOTIFICATIONS")
                        // /admin/reports/financial is narrower than the /admin/reports/** rule below and must
                        // be declared first -- requestMatchers are evaluated in declaration order (first match
                        // wins), so if this stayed after the broad rule it would never actually apply (the
                        // broad rule's CAP_FINANCE_ADVANCED-only authority set would silently override this
                        // one's CAP_FINANCE_DUES OR CAP_FINANCE_ADVANCED, blocking CAP_FINANCE_DUES-only users).
                        .requestMatchers(HttpMethod.GET, "/admin/reports/financial")
                                .hasAnyAuthority("ROLE_FINANCIAL_ADMIN", "ROLE_ADMIN", "ROLE_SUPERADMIN", "ROLE_LEADERSHIP", "CAP_FINANCE_DUES", "CAP_FINANCE_ADVANCED")
                        // Payment links/benevolence/MGR/loans/CSV reports/welfare/programs/money-flow:
                        // Financial Admin only (not delegated to officials) at the role level, or CAP_FINANCE_ADVANCED.
                        .requestMatchers(HttpMethod.GET,
                                "/admin/payment-links/**", "/admin/benevolence/**", "/admin/mgr/**", "/admin/loans/**",
                                "/admin/reports/**", "/admin/welfare/**", "/admin/programs/**", "/admin/money-flow/**")
                                .hasAnyAuthority("ROLE_FINANCIAL_ADMIN", "ROLE_ADMIN", "ROLE_SUPERADMIN", "ROLE_LEADERSHIP", "CAP_FINANCE_ADVANCED")
                        .requestMatchers(
                                "/admin/payment-links/**", "/admin/benevolence/**", "/admin/mgr/**", "/admin/loans/**",
                                "/admin/welfare/**", "/admin/programs/**", "/admin/money-flow/**")
                                .hasAnyAuthority("ROLE_FINANCIAL_ADMIN", "ROLE_ADMIN", "ROLE_SUPERADMIN", "CAP_FINANCE_ADVANCED")
                        // Content — events, content (news/articles), albums (gallery), forum (stories),
                        // scholarships, donations, partners, leadership. Paths are the real controller
                        // @RequestMapping values, not the frontend route names (e.g. gallery UI -> /admin/albums API).
                        .requestMatchers(HttpMethod.GET,
                                "/admin/events/**", "/admin/content/**", "/admin/albums/**", "/admin/forum/**",
                                "/admin/scholarships/**", "/admin/donations/**", "/admin/partners/**", "/admin/leadership/**")
                                .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPERADMIN", "ROLE_LEADERSHIP", "CAP_CONTENT")
                        .requestMatchers(
                                "/admin/events/**", "/admin/content/**", "/admin/albums/**", "/admin/forum/**",
                                "/admin/scholarships/**", "/admin/donations/**", "/admin/partners/**", "/admin/leadership/**")
                                .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPERADMIN", "CAP_CONTENT")
                        // Elections
                        .requestMatchers(HttpMethod.GET, "/admin/elections/**")
                                .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPERADMIN", "ROLE_LEADERSHIP", "CAP_ELECTIONS")
                        .requestMatchers("/admin/elections/**")
                                .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPERADMIN", "CAP_ELECTIONS")
                        // Role-scoped dashboard summaries
                        .requestMatchers(HttpMethod.GET, "/admin/dashboard/records")
                                .hasAnyAuthority("ROLE_SECRETARY", "ROLE_ADMIN", "ROLE_SUPERADMIN", "CAP_APPLICATIONS", "CAP_MEMBERS", "CAP_MEETINGS_ATTENDANCE")
                        .requestMatchers(HttpMethod.GET, "/admin/dashboard/discipline")
                                .hasAnyAuthority("ROLE_CHIEF_WHIP", "ROLE_ADMIN", "ROLE_SUPERADMIN", "CAP_DISCIPLINE")
                        .requestMatchers(HttpMethod.GET, "/admin/dashboard/compliance")
                                .hasAnyAuthority("ROLE_COMPLIANCE", "ROLE_ADMIN", "ROLE_SUPERADMIN", "CAP_CONSTITUTION", "CAP_REINSTATEMENT")
                        .requestMatchers(HttpMethod.GET, "/admin/dashboard")
                                .hasAnyAuthority("ROLE_FINANCIAL_ADMIN", "ROLE_ADMIN", "ROLE_SUPERADMIN", "ROLE_LEADERSHIP", "CAP_FINANCE_DUES", "CAP_FINANCE_ADVANCED")
                        // Leadership (read-only): full GET access to admin data, no mutations
                        .requestMatchers(HttpMethod.GET, "/admin/**").hasAnyRole("ADMIN", "SUPERADMIN", "LEADERSHIP")
                        // Admin + Superadmin: all other (mutating) admin operations — includes officials/role
                        // management (see the /superadmin/** comment above for why that's not capability-delegable)
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "SUPERADMIN")
                        // Financial admin delegation management (must be before /financial/**)
                        .requestMatchers("/financial/admin/**").hasRole("FINANCIAL_ADMIN")
                        // Manual payment operations: financial roles + read-only admins (write guards in service)
                        .requestMatchers("/financial/**").hasAnyAuthority("ROLE_FINANCIAL_ADMIN", "ROLE_FINANCIAL_OFFICIAL", "ROLE_ADMIN", "ROLE_SUPERADMIN", "CAP_FINANCE_DUES")
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
