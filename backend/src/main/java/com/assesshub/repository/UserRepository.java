package com.assesshub.repository;

import com.assesshub.domain.User;
import com.assesshub.domain.enums.RoleName;
import com.assesshub.domain.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select count(u) > 0 from User u join u.roles r where r.name = :role and u.status = com.assesshub.domain.enums.UserStatus.ACTIVE")
    boolean existsActiveWithRole(@Param("role") RoleName role);

    @Query("select count(u) from User u join u.roles r where r.name = :role and u.status = com.assesshub.domain.enums.UserStatus.ACTIVE")
    long countActiveWithRole(@Param("role") RoleName role);

    @Query(value = """
            select u from User u
            where (:search is null or lower(u.email) like :search or lower(u.fullName) like :search)
              and (:status is null or u.status = :status)
              and (:role is null or exists (select 1 from User u2 join u2.roles r where u2 = u and r.name = :role))
            """,
            countQuery = """
            select count(u) from User u
            where (:search is null or lower(u.email) like :search or lower(u.fullName) like :search)
              and (:status is null or u.status = :status)
              and (:role is null or exists (select 1 from User u2 join u2.roles r where u2 = u and r.name = :role))
            """)
    Page<User> search(@Param("search") String searchPattern,
                      @Param("role") RoleName role,
                      @Param("status") UserStatus status,
                      Pageable pageable);
}
