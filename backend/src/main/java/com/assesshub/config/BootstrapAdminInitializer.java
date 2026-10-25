package com.assesshub.config;

import com.assesshub.common.TextUtils;
import com.assesshub.domain.Role;
import com.assesshub.domain.User;
import com.assesshub.domain.enums.RoleName;
import com.assesshub.domain.enums.UserStatus;
import com.assesshub.dto.PasswordPolicy;
import com.assesshub.repository.RoleRepository;
import com.assesshub.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Ensures the four roles exist and creates the first Super Admin from environment variables
 * (BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD) when no active Super Admin exists yet.
 * Credentials are never hardcoded.
 */
@Component
@Order(1)
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    public BootstrapAdminInitializer(UserRepository userRepository, RoleRepository roleRepository,
                                     PasswordEncoder passwordEncoder, AppProperties props) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureRoles();
        if (userRepository.existsActiveWithRole(RoleName.SUPER_ADMIN)) {
            return;
        }
        String email = TextUtils.normalizeEmail(props.bootstrap().adminEmail());
        String password = props.bootstrap().adminPassword();
        if (TextUtils.isBlank(email) || TextUtils.isBlank(password)) {
            log.warn("No Super Admin exists. Set BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD and restart to create one.");
            return;
        }
        if (!PasswordPolicy.isValid(password)) {
            log.error("BOOTSTRAP_ADMIN_PASSWORD does not meet the password policy: {}", PasswordPolicy.MESSAGE);
            return;
        }
        Role superAdmin = roleRepository.findByName(RoleName.SUPER_ADMIN).orElseThrow();
        User user = userRepository.findByEmailIgnoreCase(email).orElseGet(User::new);
        user.setEmail(email);
        user.setFullName(TextUtils.isBlank(props.bootstrap().adminName()) ? "Super Admin" : props.bootstrap().adminName().trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        Set<Role> roles = new HashSet<>(user.getRoles());
        roles.add(superAdmin);
        user.setRoles(roles);
        userRepository.save(user);
        log.info("Bootstrap Super Admin created for {}. Change the password after first login and remove "
                + "BOOTSTRAP_ADMIN_PASSWORD from the environment.", email);
    }

    private void ensureRoles() {
        for (RoleName name : RoleName.values()) {
            if (roleRepository.findByName(name).isEmpty()) {
                roleRepository.save(new Role(name, name.name().replace('_', ' ').toLowerCase()));
            }
        }
    }
}
