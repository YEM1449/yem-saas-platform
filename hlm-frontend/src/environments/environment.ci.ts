// CI environment: Angular is built statically; API calls go directly to
// the backend container (no ng serve proxy available).
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080',
};
