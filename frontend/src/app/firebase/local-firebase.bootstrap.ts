import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from '../app.config';
import { App } from '../app';
import {
  FirebaseAuthBootstrapClient,
  initializeLocalFirebaseAuth,
  liveFirebaseAuthBootstrapClient,
} from './local-firebase';

export function bootstrapSaqzWeb(
  firebaseClient: FirebaseAuthBootstrapClient = liveFirebaseAuthBootstrapClient,
  bootstrapAngular: () => Promise<unknown> = () => bootstrapApplication(App, appConfig),
): Promise<unknown> {
  initializeLocalFirebaseAuth(firebaseClient);
  return bootstrapAngular();
}
