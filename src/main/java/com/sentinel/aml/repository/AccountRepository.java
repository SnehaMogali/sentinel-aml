package com.sentinel.aml.repository;

import com.sentinel.aml.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountRepository extends JpaRepository<Account, String> {

    List<Account> findByCustomer_CustomerId(String customerId);
}
