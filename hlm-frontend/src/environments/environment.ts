export const environment = {
  production: false,
  // Empty in dev: ng serve proxy forwards /auth, /api, /actuator to backend.
  // To bypass proxy, set to 'http://localhost:8080'
  // and add your origin to CorsConfig.java allowedOrigins.
  apiUrl: '',
  features: {
    // Legacy SaleContract module. Off by default so the sidebar shows a single
    // selling concept ("Pipeline Ventes", VEFA). Turn on for sociétés still on the
    // pre-VEFA contract workflow. See review finding #016.
    legacyContracts: false,
  },
};
