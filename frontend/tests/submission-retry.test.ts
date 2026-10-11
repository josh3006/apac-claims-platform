import '@angular/compiler';
import assert from 'node:assert/strict';
import { Injector, runInInjectionContext } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { of } from 'rxjs';
import { AppComponent } from '../src/app/app.component';
import { ApiService } from '../src/app/api.service';
import { Claim, SubmitClaim } from '../src/app/models';

// No browser is required for this focused state test; rendering is tested separately.
const draft: SubmitClaim = { type: 'MOTOR', market: 'MY', incidentDate: '2026-01-01', description: 'Damage', estimatedAmount: 100 };
const response = { id: 'test-claim', estimatedAmount: 100 } as Claim;

function screen(failures: number) {
  const keys: string[] = [];
  const fakeApi = {
    async submit(_claim: SubmitClaim, key: string) {
      keys.push(key);
      if (failures-- > 0) throw new HttpErrorResponse({ status: 0 });
      return response;
    },
    clearCredentials() {}
  };
  const injector = Injector.create({ providers: [{ provide: ApiService, useValue: fakeApi }] });
  const component = runInInjectionContext(injector, () => new AppComponent());
  component.draft = { ...draft };
  return { component, keys, injector };
}

const retry = screen(1);
await retry.component.submit();
assert.ok(retry.component.error);
await retry.component.submit();
assert.equal(retry.keys[0], retry.keys[1], 'Lost-response retry must reuse the same key');
retry.component.draft = { ...draft };
await retry.component.submit();
assert.notEqual(retry.keys[1], retry.keys[2], 'A new submission after success must use a new key');
retry.injector.destroy();

const edited = screen(1);
await edited.component.submit();
edited.component.draft.description = 'Different incident';
await edited.component.submit();
assert.notEqual(edited.keys[0], edited.keys[1], 'Editing the failed request must use a different key');
edited.injector.destroy();

const signedOut = screen(1);
await signedOut.component.submit();
signedOut.component.logout();
await signedOut.component.submit();
assert.notEqual(signedOut.keys[0], signedOut.keys[1], 'Signing out must discard the pending key');
signedOut.injector.destroy();
let observed: { url: string; headers: HttpHeaders } | undefined;
const apiInjector = Injector.create({ providers: [ApiService, { provide: HttpClient, useValue: {
  post(url: string, _body: unknown, options: { headers: HttpHeaders }) {
    observed = { url, headers: options.headers };
    return of(response);
  }
} }] });
const api = apiInjector.get(ApiService);
api.setCredentials('claimant', 'claimant-demo');
await api.submit(draft, 'test-key');
assert.equal(observed?.url, '/api/claims');
assert.equal(observed?.headers.get('Idempotency-Key'), 'test-key');
assert.equal(observed?.headers.get('Authorization'), 'Basic ' + btoa('claimant:claimant-demo'));
apiInjector.destroy();
console.log('PASS: retry reuses key; success, edited payload, and sign-out reset it; API sends key and credentials.');
