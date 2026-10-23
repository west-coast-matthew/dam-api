# Digital Asset Management (API)

## Background

This application is intended to provide an API to an Digital Asset Management solution. Esssentially we are supporting the ability for users to store, label & categorize, and access digital media. This includes images, PDFs, and video. 


## Media storage

By default, assets will be stored on a file system. An attempt has been made to decouple the application from a specific storage location in order to support potentially storing media in a database or a remote location such as Amazon S3 buckets.

Metadata is stored in a relational database irregardless of the physical location where the assets are stored.

## Core concepts

### Workspaces
The highest level organizational unit. In most cases it is assumed you will have only a single workspace, however it is possible that an large organization would have several. 

Workspaces contain one or more catalogs.

### Catalogs

### Collections

### Categories