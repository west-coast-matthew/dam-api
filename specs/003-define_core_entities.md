# define core entities

**Status:** ready

## Goal

We need to define the jpa persistent entities for the application.

upon a new instance of the application, we will need to perform the following:
* define an initial workspace and corresponding catalog


In the event that the user elects for a default setup, we need to do the following.
* create a new workspace named 'Default Workspace'
* create a new catalog under that default workspace named 'Default catalog'

## Requirements
* Create a new ./core package under the 'entities' package. all entities defined with the scope of this task should reside there
* the file 'dam_jpa_entities.java' contains all the entites, however in one file. parse that content, create the individual separate files under the target directory.
* create repo objects to support persistence operations
* create a new service responsible for initializing a new instance of an application. call it WorkspaceInializationService
* add a method called initializeDefaultWorkspace, have it perform initial setup with defaults
* add an additional method called setupWorkspace that accepts a workspace name and catalog, and performs the creation of the initial entities
* add description attributes to the workspace and catalog entities. ensure validation is performed, ie names must be present. 
* add required and unique flags to the workspace and catalog name attribute
