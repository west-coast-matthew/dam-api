# Digital Asset Management (API)

## Background:

This project will be a Java 21 spring based project establishing REST based APIs that expose an Digital Asset Management project.

## Tasks

1) Initialize project

2) Create new skills
We need the ability to define tasks under the ./specs folder. Each file will use a numerical naming convention as a prefix, followed by a dash. All file names will be lower case and utilize underscores rather than spaces. Each spec will have a status, by default they will be 'ready' where they have not been applied, 'completed' if they are ran, and possibly 'cancelled' if the spec will not be executed.

Example:
001-do_something.md
002-and_do_something_else.md

Skills

* `create-spec` given a string as an argument, create a new file under the skills directory, the next sequential number will be assigned as a prefix, the supplied name will be converted to lowercase, spaces replaced, and an '.md' suffix appended. The name of the new spec will be appended as a title in the new file. should a name not be provided, prompt for it as it is required. I have the 'macdown' app installed localy. after the spec is created, we need to launch macdown to display the new spec. creating a new spec should create a new git branch named after the spec file including the numerical prefix and description. we will need to switch from that current working branch to the new feature specific branch.
* `list-specs` lists all specs in the folder, along with the status. The first spec will be highlighted, the user will be allowed to scroll up and down, and upon hitting the enter key, you will executed the selected spec. should the user scroll up past the initial position, then the selection will jump to the bottom. should they scroll past the last element, the selection will jump to the first item.
* `cancel-spec` accepts a number and sets the status to 'cancelled'
* `run spec` accepts a number as an argument, for which the spec is read and executed.

3) Update the readme
Update the readme markdown file with directions on how to run the new skills we have defined here

4) Initialize project
* Initialize the Spring Boot project using maven. The base package name will be 'com.twofourseven.dam'.
* Create all subfolders for this three tiered application such as 'controllers', 'entities', 'vo', etc.
* create a global exception handler intended to handle all exceptions and return the appropriate response codes.
* add dependencies for mockito so that we may mock calls between the tiers. unit tests should not be dependent on the persistent store
* add dependencies for unit testing
* add dependency for spring data and mariadb
* we will be performing image operations, we can assume all command line tools required are installed and available on the current environment.

5) establish a health check api endpoint
this will be used to provide visibility into the running instance. the url will be /hearbeat, and initially will return success or failure for the database connection.

6) application properties
create a sample application properties file that will be used to provide credentials to the spring runtime. initially we will need to put in database credentials

7) convenience scripts
create the following scripts under the bin directory
* `deploy` performs a build of the app and launch the executeable jar. the command should use the location /etc/app-config/dam/application.properties for spring properties.

8) logging
Add support for logging, for now this should be set to debug and console output.
