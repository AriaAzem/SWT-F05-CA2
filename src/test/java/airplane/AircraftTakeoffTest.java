package airplane;

import airplane.components.Engine;
import airplane.components.FuelSystem;
import airplane.components.LandingGear;
import airplane.components.NavigationSystem;
import airplane.exceptions.FlightException;
import airplane.models.FlightStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AircraftTakeoffTest {
    private static final String ENGINE_NOT_OPERATIONAL_MESSAGE =
            "Cannot prepare for takeoff: engine is not operational.";
    private static final String INSUFFICIENT_FUEL_MESSAGE =
            "Cannot prepare for takeoff: insufficient fuel.";
    private static final String ROUTE_UNAVAILABLE_MESSAGE =
            "Cannot prepare for takeoff: route is unavailable.";
    private static final String NOT_ALLOWED_WHILE_GROUNDED_MESSAGE =
            "Operation is not allowed while aircraft status is GROUNDED.";
    private static final String NOT_ALLOWED_WHILE_READY_MESSAGE =
            "Operation is not allowed while aircraft status is READY.";
    private static final String TAKEOFF_FAILED_MESSAGE = "Takeoff failed.";

    private Engine engine;
    private FuelSystem fuelSystem;
    private LandingGear landingGear;
    private NavigationSystem navigationSystem;
    private Aircraft aircraft;

    @BeforeEach
    void setUp() {
        engine = mock(Engine.class);
        fuelSystem = mock(FuelSystem.class);
        landingGear = mock(LandingGear.class);
        navigationSystem = mock(NavigationSystem.class);
        aircraft = new Aircraft(engine, fuelSystem, landingGear, navigationSystem);
    }

    @Test
    void getStatus_returnsGroundedForNewAircraft() {
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
    }

    @Test
    void prepareForTakeoff_makesGroundedAircraftReady() {
        allowSuccessfulPreparation();

        aircraft.prepareForTakeoff();

        assertEquals(FlightStatus.READY, aircraft.getStatus());
        verifyTakeoffDidNotBegin();
    }

    @Test
    void prepareForTakeoff_rejectsInoperativeEngine() {
        when(engine.isOperational()).thenReturn(false);

        FlightException exception = assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertEquals(ENGINE_NOT_OPERATIONAL_MESSAGE, exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verifyTakeoffDidNotBegin();
        verifyNoInteractions(fuelSystem, navigationSystem);
    }

    @Test
    void prepareForTakeoff_rejectsInsufficientFuel() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(false);

        FlightException exception = assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertEquals(INSUFFICIENT_FUEL_MESSAGE, exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verifyTakeoffDidNotBegin();
        verifyNoInteractions(navigationSystem);
    }

    @Test
    void prepareForTakeoff_rejectsUnavailableRoute() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(false);

        FlightException exception = assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertEquals(ROUTE_UNAVAILABLE_MESSAGE, exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verifyTakeoffDidNotBegin();
    }

    @Test
    void prepareForTakeoff_propagatesRouteLookupFailure() {
        IllegalStateException failure = new IllegalStateException();
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenThrow(failure);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, aircraft::prepareForTakeoff);

        assertSame(failure, exception);
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verifyTakeoffDidNotBegin();
    }

    @Test
    void prepareForTakeoff_rejectsAlreadyReadyAircraft() {
        allowSuccessfulPreparation();
        aircraft.prepareForTakeoff();

        FlightException exception = assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertEquals(NOT_ALLOWED_WHILE_READY_MESSAGE, exception.getMessage());
        assertEquals(FlightStatus.READY, aircraft.getStatus());
        verifyTakeoffDidNotBegin();
    }

    @Test
    void takeOff_startsEngineAndRetractsLandingGear() {
        allowSuccessfulPreparation();
        aircraft.prepareForTakeoff();

        aircraft.takeOff();

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(engine, times(1)).start();
        verify(landingGear, times(1)).retract();
        verify(engine, never()).stop();
        verify(landingGear, never()).deploy();
    }

    @Test
    void takeOff_requiresPreparation() {
        FlightException exception = assertThrows(FlightException.class, aircraft::takeOff);

        assertEquals(NOT_ALLOWED_WHILE_GROUNDED_MESSAGE, exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verifyNoInteractions(engine, fuelSystem, landingGear, navigationSystem);
    }

    @Test
    void takeOff_abortsWhenEngineStartFails() {
        IllegalStateException failure = new IllegalStateException();
        allowSuccessfulPreparation();
        doThrow(failure).when(engine).start();
        aircraft.prepareForTakeoff();

        FlightException exception = assertThrows(FlightException.class, aircraft::takeOff);

        assertEquals(TAKEOFF_FAILED_MESSAGE, exception.getMessage());
        assertSame(failure, exception.getCause());
        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());
        verify(engine, times(1)).start();
        verifyNoInteractions(landingGear);
    }

    @Test
    void takeOff_abortsWhenLandingGearRetractionFails() {
        IllegalStateException failure = new IllegalStateException();
        allowSuccessfulPreparation();
        doThrow(failure).when(landingGear).retract();
        aircraft.prepareForTakeoff();

        FlightException exception = assertThrows(FlightException.class, aircraft::takeOff);

        assertEquals(TAKEOFF_FAILED_MESSAGE, exception.getMessage());
        assertSame(failure, exception.getCause());
        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());
        verify(engine, times(1)).start();
        verify(landingGear, times(1)).retract();
    }

    private void allowSuccessfulPreparation() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);
    }

    private void verifyTakeoffDidNotBegin() {
        verify(engine, never()).start();
        verifyNoInteractions(landingGear);
    }
}
