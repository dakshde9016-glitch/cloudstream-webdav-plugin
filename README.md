# Cloudstream WebDAV Provider

[![Cloudstream Plugin](https://img.shields.io/badge/Cloudstream-Plugin-blue?style=flat-square&logo=android)](https://github.com/recloudstream/cloudstream)
[![Build Status](https://img.shields.io/github/actions/workflow/status/dakshde9016-glitch/cloudstream-webdav-plugin/build.yml?branch=main&style=flat-square&label=Build)](https://github.com/dakshde9016-glitch/cloudstream-webdav-plugin/actions)
[![Language](https://img.shields.io/badge/Language-Kotlin-purple?style=flat-square&logo=kotlin)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-GPLv3-green?style=flat-square)](LICENSE)

A provider extension for [Cloudstream](https://github.com/recloudstream/cloudstream) designed to stream media and browse file trees directly from any standard WebDAV or WebDAVS server.

---

### Optimized for Google Drive WebDAV Workers

Works for all kinds of WebDAV protocols but it's a powerful tool if used with an existing project.
This provider was developed specifically for use alongside **[ixiumu/google-drive-webdav-workers](https://github.com/ixiumu/google-drive-webdav-workers)**. 

Deploying that Cloudflare Worker provides a serverless WebDAV gateway directly into your personal Google Drive storage. Connecting this provider to the worker turns Cloudstream into a high-speed personal media streamer with fast directory traversal, rapid seek response, and zero maintenance overhead.
It supports KV caching which caches your GDrive directory on the edge reducing loading times.

---

### Features

* **Broad Protocol Support:** Compatible with any standard RFC 4918 WebDAV endpoint, including:
  * Google Drive via Cloudflare Workers
  * Local instances via `rclone serve webdav`
  * Nextcloud / ownCloud
  * AList, Apache, or Nginx WebDAV modules
* **HTTP & HTTPS Compatibility:** Supports both encrypted `https://` endpoints and local unencrypted `http://` network paths.
* **HTTP Basic Authentication:** Fully supports credentials protection (username and password).

---

### Installation

####  Direct Raw GitHub URL
1. Open Cloudstream $\rightarrow$ navigate to **Settings** $\rightarrow$ **Extensions**.
2. Tap **Add Repository**.
3. Enter any repository name (e.g., `WebDAV`).
4. Paste this exact raw GitHub URL into the repository link field:
   ```text
   [https://raw.githubusercontent.com/dakshde9016-glitch/cloudstream-webdav-plugin/builds/repo.json](https://raw.githubusercontent.com/dakshde9016-glitch/cloudstream-webdav-plugin/builds/repo.json)
5. Tap Add Repository.

### Setup & Configuration

In Cloudstream, open Settings \rightarrow Extensions and select your newly added WebDAV repository.
Locate the WebDAV plugin and tap Download / Install.
Once the installation completes, tap Configure Plugin (or tap the plugin entry \rightarrow settings icon).
Enter your WebDAV server details:
Server URL: Your full WebDAV endpoint URL (e.g., https://my-worker.workers.dev or http://192.168.1.50:8080).
Username: Your WebDAV account username (leave blank if unauthenticated).
Password: Your WebDAV password or access token.
Root Path: The base folder containing your media files (e.g., /Media or /Movies).
Save your settings. Your files will now populate under Cloudstream's browse and search views.   
