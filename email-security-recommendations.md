# Secure Email Credential Management Recommendations

## Current Issue
The application is failing to send emails due to authentication issues with Gmail's SMTP server. The error occurs because:
1. The email credentials in `application.properties` are placeholders, not actual credentials
2. Gmail requires proper authentication, especially with accounts that have 2FA enabled

## Immediate Fix
1. Replace the placeholder credentials in `application.properties` with actual values:
   ```properties
   spring.mail.username=your-actual-gmail-address@gmail.com
   spring.mail.password=your-actual-password-or-app-password
   ```

2. For Gmail accounts with 2FA enabled:
   - Generate an App Password from Google Account settings
   - Go to: Google Account > Security > 2-Step Verification > App passwords
   - Use this App Password instead of your regular account password

3. For Gmail accounts without 2FA:
   - You may need to enable "Less secure app access" in your Google Account settings
   - Note: Google is phasing this out, so using App Passwords with 2FA is recommended

## Security Recommendations for Production

### 1. Use Environment Variables
Instead of hardcoding credentials in properties files, use environment variables:

```properties
spring.mail.username=${EMAIL_USERNAME}
spring.mail.password=${EMAIL_PASSWORD}
```

Set these variables in your deployment environment or CI/CD pipeline.

### 2. Use a Secret Management Service
For cloud deployments, use a secret management service:
- AWS: AWS Secrets Manager
- Azure: Azure Key Vault
- GCP: Google Secret Manager
- Kubernetes: Kubernetes Secrets

### 3. Use Spring Cloud Config Server
For microservices architecture, consider using Spring Cloud Config Server with encryption.

### 4. Consider Using a Dedicated Email Service
Instead of using Gmail directly, consider:
- Amazon SES
- SendGrid
- Mailgun
- Postmark

These services provide better deliverability and monitoring capabilities.

### 5. Implement Retry Logic
Add retry logic for email sending to handle temporary authentication failures:

```java
@Retryable(
    value = { MailAuthenticationException.class },
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)
)

@Recover
public void recoverSendEmail(MailAuthenticationException e, String to, String subject, String text) {
    System.err.println("Failed to send email after retries: " + e.getMessage());
}
```

### 6. Implement Circuit Breaker
Use a circuit breaker pattern to prevent cascading failures when the email service is down:

```java
@CircuitBreaker(
    name = "emailService",
    fallbackMethod = "emailServiceFallback"
)
public void sendEmail(String to, String subject, String text) {
 
}

public void emailServiceFallback(String to, String subject, String text, Exception e) {
    // Fallback logic
    System.err.println("Circuit breaker triggered for email service: " + e.getMessage());
    // Store email in database for later sending
}
```

## Testing the Fix
After updating the credentials:
1. Restart the application
2. Send a test message to the Kafka topic using the `/test` endpoint
3. Check the application logs for successful email sending
4. Verify that the email was received at the destination address