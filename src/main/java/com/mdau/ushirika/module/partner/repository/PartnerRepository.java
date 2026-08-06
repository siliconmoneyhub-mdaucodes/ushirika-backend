package com.mdau.ushirika.module.partner.repository;

import com.mdau.ushirika.module.partner.entity.Partner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PartnerRepository extends JpaRepository<Partner, UUID> {

    List<Partner> findAllByOrderBySortOrderAscNameAsc();

    List<Partner> findAllByActiveTrueOrderBySortOrderAscNameAsc();
}
