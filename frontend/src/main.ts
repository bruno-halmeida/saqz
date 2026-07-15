import { bootstrapSaqzWeb } from './app/firebase/local-firebase.bootstrap';

bootstrapSaqzWeb()
  .catch((err) => console.error(err));
