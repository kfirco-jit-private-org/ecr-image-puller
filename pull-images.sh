#!/bin/bash

# Check if Java is installed
if ! command -v java > /dev/null 2>&1; then
  echo "Java is not installed or not in PATH. Please install Java and try again."
  echo "You can install Java using:"
  echo "  macOS:   brew install openjdk@11"
  echo "  Ubuntu:  sudo apt install openjdk-11-jdk"
  exit 1
fi

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
  echo "Docker is not running. Please start Docker and try again."
  exit 1
fi

# Check if .env file exists and warn about potential expired credentials
if [ -f ".env" ]; then
  echo "Found .env file with AWS credentials."
  
  # Check if the file contains AWS_SESSION_TOKEN (indicating temporary credentials)
  if grep -q "AWS_SESSION_TOKEN" .env; then
    echo "Note: You are using temporary AWS credentials which may expire."
    echo "If authentication fails, you may need to update your credentials in the .env file."
  fi
fi

# Check if the JAR file exists
JAR_FILE="target/ecr-image-puller-1.0-SNAPSHOT-jar-with-dependencies.jar"
if [ ! -f "$JAR_FILE" ]; then
  echo "JAR file not found. Building the application..."
  mvn clean package
  
  if [ $? -ne 0 ]; then
    echo "Failed to build the application. Please check the error messages."
    exit 1
  fi
fi

# Run the application with any provided arguments
echo "Running ECR Image Puller..."
java -jar "$JAR_FILE" "$@"

# Check the exit code
EXIT_CODE=$?
if [ $EXIT_CODE -eq 1 ]; then
  echo ""
  echo "The application failed. If you see an error about expired credentials, please update your AWS credentials in the .env file."
  echo "Temporary AWS credentials typically expire after 12-24 hours."
fi

exit $EXIT_CODE 