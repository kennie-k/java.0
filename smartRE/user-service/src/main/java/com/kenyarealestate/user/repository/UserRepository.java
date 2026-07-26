package com.kenyarealestate.user.repository;

import com.kenyarealestate.user.entity.User;
import com.kenyarealestate.user.entity.Role;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByResetTokenHash(String resetTokenHash);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    boolean existsByRole(Role role);
    Page<User> findAll(Pageable pageable);
}
