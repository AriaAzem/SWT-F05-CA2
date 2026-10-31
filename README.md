# Airplane Simulator

A deliberately small Java 17 project for the Software Testing assignment on
Mockito and test doubles.

The project models an aircraft and four replaceable components. It does not
simulate real aerodynamics, navigation calculations, or physical flight.

## Requirements

- JDK 17 or newer
- Maven 3.8 or newer

## Build

```bash
mvn clean test
```

There are intentionally no assignment tests in `src/test`. Students should add
their own tests there.

## Project structure

```text
src/main/java/airplane/
├── Aircraft.java
├── components/
│   ├── Engine.java
│   ├── FuelSystem.java
│   ├── LandingGear.java
│   └── NavigationSystem.java
├── exceptions/
│   └── FlightException.java
└── models/
    └── FlightStatus.java
```

## Main concepts

`Aircraft` coordinates the components. All four dependencies are injected
through its constructor, so a test can pass Mockito mocks instead of real
components.

The components are ordinary concrete Java classes. Separate interfaces and
implementation classes are intentionally omitted to keep the codebase focused
on the assignment.

## Flight lifecycle

1. A new aircraft starts in `GROUNDED` state.
2. `prepareForTakeoff()` checks that the engine is operational, enough fuel is
   available, and a route is available. On success, the state becomes `READY`.
3. `takeOff()` starts the engine, retracts the landing gear, and changes the
   state to `IN_FLIGHT`.
4. `cruise(amount)` consumes fuel. If the engine is no longer operational, the
   aircraft makes an emergency landing. A fuel-consumption failure also causes
   an emergency landing and is reported as a `FlightException`.
5. `land()` and `emergencyLand()` deploy the landing gear, stop the engine,
   and change the state to `LANDED`.

An invalid lifecycle operation (for example, calling `takeOff()` before
preparation) throws `FlightException`.

## Notes for students

- `FlightException` is an unchecked exception.
- `IllegalArgumentException` is used for invalid numeric input.
- `Engine.fail()` can simulate an engine failure during a flight.
- `NavigationSystem.setRouteAvailable(...)` can change route availability.
- Mockito and JUnit 5 dependencies are already configured with test scope in
  `pom.xml`.
