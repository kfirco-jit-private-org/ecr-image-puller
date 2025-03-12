# Docker Image Management in VSCode-JIT Extension

## Overview

The VSCode-JIT extension uses Docker containers to run various security scanning tools. This document explains how the extension manages Docker images, including authentication, pulling, and usage.

## Docker Registry

- The extension uses a private AWS ECR registry: `899025839375.dkr.ecr.us-east-1.amazonaws.com/jit-ide`
- All security scanning tool images are stored in this registry

## Complete Image List

To pull the exact same images in a different project, you'll need these precise image URIs:

```javascript
// Base registry path
const DOCKER_REGISTRY = '899025839375.dkr.ecr.us-east-1.amazonaws.com';
const JIT_DOCKER_REPO = `${DOCKER_REGISTRY}/jit-ide`;

// Complete image URIs
const IMAGES = {
  // Secrets scanning
  gitleaks: `${JIT_DOCKER_REPO}:jit-gitleaks-control`,
  
  // SAST tools
  semgrep: `${JIT_DOCKER_REPO}:jit-semgrep-control`,
  gosec: `${JIT_DOCKER_REPO}:jit-gosec-control`,
  
  // SCA tools
  nancy: `${JIT_DOCKER_REPO}:jit-nancy-control`,
  npmAudit: `${JIT_DOCKER_REPO}:jit-npm-audit-control-latest`,
  osvScanner: `${JIT_DOCKER_REPO}:jit-osv-scanner-control`,
  
  // IaC scanning
  kics: `${JIT_DOCKER_REPO}:jit-kics-control-latest`,
  kubescape: `${JIT_DOCKER_REPO}:jit-kubescape-control`,
  trivy: `${JIT_DOCKER_REPO}:jit-trivy-control`
};
```

## Initial Controls to Pull

The extension pulls these initial control images on startup:

```javascript
// From src/config/constants.ts - these are the controls loaded initially
const INITIAL_CONTROLS = ['gitleaks', 'semgrepJS', 'trivy', 'kicsTerraform'] as ControlNamesType[];
```

## Authentication Flow

1. The extension authenticates with the Docker registry using the `authenticateRegistry()` function in `src/authentication/authenticationUtils.ts`
2. Authentication process:
   - Get the user's session token from VSCode authentication
   - Call the Jit API endpoint `ide/registry/authenticate` to get registry credentials
   - Use the returned credentials (username, password, registry URL) to log in to Docker
   - The Docker login command is executed using `docker login --username {username} --password-stdin {registry_url}`

### Authentication Code Sample

To implement the same authentication in a different project:

```typescript
// Authentication with the registry
async function authenticateRegistry() {
  try {
    // In a different project, you'd need to replace this with your own auth method
    // to obtain these credentials from Jit's API
    const { username, password, registry_url: registryUrl } = await getRegistryAuthDetails();
    
    // The docker login command used
    const loginCmd = `echo ${password} | docker login --username ${username} --password-stdin ${registryUrl}`;
    await executeShellCommand(loginCmd);
    console.log('Registry authenticated');
  } catch (error) {
    console.error('Authentication error:', error);
    throw error;
  }
}
```

## Image Management

### Image Tracking
- The extension keeps track of already installed and currently pulling images using two sets:
  - `existingImages`: Tracks images that are already installed locally
  - `currentlyPulling`: Tracks images that are currently being pulled

### Image Pulling Process

1. **Initial Check**: On extension activation, the `verifyDocker()` function checks if Docker is installed and running
2. **Authentication**: If Docker is available, the extension authenticates with the registry using `authenticateRegistry()`
3. **Initial Pulls**: The extension pulls initial control images defined in `INITIAL_CONTROLS`
4. **Determining Images to Pull**:
   - The `getImagesNamesToPull()` function checks which images need to be pulled
   - It filters out images that are already installed or currently being pulled
   - It uses `docker images` command to get the list of locally installed images
5. **Pulling Images**:
   - The `pullImages()` function handles the actual pulling of images
   - It uses the Docker command `docker pull {imageUri}` to download each image
   - Pulling happens asynchronously with Promise.allSettled
   - Status messages are displayed in the output channel

### Image Pulling Implementation

```typescript
// To implement the same image pulling logic in a different project:

// Check installed controls
async function getInstalledControls() {
  // Command to list all Docker images in the format "repository:tag"
  const command = 'docker images --format "{{.Repository}}:{{.Tag}}"';
  const { stdout } = await executeShellCommand(command);
  const localImages = stdout.split(/\r?\n/).filter((image) => image.includes(JIT_DOCKER_REPO));
  return Object.entries(controlNameToImage)
    .filter(([, image]) => localImages.includes(image.containerUri))
    .map(([key]) => key);
}

// Pull images function
async function pullImages(imagesNames, force = false, silent = false) {
  try {
    const imagesRes = force ? imagesNames : await getImagesNamesToPull([...new Set(imagesNames)]);
    if (imagesRes.length) {
      const pullImagesPromises = imagesRes.map(async (imageName) => {
        const image = controlNameToImage[imageName];
        console.log(`Pulling ${imageName} image`);
        if (!silent) {
          console.info(`Pulling image ${image.displayName}, this might take a while...`);
        }
        const pullCommand = `docker pull ${image.containerUri}`;
        try {
          const result = await executeShellCommand(pullCommand);
          console.log(`Pulling ${imageName} finished`);
          if (!silent) {
            console.info(`Image ${image.displayName} was pulled successfully`);
          }
          return result;
        } catch (error) {
          console.error(`Security tools failed to pull, make sure Docker is up & running`, error);
          throw error;
        }
      });

      await Promise.allSettled(pullImagesPromises);
    }
  } catch (error) {
    console.error(error.message, error);
  }
}
```

### Error Handling
- The extension has robust error handling for Docker-related operations:
  - Shows an error if Docker is not installed with a link to installation docs
  - Shows an error if Docker is not running
  - Automatically re-authenticates if credentials expire (via `withAuthenticationErrorHandling`)

## Security Tools and Images

The extension uses various Docker images for different security scanning purposes:

1. **Secret Scanning**:
   - `gitleaks`: Detects secrets in code
   - Complete URI: `899025839375.dkr.ecr.us-east-1.amazonaws.com/jit-ide:jit-gitleaks-control`
   - Entry point params: `detect --config /config/gitleaks.toml --source /code -v --report-format json --report-path /tmp/controls/report.json --redact --no-git --exit-code 0`

2. **Static Application Security Testing (SAST)**:
   - `semgrep`: For Python, JavaScript, Java, Scala, C#, Swift, and Rust
     - Complete URI: `899025839375.dkr.ecr.us-east-1.amazonaws.com/jit-ide:jit-semgrep-control`
     - Entry point params for JavaScript: `--json --config=/semgrep-ts-config.yml --metrics=off --severity=ERROR /code`
   - `gosec`: For Go
     - Complete URI: `899025839375.dkr.ecr.us-east-1.amazonaws.com/jit-ide:jit-gosec-control`
     - Entry point params: `-fmt=json -severity=high -exclude=G101 /code/...`

3. **Software Composition Analysis (SCA)**:
   - `nancy`: For Go dependencies
     - Complete URI: `899025839375.dkr.ecr.us-east-1.amazonaws.com/jit-ide:jit-nancy-control`
   - `npm-audit`: For JavaScript dependencies
     - Complete URI: `899025839375.dkr.ecr.us-east-1.amazonaws.com/jit-ide:jit-npm-audit-control-latest`
     - Entry point params: `audit --json --production`
   - `osv-scanner`: For Python and PHP dependencies
     - Complete URI: `899025839375.dkr.ecr.us-east-1.amazonaws.com/jit-ide:jit-osv-scanner-control`
     - Entry point params: `--recursive /code`

4. **Infrastructure as Code (IaC) Scanning**:
   - `kics`: For Terraform, Serverless, Pulumi, CloudFormation
     - Complete URI: `899025839375.dkr.ecr.us-east-1.amazonaws.com/jit-ide:jit-kics-control-latest`
     - Entry point params for Terraform: `scan -t Terraform -p /code -o /tmp/controls/kics/jit-report -f json --config /terraform-config.yaml --disable-secrets`
   - `kubescape`: For Kubernetes
     - Complete URI: `899025839375.dkr.ecr.us-east-1.amazonaws.com/jit-ide:jit-kubescape-control`
     - Entry point params: `scan -v --format json --format-version v2 --output /tmp/controls/kubescape/jit-report/raw-kubescape-results.json .`
   - `trivy`: For Docker files
     - Complete URI: `899025839375.dkr.ecr.us-east-1.amazonaws.com/jit-ide:jit-trivy-control`
     - Entry point params: `--quiet config --severity HIGH,CRITICAL -f json --ignorefile /opt/.trivyignore /code`

## Running the Containers

When running a container for scanning, the extension uses a command like this:

```
docker run --rm -v {localPath}:/code {imageUri} {entryPointParams}
```

Example for running gitleaks:

```
docker run --rm -v /path/to/project:/code 899025839375.dkr.ecr.us-east-1.amazonaws.com/jit-ide:jit-gitleaks-control detect --config /config/gitleaks.toml --source /code -v --report-format json --report-path /tmp/controls/report.json --redact --no-git --exit-code 0
```

## Technical Implementation

1. **Docker Commands**: Defined in `src/config/constants.ts` under the `DOCKER` constant
2. **Image Definitions**: Found in `src/controls/dockerImages.ts`
3. **Docker Utilities**: Implemented in `src/controls/dockerUtils.ts`
4. **Authentication**: Handled in `src/authentication/authenticationUtils.ts`

## Complete Replication Steps

To pull and use these images in a different project:

1. **Authentication**:
   - You need credentials for the AWS ECR registry
   - Use the login command: `docker login --username AWS --password-stdin 899025839375.dkr.ecr.us-east-1.amazonaws.com`
   - The password needs to be obtained from AWS ECR (typically via `aws ecr get-login-password`)

2. **Pulling Images**:
   - Use `docker pull` with the complete URIs listed above
   - Example: `docker pull 899025839375.dkr.ecr.us-east-1.amazonaws.com/jit-ide:jit-gitleaks-control`

3. **Running Security Scans**:
   - Mount your code directory to `/code` in the container
   - Use the entry point parameters specified for each tool
   - Collect results from the output locations (typically `/tmp/controls/`)

Note: Since this is a private registry, you'll need proper AWS credentials to access it. In a different project, you may either need to:
1. Use your own AWS credentials with access to this registry
2. Set up your own registry with similar images
3. Contact Jit for access permissions 