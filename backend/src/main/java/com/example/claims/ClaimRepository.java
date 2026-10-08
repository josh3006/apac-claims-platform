package com.example.claims;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

interface ClaimRepository extends JpaRepository<Claim, UUID> {
    List<Claim> findAllByOrderByCreatedAtDesc();
    List<Claim> findByClaimantOrderByCreatedAtDesc(String claimant);
}
