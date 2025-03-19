Automated Test Generator for java projects (WIP)
-

At the moment this project can generate simple unit tests for most methods and it can also generate API tests using RESTAssured for simple end points. 
The end goal is to be able to generate unit tests for full coverage and API tests for all end points in a restfull web application.

At the moment only maven projects are supported but gradle projects will be added soon.

The expression evaluation engine is used in the test generation project and it relies heavily on reflection. So don't forget to add the following VM argument
     `--add-opens java.base/java.util.stream=ALL-UNNAMED`
