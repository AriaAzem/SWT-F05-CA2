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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AircraftTakeoffTest {
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
    void preparationMakesGroundedAircraftReady() {
        allowSuccessfulPreparation();
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());

        aircraft.prepareForTakeoff();

        assertEquals(FlightStatus.READY, aircraft.getStatus());
        verify(engine).isOperational();
        verify(fuelSystem).hasEnoughFuel();
        verify(navigationSystem).isRouteAvailable();
        verify(engine, never()).start();
        verifyNoInteractions(landingGear);
    }

    @Test
    void preparationRejectsAnInoperativeEngine() {
        when(engine.isOperational()).thenReturn(false);

        FlightException exception = assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertEquals("Cannot prepare for takeoff: engine is not operational.", exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(engine, never()).start();
        verifyNoInteractions(fuelSystem, navigationSystem, landingGear);
    }

    @Test
    void preparationRejectsInsufficientFuel() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(false);

        FlightException exception = assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertEquals("Cannot prepare for takeoff: insufficient fuel.", exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(engine, never()).start();
        verifyNoInteractions(navigationSystem, landingGear);
    }

    @Test
    void preparationRejectsAnUnavailableRoute() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(false);

        FlightException exception = assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertEquals("Cannot prepare for takeoff: route is unavailable.", exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(engine, never()).start();
        verifyNoInteractions(landingGear);
    }

    @Test
    void preparationPropagatesARouteLookupFailure() {
        IllegalStateException failure = new IllegalStateException("Navigation system is unavailable.");
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenThrow(failure);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, aircraft::prepareForTakeoff);

        assertSame(failure, exception);
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(engine, never()).start();
        verifyNoInteractions(landingGear);
    }

    @Test
    void takeoffStartsEngineAndRetractsLandingGear() {
        allowSuccessfulPreparation();
        doNothing().when(engine).start();
        doNothing().when(landingGear).retract();
        aircraft.prepareForTakeoff();

        aircraft.takeOff();

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(engine, times(1)).start();
        verify(landingGear, times(1)).retract();
        verify(engine, never()).stop();
        verify(landingGear, never()).deploy();
    }

    @Test
    void takeoffRequiresPreparation() {
        FlightException exception = assertThrows(FlightException.class, aircraft::takeOff);

        assertEquals("Operation is not allowed while aircraft status is GROUNDED.", exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verifyNoInteractions(engine, fuelSystem, landingGear, navigationSystem);
    }

    @Test
    void failedEngineStartAbortsTakeoff() {
        IllegalStateException failure = new IllegalStateException("Engine could not start.");
        allowSuccessfulPreparation();
        doThrow(failure).when(engine).start();
        aircraft.prepareForTakeoff();

        FlightException exception = assertThrows(FlightException.class, aircraft::takeOff);

        assertEquals("Takeoff failed.", exception.getMessage());
        assertSame(failure, exception.getCause());
        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());
        verify(engine, times(1)).start();
        verifyNoInteractions(landingGear);
    }

    private void allowSuccessfulPreparation() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);
    }
}
