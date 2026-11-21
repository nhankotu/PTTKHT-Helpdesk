package com.helpdesk.modules.identity.config;

import com.helpdesk.modules.identity.entity.AppUser;
import com.helpdesk.modules.identity.entity.Role;
import com.helpdesk.modules.identity.entity.RoleCode;
import com.helpdesk.modules.identity.entity.SupportTeam;
import com.helpdesk.modules.identity.repository.AppUserRepository;
import com.helpdesk.modules.identity.repository.RoleRepository;
import com.helpdesk.modules.identity.repository.SupportTeamRepository;
import com.helpdesk.modules.identity.service.CustomerAccountPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo bộ tài khoản và nhóm hỗ trợ mẫu cho môi trường dev (profile = "dev").
 * Mật khẩu mặc định: Password123!
 */
@Component
@Profile("dev")
public class DevSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedRunner.class);
    private static final String DEFAULT_DEV_PASSWORD = "Password123!";

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final SupportTeamRepository supportTeamRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<CustomerAccountPort> customerAccountPortProvider;

    public DevSeedRunner(
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            SupportTeamRepository supportTeamRepository,
            PasswordEncoder passwordEncoder,
            ObjectProvider<CustomerAccountPort> customerAccountPortProvider
    ) {
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.supportTeamRepository = supportTeamRepository;
        this.passwordEncoder = passwordEncoder;
        this.customerAccountPortProvider = customerAccountPortProvider;
    }

    @Override
    @Transactional
    public void run(String... args) {
        AppUser admin = ensureUser("Quản trị viên", "admin@helpdesk.local", "0900000001", RoleCode.ADMIN, false);
        AppUser supervisor = ensureUser("Lê Quốc Bảo (Supervisor)", "supervisor@helpdesk.local", "0900000002", RoleCode.SUPERVISOR, true);
        AppUser agent = ensureUser("Nguyễn Văn An (Agent)", "agent.an@helpdesk.local", "0900000003", RoleCode.AGENT, true);
        AppUser customer = ensureUser("Trần Minh Quân (Customer)", "customer@helpdesk.local", "0900000004", RoleCode.CUSTOMER, false);

        CustomerAccountPort customerPort = customerAccountPortProvider.getIfAvailable();
        if (customerPort != null && customerPort.findCustomerIdByUserId(customer.getId()).isEmpty()) {
            customerPort.createVerifiedCustomerForUser(
                    customer.getId(), customer.getFullName(), customer.getEmail(), customer.getPhone()
            );
        }

        supportTeamRepository.findByCode("SUPPORT_GENERAL").orElseGet(() -> {
            SupportTeam team = new SupportTeam("SUPPORT_GENERAL", "Nhóm Hỗ trợ Chung", supervisor);
            team.addMember(agent);
            team.addMember(supervisor);
            return supportTeamRepository.save(team);
        });

        log.info("Dev seed verified (admin={}, supervisor={}, agent={}). Default password: {}",
                admin.getEmail(), supervisor.getEmail(), agent.getEmail(), DEFAULT_DEV_PASSWORD);
    }

    private AppUser ensureUser(String fullName, String email, String phone, RoleCode roleCode, boolean available) {
        return appUserRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            Role role = roleRepository.findByCode(roleCode)
                    .orElseThrow(() -> new IllegalStateException("Missing seeded role: " + roleCode));
            AppUser user = new AppUser(fullName, email, passwordEncoder.encode(DEFAULT_DEV_PASSWORD), phone);
            user.setAvailable(available);
            user.addRole(role);
            return appUserRepository.save(user);
        });
    }
}
