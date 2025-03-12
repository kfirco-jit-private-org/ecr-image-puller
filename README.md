# ECR Image Puller

A Java application for authenticating with AWS ECR and pulling Docker images to your local Docker environment.

## Description

This application authenticates with the AWS Elastic Container Registry (ECR) and pulls specified Docker images to your local Docker environment. It's designed to work with the JIT IDE security scanning tools stored in the private ECR registry.

## Prerequisites

- Java 11 or higher
- Maven
- Docker
- AWS credentials with access to ECR

### Installing Java

If you don't have Java installed, you can install it using one of the following methods:

#### macOS (using Homebrew)
```bash
brew install openjdk@11
```

#### Linux (Debian/Ubuntu)
```bash
sudo apt update
sudo apt install openjdk-11-jdk
```

#### Windows
Download and install from [AdoptOpenJDK](https://adoptopenjdk.net/) or [Oracle](https://www.oracle.com/java/technologies/javase-jdk11-downloads.html)

### Verifying Java Installation
```bash
java -version
```

## Setup AWS Credentials

The application supports reading AWS credentials from a `.env` file in the project directory. Create a `.env` file with the following format:

```
AWS_ACCESS_KEY_ID=your_access_key
AWS_SECRET_ACCESS_KEY=your_secret_key
AWS_SESSION_TOKEN=your_session_token  # Required for temporary credentials
```

If you're using temporary credentials (access keys that start with "ASIA"), you must include the `AWS_SESSION_TOKEN`.

### Handling Expired AWS Credentials

Temporary AWS credentials typically expire after 12-24 hours. If you see an error like:
```
Error during ECR authentication: The security token included in the request is expired
```

You'll need to update your `.env` file with fresh credentials. You can obtain new temporary credentials through:
- AWS Management Console
- AWS CLI using `aws sts get-session-token`
- Your organization's identity provider

Alternatively, you can configure AWS credentials using one of the following methods:
- AWS credentials file (`~/.aws/credentials`)
- Environment variables
- IAM roles for EC2 instances or ECS tasks

## Building the Application

```bash
mvn clean package
```

This will create an executable JAR file at `target/ecr-image-puller-1.0-SNAPSHOT-jar-with-dependencies.jar`.

## Running the Application

### Using the Makefile (Recommended)

The easiest way to run the application is to use the provided Makefile:

```bash
# Build the application
make build

# Run with default images (gitleaks, semgrep, trivy, kics)
make run

# Pull all available images
make pull-all

# Pull only SAST tools (semgrep, gosec)
make pull-sast

# Pull only SCA tools (nancy, npm-audit, osv-scanner)
make pull-sca

# Pull only IaC scanning tools (kics, kubescape, trivy)
make pull-iac

# Pull only secrets scanning tools (gitleaks)
make pull-secrets

# Display help
make help
```

### Using the Shell Script

You can also use the provided shell script:

```bash
./pull-images.sh
```

This script will:
1. Check if Docker is running
2. Build the application if the JAR file doesn't exist
3. Run the application with any provided arguments

To pull specific images:

```bash
./pull-images.sh gitleaks semgrep
```

### Using Java Directly

Alternatively, you can run the application directly with Java:

```bash
java -jar target/ecr-image-puller-1.0-SNAPSHOT-jar-with-dependencies.jar
```

By default, the application will pull the following images:
- gitleaks
- semgrep
- trivy
- kics

To specify which images to pull, provide their names as command-line arguments:

```bash
java -jar target/ecr-image-puller-1.0-SNAPSHOT-jar-with-dependencies.jar gitleaks semgrep
```

## Available Images

The application can pull the following Docker images:

### Secrets Scanning
- `gitleaks`: Scan git repositories for secrets and keys

### Static Application Security Testing (SAST)
- `semgrep`: Static analysis tool for finding bugs and enforcing code standards
- `gosec`: Security scanner for Go code

### Software Composition Analysis (SCA)
- `nancy`: Security scanner for Go dependencies
- `npm-audit`: Security audit tool for npm packages
- `osv-scanner`: Vulnerability scanner that uses the Open Source Vulnerabilities database

### Infrastructure as Code (IaC) Scanning
- `kics`: Find security vulnerabilities, compliance issues, and infrastructure misconfigurations
- `kubescape`: Security scanner for Kubernetes
- `trivy`: Vulnerability scanner for containers and other artifacts

## How It Works

1. The application first checks if Docker is installed and running.
2. It authenticates with AWS ECR using the provided credentials.
3. It pulls the specified Docker images in parallel.
4. It streams the Docker pull output to the console.

## Troubleshooting

### Authentication Errors

If you encounter authentication errors:
- Ensure your AWS credentials are valid and have permission to access ECR
- For temporary credentials, make sure you've included the session token
- Check that your credentials haven't expired

### Docker Errors

If you encounter Docker-related errors:
- Ensure Docker is installed and running
- Check that you have sufficient disk space
- Verify that you have permission to pull from the ECR repository

### Network Issues

If you encounter network issues:
- Check your internet connection
- Verify that outbound connections to AWS ECR are allowed by your firewall
- Try using a VPN if you're on a restricted network

## Documentation

Additional documentation is available in the `docs/` folder:

- [Project Summary](docs/SUMMARY.md) - Overview of the project and its components
- [Final Summary](docs/FINAL_SUMMARY.md) - Comprehensive summary of the project
- [Setup Instructions](docs/SETUP_INSTRUCTIONS.md) - Detailed setup instructions
- [Improvements Summary](docs/IMPROVEMENTS_SUMMARY.md) - Overview of all improvements made

For a complete list of documentation, see the [Documentation Index](docs/INDEX.md).

## License

This project is licensed under the MIT License.