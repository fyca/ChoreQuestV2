/**
 * Test OAuth Setup
 * Run this function to verify OAuth credentials are configured correctly
 */
function testOAuthSetup() {
  Logger.log('=== Testing OAuth Setup ===');
  
  // Check Script Properties
  const scriptProps = PropertiesService.getScriptProperties();
  const clientId = scriptProps.getProperty('OAUTH_CLIENT_ID');
  const clientSecret = scriptProps.getProperty('OAUTH_CLIENT_SECRET');
  
  Logger.log('OAuth Client ID: ' + (clientId ? clientId.substring(0, 30) + '...' : 'NOT SET'));
  Logger.log('OAuth Client Secret: ' + (clientSecret ? 'SET (hidden)' : 'NOT SET'));
  
  if (!clientId || !clientSecret) {
    Logger.log('ERROR: OAuth credentials not configured!');
    Logger.log('Please set OAUTH_CLIENT_ID and OAUTH_CLIENT_SECRET in Script Properties');
    return;
  }
  
  // Verify Client ID format
  const expectedPrefix = '156195149694-';
  if (!clientId.startsWith(expectedPrefix)) {
    Logger.log('WARNING: OAuth Client ID does not match expected format');
    Logger.log('Expected to start with: ' + expectedPrefix);
    Logger.log('Make sure this matches GOOGLE_WEB_CLIENT_ID in Constants.kt');
  } else {
    Logger.log('✓ OAuth Client ID format looks correct');
  }
  
  // Check if it ends with .apps.googleusercontent.com
  if (!clientId.endsWith('.apps.googleusercontent.com')) {
    Logger.log('WARNING: OAuth Client ID should end with .apps.googleusercontent.com');
  } else {
    Logger.log('✓ OAuth Client ID format is valid');
  }
  
  Logger.log('');
  Logger.log('Next steps:');
  Logger.log('1. Verify the Client ID matches the one in Google Cloud Console');
  Logger.log('2. Verify redirect URI "urn:ietf:wg:oauth:2.0:oob" is configured in Google Cloud Console');
  Logger.log('3. Make a test login request from the Android app');
  Logger.log('4. Check the execution logs for token exchange results');
}
