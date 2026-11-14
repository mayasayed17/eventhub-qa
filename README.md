# EventHub QA Automation: US-114 "Book tickets for an event"

UI and API automation for the EventHub booking flow, in **Java 17**: **Playwright for Java** drives the UI and **REST Assured** tests the API. Both sit on one shared framework and run under JUnit 5.

- Web app: https://eventhub.rahulshettyacademy.com
- API: https://api.eventhub.rahulshettyacademy.com/api (Swagger: `/api/docs`)

Contents: [Run it](#run-it) · [Architecture](#architecture) · [Authorization](#how-authorization-works) · [Extending](#extending-the-framework) · [Coverage](#what-is-tested-and-why) · [Story review](#story-review-us-114) · [Decisions](#key-decisions) · [More time](#what-id-do-with-more-time)

---

## Run it

**Prerequisites:** JDK 17+ and Maven 3.9+. Nothing else: Playwright downloads its browser on first run.

**1. Credentials.** These are never committed. Pick one way to provide them:

```bash
cp local.properties.example local.properties    # then fill in users.default.email / users.default.password
# or
export EVENTHUB_USERS_DEFAULT_EMAIL=...  EVENTHUB_USERS_DEFAULT_PASSWORD=...
```

The credentials for the account used to build this suite are in the submission form. A second account (`users.secondary.*`) is optional. It turns on the cross-user authorization test, which is **skipped** (not failed) when the second account is missing.

**2. Install the browser** (once; `--with-deps` also installs the OS libraries on Linux):

```bash
mvn -q compile exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --with-deps chromium"
```

**3. Run**

| Command | Runs |
|---|---|
| `mvn test` | everything |
| `mvn test -Dgroups=api` | API tests only (≈ seconds) |
| `mvn test -Dgroups=ui` | UI tests only |
| `mvn test -Dgroups=smoke` | the handful of must-pass tests, across API and UI |
| `mvn test -Dgroups=framework` | the framework's own offline tests (no network or credentials) |
| `mvn test -Dtest=CreateBookingApiTest` | one class |

Useful switches (each one also works as `-Dkey=value`, as an entry in `local.properties`, or as the env var `EVENTHUB_<KEY>`):

| Key | Default | Purpose |
|---|---|---|
| `ui.headless` | `true` | `false` to watch the browser |
| `ui.slowMoMs` | `0` | slow every action down, for demos/debugging |
| `ui.browser` | `chromium` | `firefox`, `webkit`, `chrome`, `msedge` |
| `ui.auth.strategy` | `token-injection` | `login-form` logs in through the real login page instead |
| `ui.trace` | `retain-on-failure` | `always` / `off` |
| `api.logging` | `false` | log every request/response (Authorization is always masked) |
| `env` | `public` | which `config/<env>.properties` to load |

**When something fails:**
- **API:** the failing test's last HTTP exchanges (request, status, body, timing) are printed, with passwords and tokens masked.
- **UI:** a screenshot and a Playwright trace go to `target/ui-artifacts/<TestClass>/<test>/`. Open the trace with `mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="show-trace <path>/trace.zip"`.

**CI:** `.github/workflows/tests.yml` runs on every push and PR. The framework self-tests and API tests run in one job, UI tests in a parallel job, and reports and traces are uploaded as artifacts. Add the repository secrets `EVENTHUB_USERS_DEFAULT_EMAIL` and `EVENTHUB_USERS_DEFAULT_PASSWORD`, plus optionally `..._SECONDARY_...`.

---

## Architecture

```
src/main/java/com/eventhub/framework     <- the framework (tests depend on it, never the reverse)
  config/    Config               layered config: defaults -> env file -> local.properties -> env vars -> -D
  auth/      TestUsers            logical personas ("default", "secondary", ...) -> credentials from config
             TokenProvider        the ONLY code that logs in: once per user per run, cached, renewed before expiry
             BearerTokenFilter    REST Assured filter that adds "Authorization: Bearer ..." to every request
  api/       ApiSpecFactory       builds request specs: base URL, JSON, timeouts, recorder, auth filter
             Envelope             the one place that knows responses are wrapped in "data"
             clients/             BookingsClient, EventsClient, ApiClients (one set per identity)
             models/              Booking, BookingRequest, Event (records; unknown fields ignored)
  ui/        BrowserFactory       one browser per worker thread; a fresh context per test
             UiSession            context + page + tracing + "booking created" hook
             auth/                UiAuthStrategy: token injection (default) or login form
             pages/               page objects (locators + user actions)
  data/      TestData             unique, traceable test data ("QA Auto <runId>-<n>")
  cleanup/   CleanupRegistry      everything a test creates is undone after it, pass or fail
  junit/     @ApiTest @UiTest     meta-annotations: tags + extensions
             @AsUser @Anonymous   choose an identity per class, method or parameter
             EventHubExtension    injects clients, skips tests whose persona isn't configured, runs cleanup
             UiExtension          browser lifecycle, page-object injection, traces on failure

src/test/java/com/eventhub/tests         <- tests, grouped by feature (api/bookings, ui/bookings)
src/test/java/com/eventhub/framework     <- the framework's own unit tests
```

A test only says *what* it needs. The framework decides *how*:

```java
@ApiTest
class BookingAuthorizationApiTest {
    @Test
    void otherUsersCannotSeeOrCancelMyBooking(EventsClient events, BookingsClient mine,
                                              @AsUser("secondary") BookingsClient theirs) { ... }
}

@UiTest
class ManageBookingsUiTest {
    @Test
    void bookingIsListedInMyBookings(EventsClient events, BookingsClient bookings, MyBookingsPage myBookings) {
        Booking booking = bookings.create(TestData.bookingFor(events.findBookable(2), 2)); // fast API setup
        myBookings.open();                                                                  // behaviour under test in the UI
        assertThat(myBookings.cardFor(booking.bookingRef())).isVisible();
    }
}
```

## How authorization works

Authorization is handled entirely by the framework. No test, client or page object handles a token, header or login form.

1. A test names a **persona**, or doesn't. `@AsUser("x")` works on a class, method or parameter, `@Anonymous` means no credentials, and the default is `default`.
2. `TestUsers` maps the persona to credentials from config (`users.<persona>.email/password`).
3. `TokenProvider` logs that persona in **once per run** and caches the token. It reads the JWT `exp` claim, falls back to a TTL for opaque tokens, and logs in again shortly before expiry. It is thread-safe, so parallel tests share one login per persona.
4. **API:** `ApiSpecFactory` attaches `BearerTokenFilter` to the persona's request spec. An anonymous spec simply has no filter, which is how the 401 tests are written.
5. **UI:** `UiExtension` creates a fresh browser context and applies the configured `UiAuthStrategy`. By default it writes the same API token to `localStorage` (`eventhub_token`) before the app loads, so UI tests skip the login page and reuse the API login. `-Dui.auth.strategy=login-form` drives the real login page instead.
6. A persona that isn't configured makes its tests **skip** with a message. The exception is `default`, which fails fast with setup instructions.

Adding an admin persona later means two config lines and `@AsUser("admin")`. No code changes.

## Extending the framework

| To add... | Do this |
|---|---|
| a test | put a class under `tests/<area>`, annotate it `@ApiTest` or `@UiTest`, and declare the clients/pages you need as parameters |
| an endpoint group | add a `XxxClient` (raw + typed methods), expose it on `ApiClients`, and add its type to `EventHubExtension.SUPPORTED` |
| a page | extend `BasePage` with a `(UiSession)` constructor; it becomes injectable automatically |
| a persona | add `users.<name>.email/password` to config or CI secrets and use `@AsUser("<name>")` |
| an environment | add `src/main/resources/config/<env>.properties` and run with `-Denv=<env>` |
| cleanup for a new resource | have its client register a delete action with `CleanupRegistry` when it creates one |

Conventions the code follows:
- **Client methods come in two kinds:** `...Raw` returns the response, for tests about status codes and errors. Typed methods assert success and return models, for setup.
- **Page objects don't assert business rules.** They wait for the state an action needs.
- **Locators prefer** `data-testid`, then role/label, then CSS.
- **Tests never hard-code event ids.** The shared data changes, so `EventsClient.findBookable(n)` picks a live, upcoming, paid event with spare seats.

---

## What is tested and why

I focused on the booking flow's risks rather than on volume. The API layer carries most of the rules (it is fast, precise and less brittle). The UI layer covers what only a browser can show: the user journey, the confirmation, the list and cancellation. Each UI test does its setup through the API and drives only the behaviour under test through the UI.

| AC | API | UI |
|---|---|---|
| AC1 create + reference | `createsConfirmedBookingWithReference`, `bookingIsRetrievableByReference`, 9 validation cases, unknown event, over-booking | `bookingShowsConfirmationWithReferenceAndTotal`, `confirmWithoutCustomerDetailsCreatesNothing` |
| AC2 total = price × qty | `totalPriceIsTicketPriceTimesQuantity` (1, 2, 3 tickets), `clientSuppliedTotalIsIgnored` | total checked on the confirmation **and** against the stored booking |
| AC3 My Bookings | `newBookingAppearsInMyBookings` | `bookingIsListedInMyBookings` |
| AC4 cancel | `cancellingBookingReleasesSeats`, `cancellingTwiceIsRejected` | `bookingCanBeCancelled` |
| AC5 via API | every API test | n/a |
| (not in story) seats & access | `bookingReducesAvailableSeats`, `createRequiresAuthentication`, `listRequiresAuthentication`, `otherUsersCannotSeeOrCancelMyBooking` | `loggedOutVisitorIsAskedToLogIn` |

Why these and not more:
- **Price tampering and cross-user access** are the highest-impact failures in a booking system (revenue and personal data), so they get tests even though the story doesn't mention them.
- **Seat counts** are how a booking actually "reserves" something; the story is silent on this, but a booking that doesn't consume a seat is a bug.
- **Validation** is parameterised. One readable table covers nine bad inputs without nine copies of the test.
- **Deliberately not automated:** visual layout, cross-browser runs on every push (the config supports it), exhaustive UI form validation (cheaper at API level), and login-page tests (outside this story).

Where the story doesn't define behaviour (e.g. 400 vs 409 for over-booking, deleted vs "cancelled" status), the tests accept the reasonable options explicitly (`ApiAssert.statusIn`) and the question goes to the PO below. That way a test never locks in a guess.

---

## Story review (US-114)

**Overall:** the story describes the happy path only. It doesn't say who the user is, what the limits are, or what "cancel" means. That leaves the most expensive questions (money, seats, personal data) to whoever implements it.

### Missing, unclear or wrong

1. **"As a visitor" conflicts with the app.** Booking and My Bookings need a logged-in account. Is a *visitor* a guest (checkout without an account) or a registered user? This changes AC3 too: a guest has no "My Bookings".
2. **AC1 has no validation rules.** Which of name, email and phone are required? What formats and lengths are allowed (international phone numbers, unicode names)? Must the email be the account's email, or can I book for someone else?
3. **Number of tickets has no bounds.** What are the minimum and the maximum per booking, and is there a per-user or per-event limit? What happens when I ask for more than the remaining seats, or the event sells out while I'm filling the form?
4. **The booking reference is undefined.** Its format, uniqueness, and where else it appears (email? ticket?) aren't specified. Testers can only assert "something is shown".
5. **AC2 says nothing about currency, rounding, fees or taxes, discounts, or free events.** Which price applies if it changes between page load and confirmation? It also doesn't say the server must compute the total. That should be explicit: a client-supplied total must never be trusted.
6. **AC3 is vague.** What details must a booking show, in what order, and how quickly after booking?
7. **AC4 is the most underspecified.**
   - Can I cancel some of the tickets or only all of them, and until when (before the event? a cut-off)?
   - Are seats returned to the event?
   - Is there a refund, and on what rule?
   - Is a confirmation step required?
   - Does a cancelled booking **disappear** or stay visible as **cancelled**? (This matters for support and audit.)
8. **AC5 "the same works through the API" isn't testable as written.** It needs the contract: auth, request schema, success status (201?), response body, error format and codes. AC3 and AC4 via the API also need `GET /bookings` and `DELETE /bookings/{id}`, which the AC doesn't mention.
9. **Non-functional requirements are absent.** Concurrency (two people booking the last seat), accessibility of the form, performance, and handling of personal data (who can see a booking) all need stating.
10. **Booking a past event** isn't addressed.

### Questions I'd ask the Product Owner

1. Who is the user: guest, registered user, or both?
2. What are the min/max tickets per booking and per user? What should the user see when seats run out?
3. Which customer fields are mandatory, and what formats do they accept? Can I book on behalf of someone else?
4. Is there any pricing beyond `price × quantity` (fees, tax, discounts, currency)?
5. What does cancel mean? Full or partial, deadline, refund rule, seat release, and does the booking stay visible as "cancelled"?
6. Is the refund-eligibility rule shown in the app (below) part of this story, another story, or unintended?
7. Is a confirmation email expected?
8. What is the API contract for create, list, get and cancel, and what are the error codes?

### Where the application differs from the story or docs

<!-- TODO before submitting: confirm each item on the live app/Swagger and delete any you can't reproduce. -->

- **Login is required to book.** That conflicts with "As a visitor" (covered by `loggedOutVisitorIsAskedToLogIn`).
- **Cancelling deletes the booking.** Looking it up by reference afterwards returns 404, rather than returning it with status `cancelled`. There's no cancellation history.
- **Cancel is `DELETE /bookings/{id}`.** That is semantically a deletion; if bookings should be kept as cancelled, a state change (e.g. `PATCH .../cancel`) fits better.
- **Refund eligibility depends on the number of tickets.** Single-ticket bookings are shown as "Eligible for refund", group bookings (e.g. 3 tickets) as non-refundable. The story says nothing about refunds.
- **`price` type differs between request and response.** It is accepted as a number when creating an event but returned as a string (e.g. `"1500.00"`). The framework models money as `BigDecimal` and accepts both, but clients doing arithmetic on it would break.
- **My Bookings has a "Clear all bookings" bulk action** that isn't in any story.
- **The ticket quantity is a +/- stepper,** not a free-text field. The story's "choose the number of tickets" is fine, but the bounds of the stepper are the undocumented limits from question 2.
- **The environment is shared and public.** My Bookings and events change underneath you, which is why tests pick events dynamically and clean up after themselves.

---

## Key decisions

- **One language (Java) for UI and API.** One build, one framework, one set of models and one auth implementation, so UI tests can use the same API clients and the same token for setup and cleanup. The trade-off: Playwright's Java test tooling is thinner than the TypeScript test runner (no built-in HTML reporter or fixtures), so the JUnit extensions provide fixtures and tracing provides failure artifacts.
- **JUnit 5 extensions and parameter injection instead of base classes.** Composition scales better than an inheritance tree. A test declares what it needs, and new capabilities are new resolvers rather than changes to a `BaseTest` everyone depends on.
- **Framework in `src/main`, tests in `src/test`.** This enforces the dependency direction, and the framework can become its own module or artifact later without changes.
- **Auth is token-based and centralised.** One login per persona per run. The UI reuses the API token instead of typing into the login form in every test: faster, less flaky, and the login page deserves its own tests rather than being exercised implicitly by all of them. A login-form strategy exists as a fallback.
- **Automatic cleanup.** Clients register every successful create, and so does the UI page object via the network response. Cleanup runs after every test, even failed ones, and is tolerant of 404s. Test data carries a run id so leftovers are traceable. This was a stated requirement for the shared environment.
- **Lenient models, strict assertions.** Unknown JSON fields are ignored so additive API changes don't break unrelated tests, but every assertion is specific. The `data` envelope is unwrapped in one place (`Envelope`).
- **Money is `BigDecimal`, compared with `isEqualByComparingTo`,** never `double`.
- **Parallel by class, sequential within a class,** with low parallelism out of courtesy to a shared environment. `TokenProvider` and the cleanup registry are thread-safe, and Playwright objects are confined to one thread each.
- **Failures are self-explanatory.** API failures print the last masked HTTP exchanges; UI failures keep a screenshot and a full Playwright trace.

### Assumptions

- The selectors are the app's published `data-testid`s (`book-now-btn`, `quantity-input`, `customer-name`, `confirm-booking-btn`, `booking-ref`, `booking-card`, `cancel-booking-btn`, `confirm-dialog-yes`, `nav-bookings`, ...).
- The login response carries the token at one of `auth.tokenJsonPaths`. This is configurable if it's elsewhere.
- Successful create returns **201** and successful cancel **200**.
- A booking reference looks like `EVT-` followed by 6 alphanumerics.
- The confirmation page shows the total as a number. It has no test id, so the UI test checks that the expected total is among the amounts displayed and, separately, that the stored booking's total is right.

---

## What I'd do with more time

1. **Contract testing:** validate responses against the Swagger/OpenAPI schema (e.g. REST Assured's JSON-schema validator, or generate models from the spec) so contract drift fails loudly in one place.
2. **Reporting:** add Allure with request/response and trace attachments per test, published from CI.
3. **Better isolation:** create a dedicated event per test run through the admin API instead of booking on shared events, so seat-count assertions become exact and nothing competes with other users.
4. **A sweeper job** that deletes any bookings matching the `QA Auto` prefix older than a day, as a safety net for crashed runs.
5. **A multi-module build** (`framework`, `api-tests`, `ui-tests`) once more teams contribute. The package boundaries are already in place.
6. **More coverage:** sold-out and last-seat concurrency (parallel requests for the remaining seats), boundary values once the PO defines limits, the refund-eligibility rule, the booking detail page, accessibility checks (axe) on the booking form, and mobile viewports.
7. **CI:** a cross-browser matrix nightly, retries only for known-flaky infrastructure errors (never for assertions), and test-impact tagging so a PR runs the relevant subset first.
8. **Secrets:** fetch test credentials from a vault in CI and rotate them.
