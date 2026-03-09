# E2E fixture projects

These projects are intentionally small so that E2E tests can demonstrate what FUL can analyze
with easy-to-read Java examples.

`CliE2ETest` uses these fixtures to validate `analyze`, `junit-select`, and full `run` pipeline
behavior across basic Java syntax and object-oriented patterns.

To avoid IDE package/path inspection noise inside `resources/`, fixture source files are stored as
`*.java.txt` and renamed to `*.java` when copied by E2E tests.

## Fixture list

| Fixture | Syntax focus | Main classes |
| --- | --- | --- |
| `simple-java` | arithmetic + basic methods | `Calculator` |
| `basic-if-switch` | `if`/`else` + `switch` expression | `DecisionService` |
| `basic-loops` | `for`/enhanced for/`while`/`do-while` | `LoopService` |
| `basic-exception` | `throw` + `try`/`catch`/`finally` | `SafeCalculator` |
| `basic-collections` | `List`/`Map` and iteration | `InventoryService` |
| `basic-encapsulation` | field encapsulation + constructor guards | `BankAccount` |
| `basic-inheritance` | interface + implementation + override | `PricingPolicy`, `MemberPricingPolicy` |
| `oop-abstraction` | abstract class + template method override | `AbstractPriceCalculator`, `HolidayPriceCalculator` |
| `oop-polymorphism` | interface polymorphism + runtime strategy switch | `NotificationChannel`, `NotificationService` |
| `oop-composition` | composition (`Order` has many `OrderLine`) | `Order`, `OrderLine` |
| `oop-generics` | bounded generic repository (`T extends Identifiable`) | `MemoryRepository`, `Product` |
| `oop-enum-behavior` | enum-specific behavior + switch usage | `MembershipLevel`, `LoyaltyService` |
| `oop-record` | immutable data model with `record` + compact constructor | `InvoiceLine`, `Invoice` |
| `oop-sealed-hierarchy` | `sealed` hierarchy with permitted policy types | `DiscountPolicy`, `DiscountService` |
| `oop-default-method` | interface `default` method shared by implementations | `EventPublisher`, `UserActionService` |
| `oop-nested-class` | static nested value class inside aggregate | `Schedule`, `Schedule.Slot` |
| `oop-builder-pattern` | fluent builder for immutable object construction | `UserProfile`, `UserProfileRegistrationService` |
| `oop-annotation` | custom annotation + reflection-based metadata read | `AuditedAction`, `AuditMetadataReader` |
| `oop-factory-method` | factory method that selects implementation by format | `ReportFormatterFactory`, `ReportService` |
| `oop-strategy-pattern` | replaceable shipping strategy at runtime | `ShippingStrategy`, `CheckoutService` |
| `oop-state-pattern` | state object transitions across lifecycle | `OrderWorkflow`, `OrderState` |
| `oop-observer-pattern` | observer notification for stock changes | `InventorySubject`, `InventoryObserver` |
