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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AircraftFlightTest {
    private static final double FUEL_TO_CONSUME = 12.5;
    private static final double ZERO_FUEL = 0;
    private static final double NEGATIVE_FUEL = -5;

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
    void cruise_consumesTheRequestedFuel() {
        putAircraftInFlight();

        aircraft.cruise(FUEL_TO_CONSUME);

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(fuelSystem, times(1)).consumeFuel(FUEL_TO_CONSUME);
        verifyNoLandingHappened();
    }

    @Test
    void cruise_rejectsZeroFuel() {
        putAircraftInFlight();

        assertThrows(IllegalArgumentException.class, () -> aircraft.cruise(ZERO_FUEL));

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(fuelSystem, never()).consumeFuel(any(Double.class));
        verifyNoLandingHappened();
    }

    @Test
    void cruise_rejectsNegativeFuel() {
        putAircraftInFlight();

        assertThrows(IllegalArgumentException.class, () -> aircraft.cruise(NEGATIVE_FUEL));

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(fuelSystem, never()).consumeFuel(any(Double.class));
        verifyNoLandingHappened();
    }

    @Test
    void cruise_landsWithoutExceptionWhenEngineFails() {
        putAircraftInFlight();
        reset(engine);
        when(engine.isOperational()).thenReturn(false);

        aircraft.cruise(FUEL_TO_CONSUME);

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(fuelSystem, never()).consumeFuel(any(Double.class));
        verifyLandingHappenedOnce();
    }

    @Test
    void cruise_landsAndReportsCauseWhenFuelConsumptionFails() {
        IllegalStateException failure = new IllegalStateException();
        putAircraftInFlight();
        doThrow(failure).when(fuelSystem).consumeFuel(any(Double.class));

        FlightException exception = assertThrows(
                FlightException.class, () -> aircraft.cruise(FUEL_TO_CONSUME));

        assertSame(failure, exception.getCause());
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(fuelSystem, times(1)).consumeFuel(FUEL_TO_CONSUME);
        verifyLandingHappenedOnce();
    }

    @Test
    void cruise_landsWhenFuelConsumptionFailsOnLaterCall() {
        IllegalStateException failure = new IllegalStateException();
        putAircraftInFlight();
        doNothing().doThrow(failure).when(fuelSystem).consumeFuel(any(Double.class));
        aircraft.cruise(FUEL_TO_CONSUME);

        FlightException exception = assertThrows(
                FlightException.class, () -> aircraft.cruise(FUEL_TO_CONSUME));

        assertSame(failure, exception.getCause());
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(fuelSystem, times(2)).consumeFuel(FUEL_TO_CONSUME);
        verifyLandingHappenedOnce();
    }

    @Test
    void land_deploysLandingGearAndStopsEngine() {
        putAircraftInFlight();

        aircraft.land();

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verifyLandingHappenedOnce();
    }

    @Test
    void emergencyLand_deploysLandingGearAndStopsEngine() {
        putAircraftInFlight();

        aircraft.emergencyLand();

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verifyLandingHappenedOnce();
    }

    @Test
    void cruise_requiresTakeoff() {
        assertThrows(FlightException.class, () -> aircraft.cruise(FUEL_TO_CONSUME));

        verifyNothingHappenedOnTheGround();
    }

    @Test
    void land_requiresFlight() {
        assertThrows(FlightException.class, aircraft::land);

        verifyNothingHappenedOnTheGround();
    }

    @Test
    void emergencyLand_requiresFlight() {
        assertThrows(FlightException.class, aircraft::emergencyLand);

        verifyNothingHappenedOnTheGround();
    }

    @Test
    void prepareForTakeoff_allowsLandedAircraft() {
        putAircraftInFlight();
        aircraft.land();

        aircraft.prepareForTakeoff();

        assertEquals(FlightStatus.READY, aircraft.getStatus());
    }

    private void putAircraftInFlight() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);
        aircraft.prepareForTakeoff();
        aircraft.takeOff();
    }

    private void verifyLandingHappenedOnce() {
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    private void verifyNoLandingHappened() {
        verify(landingGear, never()).deploy();
        verify(engine, never()).stop();
    }

    private void verifyNothingHappenedOnTheGround() {
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verifyNoInteractions(engine, fuelSystem, landingGear, navigationSystem);
    }
}
