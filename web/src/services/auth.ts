/**
 * Authentication service for ChoreQuest
 * Handles Google OAuth and QR code authentication
 */

import type { AuthResponse, DeviceSession, QRCodePayload } from '../types/models';
import { STORAGE_KEYS, QR_CODE, ERROR_MESSAGES } from '../types/constants';

const APPS_SCRIPT_URL = import.meta.env.VITE_APPS_SCRIPT_URL;

/**
 * Session Manager
 */
export class SessionManager {
  private static SESSION_KEY = STORAGE_KEYS.SESSION;

  /**
   * Save session to localStorage
   */
  static saveSession(session: DeviceSession): void {
    try {
      const encrypted = this.encryptSession(session);
      localStorage.setItem(this.SESSION_KEY, encrypted);
    } catch (error) {
      console.error('Error saving session:', error);
    }
  }

  /**
   * Load session from localStorage
   */
  static loadSession(): DeviceSession | null {
    try {
      const encrypted = localStorage.getItem(this.SESSION_KEY);
      if (!encrypted) return null;

      return this.decryptSession(encrypted);
    } catch (error) {
      console.error('Error loading session:', error);
      return null;
    }
  }

  /**
   * Clear session
   */
  static clearSession(): void {
    localStorage.removeItem(this.SESSION_KEY);
    // Also clear any cached data
    Object.keys(localStorage).forEach(key => {
      if (key.startsWith(STORAGE_KEYS.CACHE_PREFIX)) {
        localStorage.removeItem(key);
      }
    });
  }

  /**
   * Check if session is valid
   */
  static hasValidSession(): boolean {
    const session = this.loadSession();
    return session !== null;
  }

  /**
   * Encrypt session data (basic implementation)
   */
  private static encryptSession(session: DeviceSession): string {
    // In production, use proper encryption (Web Crypto API)
    return btoa(JSON.stringify(session));
  }

  /**
   * Decrypt session data
   */
  private static decryptSession(encrypted: string): DeviceSession {
    return JSON.parse(atob(encrypted));
  }

  /**
   * Get current session
   */
  static getCurrentSession(): DeviceSession | null {
    return this.loadSession();
  }

  /**
   * Update session
   */
  static updateSession(updates: Partial<DeviceSession>): void {
    const session = this.loadSession();
    if (session) {
      const updated = { ...session, ...updates };
      this.saveSession(updated);
    }
  }
}

/**
 * Authenticate with Google OAuth (primary parent)
 */
export async function authenticateWithGoogle(googleToken: string): Promise<AuthResponse> {
  try {
    const response = await fetch(`${APPS_SCRIPT_URL}?path=auth&action=google`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        googleToken,
        deviceType: 'web',
      }),
    });

    if (!response.ok) {
      throw new Error(ERROR_MESSAGES.AUTH_FAILED);
    }

    const result: AuthResponse = await response.json();

    if (result.success && result.sessionData) {
      // Save session
      SessionManager.saveSession(result.sessionData);
    }

    return result;
  } catch (error) {
    console.error('Google auth error:', error);
    throw error;
  }
}

/**
 * Authenticate with QR code
 */
export async function authenticateWithQR(qrPayload: QRCodePayload): Promise<AuthResponse> {
  try {
    // Validate QR code format
    if (!qrPayload.familyId || !qrPayload.userId || !qrPayload.token) {
      throw new Error(ERROR_MESSAGES.QR_INVALID);
    }

    // Generate device ID
    const deviceId = getDeviceId();
    const deviceName = getDeviceName();

    const response = await fetch(`${APPS_SCRIPT_URL}?path=auth&action=qr`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        ...qrPayload,
        deviceId,
        deviceName,
        deviceType: 'web',
      }),
    });

    if (!response.ok) {
      const errorData = await response.json();
      if (errorData.reason === 'token_regenerated') {
        throw new Error(ERROR_MESSAGES.SESSION_EXPIRED);
      }
      throw new Error(ERROR_MESSAGES.QR_INVALID);
    }

    const result: AuthResponse = await response.json();

    if (result.success && result.sessionData) {
      // Save session
      SessionManager.saveSession(result.sessionData);
    }

    return result;
  } catch (error) {
    console.error('QR auth error:', error);
    throw error;
  }
}

/**
 * Validate current session with server
 */
export async function validateSession(): Promise<boolean> {
  try {
    const session = SessionManager.loadSession();
    if (!session) return false;

    const response = await fetch(
      `${APPS_SCRIPT_URL}?path=auth&action=validate&userId=${session.userId}&token=${session.authToken}&tokenVersion=${session.tokenVersion}`,
      {
        method: 'GET',
      }
    );

    if (!response.ok) return false;

    const result = await response.json();
    return result.valid === true;
  } catch (error) {
    console.error('Session validation error:', error);
    return false;
  }
}

/**
 * Logout
 */
export async function logout(): Promise<void> {
  const session = SessionManager.loadSession();
  
  if (session) {
    // Log the logout action (optional)
    try {
      await fetch(`${APPS_SCRIPT_URL}?path=auth&action=logout`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          userId: session.userId,
          deviceId: session.deviceId,
        }),
      });
    } catch (error) {
      console.error('Logout notification error:', error);
      // Continue with logout even if server notification fails
    }
  }

  // Clear local session
  SessionManager.clearSession();
}

/**
 * Parse QR code data
 */
export function parseQRCode(qrData: string): QRCodePayload | null {
  try {
    // Check if it's a chorequest URI
    if (!qrData.startsWith(QR_CODE.URI_SCHEME)) {
      return null;
    }

    // Extract payload from URI
    const url = new URL(qrData);
    const payloadParam = url.searchParams.get('payload');
    
    if (!payloadParam) {
      return null;
    }

    // Decode base64 payload
    const decoded = atob(payloadParam);
    const payload: QRCodePayload = JSON.parse(decoded);

    // Validate required fields
    if (!payload.familyId || !payload.userId || !payload.token) {
      return null;
    }

    return payload;
  } catch (error) {
    console.error('QR code parse error:', error);
    return null;
  }
}

/**
 * Get or create device ID
 */
function getDeviceId(): string {
  const DEVICE_ID_KEY = 'chorequest_device_id';
  let deviceId = localStorage.getItem(DEVICE_ID_KEY);
  
  if (!deviceId) {
    deviceId = crypto.randomUUID();
    localStorage.setItem(DEVICE_ID_KEY, deviceId);
  }
  
  return deviceId;
}

/**
 * Get device name
 */
function getDeviceName(): string {
  const userAgent = navigator.userAgent;
  
  // Simple device detection
  if (/Mobile/i.test(userAgent)) {
    if (/iPhone/i.test(userAgent)) return 'iPhone';
    if (/iPad/i.test(userAgent)) return 'iPad';
    if (/Android/i.test(userAgent)) return 'Android Device';
    return 'Mobile Device';
  }
  
  if (/Mac/i.test(userAgent)) return 'Mac';
  if (/Windows/i.test(userAgent)) return 'Windows PC';
  if (/Linux/i.test(userAgent)) return 'Linux PC';
  
  return 'Web Browser';
}

/**
 * Check if user is authenticated
 */
export function isAuthenticated(): boolean {
  return SessionManager.hasValidSession();
}

/**
 * Get current user info from session
 */
export function getCurrentUser() {
  const session = SessionManager.loadSession();
  if (!session) return null;

  return {
    userId: session.userId,
    userName: session.userName,
    userRole: session.userRole,
    familyId: session.familyId,
  };
}
