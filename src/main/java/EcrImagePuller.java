import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.AuthorizationData;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenRequest;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenResponse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EcrImagePuller {
    private static final String DOCKER_REGISTRY = "899025839375.dkr.ecr.us-east-1.amazonaws.com";
    private static final String JIT_DOCKER_REPO = DOCKER_REGISTRY + "/jit-ide";
    private static final Region AWS_REGION = Region.US_EAST_1;
    private static final Map<String, String> IMAGE_URIS = new HashMap<>();
    
    static {
        // Define the image URIs exactly as in docker-image-management.md
        // Secrets scanning
        IMAGE_URIS.put("gitleaks", JIT_DOCKER_REPO + ":jit-gitleaks-control");
        
        // SAST tools
        IMAGE_URIS.put("semgrep", JIT_DOCKER_REPO + ":jit-semgrep-control");
        IMAGE_URIS.put("gosec", JIT_DOCKER_REPO + ":jit-gosec-control");
        
        // SCA tools
        IMAGE_URIS.put("nancy", JIT_DOCKER_REPO + ":jit-nancy-control");
        IMAGE_URIS.put("npm-audit", JIT_DOCKER_REPO + ":jit-npm-audit-control-latest");
        IMAGE_URIS.put("osv-scanner", JIT_DOCKER_REPO + ":jit-osv-scanner-control");
        
        // IaC scanning
        IMAGE_URIS.put("kics", JIT_DOCKER_REPO + ":jit-kics-control-latest");
        IMAGE_URIS.put("kubescape", JIT_DOCKER_REPO + ":jit-kubescape-control");
        IMAGE_URIS.put("trivy", JIT_DOCKER_REPO + ":jit-trivy-control");
    }
    
    private final EcrClient ecrClient;
    private final ExecutorService executorService;
    
    public EcrImagePuller() {
        Map<String, String> envVars = loadEnvFile();
        
        if (envVars.containsKey("AWS_ACCESS_KEY_ID") && envVars.containsKey("AWS_SECRET_ACCESS_KEY")) {
            System.out.println("Using AWS credentials from .env file");
            
            // Check if we have a session token
            if (envVars.containsKey("AWS_SESSION_TOKEN")) {
                // Create session credentials with token
                AwsSessionCredentials credentials = AwsSessionCredentials.create(
                    envVars.get("AWS_ACCESS_KEY_ID"),
                    envVars.get("AWS_SECRET_ACCESS_KEY"),
                    envVars.get("AWS_SESSION_TOKEN")
                );
                this.ecrClient = EcrClient.builder()
                    .region(AWS_REGION)
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .build();
            } else {
                // Create basic credentials without token
                AwsBasicCredentials credentials = AwsBasicCredentials.create(
                    envVars.get("AWS_ACCESS_KEY_ID"),
                    envVars.get("AWS_SECRET_ACCESS_KEY")
                );
                this.ecrClient = EcrClient.builder()
                    .region(AWS_REGION)
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .build();
            }
        } else {
            // Fall back to default credentials provider
            System.out.println("No AWS credentials found in .env file, using default credentials provider");
            this.ecrClient = EcrClient.builder()
                .region(AWS_REGION)
                .build();
        }
        
        this.executorService = Executors.newFixedThreadPool(4);
    }
    
    private Map<String, String> loadEnvFile() {
        Map<String, String> envVars = new HashMap<>();
        File envFile = new File(".env");
        
        if (!envFile.exists()) {
            System.out.println("No .env file found");
            return envVars;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                int equalIndex = line.indexOf('=');
                if (equalIndex > 0) {
                    String key = line.substring(0, equalIndex).trim();
                    String value = line.substring(equalIndex + 1).trim();
                    
                    // Remove quotes if present
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    
                    envVars.put(key, value);
                }
            }
            
            System.out.println("Loaded " + envVars.size() + " environment variables from .env file");
        } catch (IOException e) {
            System.err.println("Error reading .env file: " + e.getMessage());
        }
        
        return envVars;
    }
    
    public static void main(String[] args) {
        // Verify Docker is installed and running
        if (!isDockerRunning()) {
            System.err.println("Docker is not installed or not running. Please install Docker and try again.");
            System.exit(1);
        }

        EcrImagePuller puller = new EcrImagePuller();
        
        try {
            // Authenticate with ECR
            System.out.println("Authenticating with AWS ECR...");
            if (!puller.authenticateEcr()) {
                System.err.println("Failed to authenticate with ECR. Check your AWS credentials.");
                System.exit(1);
            }
            
            // Determine which images to pull
            String[] imagesToPull;
            if (args.length > 0) {
                // Validate image names
                boolean hasUnknownImage = false;
                for (String imageName : args) {
                    if (!IMAGE_URIS.containsKey(imageName)) {
                        System.err.println("Unknown image: " + imageName);
                        hasUnknownImage = true;
                    }
                }
                
                if (hasUnknownImage) {
                    System.out.println("\nAvailable images:");
                    IMAGE_URIS.keySet().stream().sorted().forEach(name -> System.out.println("- " + name));
                    System.exit(1);
                }
                
                imagesToPull = args;
            } else {
                // Default images to pull if none specified - based on INITIAL_CONTROLS from the doc
                imagesToPull = new String[]{"gitleaks", "semgrep", "trivy", "kics"};
            }
            
            // Pull the specified images
            puller.pullImages(imagesToPull);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            puller.shutdown();
        }
    }
    
    private static boolean isDockerRunning() {
        try {
            Process process = Runtime.getRuntime().exec("docker info");
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
    
    private boolean authenticateEcr() {
        try {
            GetAuthorizationTokenResponse response = ecrClient.getAuthorizationToken(
                GetAuthorizationTokenRequest.builder().build()
            );
            
            List<AuthorizationData> authDataList = response.authorizationData();
            if (authDataList.isEmpty()) {
                System.err.println("No authorization data received from ECR");
                return false;
            }
            
            AuthorizationData authData = authDataList.get(0);
            String authToken = authData.authorizationToken();
            String decodedToken = new String(Base64.getDecoder().decode(authToken), StandardCharsets.UTF_8);
            String[] parts = decodedToken.split(":");
            
            if (parts.length != 2) {
                System.err.println("Invalid authorization token format");
                return false;
            }
            
            String username = parts[0];
            String password = parts[1];
            
            // Execute docker login command
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "login", "--username", username, "--password-stdin", DOCKER_REGISTRY
            );
            
            Process process = processBuilder.start();
            process.getOutputStream().write(password.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            
            // Read the output
            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("Successfully authenticated with ECR");
                return true;
            } else {
                System.err.println("Docker login failed: " + output);
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error during ECR authentication: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private void pullImages(String[] imageNames) {
        System.out.println("Pulling " + imageNames.length + " Docker images...");
        
        CompletableFuture<?>[] futures = new CompletableFuture[imageNames.length];
        
        for (int i = 0; i < imageNames.length; i++) {
            final String imageName = imageNames[i];
            futures[i] = CompletableFuture.runAsync(() -> {
                String imageUri = IMAGE_URIS.get(imageName);
                if (imageUri == null) {
                    System.err.println("Unknown image: " + imageName);
                    return;
                }
                
                try {
                    System.out.println("Pulling image: " + imageName + " (" + imageUri + ")");
                    ProcessBuilder processBuilder = new ProcessBuilder("docker", "pull", imageUri);
                    processBuilder.inheritIO(); // Stream output to console
                    
                    Process process = processBuilder.start();
                    int exitCode = process.waitFor();
                    
                    if (exitCode == 0) {
                        System.out.println("Successfully pulled image: " + imageName);
                    } else {
                        System.err.println("Failed to pull image: " + imageName);
                    }
                } catch (Exception e) {
                    System.err.println("Error pulling image " + imageName + ": " + e.getMessage());
                }
            }, executorService);
        }
        
        // Wait for all pulls to complete
        CompletableFuture.allOf(futures).join();
        System.out.println("All image pulls completed");
    }
    
    private void shutdown() {
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        ecrClient.close();
    }
} 