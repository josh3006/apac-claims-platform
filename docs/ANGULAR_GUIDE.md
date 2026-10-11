# Angular guide for a Java developer

The UI is deliberately small: one screen, one component, one API service, typed request/response interfaces, and plain CSS. There is no router or state-management library to learn.

## Read these files in this order

| File | What it does | Java comparison |
| --- | --- | --- |
| `src/main.ts` | Starts Angular and registers HttpClient | Application entry point and bean configuration |
| `src/app/models.ts` | Describes JSON data shapes | Request/response DTOs (compile-time only) |
| `src/app/api.service.ts` | Sends authenticated HTTP requests | An injectable REST client |
| `src/app/app.component.ts` | Holds screen state and handles actions | A controller/presenter with fields |
| `src/app/app.component.html` | Renders state and binds form inputs | A server template, but updated in the browser |
| `src/styles.css` | Controls appearance | Styles only; no business rules |

## The essential syntax

`@Component` tells Angular which class controls a piece of the screen. `standalone: true` means its dependencies are listed directly in `imports`; no separate NgModule is needed. `CommonModule` supplies formatting pipes; `FormsModule` supplies form binding.

`inject(ApiService)` asks Angular's dependency injector for the service, much like a Spring-injected dependency. `@Injectable({ providedIn: 'root' })` registers one shared service instance for the application.

The screen uses normal properties, not signals: `claims`, `selected`, `user`, `busy`, and `error`. Assigning new values causes Angular to update the template through its default change detection and Zone.js.

| Template syntax | Meaning | Example |
| --- | --- | --- |
| `{{ value }}` | Render a text value; Angular escapes it | `{{ user.username }}` |
| `[disabled]="expression"` | Set an element property | `[disabled]="busy"` |
| `(click)="method()"` | Handle an event | `(click)="act('assign')"` |
| `[(ngModel)]="field"` | Keep form input and component field in sync | `[(ngModel)]="message"` |
| `@if (...) { ... }` | Conditionally render content | Officer controls appear by role |
| `@for (...; track ...) { ... }` | Render a list, keyed by identity | Claims keyed by their UUID |
| `value \| date:'medium'` | Format output with a pipe | History timestamps |

`#claimForm="ngForm"` gives the template access to form validity. Inputs inside a form need a `name` when using `ngModel`. The submit button is disabled while invalid or busy. Server-side validation still decides whether a request is accepted.

## Follow one request: submit a claim

1. The form fields bind to `draft` in `AppComponent`.
2. `(ngSubmit)="submit()"` calls the component method.
3. `run(...)` sets `busy`, clears messages, catches errors, and restores `busy` in `finally`.
4. The component creates a UUID for the logical submission. `api.submit(draft, key)` calls `HttpClient.post<Claim>('/api/claims', ...)` with `Idempotency-Key`.
5. Angular HttpClient returns an Observable. `firstValueFrom` converts this one-response stream into a Promise, so the component can use familiar `async` / `await`.
6. The development proxy or Docker nginx forwards the request to Spring Boot.
7. The API validates and saves the claim, history, and outbox event in one transaction.
8. The component refreshes the list and displays the submitted claim.

If a response is lost, the component keeps the key while the draft is unchanged. Retrying returns the persisted original result. Editing the draft, success, or signing out resets the key. It is kept in memory: a page reload loses the pending key; cross-reload recovery is not implemented. `npm run test:submission` verifies these rules and the HTTP header without requiring a browser.

TypeScript interfaces disappear at runtime; `post<Claim>` is not runtime JSON validation. The backend DTOs are the source of truth for validation. If you change a backend response field, update `models.ts` and the template together.

## Two user roles in one app

After login, `/api/me` returns the authenticated username and role. The template chooses claimant forms or officer actions with `@if`. These UI conditions are conveniences: Spring Security enforces officer endpoints, and the service checks claim ownership and the assigned officer.

HTTP Basic credentials live only in `ApiService` memory. Refreshing the page signs you out. Sign out clears the credentials and polling timer. Do not put passwords in localStorage.

## Asynchronous updates

After login, a single timer fetches claims every 10 seconds. The officer also fetches the dashboard. `refreshing` avoids overlapping polls; `busy` avoids polling during mutations. The timer is cleared on sign out or component destruction.

REST polling observes committed database state. Kafka publication is independent; the UI does not connect to Kafka. Polling can show a change before the outbox publisher sends the event. This distinction is useful to explain in the panel.

## Safely make common edits

- Add a display field: Java response DTO → `models.ts` → HTML.
- Add an operation: backend controller/service → method in `ApiService` → handler in `AppComponent` → button/form in HTML.
- Change appearance: `styles.css`.
- Add another page later: introduce Angular Router when there is actually a second route.
- Split the component later: extract claimant submission, claim details, and dashboard components when the screen grows. The single component is a deliberate assessment shortcut.

Run `npm run build` after edits. Strict TypeScript and strict Angular template checking catch mismatched fields and template types. For this prototype, manually exercise the complete role-switching flow after changing screen behaviour.

## Useful official references

- [Angular components](https://angular.dev/guide/components)
- [Template-driven forms](https://angular.dev/guide/forms/template-driven-forms)
- [HTTP client](https://angular.dev/guide/http)
- [Version compatibility](https://angular.dev/reference/versions)
