.PHONY: build run clean pull-all pull-sast pull-sca pull-iac pull-secrets help check-java check-aws

# Default target
.DEFAULT_GOAL := help

# Variables
JAR_FILE = target/ecr-image-puller-1.0-SNAPSHOT-jar-with-dependencies.jar
JAVA_CMD = java

# Help target
help:
	@echo "ECR Image Puller - Makefile Help"
	@echo ""
	@echo "Available targets:"
	@echo "  build       - Build the Java application"
	@echo "  run         - Run the application with default images"
	@echo "  clean       - Clean the build artifacts"
	@echo "  pull-all    - Pull all available images"
	@echo "  pull-sast   - Pull SAST (Static Application Security Testing) images"
	@echo "  pull-sca    - Pull SCA (Software Composition Analysis) images"
	@echo "  pull-iac    - Pull IaC (Infrastructure as Code) scanning images"
	@echo "  pull-secrets- Pull secrets scanning images"
	@echo "  check-aws   - Check AWS credentials in .env file"
	@echo ""
	@echo "Examples:"
	@echo "  make build"
	@echo "  make run"
	@echo "  make pull-sast"
	@echo ""

# Check if Java is installed
check-java:
	@echo "Checking if Java is installed..."
	@command -v $(JAVA_CMD) > /dev/null 2>&1 || (echo "Java is not installed or not in PATH. Please install Java and try again." && exit 1)
	@echo "Java is installed."

# Check AWS credentials
check-aws:
	@echo "Checking AWS credentials..."
	@if [ -f ".env" ]; then \
		echo "Found .env file with AWS credentials."; \
		if grep -q "AWS_SESSION_TOKEN" .env; then \
			echo "Note: You are using temporary AWS credentials which may expire."; \
			echo "If authentication fails, you may need to update your credentials in the .env file."; \
		fi; \
	else \
		echo "No .env file found. Will use default AWS credential provider chain."; \
	fi

# Build the application
build: check-java
	@echo "Building the application..."
	mvn clean package

# Run the application with default images
run: check-docker check-java check-aws
	@echo "Running the application with default images..."
	$(JAVA_CMD) -jar $(JAR_FILE) || (echo ""; echo "If you see an error about expired credentials, please update your AWS credentials in the .env file."; echo "Temporary AWS credentials typically expire after 12-24 hours."; exit 1)

# Clean the build artifacts
clean:
	@echo "Cleaning build artifacts..."
	mvn clean

# Check if Docker is running
check-docker:
	@echo "Checking if Docker is running..."
	@docker info > /dev/null 2>&1 || (echo "Docker is not running. Please start Docker and try again." && exit 1)
	@echo "Docker is running."

# Pull all available images
pull-all: check-docker check-java check-aws build
	@echo "Pulling all available images..."
	$(JAVA_CMD) -jar $(JAR_FILE) gitleaks semgrep gosec nancy npm-audit osv-scanner kics kubescape trivy || (echo ""; echo "If you see an error about expired credentials, please update your AWS credentials in the .env file."; echo "Temporary AWS credentials typically expire after 12-24 hours."; exit 1)

# Pull SAST (Static Application Security Testing) images
pull-sast: check-docker check-java check-aws build
	@echo "Pulling SAST images..."
	$(JAVA_CMD) -jar $(JAR_FILE) semgrep gosec || (echo ""; echo "If you see an error about expired credentials, please update your AWS credentials in the .env file."; echo "Temporary AWS credentials typically expire after 12-24 hours."; exit 1)

# Pull SCA (Software Composition Analysis) images
pull-sca: check-docker check-java check-aws build
	@echo "Pulling SCA images..."
	$(JAVA_CMD) -jar $(JAR_FILE) nancy npm-audit osv-scanner || (echo ""; echo "If you see an error about expired credentials, please update your AWS credentials in the .env file."; echo "Temporary AWS credentials typically expire after 12-24 hours."; exit 1)

# Pull IaC (Infrastructure as Code) scanning images
pull-iac: check-docker check-java check-aws build
	@echo "Pulling IaC scanning images..."
	$(JAVA_CMD) -jar $(JAR_FILE) kics kubescape trivy || (echo ""; echo "If you see an error about expired credentials, please update your AWS credentials in the .env file."; echo "Temporary AWS credentials typically expire after 12-24 hours."; exit 1)

# Pull secrets scanning images
pull-secrets: check-docker check-java check-aws build
	@echo "Pulling secrets scanning images..."
	$(JAVA_CMD) -jar $(JAR_FILE) gitleaks || (echo ""; echo "If you see an error about expired credentials, please update your AWS credentials in the .env file."; echo "Temporary AWS credentials typically expire after 12-24 hours."; exit 1) 