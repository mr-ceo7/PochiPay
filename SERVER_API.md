# Server API Reference

This document outlines the API endpoints the app communicates with and the expected JSON responses from the server.

## 1. Register Application ID

This endpoint is used to check if a proposed ID for the application is available or has already been taken.

*   **Endpoint:** `POST /api/register-id`
*   **Request Body:**
    ```json
    {
      "proposed_id": "some-unique-id"
    }
    ```
*   **Expected Server Response:**
    The server should respond with a JSON object containing a `status` field.
    ```json
    {
      "status": "available"
    }
    ```
    *   **`status` (String):** Can be either `"available"` or `"taken"`.

## 2. Verify Authentication Code

This endpoint sends the app's ID and an authentication code to the server to verify it.

*   **Endpoint:** `POST /api/verify-auth-code`
*   **Request Body:**
    ```json
    {
      "app_id": "the-app-id",
      "auth_code": "the-auth-code"
    }
    ```
*   **Expected Server Response:**
    The server returns a status indicating if the code was accepted.
    ```json
    {
      "status": "allowed"
    }
    ```
    *   **`status` (String):** Can be either `"allowed"` or `"denied"`.

## 3. Check Authentication Status

This is used to check if the app's current ID and auth code are still considered valid by the server.

*   **Endpoint:** `POST /api/check-auth-status`
*   **Request Body:**
    ```json
    {
      "app_id": "the-app-id",
      "auth_code": "the-auth-code"
    }
    ```
*   **Expected Server Response:**
    The server returns a boolean indicating the authorization status.
    ```json
    {
      "authorized": true
    }
    ```
    *   **`authorized` (Boolean):** `true` if the app is authorized, `false` otherwise.

## 4. Check for Application Updates

This endpoint allows the app to check if a newer version is available for download.

*   **Endpoint:** `POST /api/check-update`
*   **Request Body:**
    ```json
    {
      "version_code": 101
    }
    ```
*   **Expected Server Response:**
    The server responds with details about the available update. If no update is available, `isUpdateAvailable` will be `false`.
    ```json
    {
      "isUpdateAvailable": true,
      "newVersionName": "1.2.0",
      "downloadUrl": "https://example.com/app/new_version.apk",
      "fileSize": 5242880,
      "md5Checksum": "a1b2c3d4e5f6..."
    }
    ```
    *   `isUpdateAvailable` (Boolean): `true` if an update exists.
    *   `newVersionName` (String, optional): The user-friendly version name (e.g., "1.2.0").
    *   `downloadUrl` (String, optional): The URL to download the new APK.
    *   `fileSize` (Long, optional): The size of the update file in bytes.
    *   `md5Checksum` (String, optional): An MD5 hash of the file for integrity verification.

## 5. Server Health Check

This is a special endpoint used to see if the server is online and responding. It does not expect a JSON body in the response.

*   **Endpoint:** `GET /sync/health/`
*   **Request Body:** None
*   **Expected Server Response:** A successful HTTP status code (e.g., `200 OK`). The body of the response is not used by the app.

## 6. Payment Verification API (Bridge)

Allows developers to verify manual customer payments against genuine Safaricom M-PESA confirmation SMS messages stored in the app database.

*   **Socket.io Event (Server -> App):** `request_verification`
*   **REST Polling Endpoint:** `GET /api/pending-verifications/`
    *   **Response:**
        ```json
        {
          "pending": [
            {
              "id": "trans-123",
              "code": "TK12345678",
              "amount": 250,
              "date": "2026-09-20"
            }
          ]
        }
        ```
*   **REST Result Callback:** `POST /api/verify-result/`
    *   **Request Body:**
        ```json
        {
          "transactionId": "trans-123",
          "isValid": true,
          "metadata": { "worker": "socket_push" }
        }
        ```
    *   **Response:**
        ```json
        {
          "success": true
        }
        ```

## 7. Business STK Push API (PochiPay Automation)

Allows central servers and developers to initiate an on-device STK push via M-PESA for Business UI automation.

*   **Socket.io Event (Server -> App):** `request_stk_push`
    *   **Payload:**
        ```json
        {
          "requestId": "stk-req-9901",
          "phone": "0746957502",
          "amount": 10.0,
          "reference": "Order #542"
        }
        ```
*   **Socket.io Result Event (App -> Server):** `stk_push_result`
    *   **Payload:**
        ```json
        {
          "requestId": "stk-req-9901",
          "success": true,
          "customerName": "HALIMAH HASSAN",
          "errorMessage": "",
          "reference": "Order #542"
        }
        ```
*   **REST Polling Fallback:** `GET /api/pending-stk/`
    *   **Response:**
        ```json
        {
          "pending": [
            {
              "id": "stk-req-9901",
              "phone": "0746957502",
              "amount": 10.0,
              "reference": "Order #542"
            }
          ]
        }
        ```
*   **REST Result Callback:** `POST /api/stk-result/`
    *   **Request Body:**
        ```json
        {
          "requestId": "stk-req-9901",
          "success": true,
          "customerName": "HALIMAH HASSAN",
          "errorMessage": null
        }
        ```

