package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Role;
import com.patrick.fintech.loan_backend.repository.RoleRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RoleService {
    private final RoleRepository roleRepository;
    public RoleService(RoleRepository roleRepository) { this.roleRepository = roleRepository; }
    public Role save(Role role) { return roleRepository.save(role); }
    public List<Role> getAll() { return roleRepository.findAll(); }
}