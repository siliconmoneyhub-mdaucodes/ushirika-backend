package com.mdau.ushirika.module.report.service;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.member.entity.MembershipApplication;
import com.mdau.ushirika.module.member.enums.ApplicationStatus;
import com.mdau.ushirika.module.member.repository.MembershipApplicationRepository;
import com.mdau.ushirika.module.reconciliation.entity.BankReconciliation;
import com.mdau.ushirika.module.reconciliation.repository.BankReconciliationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the two report types that turned out to be missing entirely -- an
 * admin clicking "Download Report" on the Membership Applications or Bank Reconciliation pages
 * got real data from a *different* domain (Program Applications / computed program balances)
 * because the closest-sounding existing endpoint was wired up instead of the actual one, which
 * didn't exist yet. This locks in that the new dedicated reports actually reflect their own
 * domain's data, not a same-named neighbor's.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {

    @Mock private MembershipApplicationRepository membershipApplicationRepository;
    @Mock private BankReconciliationRepository bankReconciliationRepository;

    @InjectMocks
    private ReportService reportService;

    @Test
    void membershipApplicationsXlsx_reflectsRealApplicantData_notProgramApplications() throws Exception {
        User applicant = new User();
        applicant.setFirstName("Brian");
        applicant.setLastName("Wafula");
        applicant.setEmail("brian@example.com");
        applicant.setPhone("+12145550142");

        MembershipApplication app = MembershipApplication.builder()
                .user(applicant)
                .referenceNumber("UWF-APP-TEST0001")
                .status(ApplicationStatus.FORM_SENT)
                .submittedAt(LocalDateTime.of(2026, 8, 1, 9, 0))
                .formSentAt(LocalDateTime.of(2026, 8, 2, 10, 0))
                .registrationFeeWaived(true)
                .build();

        when(membershipApplicationRepository.findAllByOrderByCreatedAtDesc(any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(app)));

        byte[] bytes = reportService.membershipApplicationsXlsx();

        try (var wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = wb.getSheetAt(0);
            var headerRow = sheet.getRow(4);
            assertEquals("Reference", headerRow.getCell(0).getStringCellValue());
            var dataRow = sheet.getRow(5);
            assertEquals("UWF-APP-TEST0001", dataRow.getCell(0).getStringCellValue());
            assertEquals("Brian Wafula", dataRow.getCell(1).getStringCellValue());
            assertEquals("brian@example.com", dataRow.getCell(2).getStringCellValue());
            assertEquals("FORM_SENT", dataRow.getCell(4).getStringCellValue());
        }
    }

    @Test
    void membershipApplicationsXlsx_publicApplicantWithNoUserAccountYet_doesNotThrow() throws Exception {
        // A freshly-submitted public application has no linked User account yet -- falls back to
        // the raw applicant* fields captured at submission time.
        MembershipApplication app = MembershipApplication.builder()
                .referenceNumber("UWF-APP-TEST0002")
                .applicantName("Caroline Weche")
                .applicantEmail("caweche@yahoo.com")
                .status(ApplicationStatus.SUBMITTED)
                .submittedAt(LocalDateTime.of(2026, 8, 27, 12, 0))
                .build();

        when(membershipApplicationRepository.findAllByOrderByCreatedAtDesc(any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(app)));

        assertDoesNotThrow(() -> reportService.membershipApplicationsCsv());
        String csv = new String(reportService.membershipApplicationsCsv(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("Caroline Weche"));
        assertTrue(csv.contains("caweche@yahoo.com"));
    }

    @Test
    void reconciliationXlsx_reflectsBankReconciliationRecords_notComputedBalances() throws Exception {
        BankReconciliation record = BankReconciliation.builder()
                .scope(null)
                .physicalBalance(new BigDecimal("10500.00"))
                .expectedBalance(new BigDecimal("10450.00"))
                .variance(new BigDecimal("50.00"))
                .note("Bank fee not yet recorded in ledger")
                .recordedByName("Jane Doe")
                .recordedByTitle("TREASURER")
                .recordedAt(LocalDateTime.of(2026, 8, 20, 8, 30))
                .build();

        when(bankReconciliationRepository.findByScope(org.mockito.ArgumentMatchers.isNull(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(record)));

        byte[] bytes = reportService.reconciliationXlsx();

        try (var wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = wb.getSheetAt(0);
            var dataRow = sheet.getRow(5);
            assertEquals("Org-wide", dataRow.getCell(1).getStringCellValue());
            assertEquals(10500.00, dataRow.getCell(2).getNumericCellValue(), 0.001);
            assertEquals(10450.00, dataRow.getCell(3).getNumericCellValue(), 0.001);
            assertEquals("Jane Doe (TREASURER)", dataRow.getCell(5).getStringCellValue());
        }
    }
}
