package com.helpdesk.modules.intake.service;

import com.helpdesk.modules.identity.service.CustomerAccountPort;
import com.helpdesk.modules.intake.entity.Customer;
import com.helpdesk.modules.intake.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CustomerAccountService implements CustomerAccountPort {

    private final CustomerRepository customerRepository;

    public CustomerAccountService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findCustomerIdByUserId(Long userId) {
        return customerRepository.findByUserId(userId).map(Customer::getId);
    }

    @Override
    @Transactional
    public Long createVerifiedCustomerForUser(Long userId, String fullName, String email, String phone) {
        return customerRepository.findByUserId(userId)
                .map(Customer::getId)
                .orElseGet(() -> customerRepository.save(
                        new Customer(userId, fullName, email, phone, true)
                ).getId());
    }
}
