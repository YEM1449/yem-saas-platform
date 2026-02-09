export const environment = {
  production: false,
  // Empty in dev: ng serve proxy forwards /auth, /api, /actuator to backend.
  // To bypass proxy, set to 'http://localhost:8080'
  // and add your origin to CorsConfig.java allowedOrigins.
  apiUrl: '',
};
