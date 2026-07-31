package com.uavcommand.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DemoAuthorizationServiceTest {
    @Test
    void allowsFlightOperatorInLocalDemoMode() {
        DemoAuthorizationService service = new DemoAuthorizationService(true);

        assertEquals("张晨", service.requireControlOperator(" 张晨 ", "FLIGHT_OPERATOR"));
    }

    @Test
    void rejectsViewerAndDisabledDemoMode() {
        DemoAuthorizationService localService = new DemoAuthorizationService(true);
        DemoAuthorizationService sharedService = new DemoAuthorizationService(false);

        assertThrows(SecurityException.class, () -> localService.requireControlOperator("访客", "VIEWER"));
        assertThrows(SecurityException.class, () -> sharedService.requireControlOperator("张晨", "ADMIN"));
    }
}
