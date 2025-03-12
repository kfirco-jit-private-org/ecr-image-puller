import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenRequest;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenResponse;
import software.amazon.awssdk.services.ecr.model.AuthorizationData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class EcrImagePuller {
    // AWS ECR Registry details
    private static final String DOCKER_REGISTRY = "899025839375.dkr.ecr.us-east-1.amazonaws.com";
    private static final String JIT_DOCKER_REPO = DOCKER_REGISTRY + "/jit-ide";
    private static final Region AWS_REGION = Region.US_EAST_1;

    // Map of image names to their complete URIs
    private static final Map<String, String> IMAGES = new HashMap<>();
    static {
        // Secrets scanning
        IMAGES.put("gitleaks", JIT_DOCKER_REPO + ":jit-gitleaks-control");
        
        // SAST tools
        IMAGES.put("semgrep", JIT_DOCKER_REPO + ":jit-semgrep-control");
        IMAGES.put("gosec", JIT_DOCKER_REPO + ":jit-gosec-control");
        
        // SCA tools
        IMAGES.put("nancy", JIT_DOCKER_REPO + ":jit-nancy-control");
        IMAGES.put("npmAudit", JIT_DOCKER_REPO + ":jit-npm-audit-control-latest");
        IMAGES.put("osvScanner", JIT_DOCKER_REPO + ":jit-osv-scanner-control");
        
        // IaC scanning
        IMAGES.put("kics", JIT_DOCKER_REPO + ":jit-kics-control-latest");
        IMAGES.put("kubescape", JIT_DOCKER_REPO + ":jit-kubescape-control");
        IMAGES.put("trivy", JIT_DOCKER_REPO + ":jit-trivy-control");
    }

    // Default images to pull if none specified
    private static final String[] DEFAULT_IMAGES = {"gitleaks", "semgrep", "trivy", "kics"};

    private final EcrClient ecrClient;
    private final ExecutorService executorService;

    public EcrImagePuller() {
        // Initialize the ECR client
        this.ecrClient = EcrClient.builder()
                .region(AWS_REGION)
                .credentialsProvider(ProfileCredentialsProvider.create())
                .build();
        
        // Create a thread pool for parallel image pulls
        this.executorService = Executors.newFixedThreadPool(4);
    }

    public static void main(String[] args) {
        EcrImagePuller puller = new EcrImagePuller();
        
        try {
            // Verify Docker is installed and running
            if (!puller.verifyDocker()) {
                System.err.println("Docker is not installed or not running. Please install Docker and try again.");
                System.exit(1);
            }
            
            // Authenticate with the ECR registry
            if (!puller.authenticateEcrRegistry()) {
                System.err.println("Failed to authenticate with ECR registry.");
                System.exit(1);
            }
            
            // Pull the default images or images specified in args
            String[] imagesToPull = args.length > 0 ? args : DEFAULT_IMAGES;
            puller.pullImages(imagesToPull);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            puller.shutdown();
        }
    }

    public boolean verifyDocker() {
        try {
            Process process = Runtime.getRuntime().exec("docker --version");
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                return false;
            }
            
            // Also verify Docker daemon is running
            process = Runtime.getRuntime().exec("docker info");
            exitCode = process.waitFor();
            return exitCode == 0;
            
        } catch (IOException | InterruptedException e) {
            System.err.println("Error verifying Docker: " + e.getMessage());
            return false;
        }
    }

    public boolean authenticateEcrRegistry() {
        try {
            // Request authorization token for ECR
            GetAuthorizationTokenRequest tokenRequest = GetAuthorizationTokenRequest.builder().build();
            GetAuthorizationTokenResponse tokenResponse = ecrClient.getAuthorizationToken(tokenRequest);
            
            if (tokenResponse.authorizationData().isEmpty()) {
                System.err.println("Failed to get ECR authorization token");
                return false;
            }
            
            AuthorizationData authData = tokenResponse.authorizationData().get(0);
            String authToken = authData.authorizationToken();
            
            // Decode authorization token (format: AWS:password)
            String decodedToken = new String(Base64.getDecoder().decode(authToken));
            String[] parts = decodedToken.split(":");
            
            if (parts.length != 2) {
                System.err.println("Invalid authorization token format");
                return false;
            }
            
            String username = parts[0];
            String password = parts[1];
            
            // Execute docker login command
            String[] loginCommand = {
                "sh", "-c", "echo " + password + " | docker login --username " + username + " --password-stdin " + DOCKER_REGISTRY
            };
            
            Process process = Runtime.getRuntime().exec(loginCommand);
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                System.out.println("Successfully authenticated with ECR registry");
                return true;
            } else {
                String errorOutput = new BufferedReader(new InputStreamReader(process.getErrorStream()))
                        .lines().collect(Collectors.joining("\n"));
                System.err.println("Docker login failed: " + errorOutput);
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("Authentication error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void pullImages(String[] imageNames) {
        System.out.println("Starting to pull images...");
        
        // Create a future for each image pull task
        CompletableFuture<?>[] futures = new CompletableFuture[imageNames.length];
        
        for (int i = 0; i < imageNames.length; i++) {
            final String imageName = imageNames[i];
            
            if (!IMAGES.containsKey(imageName)) {
                System.err.println("Unknown image: " + imageName);
                continue;
            }
            
            final String imageUri = IMAGES.get(imageName);
            
            // Create a future for each image pull
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    System.out.println("Pulling image: " + imageName + " (" + imageUri + ")");
                    
                    Process process = Runtime.getRuntime().exec("docker pull " + imageUri);
                    
                    // Stream the output as the image is being pulled
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(imageName + ": " + line);
                    }
                    
                    int exitCode = process.waitFor();
                    
                    if (exitCode == 0) {
                        System.out.println("Successfully pulled image: " + imageName);
                    } else {
                        String errorOutput = new BufferedReader(new InputStreamReader(process.getErrorStream()))
                                .lines().collect(Collectors.joining("\n"));
                        System.err.println("Failed to pull image " + imageName + ": " + errorOutput);
                    }
                    
                } catch (IOException | InterruptedException e) {
                    System.err.println("Error pulling image " + imageName + ": " + e.getMessage());
                }
            }, executorService);
        }
        
        // Wait for all pulls to complete
        CompletableFuture.allOf(futures).join();
        System.out.println("All image pull tasks completed");
    }

    public void shutdown() {
        // Shutdown the executor service
        executorService.shutdown();
        
        // Close the ECR client
        ecrClient.close();
    }
} 