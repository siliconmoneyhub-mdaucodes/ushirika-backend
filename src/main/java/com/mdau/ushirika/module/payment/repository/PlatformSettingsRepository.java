package com.mdau.ushirika.module.payment.repository;

import com.mdau.ushirika.module.payment.entity.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, UUID> {
}
