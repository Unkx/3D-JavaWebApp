module.exports = {
  '/api/**': {
    target: process.env['API_URL'] || 'http://localhost:8080',
    secure: false,
    changeOrigin: true,
    logLevel: 'warn',
  },
  '/inpost-api/**': {
    target: 'https://api-shipx-pl.easypack24.net',
    secure: true,
    changeOrigin: true,
    pathRewrite: { '^/inpost-api': '/v1' },
  },
};
