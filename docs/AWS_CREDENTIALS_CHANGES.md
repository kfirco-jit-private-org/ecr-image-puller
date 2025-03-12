# AWS Credentials Handling - Changes Summary

## Problem Identified
The application was failing with the error:
```
Error during ECR authentication: The security token included in the request is expired
```

This is a common issue with temporary AWS credentials, which typically expire after 12-24 hours.

## Changes Made

### 1. Updated README.md
- Added a new section "Handling Expired AWS Credentials" that:
  - Explains the expiration issue with temporary credentials
  - Shows the error message users might encounter
  - Provides guidance on how to obtain new credentials
  - Lists alternative credential configuration methods

### 2. Updated Makefile
- Added a new `check-aws` target that:
  - Checks if a `.env` file exists
  - Detects if temporary credentials (with session token) are being used
  - Warns users about potential expiration
- Modified all run targets to:
  - Depend on the `check-aws` target
  - Provide helpful error messages if authentication fails
  - Suggest updating credentials when they expire

### 3. Updated Shell Script (pull-images.sh)
- Added credential checking before running the application
- Added post-execution checks to detect authentication failures
- Provided clear guidance on updating expired credentials

## Benefits
- Improved user experience with proactive warnings about credential expiration
- Clearer error messages when authentication fails
- Specific guidance on how to resolve credential issues
- Reduced confusion for users encountering expired credentials

## Testing
- Verified that the `check-aws` target correctly identifies temporary credentials
- Confirmed that appropriate warnings are displayed
- Ensured that error messages are clear and actionable

## Next Steps
- Consider adding an option to automatically refresh credentials if possible
- Add more detailed troubleshooting steps for different AWS credential scenarios
- Consider implementing a credential validation check before attempting to authenticate with ECR 