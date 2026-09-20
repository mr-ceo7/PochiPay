### **Instructions for NTFY5 Android Application Integration**

**Objective:**
This document outlines the API endpoints and communication protocols required for the NTFY5 Android application to synchronize with the backend server.

---

### **API Base URL**
All API endpoints are prefixed with `/ntfy/`. The full URL will be `https://mint-monitor.onrender.com/ntfy/`.

---

### **API Specification**
The server exposes four (4) RESTful API endpoints. All request and response bodies must be in JSON format.

#### **1. Update Check Endpoint**
This endpoint allows the client to check if a mandatory update is available.

*   **Method:** `POST`
*   **Path:** `/api/check-update`
*   **Request Body:**
    ```json
    {
      "version_code": 1
    }
    ```
*   **Success Response (Update Available):**
    ```json
    {
      "isUpdateAvailable": true,
      "newVersionName": "1.1.0",
      "downloadUrl": "https://mint-monitor.onrender.com/ntfy/update.apk"
    }
    ```
*   **Success Response (No Update):**
    ```json
    {
      "isUpdateAvailable": false
    }
    ```

---

#### **2. App ID Registration Endpoint**
This endpoint allows a new app instance to register a unique identifier. The client should generate a UUID and propose it to the server.

*   **Method:** `POST`
*   **Path:** `/api/register-id`
*   **Request Body:**
    ```json
    {
      "proposed_id": "a-unique-uuid-generated-by-the-client"
    }
    ```
*   **Success Response (ID Available):**
    The client can now use this ID.
    ```json
    {
      "status": "available"
    }
    ```
*   **Success Response (ID Taken):**
    The client must generate a new UUID and try again.
    ```json
    {
      "status": "taken"
    }
    ```

---

#### **3. Authorization Code Verification Endpoint**
This endpoint is used by an app instance to become authorized by submitting a valid authorization code.

*   **Method:** `POST`
*   **Path:** `/api/verify-auth-code`
*   **Request Body:**
    ```json
    {
      "app_id": "the-client's-confirmed-unique-id",
      "auth_code": "the-code-entered-by-the-user"
    }
    ```
*   **Success Response (Code is Valid):**
    The client is now authorized and should save the `app_id` and `auth_code` locally.
    ```json
    {
      "status": "allowed"
    }
    ```
*   **Failure Response (Code is Invalid):**
    The client should inform the user that the code is incorrect.
    ```json
    {
      "status": "denied"
    }
    ```

---

#### **4. Authorization Status Check Endpoint**
This endpoint allows an already authorized client to confirm its status on subsequent launches.

*   **Method:** `POST`
*   **Path:** `/api/check-auth-status`
*   **Request Body:**
    ```json
    {
      "app_id": "the-client's-saved-unique-id",
      "auth_code": "the-client's-saved-auth-code"
    }
    ```
*   **Success Response (Authorized):**
    The client can proceed with normal operation.
    ```json
    {
      "authorized": true
    }
    ```
*   **Success Response (Not Authorized):**
    The client should de-authorize itself, clear the locally saved `app_id` and `auth_code`, and prompt the user for a new authorization code. This can happen if the authorization code has been revoked on the server.
    ```json
    {
      "authorized": false
    }
    ```
