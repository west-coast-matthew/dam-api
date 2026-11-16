# initial user security

**Status:** ready

## Goal


we need to introduce users, and a security model. assitionally, we will break from the assumption that all users will belong to the same organization. while that would be the case in instances where a local on premise or dedicated cloud instance was in play, that would not be the case for commericial saas offerings. the scope of this effort is to introduce persistent entities, servvices, and supporting apis.

entities:
organization
	a top level grouping, representing a customer in saas deployments.
UserAccount
	an individual user account as the name implies. user name, full name, password, and password hint would be attributes. we also need an active flag.	
UserRole
	support for roles such as READ_ONLY, ADMIN, EDIT. we need to establish an enum to represent these roles as they dont change. the pk of the entity should be one of these enum values.

(additional entities)
	we need additional entity/entities to restrict access between users and organizations (a user can only belong to a single organizattion), and support for restricting access at the catalog and workspace level.

	we additionally need to model the ability to have admins assigned at the ogranization level, where they have complete control over everything within the organization, and the ability for admins with organizational control to assign admin privelidges to users at the catalog and workspace level, essentiially for large organizations we would support the ability for higher level admins to delegate admin authority to other users at a more granular level. 

tasks:
create persistent entities for all of the above entities. include descriptions in the header comments for additional context.
create rest endpoints, services, and repo objects to support crud management of the new entities 
add additional entities required for support of the new security functions
add a service that will be used during the initial setup process that will perform the following:
	accept a information for a super admin with top level access and the ability to manage organizations. this wouold be the equivelent of root on a unix system.
	accept organization information and create an initial organization
	accept workspace and catalog minimal information, or create an 'default' for each entity, then persist it. at this point there will be no api to call this new method. call the service ApplicationAdminService and the new method 'initializeProduct' 
	a new category named 'Unsorted' will need to be created. this is where new assets with no assigned categories will be associated, essentially a default bucket

