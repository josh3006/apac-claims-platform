import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { AppComponent } from './app/app.component';

// Starts one standalone component. There is no routing or global state library.
bootstrapApplication(AppComponent, [provideHttpClient()]).catch(console.error);
