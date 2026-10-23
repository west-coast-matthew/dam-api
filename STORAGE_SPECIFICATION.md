# Technical Specification: File-Based Persistence Strategy
**Project:** Digital Asset Management (DAM) Solution  
**Component:** File Storage Service & Disk Persistence  
**Target Stack:** Java 17+, Spring Boot 3+  

---

## 1. Architectural Overview

The DAM system uses a **decoupled storage architecture**:
* **Relational Database (PostgreSQL/MySQL):** Stores all metadata, enterprise hierarchy relationships (Workspaces, Catalogs, Categories, Tags), access controls, and **relative storage paths**.
* **Local Disk File System:** Stores raw binary files, generated thumbnails, and version files, completely isolated from category structures to prevent expensive I/O operations during folder reorganizations.

---

## 2. Directory Structure Hierarchy

Files must be organized on disk using a multi-tenant, container-aware tree structure. This ensures absolute isolation between Workspaces and Catalogs, while avoiding operating system performance bottlenecks caused by flat directories containing too many files.

### Physical Disk Layout
```text
${dam.storage.root-dir}/
 └── workspaces/
      ├── {workspace-uuid}/
      │    └── catalogs/
      │         └── {catalog-uuid}/
      │              └── assets/
      │                   └── {sharded-hash-path}/
      │                        └── {asset-uuid}/
      │                             ├── original.{ext}
      │                             └── thumbnail.jpg
```

---

## 3. Sharding & Naming Conventions

To prevent performance degradation (inode limits and directory lookup overhead), assets must be sharded using a **hashed directory splitting strategy** derived from the Asset's UUID.

### Sharding Rule (3-Tier Hex Split)
1. Generate a UUIDv4 (or UUIDv7) for the Asset: `550e8400-e29b-41d4-a716-446655440000`
2. Extract the first 6 characters to create a 3-level directory tree (2 characters per level):
   * Level 1: `55`
   * Level 2: `0e`
   * Level 3: `84`
3. Resulting sub-path: `55/0e/84/{asset-uuid}/`

### Internal Asset Folder Naming Convention
Inside each individual asset folder, standardize filenames regardless of the user's uploaded name to simplify retrieval and processing:
* `original.{ext}` — The source file matching its original file extension (e.g., `.png`, `.mp4`, `.pdf`).
* `thumbnail.jpg` — A standardized, compressed JPEG preview generated asynchronously upon upload.
* *(Optional)* `version_{n}.{ext}` — Used if asset versioning is invoked for updates.

---

## 4. Database Path Contract

* **Rule:** The database table (`assets` and `asset_versions`) must **never** store absolute server paths (e.g., `/var/data/dam/...` or `C:/...`). 
* **Stored Value:** Store only the **relative path** from the workspace root or catalog root.
* **Example Stored Value in `storage_path` column:**
  `catalogs/{catalog-uuid}/assets/55/0e/84/{asset-uuid}/original.png`

---

## 5. Expected `StorageService` Behavior & Guidelines for Implementation

When implementing the `FileSystemStorageService` in Spring Boot, ensure the following requirements are met:

1. **Configuration Property:**
   Read the root storage directory from `application.yml`:
   ```yaml
   dam:
     storage:
       root-dir: /var/data/dam/workspaces
   ```

2. **Automatic Directory Creation:**
   Before writing any file or thumbnail, ensure all parent directories (`workspace`, `catalog`, and the hashed sub-path) are automatically created using `Files.createDirectories()`.

3. **Path Traversal Security:**
   Validate all file retrieval requests to prevent path traversal attacks (e.g., ensuring resolved paths stay strictly within the designated storage root using `.normalize()` and `.startsWith()`).

4. **Asynchronous Handlers:**
   File writing, multi-part stream handling, and thumbnail generation should be decoupled from the core HTTP request thread (or handled cleanly in a transactional service boundary) so that `PENDING_PROCESSING` status is correctly transitioned to `READY` once writing is verified.