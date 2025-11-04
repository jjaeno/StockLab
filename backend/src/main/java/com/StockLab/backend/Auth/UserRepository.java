package com.StockLab.backend.Auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * 사용자 Repository
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByEmail(String email);
}
