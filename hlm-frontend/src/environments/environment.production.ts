export const environment = {
  production: true,
  apiUrl: '',  // same-origin in production
  features: {
    // Legacy SaleContract module — off by default (one selling concept). See finding #016.
    legacyContracts: false,
  },
};
