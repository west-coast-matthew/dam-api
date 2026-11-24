# init logic

**Status:** completed

## Summary


When the application is launched for the first time (i.e., no workspaces exist), we need to stage some initial data. 

Define a new service interface and an interface impl named AppInitializationService && AppInitializationServiceImpl that will perform these actions.

# Tasks
* Create a workspace named 'default'
* Create a child catalog named 'default catalog'
* Create a category named 'default category'
* Set default flags on those new entities as appropriate.
* Create new service and impl.
* Add a hook into the Spring startup lifecycle that will check for the presence of any workspaces, and if this is not the case, execute the logic in the new service.
