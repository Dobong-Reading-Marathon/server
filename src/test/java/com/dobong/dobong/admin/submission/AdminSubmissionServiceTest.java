package com.dobong.dobong.admin.submission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminSubmissionServiceTest {

    private final AdminSubmissionService service = new AdminSubmissionService(null);

    @Test
    void statusValidation() {
        assertTrue(service.isValidStatus(null));
        assertTrue(service.isValidStatus(""));
        assertTrue(service.isValidStatus("SUBMITTED"));
        assertTrue(service.isValidStatus("APPROVED"));
        assertTrue(service.isValidStatus("REVISION_REQUESTED"));
        assertFalse(service.isValidStatus("DRAFT"));
        assertFalse(service.isValidStatus("abc"));
    }

    @Test
    void keywordPattern() {
        assertNull(AdminSubmissionService.toPattern(null));
        assertNull(AdminSubmissionService.toPattern("   "));
        assertEquals("%홍길%", AdminSubmissionService.toPattern(" 홍길 "));
        assertEquals("%100\\%%", AdminSubmissionService.toPattern("100%"));
    }
}