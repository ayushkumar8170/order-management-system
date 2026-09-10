package com.example.oms.dao;

import com.example.oms.model.Customer;

import java.util.List;
import java.util.Optional;

public interface CustomerDao {

    Optional<Customer> findById(long id);

    Optional<Customer> findByEmail(String email);

    List<Customer> findAll();

    Customer save(Customer customer);
}
