# Setup Instructions

We've created a Java application that can authenticate with AWS ECR and pull Docker images to your local environment. Here's what we've done and what you need to do next:

## What's Been Done

1. Created a Java application (`EcrImagePuller.java`) that:
   - Authenticates with AWS ECR using the AWS SDK
   - Pulls Docker images from the JIT ECR registry
   - Handles errors and provides feedback

2. Set up a Maven project with the necessary dependencies (`pom.xml`)

3. Created documentation (`README.md`) with usage instructions

4. Installed the required tools:
   - Maven (via Homebrew)
   - Java 11 (via Homebrew)

## What You Need to Do

1. **Start Docker Desktop**:
   - Open Docker Desktop application
   - Wait for it to start completely

2. **Configure AWS Credentials**:
   You need valid AWS credentials with access to the ECR repository. Set them up using one of these methods:

   a. Create or update `~/.aws/credentials`:
      ```
      [default]
      aws_access_key_id=YOUR_ACCESS_KEY
      aws_secret_access_key=YOUR_SECRET_KEY
      ```

   b. Set environment variables:
      ```
      export AWS_ACCESS_KEY_ID=YOUR_ACCESS_KEY
      export AWS_SECRET_ACCESS_KEY=YOUR_SECRET_KEY
      ```

3. **Run the Application**:
   ```
   java -jar target/ecr-image-puller-1.0-SNAPSHOT-jar-with-dependencies.jar
   ```

   Or specify which images to pull:
   ```
   java -jar target/ecr-image-puller-1.0-SNAPSHOT-jar-with-dependencies.jar gitleaks semgrep
   ```

## Troubleshooting

- If you see SLF4J warnings, they can be safely ignored. They're just informational messages about logging.
- If you get authentication errors, double-check your AWS credentials and ensure they have access to the ECR repository.
- If Docker commands fail, make sure Docker Desktop is running and you have internet connectivity.

## Available Images

The following images are available to pull:
- `gitleaks`: Secret scanning tool
- `semgrep`: Static code analysis tool
- `gosec`: Go security scanner
- `trivy`: Vulnerability scanner
- `kics`: Infrastructure as Code scanner
- `checkov`: Infrastructure as Code scanner
- `npm-audit`: Node.js dependency scanner
- `osv-scanner`: Open Source Vulnerability scanner 