# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in this project, please report it responsibly. **Do not open a public GitHub issue** for security concerns.

### How to Report

**Email**: ymidortega@gmail.com

Please include in your report:
- Description of the vulnerability
- Steps to reproduce the issue
- Potential impact assessment
- Any suggested fixes (optional)

### Response Timeline

- **Initial acknowledgment**: Within 48 hours
- **Status update**: Within 7 days
- **Resolution timeline**: Varies based on severity

We take security seriously and appreciate your effort in responsibly disclosing vulnerabilities.

---

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

---

## Security Notice

**This is an educational project** designed for learning and demonstration purposes. While it implements several security best practices, it requires additional hardening before production use.

---

## Current Security Features

### Authentication & Authorization
- BCrypt password hashing (12 rounds)
- HTTP Basic Authentication for REST API
- Session-based authentication for BOE protocol
- Credential validation on all protected endpoints

### Network Security
- Configurable connection limits
- Socket timeouts to prevent resource exhaustion
- Rate limiting on BOE protocol (100 messages/minute per connection)
- CORS configuration support

### Data Security
- RocksDB persistence (no SQL injection risk)
- Thread-safe concurrent data structures
- Proper synchronization mechanisms
- Input validation on message processing

### Operational Security
- Automatic session cleanup
- DEMO_MODE for safe development/testing
- Environment-based configuration
- Audit logging for critical operations

---

## Known Limitations

### Critical
1. **No TLS/SSL encryption** - All traffic is transmitted in plaintext
2. **No REST API rate limiting** - Vulnerable to brute force attacks
3. **Basic authentication only** - No token expiration or refresh mechanisms
4. **Unauthenticated WebSocket endpoint** - Market data feed accessible to anyone

### Important
5. **No RBAC** - All users have identical permissions
6. **Permissive CORS by default** - Configured for development ease
7. **Limited audit logging** - Security events not comprehensively tracked
8. **Demo credentials when enabled** - Predictable usernames and passwords

---

## Production Deployment Recommendations

Before deploying to production environments:

### Must Have
- [ ] Enable TLS/HTTPS with valid certificates
- [ ] Implement proper secrets management
- [ ] Configure specific CORS origins (remove wildcards)
- [ ] Disable DEMO_MODE and create secure user accounts
- [ ] Set up comprehensive monitoring and alerting

### Strongly Recommended
- [ ] Add rate limiting to REST API endpoints
- [ ] Implement JWT-based authentication
- [ ] Add authentication to WebSocket endpoints
- [ ] Enable security event audit logging
- [ ] Implement account lockout policies
- [ ] Configure IP whitelisting where applicable

### Nice to Have
- [ ] Implement role-based access control
- [ ] Add request size limits
- [ ] Enable database encryption at rest
- [ ] Set HTTP security headers
- [ ] Conduct penetration testing
- [ ] Run dependency vulnerability scans regularly

---

## Configuration

### Secure Production Setup

```bash
# Disable demo mode
DEMO_MODE=false

# Configure CORS properly
ALLOWED_ORIGINS=https://yourdomain.com

# Use strong credentials (never commit these)
# Store in secure secrets manager
```

### Development vs Production

| Feature              | Development        | Production         |
|---------------------|--------------------|--------------------|
| DEMO_MODE           | true               | **false**          |
| CORS                | Permissive         | **Restricted**     |
| TLS                 | Optional           | **Required**       |
| Rate Limiting       | Lenient            | **Strict**         |
| Logging Level       | INFO/DEBUG         | **WARNING/ERROR**  |

---

## Security Best Practices for Users

### For Developers
1. Never commit secrets or credentials to version control
2. Use strong, unique passwords (min 10 characters, mixed case, numbers, symbols)
3. Keep dependencies updated regularly
4. Review security advisories in the GitHub Security tab
5. Run local security scans before submitting PRs

### For Operators
1. Use environment variables for configuration
2. Implement network segmentation
3. Monitor logs for suspicious activity
4. Keep the application and dependencies patched
5. Perform regular security assessments

---

## Dependencies Security

This project uses:
- RocksDB for persistence
- Javalin for REST API
- BCrypt for password hashing
- Jackson for JSON processing

Keep dependencies updated by:
- Monitoring GitHub Dependabot alerts
- Running `mvn versions:display-dependency-updates`
- Reviewing security advisories for Java ecosystem

---

## Acknowledgments

We appreciate security researchers who report vulnerabilities responsibly. Contributors will be acknowledged in release notes (unless they prefer to remain anonymous).

---

**Last Updated**: November 17, 2024  
**Project Version**: 1.0.0-SNAPSHOT  
**Security Review Status**: Educational project - not professionally audited
