# Java Web Agent

LLM-powered browser automation framework for intelligent web navigation.

## Overview

Java Web Agent is an agentic browser automation tool that leverages Large Language Models to interact with web applications autonomously. Unlike traditional browser automation tools that rely on visual screenshots, this framework extracts textual information from the accessibility tree, enabling more efficient and reliable LLM-driven interactions.

## Key Features

- **Semantic Snapshots**: Extracts structured data from accessibility trees instead of processing pixel-based screenshots
- **LLM-Driven Navigation**: Uses natural language prompts to autonomously navigate and interact with web pages
- **Reference-Based Interaction**: Elements are numbered with `[ref=N]` tags, allowing precise LLM control
- **REST API**: Asynchronous job-based API for scalable automation workflows
- **Resource Optimized**: Designed to run efficiently on low-power devices like Raspberry Pi 4
- **Docker Ready**: Includes hardened Docker configuration for secure isolated execution
- **Cookie Support**: Authenticate and act on behalf of users by importing browser cookies

## Supported Actions

- `click` - Click on interactive elements
- `type` - Type text into input fields
- `press` - Press keyboard keys
- `hover` - Hover over elements
- `fill` - Fill form fields
- `select` - Select dropdown options
- `drag` - Drag and drop operations
- `submit` - Submit forms

## How It Works

1. **Prompt Submission**: Send a natural language instruction via REST API
2. **Semantic Snapshot**: The browser navigates to the page and extracts the accessibility tree
3. **LLM Decision**: The snapshot is sent to an LLM which decides the next action
4. **Action Execution**: The chosen action is executed via Playwright
5. **Iteration**: Steps 2-4 repeat until the goal is achieved or max steps reached

```
User Prompt → Semantic Snapshot → LLM Analysis → Browser Action → Goal Achieved
                     ↑                                    ↓
                     └────────────────────────────────────┘
```

## API Usage

### Submit a Browse Job

```bash
POST /api/v1/browser/callagent
Content-Type: application/json

{
  "prompt": "Navigate to devjobs.at and find the top 3 payed Java Jobs."
}
```

**Response (202 Accepted):**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "message": "Accepted"
}
```

### Check Job Status

```bash
GET /api/v1/browser/callagent/{jobId}
```

**Response:**
```json
{
  "jobId" : "ceec1518-0e51-493b-9a3b-60a1be092950",
  "status" : "SUCCEEDED",
  "submittedAt" : 1.771144542781481E9,
  "startedAt" : 1.771144542781757E9,
  "finishedAt" : 1.771144697288907E9,
  "error" : null,
  "result" : {
    "status" : "success",
    "reason" : "The top 3 highest-paid Java jobs on devjobs.at are: 1. Teamleiter Software Development at Bundesrechenzentrum GmbH (Wien) with a salary starting at 101k € (https://devjobs.at/job/a0cb32ec837af1e2b01888fbcb472e65); 2. Senior Software Engineer at XiTrust Secure Technologies GmbH (Graz) with a salary range of 75k - 95k € (https://devjobs.at/job/20431cc52a12e8248e80f3562f5e600a); 3. JAVA Software Engineer at epunkt GmbH (Wien) with a salary range of 42k - 90k € (https://devjobs.at/job/c20674aaff9708540905b4c266eca022).",
    "steps" : 15,
    "trace" : ["step 1 | llm: {\"status\":\"action\",\"kind\":\"navigate\",\"url\":\"https://devjobs.at\",\"message\":\"Navigating to devjobs.at to find Java jobs.\"}", "step 2 | llm: {\"status\":\"action\",\"kind\":\"click\",\"ref\":\"e12\",\"message\":\"Clicking on the Java technology link to filter jobs.\"}"],
    "finalScreenshot" : "base64-image",
    "stepScreenshots" : ["base64-image"]
  }
}
```

## Configuration

Configure the application using environment variables or `application.properties`:

```properties
# LLM Configuration (via Replicate)
spring.ai.replicate.api-key=your_api_key_here

# Browser Configuration
BROWSER_LOW_RESOURCE_MODE=true
BROWSER_BLOCK_IMAGES=true
BROWSER_BLOCK_FONTS=true
BROWSER_BLOCK_MEDIA=true
BROWSER_MAX_STEPS=10
BROWSER_SNAPSHOT_MAX_CHARS=4000
BROWSER_SNAPSHOT_INCLUDE_DETAILS=false
```

### Using a System-Installed Browser

By default, Playwright downloads and manages its own Chromium binary. If you want to use a browser already installed on your system, you need two things:

1. **Skip the Playwright browser download** by setting the environment variable before starting the application:
   ```bash
   export PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
   ```
   This is a Playwright-level setting and **must** be an OS environment variable, not a property of this Application.

2. **Point the application to your browser executable** via `application.properties` or an environment variable:
   ```properties
   browser.executable-path=/Applications/Google Chrome.app/Contents/MacOS/Google Chrome
   ```
   or
   ```bash
   export BROWSER_EXECUTABLE_PATH=/Applications/Google Chrome.app/Contents/MacOS/Google Chrome
   ```

The Docker image (`mcr.microsoft.com/playwright/java`) ships with browsers pre-installed, so these settings are only needed when running outside Docker with your own browser.

## Docker
The included `docker-compose.yml` provides:
- Resource limits
- Network isolation
- Read-only filesystem where possible
- Non-root user execution


## Security Note: 
This configuration provides basic hardening but should be reviewed and enhanced based on your security requirements before production use.
Also, as any LLM-Based Tool this is prone to Prompt Injection Attacks. You can configure (un)trusted URLs in `src/main/java/com/renemrhfr/browser/security/NavigationSecurity.java`

## With great power comes great responsibility.
Always remember that not all websites are welcoming to browser automation. Respect their policies.
If you use your own accounts/cookies there's always a risk of a ban of your accounts/ip.

## Performance Optimization

### Low-Resource Mode (Raspberry Pi, ARM devices)

Optimized defaults for devices like Raspberry Pi 4:

```properties
BROWSER_LOW_RESOURCE_MODE=true
BROWSER_BLOCK_IMAGES=true
BROWSER_BLOCK_FONTS=true
BROWSER_BLOCK_MEDIA=true
BROWSER_SNAPSHOT_INCLUDE_DETAILS=false
BROWSER_SNAPSHOT_MAX_CHARS=4000
BROWSER_MAX_STEPS=10
```

### High-Performance Mode

For better reliability on powerful hardware:

```properties
BROWSER_LOW_RESOURCE_MODE=false
BROWSER_SNAPSHOT_INCLUDE_DETAILS=true
```

## Architecture

```
┌─────────────────┐
│   REST API      │
│  (Controller)   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐      ┌──────────────┐
│  Browser Core   │◄────►│  Playwright  │
│   (Executor)    │      │   Session    │
└────────┬────────┘      └──────────────┘
         │
         ▼
┌─────────────────┐      ┌──────────────┐
│ Snapshot Builder│◄────►│     LLM      │
│                 │      │  (Replicate) │
└─────────────────┘      └──────────────┘
```

## Tech Stack

- **[Spring Boot](https://spring.io/projects/spring-boot)** - Main Web Framework 
- **[Playwright](https://playwright.dev/)** - Browser automation
- **[Spring AI Replicate](https://github.com/spring-ai-community/spring-ai-replicate)** - LLM integration

## Customization

### Using Different LLM Providers

The default implementation uses Replicate.com via Spring AI. To use a different provider:

1. Remove the Spring AI Replicate dependency from `pom.xml`
2. Update the LLM calls in `src/main/java/com/renemrhfr/browser/core/Browser.java`

## Acknowledgments

- Inspired by the Browser Tool implemented in [Openclaw](https://github.com/openclaw/openclaw)
- Built with on top of my other Library [Spring AI Replicate](https://github.com/spring-ai-community/spring-ai-replicate) for seamless LLM integration via Replicate.

## License
Apache License 2.0