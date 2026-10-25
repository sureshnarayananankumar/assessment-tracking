package com.assesshub.repository;

import com.assesshub.domain.Role;
import com.assesshub.domain.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);

    List<Role> findByNameIn(Collection<RoleName> names);
}
